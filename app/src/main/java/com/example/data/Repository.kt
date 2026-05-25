package com.example.data

import kotlinx.coroutines.flow.Flow

class FinancialRepository(private val db: AppDatabase) {

    // Months
    val allMonths: Flow<List<FinancialMonth>> = db.financialMonthDao().getAllMonths()

    suspend fun getAllMonthsDirect(): List<FinancialMonth> {
        return db.financialMonthDao().getAllMonthsDirect()
    }

    suspend fun insertMonth(month: FinancialMonth) {
        db.financialMonthDao().insertMonth(month)
    }

    suspend fun deleteMonth(month: FinancialMonth) {
        db.financialMonthDao().deleteMonth(month)
    }

    suspend fun updateMonth(month: FinancialMonth) {
        db.financialMonthDao().updateMonth(month)
    }

    // Entries
    fun getEntriesForMonth(monthId: String): Flow<List<TransactionEntry>> {
        return db.transactionEntryDao().getEntriesForMonth(monthId)
    }

    suspend fun getEntriesForMonthDirect(monthId: String): List<TransactionEntry> {
        return db.transactionEntryDao().getEntriesForMonthDirect(monthId)
    }

    val allEntries: Flow<List<TransactionEntry>> = db.transactionEntryDao().getAllEntries()

    suspend fun getAllEntriesDirect(): List<TransactionEntry> {
        return db.transactionEntryDao().getAllEntriesDirect()
    }

    suspend fun insertEntry(entry: TransactionEntry): Int {
        return db.transactionEntryDao().insertEntry(entry).toInt()
    }

    suspend fun updateEntry(entry: TransactionEntry) {
        db.transactionEntryDao().updateEntry(entry)
    }

    suspend fun deleteEntry(entry: TransactionEntry) {
        db.transactionEntryDao().deleteEntry(entry)
    }

    suspend fun deleteEntryById(id: Int) {
        db.transactionEntryDao().deleteEntryById(id)
    }

    // Categories
    val allCategories: Flow<List<Category>> = db.categoryDao().getAllCategories()

    suspend fun getAllCategoriesDirect(): List<Category> {
        return db.categoryDao().getAllCategoriesDirect()
    }

    suspend fun insertCategory(category: Category): Int {
        return db.categoryDao().insertCategory(category).toInt()
    }

    suspend fun updateCategory(category: Category) {
        db.categoryDao().updateCategory(category)
    }

    suspend fun deleteCategory(category: Category) {
        db.categoryDao().deleteCategory(category)
    }

    // Global App Settings
    val allSettings: Flow<List<AppSetting>> = db.appSettingDao().getAllSettings()

    suspend fun getSetting(key: String): AppSetting? {
        return db.appSettingDao().getSetting(key)
    }

    suspend fun saveSetting(key: String, value: String) {
        db.appSettingDao().insertSetting(AppSetting(key, value))
    }

    suspend fun clearAllData() {
        db.clearAllTables()
    }
}
