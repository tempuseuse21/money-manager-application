package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = FinancialRepository(db)

    // User Session Fields
    private val _currentUser = MutableStateFlow<String?>(null)
    val currentUser: StateFlow<String?> = _currentUser.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _isFirebaseConnected = MutableStateFlow(false)
    val isFirebaseConnected: StateFlow<Boolean> = _isFirebaseConnected.asStateFlow()

    // Firestore listeners
    private var expensesListener: ListenerRegistration? = null
    private var categoriesListener: ListenerRegistration? = null

    // Current State
    val monthsList: StateFlow<List<FinancialMonth>> = repository.allMonths
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeMonthId = MutableStateFlow<String?>("2026-05")
    val activeMonthId: StateFlow<String?> = _activeMonthId.asStateFlow()

    // Dynamically retrieve active month
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeMonth: StateFlow<FinancialMonth?> = _activeMonthId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repository.allMonths.map { list -> list.find { it.id == id } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Query filters
    val searchQuery = MutableStateFlow("")
    val filterType = MutableStateFlow("ALL") // ALL, INCOME, EXPENSE
    val filterCategory = MutableStateFlow("ALL")

    // Active month transaction list
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val rawMonthlyEntries: StateFlow<List<TransactionEntry>> = _activeMonthId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getEntriesForMonth(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered month transaction entries (for Search and Spreadsheet view toggles)
    val filteredMonthlyEntries: StateFlow<List<TransactionEntry>> = combine(
        rawMonthlyEntries,
        searchQuery,
        filterType,
        filterCategory
    ) { list, query, type, cat ->
        list.filter { tx ->
            val matchesQuery = query.isEmpty() ||
                    tx.notes.contains(query, ignoreCase = true) ||
                    tx.category.contains(query, ignoreCase = true) ||
                    tx.day.toString() == query

            val matchesType = type == "ALL" || tx.type == type
            val matchesCategory = cat == "ALL" || tx.category == cat

            matchesQuery && matchesType && matchesCategory
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All categories
    val categoriesList: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Settings Configuration
    private val _currencySymbol = MutableStateFlow("₹")
    val currencySymbol: StateFlow<String> = _currencySymbol.asStateFlow()

    private val _themeMode = MutableStateFlow("SYSTEM") // SYSTEM, LIGHT, DARK
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    init {
        // Programmatically initialize Firebase if configuration exists
        val fbInit = FirebaseManager.init(application)
        _isFirebaseConnected.value = fbInit

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Ensure default settings exist in DB
                if (repository.getSetting("currency") == null) {
                    repository.saveSetting("currency", "₹")
                }
                if (repository.getSetting("theme_mode") == null) {
                    repository.saveSetting("theme_mode", "SYSTEM")
                }

                // Check for existing user session
                val cachedUserSetting = repository.getSetting("session_user")
                val cachedUser = cachedUserSetting?.value
                if (!cachedUser.isNullOrBlank()) {
                    _currentUser.value = cachedUser
                    withContext(Dispatchers.Main) {
                        setupFirestoreSync(cachedUser)
                    }
                } else {
                    // Do not load data before login
                    repository.clearAllData()
                }
            } catch (e: Exception) {
                Log.e("FinanceViewModel", "Init error", e)
            }
        }

        // Load settings reactively
        viewModelScope.launch {
            repository.allSettings.collect { settings ->
                settings.find { it.key == "currency" }?.let {
                    _currencySymbol.value = it.value
                }
                settings.find { it.key == "theme_mode" }?.let {
                    _themeMode.value = it.value
                }
            }
        }

        // Auto-select first available budget month if current selection is invalid or null
        viewModelScope.launch {
            monthsList.collect { list ->
                if (list.isNotEmpty()) {
                    val currentId = _activeMonthId.value
                    if (currentId == null || !list.any { it.id == currentId }) {
                        _activeMonthId.value = list.first().id
                    }
                }
            }
        }
    }

    // Interactive Heuristic-based Savings Tips
    val smartSavingsTips: StateFlow<List<String>> = rawMonthlyEntries
        .map { entries ->
            val tips = mutableListOf<String>()
            val totalIncome = entries.filter { it.type == "INCOME" }.sumOf { it.amount }
            val totalExpense = entries.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            val netSavings = totalIncome - totalExpense

            if (entries.isEmpty()) {
                tips.add("Welcome to your Smart Money Dashboard! Secure your goals by logging daily transactions.")
                tips.add("Establishing a reasonable budget monthly stops discretionary overhead before it happens.")
                return@map tips
            }

            if (totalIncome > 0) {
                val savingsRate = (netSavings / totalIncome) * 100
                if (savingsRate > 30) {
                    tips.add(String.format("Outstanding! You safely saved %.1f%% of your income this month. Keep inflating your investments! 📈", savingsRate))
                } else if (savingsRate in 0.0..15.0) {
                    tips.add(String.format("Your savings cushion is slim (%.1f%%). Scan for discretionary leaking in 'Shopping' or 'Bills'.", savingsRate))
                } else if (savingsRate < 0) {
                    tips.add("Critical Status: Outbound purchases surpass monthly income. Enforce strict budget controls! ⚠️")
                }
            } else {
                tips.add("Recording a robust stream of income will unlock premium budget indicators.")
            }

            val expensesByCategory = entries.filter { it.type == "EXPENSE" }
                .groupBy { it.category }
                .mapValues { it.value.sumOf { tx -> tx.amount } }

            val largestCategory = expensesByCategory.maxByOrNull { it.value }
            if (largestCategory != null && largestCategory.value > 0) {
                tips.add("Category Alert: Outbound spending peaked in '${largestCategory.key}', accumulating ${currencySymbol.value}${String.format("%.2f", largestCategory.value)}.")
                if (largestCategory.key.equals("Food", ignoreCase = true) && largestCategory.value > 3000) {
                    tips.add("Grocery Insight: Committing to home food preparation can trim monthly expenses considerably.")
                } else if (largestCategory.key.equals("Shopping", ignoreCase = true)) {
                    tips.add("Saving Anchor: Keep elements in purchase shopping carts for at least 48 hours before completion.")
                }
            }

            val maxDayExpenseGroup = entries.filter { it.type == "EXPENSE" }
                .groupBy { it.day }
                .mapValues { it.value.sumOf { tx -> tx.amount } }
                .maxByOrNull { it.value }

            if (maxDayExpenseGroup != null && maxDayExpenseGroup.value > 5000) {
                tips.add(String.format("Extreme Day Outbound: Day %d was your peak cost session with %s%.2f.",
                    maxDayExpenseGroup.key, currencySymbol.value, maxDayExpenseGroup.value))
            }

            if (tips.size < 3) {
                tips.add("Daily routine audits can trim recurring subscriptions or passive membership outflows.")
                tips.add("Registering operations right when they happen leads to bulletproof financial records.")
            }

            tips
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("Formulating insights..."))

    // ==========================================
    // USER SESSIONS & LOGIN FLOW
    // ==========================================

    fun login(username: String, pin: String, onSuccess: () -> Unit) {
        println("viewmodel login() called. username: '$username', pin: '$pin'")
        _loginError.value = null
        if (pin.isBlank()) {
            _loginError.value = "PIN cannot be empty."
            return
        }

        val expectedPin = if (username.lowercase() == "bhavesh") "1004" else "7542"
        if (pin == expectedPin) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    _currentUser.value = username
                    repository.saveSetting("session_user", username)

                    // Wipe any residual database entries to avoid data bleed before pulling the new user's profiles
                    repository.clearAllData()

                    // Seeding default categories
                    seedDefaultCategories()

                    withContext(Dispatchers.Main) {
                        setupFirestoreSync(username)
                        onSuccess()
                    }
                } catch (e: Exception) {
                    println("EXCEPTION INDEED IN LOGIN COROUTINE: " + e.message)
                    e.printStackTrace()
                    _loginError.value = "Internal initialization error: ${e.message}"
                }
            }
        } else {
            _loginError.value = "Incorrect PIN. Please check your credentials."
        }
    }

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            clearFirestoreListeners()
            repository.clearAllData()
            repository.saveSetting("session_user", "")
            _currentUser.value = null
            _activeMonthId.value = null
        }
    }

    private suspend fun seedDefaultCategories() {
        val categories = repository.getAllCategoriesDirect()
        if (categories.isEmpty()) {
            val defaults = listOf(
                Category(name = "Food", colorHex = "#FF7043", isSystem = false),
                Category(name = "Travel", colorHex = "#29B6F6", isSystem = false),
                Category(name = "Bills", colorHex = "#AB47BC", isSystem = false),
                Category(name = "Shopping", colorHex = "#26A69A", isSystem = false),
                Category(name = "Other", colorHex = "#78909C", isSystem = false)
            )
            for (cat in defaults) {
                repository.insertCategory(cat)
            }
        }
    }

    private suspend fun seedOfflineStarterData() {
        val months = repository.getAllMonthsDirect()
        if (months.isEmpty()) {
            val defaultMonth = FinancialMonth(
                id = "2026-05",
                year = 2026,
                month = 5,
                displayLabel = "May 2026",
                budgetLimit = 25000.0
            )
            repository.insertMonth(defaultMonth)

            val starters = listOf(
                TransactionEntry(monthId = "2026-05", day = 1, type = "INCOME", amount = 60000.0, category = "Other", notes = "Monthly salary compensation credit"),
                TransactionEntry(monthId = "2026-05", day = 3, type = "EXPENSE", amount = 1500.0, category = "Food", notes = "Supermarket fresh groceries"),
                TransactionEntry(monthId = "2026-05", day = 5, type = "EXPENSE", amount = 500.0, category = "Travel", notes = "Commute transit refueling"),
                TransactionEntry(monthId = "2026-05", day = 10, type = "EXPENSE", amount = 3000.0, category = "Bills", notes = "High-speed broadband electricity balance")
            )
            for (entry in starters) {
                repository.insertEntry(entry)
            }
        }
    }

    // ==========================================
    // FIRESTORE SYNCHRONIZER
    // ==========================================

    private fun clearFirestoreListeners() {
        expensesListener?.remove()
        expensesListener = null
        categoriesListener?.remove()
        categoriesListener = null
    }

    private fun setupFirestoreSync(userId: String) {
        clearFirestoreListeners()

        val checkInit = FirebaseManager.init(getApplication())
        _isFirebaseConnected.value = checkInit

        if (!checkInit) {
            // Firebase credentials are not compiled in yet, work in offline/Room mode safely!
            viewModelScope.launch(Dispatchers.IO) {
                seedOfflineStarterData()
            }
            return
        }

        val firestore = FirebaseManager.firestore ?: return

        // Categories Snapshot Sync
        categoriesListener = firestore.collection("users").document(userId).collection("categories")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreSync", "Categories subscription failed: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val fsCats = snapshot.documents.mapNotNull { doc ->
                                val idVal = doc.id.toIntOrNull() ?: return@mapNotNull null
                                Category(
                                    id = idVal,
                                    name = doc.getString("name") ?: "",
                                    colorHex = doc.getString("colorHex") ?: "#78909C",
                                    isSystem = doc.getBoolean("isSystem") ?: false
                                )
                            }

                            // Sync to local DB
                            for (cat in fsCats) {
                                repository.insertCategory(cat)
                            }

                            // Remove categories deleted remotely
                            val localCats = repository.getAllCategoriesDirect()
                            for (lc in localCats) {
                                if (!fsCats.any { it.id == lc.id }) {
                                    repository.deleteCategory(lc)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("FirestoreSync", "Error syncing remote categories: ${e.message}", e)
                        }
                    }
                }
            }

        // Expenses Snapshot Sync
        expensesListener = firestore.collection("users").document(userId).collection("expenses")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreSync", "Expenses subscription failed: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val fsEntries = snapshot.documents.mapNotNull { doc ->
                                val idVal = doc.id.toIntOrNull() ?: return@mapNotNull null
                                TransactionEntry(
                                    id = idVal,
                                    monthId = doc.getString("monthId") ?: "2026-05",
                                    day = (doc.getLong("day") ?: 1).toInt(),
                                    type = doc.getString("type") ?: "EXPENSE",
                                    amount = doc.getDouble("amount") ?: 0.0,
                                    category = doc.getString("category") ?: "Other",
                                    notes = doc.getString("notes") ?: "",
                                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                                )
                            }

                            // Ensure month structures exist locally
                            for (entry in fsEntries) {
                                val targetId = entry.monthId
                                val parts = targetId.split("-")
                                val yr = parts.getOrNull(0)?.toIntOrNull() ?: 2026
                                val mn = (parts.getOrNull(1)?.toIntOrNull() ?: 5).coerceIn(1, 12)
                                val sanitizedMonthId = String.format(Locale.US, "%04d-%02d", yr, mn)

                                val exists = repository.getAllMonthsDirect().any { it.id == sanitizedMonthId }
                                if (!exists) {
                                    val shorts = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                                    val lbl = "${shorts[mn - 1]} $yr"
                                    repository.insertMonth(FinancialMonth(sanitizedMonthId, yr, mn, lbl))
                                }

                                val sanitizedEntry = if (entry.monthId != sanitizedMonthId) {
                                    entry.copy(monthId = sanitizedMonthId)
                                } else {
                                    entry
                                }
                                repository.insertEntry(sanitizedEntry)
                            }

                            // Delete local details removed remotely
                            val dbEntries = repository.getAllEntriesDirect()
                            for (le in dbEntries) {
                                if (!fsEntries.any { it.id == le.id }) {
                                    repository.deleteEntry(le)
                                }
                            }

                            // Auto-refresh active selection on first sync loading
                            val currentList = repository.getAllMonthsDirect()
                            if (currentList.isNotEmpty() && (_activeMonthId.value == null || !currentList.any { it.id == _activeMonthId.value })) {
                                _activeMonthId.value = currentList.first().id
                            }
                        } catch (e: Exception) {
                            Log.e("FirestoreSync", "Error syncing remote expenses: ${e.message}", e)
                        }
                    }
                }
            }
    }

    // Set Active Month
    fun setActiveMonth(monthId: String) {
        _activeMonthId.value = monthId
    }

    // CRUD Months
    fun createMonth(year: Int, month: Int, budgetLimit: Double) {
        val sanitizedMonth = month.coerceIn(1, 12)
        viewModelScope.launch(Dispatchers.IO) {
            val monthsShortNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val displayLabel = "${monthsShortNames[sanitizedMonth - 1]} $year"
            val monthId = String.format(Locale.US, "%04d-%02d", year, sanitizedMonth)
            val newMonth = FinancialMonth(
                id = monthId,
                year = year,
                month = sanitizedMonth,
                displayLabel = displayLabel,
                budgetLimit = budgetLimit
            )
            repository.insertMonth(newMonth)
            _activeMonthId.value = monthId
        }
    }

    fun deleteActiveMonth() {
        val currentId = _activeMonthId.value
        if (currentId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                // Remove local entries first
                val targetEntries = repository.getEntriesForMonthDirect(currentId)
                
                // If firestore is available, clear remote records
                val firestore = FirebaseManager.firestore
                val user = currentUser.value
                if (firestore != null && user != null) {
                    for (tx in targetEntries) {
                        firestore.collection("users").document(user)
                            .collection("expenses").document(tx.id.toString()).delete()
                    }
                }

                activeMonth.value?.let { month ->
                    repository.deleteMonth(month)
                    val remains = monthsList.value.filter { it.id != currentId }
                    if (remains.isNotEmpty()) {
                        _activeMonthId.value = remains.first().id
                    } else {
                        _activeMonthId.value = null
                    }
                }
            }
        }
    }

    fun updateMonthBudget(budgetLimit: Double) {
        val currentMonth = activeMonth.value
        if (currentMonth != null) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateMonth(currentMonth.copy(budgetLimit = budgetLimit))
            }
        }
    }

    // ==========================================
    // CRUDS FOR TRANSACTIONS & CATEGORIES WITH REMOTE FIRESTORE MIRRORING
    // ==========================================

    fun addTransaction(day: Int, type: String, amount: Double, category: String, notes: String) {
        val mId = _activeMonthId.value ?: return
        val user = _currentUser.value
        viewModelScope.launch(Dispatchers.IO) {
            val tx = TransactionEntry(
                monthId = mId,
                day = day,
                type = type,
                amount = amount,
                category = category,
                notes = notes
            )
            val assignedId = repository.insertEntry(tx)

            // Sync to firestore
            val firestore = FirebaseManager.firestore
            if (firestore != null && user != null) {
                val data = hashMapOf(
                    "monthId" to mId,
                    "day" to day,
                    "type" to type,
                    "amount" to amount,
                    "category" to category,
                    "notes" to notes,
                    "timestamp" to System.currentTimeMillis()
                )
                firestore.collection("users").document(user)
                    .collection("expenses").document(assignedId.toString()).set(data)
                    .addOnFailureListener { e ->
                        Log.e("FirebaseSync", "Add expense sync failed: ${e.message}")
                    }
            }
        }
    }

    fun updateTransaction(id: Int, day: Int, type: String, amount: Double, category: String, notes: String) {
        val mId = _activeMonthId.value ?: return
        val user = _currentUser.value
        viewModelScope.launch(Dispatchers.IO) {
            val tx = TransactionEntry(
                id = id,
                monthId = mId,
                day = day,
                type = type,
                amount = amount,
                category = category,
                notes = notes
            )
            repository.updateEntry(tx)

            // Sycn to firestore
            val firestore = FirebaseManager.firestore
            if (firestore != null && user != null) {
                val data = hashMapOf(
                    "monthId" to mId,
                    "day" to day,
                    "type" to type,
                    "amount" to amount,
                    "category" to category,
                    "notes" to notes,
                    "timestamp" to System.currentTimeMillis()
                )
                firestore.collection("users").document(user)
                    .collection("expenses").document(id.toString()).set(data)
                    .addOnFailureListener { e ->
                        Log.e("FirebaseSync", "Update expense sync failed: ${e.message}")
                    }
            }
        }
    }

    fun deleteTransaction(tx: TransactionEntry) {
        val user = _currentUser.value
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteEntry(tx)

            // Sync deleted on firestore
            val firestore = FirebaseManager.firestore
            if (firestore != null && user != null) {
                firestore.collection("users").document(user)
                    .collection("expenses").document(tx.id.toString()).delete()
                    .addOnFailureListener { e ->
                        Log.e("FirebaseSync", "Delete expense sync failed: ${e.message}")
                    }
            }
        }
    }

    // CRUD Categories
    fun addCategory(name: String, colorHex: String) {
        val user = _currentUser.value
        viewModelScope.launch(Dispatchers.IO) {
            val newCat = Category(name = name, colorHex = colorHex, isSystem = false)
            val assignedId = repository.insertCategory(newCat)

            // Sync categories on firestore
            val firestore = FirebaseManager.firestore
            if (firestore != null && user != null) {
                val data = hashMapOf(
                    "name" to name,
                    "colorHex" to colorHex,
                    "isSystem" to false
                )
                firestore.collection("users").document(user)
                    .collection("categories").document(assignedId.toString()).set(data)
                    .addOnFailureListener { e ->
                        Log.e("FirebaseSync", "Category insert sync failed: ${e.message}")
                    }
            }
        }
    }

    fun updateCategory(category: Category) {
        val user = _currentUser.value
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateCategory(category)

            // Sync categories on Firestore
            val firestore = FirebaseManager.firestore
            if (firestore != null && user != null) {
                val data = hashMapOf(
                    "name" to category.name,
                    "colorHex" to category.colorHex,
                    "isSystem" to category.isSystem
                )
                firestore.collection("users").document(user)
                    .collection("categories").document(category.id.toString()).set(data)
                    .addOnFailureListener { e ->
                        Log.e("FirebaseSync", "Category update sync failed: ${e.message}")
                    }
            }
        }
    }

    fun deleteCategory(category: Category) {
        val user = _currentUser.value
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCategory(category)

            // Delete on Firestore
            val firestore = FirebaseManager.firestore
            if (firestore != null && user != null) {
                firestore.collection("users").document(user)
                    .collection("categories").document(category.id.toString()).delete()
                    .addOnFailureListener { e ->
                        Log.e("FirebaseSync", "Category delete sync failed: ${e.message}")
                    }
            }
        }
    }

    // Save Preference Settings
    fun setCurrency(symbol: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSetting("currency", symbol)
            _currencySymbol.value = symbol
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSetting("theme_mode", theme)
            _themeMode.value = theme
        }
    }

    // SQLite Database Backup & Restore Flow
    fun launchDatabaseBackup(context: Context, onComplete: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbFile = context.getDatabasePath("smart_money_manager_db")
                if (dbFile.exists()) {
                    val backupFile = File(context.cacheDir, "SmartMoney_Database_Backup.db")
                    FileInputStream(dbFile).use { input ->
                        FileOutputStream(backupFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        onComplete(backupFile)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Database doesn't exist yet!", Toast.LENGTH_SHORT).show()
                        onComplete(null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    onComplete(null)
                }
            }
        }
    }

    fun restoreDatabaseBackup(context: Context, backupUri: Uri, onRestartMessage: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.close()

                val dbFile = context.getDatabasePath("smart_money_manager_db")
                val dbWal = context.getDatabasePath("smart_money_manager_db-wal")
                val dbShm = context.getDatabasePath("smart_money_manager_db-shm")

                if (dbWal.exists()) dbWal.delete()
                if (dbShm.exists()) dbShm.delete()

                context.contentResolver.openInputStream(backupUri)?.use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }

                withContext(Dispatchers.Main) {
                    onRestartMessage()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Database restore failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
