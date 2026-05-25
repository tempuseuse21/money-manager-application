package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppSetting
import com.example.data.Category
import com.example.data.FinancialMonth
import com.example.data.TransactionEntry
import com.example.ui.export.DocumentExporter
import com.example.ui.theme.AlertOrange
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.HighSpendYellow
import com.example.ui.theme.IncomeGreen
import com.example.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

fun safeParseColor(hex: String, fallback: Color = Color.Gray): Color {
    if (hex.isBlank()) return fallback
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        fallback
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartMoneyManagerApp(viewModel: FinanceViewModel) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    if (currentUser == null) {
        SmartMoneyLoginScreen(viewModel = viewModel)
        return
    }

    var selectedTab by remember { mutableStateOf(0) } // 0: Sheet, 1: Analytics, 2: Months, 3: Settings

    val activeMonth by viewModel.activeMonth.collectAsStateWithLifecycle()
    val currencySymbol by viewModel.currencySymbol.collectAsStateWithLifecycle()
    val rawMonths by viewModel.monthsList.collectAsStateWithLifecycle()

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        selectedTab = 0
                    },
                    icon = { Icon(Icons.Filled.GridOn, contentDescription = "Excel Sheet") },
                    label = { Text("Sheet", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.testTag("nav_tab_sheet")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        selectedTab = 1
                    },
                    icon = { Icon(Icons.Filled.BarChart, contentDescription = "Analytics") },
                    label = { Text("Analytics", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.testTag("nav_tab_analytics")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        selectedTab = 2
                    },
                    icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = "Months") },
                    label = { Text("Months", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.testTag("nav_tab_months")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        selectedTab = 3
                    },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> MonthlySheetScreen(viewModel, currencySymbol)
                1 -> AnalyticsDashboardScreen(viewModel, currencySymbol)
                2 -> MonthManagerScreen(viewModel, currencySymbol)
                3 -> SettingsScreen(viewModel, currencySymbol)
            }
        }
    }
}

// ==========================================
// SCREEN 1: MONTHLY SHEET (EXCEL GRID VIEW)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlySheetScreen(viewModel: FinanceViewModel, currencySymbol: String) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeMonth by viewModel.activeMonth.collectAsStateWithLifecycle()
    val transactions by viewModel.filteredMonthlyEntries.collectAsStateWithLifecycle()
    val categories by viewModel.categoriesList.collectAsStateWithLifecycle()

    var showQuickAddDialog by remember { mutableStateOf(false) }
    var showDayDetailsSheet by remember { mutableStateOf<Int?>(null) } // Day number clicked

    // Input state query variables
    val searchVal by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterTypeVal by viewModel.filterType.collectAsStateWithLifecycle()
    val filterCategoryVal by viewModel.filterCategory.collectAsStateWithLifecycle()

    if (activeMonth == null) {
        EmptyMonthsDisclaimer(viewModel)
        return
    }

    val month = activeMonth!!

    // Dynamic Calculations
    val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
    val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val currentBalance = totalIncome - totalExpense
    val overBudget = totalExpense > month.budgetLimit

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // TOP SUMMARY CARD + HIGHLIGHTS
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (overBudget) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = month.displayLabel,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (overBudget) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Spreadsheet Grid View",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    // Share document icons
                    Row {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    DocumentExporter.exportToPdf(context, month, transactions, currencySymbol)
                                }
                            },
                            modifier = Modifier.testTag("export_pdf_button")
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, "Pdf Statement", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    DocumentExporter.exportToExcel(context, month, transactions, currencySymbol)
                                }
                            },
                            modifier = Modifier.testTag("export_excel_button")
                        ) {
                            Icon(Icons.Filled.TableChart, "Excel Export", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Numerical Summaries with responsive sizing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryMetric(
                        label = "Total Income",
                        value = "$currencySymbol${String.format("%.2f", totalIncome)}",
                        color = IncomeGreen,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetric(
                        label = "Total Expense",
                        value = "$currencySymbol${String.format("%.2f", totalExpense)}",
                        color = ExpenseRed,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetric(
                        label = "Balance",
                        value = "$currencySymbol${String.format("%.2f", currentBalance)}",
                        color = if (currentBalance >= 0) IncomeGreen else ExpenseRed,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (overBudget) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Over budget by $currencySymbol${String.format("%.2f", totalExpense - month.budgetLimit)}!",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // GRID SEARCH, FILTER CHIPS & TOGGLES
        OutlinedTextField(
            value = searchVal,
            onValueChange = { viewModel.searchQuery.value = it },
            placeholder = { Text("Search grid notes, category, date...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .testTag("search_text_input"),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Type Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start)
        ) {
            listOf("ALL", "INCOME", "EXPENSE").forEach { type ->
                FilterChip(
                    selected = filterTypeVal == type,
                    onClick = { viewModel.filterType.value = type },
                    label = { Text(type, fontSize = 11.sp) },
                    modifier = Modifier.testTag("filter_chip_$type")
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Short Reset Filters option
            if (searchVal.isNotEmpty() || filterTypeVal != "ALL" || filterCategoryVal != "ALL") {
                TextButton(
                    onClick = {
                        viewModel.searchQuery.value = ""
                        viewModel.filterType.value = "ALL"
                        viewModel.filterCategory.value = "ALL"
                    },
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Clear Filters", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // EXCEL SHEET TABLE
        SpreadsheetGrid(
            month = month,
            transactions = transactions,
            currencySymbol = currencySymbol,
            budgetLimit = month.budgetLimit,
            onDayClicked = { day -> showDayDetailsSheet = day },
            isFiltered = searchVal.isNotEmpty() || filterTypeVal != "ALL" || filterCategoryVal != "ALL",
            modifier = Modifier.weight(1f)
        )
    }

    // Quick Add FAB
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        FloatingActionButton(
            onClick = { showQuickAddDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            modifier = Modifier.testTag("quick_add_fab")
        ) {
            Icon(Icons.Filled.Add, "Quick Add Transaction", modifier = Modifier.size(30.dp))
        }
    }

    // Modal Forms System
    if (showQuickAddDialog) {
        TransactionLoggerDialog(
            categories = categories,
            currencySymbol = currencySymbol,
            onDismiss = { showQuickAddDialog = false },
            onSave = { day, type, amount, category, notes ->
                viewModel.addTransaction(day, type, amount, category, notes)
                showQuickAddDialog = false
            }
        )
    }

    if (showDayDetailsSheet != null) {
        val targetDay = showDayDetailsSheet!!
        DayDetailsModalSheet(
            day = targetDay,
            monthLabel = month.displayLabel,
            transactions = transactions.filter { it.day == targetDay },
            categories = categories,
            currencySymbol = currencySymbol,
            onDismiss = { showDayDetailsSheet = null },
            onAddTx = { type, amt, cat, notes ->
                viewModel.addTransaction(targetDay, type, amt, cat, notes)
            },
            onDeleteTx = { tx ->
                viewModel.deleteTransaction(tx)
            },
            onUpdateTx = { id, type, amt, cat, notes ->
                viewModel.updateTransaction(id, targetDay, type, amt, cat, notes)
            }
        )
    }
}

@Composable
fun SummaryMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// THE HERO EXCEL SPREADSHEET COMPONENT
@Composable
fun SpreadsheetGrid(
    month: FinancialMonth,
    transactions: List<TransactionEntry>,
    currencySymbol: String,
    budgetLimit: Double,
    onDayClicked: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isFiltered: Boolean = false
) {
    val daysInMonth = 31 // Simplified or matching Selected month
    val txGroups = remember(transactions) { transactions.groupBy { it.day } }

    val visibleDays = remember(transactions, isFiltered) {
        if (isFiltered) {
            txGroups.keys.sorted()
        } else {
            (1..daysInMonth).toList()
        }
    }

    // Precalculate running balance pure, thread-safe and side-effect free outside composition items
    val runningBalances = remember(txGroups, daysInMonth) {
        val balances = mutableMapOf<Int, Double>()
        var current = 0.0
        for (day in 1..daysInMonth) {
            val dayTxs = txGroups[day] ?: emptyList()
            val dayIncome = dayTxs.filter { it.type == "INCOME" }.sumOf { it.amount }
            val dayExpense = dayTxs.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            current += (dayIncome - dayExpense)
            balances[day] = current
        }
        balances
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .padding(12.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(800.dp)
            ) {
                // TABLE HEADER
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Date", modifier = Modifier.weight(1.2f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Type", modifier = Modifier.weight(1.3f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Category", modifier = Modifier.weight(1.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Notes", modifier = Modifier.weight(2.2f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Amount", modifier = Modifier.weight(1.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Bal", modifier = Modifier.weight(1.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ROW ITEMS
                if (isFiltered && visibleDays.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching entries found for this specific search.",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            fontStyle = FontStyle.Italic
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(visibleDays) { day ->
                            val dayTxs = txGroups[day] ?: emptyList()
                        val dayExpense = dayTxs.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                        val runningBalance = runningBalances[day] ?: 0.0

                        // Identify High Expense Days (> 10% of standard monthly budget limits)
                        val isHighSpendDay = dayExpense > (budgetLimit * 0.08)

                        val rowBg = when {
                            isHighSpendDay -> AlertOrange.copy(alpha = 0.15f)
                            day % 2 == 0 -> MaterialTheme.colorScheme.surface
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        }

                        if (dayTxs.isEmpty()) {
                            // Empty ledger cell placeholder matching standard spreadsheet
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(rowBg)
                                        .clickable { onDayClicked(day) }
                                        .padding(vertical = 12.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "Day $day", modifier = Modifier.weight(1.2f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(text = "—", modifier = Modifier.weight(1.3f), fontSize = 11.sp, color = Color.Gray)
                                    Text(text = "—", modifier = Modifier.weight(1.8f), fontSize = 11.sp, color = Color.Gray)
                                    Text(text = "No entries", modifier = Modifier.weight(2.2f), fontSize = 11.sp, fontStyle = FontStyle.Italic, color = Color.Gray)
                                    Text(text = "—", modifier = Modifier.weight(1.8f), fontSize = 11.sp, textAlign = TextAlign.End, color = Color.Gray)
                                    Text(
                                        text = "$currencySymbol${String.format("%.0f", runningBalance)}",
                                        modifier = Modifier.weight(1.7f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.End,
                                        color = if (runningBalance >= 0) IncomeGreen else ExpenseRed
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        } else {
                            // Group lists together in cell blocks representing day summaries
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowBg)
                                    .clickable { onDayClicked(day) }
                            ) {
                                for (index in dayTxs.indices) {
                                    val tx = dayTxs[index]
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Date only on first item block in day cell
                                        if (index == 0) {
                                            Text(
                                                text = "Day $day",
                                                modifier = Modifier.weight(1.2f),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isHighSpendDay) AlertOrange else MaterialTheme.colorScheme.onSurface
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.weight(1.2f))
                                        }

                                        // Type
                                        Text(
                                            text = tx.type,
                                            modifier = Modifier.weight(1.3f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (tx.type == "INCOME") IncomeGreen else ExpenseRed
                                        )

                                        // Category
                                        Text(
                                            text = tx.category,
                                            modifier = Modifier.weight(1.8f),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        // Notes
                                        Text(
                                            text = tx.notes,
                                            modifier = Modifier.weight(2.2f),
                                            fontSize = 11.sp,
                                            fontStyle = FontStyle.Italic,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        // Item Amount
                                        Text(
                                            text = "$currencySymbol${String.format("%.1f", tx.amount)}",
                                            modifier = Modifier.weight(1.8f),
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.End,
                                            fontWeight = FontWeight.Bold,
                                            color = if (tx.type == "INCOME") IncomeGreen else ExpenseRed
                                        )

                                        // Showing intermediate calculated balanced after last index in block
                                        if (index == dayTxs.size - 1) {
                                            Text(
                                                text = "$currencySymbol${String.format("%.0f", runningBalance)}",
                                                modifier = Modifier.weight(1.7f),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.End,
                                                color = if (runningBalance >= 0) IncomeGreen else ExpenseRed
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.weight(1.7f))
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

// ==========================================
// SCREEN 2: ANALYTICS DASHBOARD SCREEN
// ==========================================
@Composable
fun AnalyticsDashboardScreen(viewModel: FinanceViewModel, currencySymbol: String) {
    val activeMonth by viewModel.activeMonth.collectAsStateWithLifecycle()
    val transactions by viewModel.filteredMonthlyEntries.collectAsStateWithLifecycle()
    val tips by viewModel.smartSavingsTips.collectAsStateWithLifecycle()

    if (activeMonth == null) {
        EmptyMonthsDisclaimer(viewModel)
        return
    }

    val month = activeMonth!!
    val expenses = transactions.filter { it.type == "EXPENSE" }
    val incomeVal = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
    val expenseVal = expenses.sumOf { it.amount }
    val maxSpendDayGroup = expenses.groupBy { it.day }.mapValues { it.value.sumOf { tx -> tx.amount } }.maxByOrNull { it.value }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        item {
            Text(
                text = "${month.displayLabel} Financial Status",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Dynamic Charts & AI Savings Advice",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // HEURISTIC / AI INSIGHTS CARD
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lightbulb, "Savings Advice", tint = Color(0xFFFFD54F))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Smart Financial Insights",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    tips.forEachIndexed { i, tip ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(text = "•", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                            Text(
                                text = tip,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }

        // INTERACTIVE PIE CHART (CATEGORY SPENDING)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Category Expenses",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CategoryPieChart(transactions, currencySymbol)
                }
            }
        }

        // DAILY SPENDING BAR CHART
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DailySpendingBarChart(transactions, currencySymbol, month.budgetLimit)
                }
            }
        }

        // ANOMALY METRICS HIGHLIGHTS
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Risk & Limit Metrics", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    MetricRow(
                        label = "Highest Expense Day",
                        value = if (maxSpendDayGroup != null) "Day ${maxSpendDayGroup.key} ($currencySymbol${String.format("%.2f", maxSpendDayGroup.value)})" else "None"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val savingsRate = if (incomeVal > 0) ((incomeVal - expenseVal) / incomeVal) * 100 else 0.0
                    MetricRow(
                        label = "Computed Savings Margin",
                        value = "${String.format("%.1f", savingsRate)}%"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val budgetUtilization = if (month.budgetLimit > 0.0) (expenseVal / month.budgetLimit) * 100 else 0.0
                    MetricRow(
                        label = "Budget Utilization Status",
                        value = "${String.format("%.1f", budgetUtilization)}%"
                    )
                }
            }
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// ==========================================
// SCREEN 3: MONTH MANAGER SCREEN
// ==========================================
@Composable
fun MonthManagerScreen(viewModel: FinanceViewModel, currencySymbol: String) {
    val months by viewModel.monthsList.collectAsStateWithLifecycle()
    val activeMonthId by viewModel.activeMonthId.collectAsStateWithLifecycle()

    var showCreateMonthDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Financial Months Manager", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text("Select & Configure Statement Budgets", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = { showCreateMonthDialog = true },
                modifier = Modifier.testTag("add_month_nav_button")
            ) {
                Icon(Icons.Filled.Add, "New Month")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (months.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("No months configured yet. Click 'Add' to set up your budget statement.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(months) { month ->
                    val isActive = month.id == activeMonthId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setActiveMonth(month.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = month.displayLabel,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Budget Limit: $currencySymbol${String.format("%.2f", month.budgetLimit)}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Switch indicator or delete
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isActive) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                        Text("Active", color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        if (isActive) {
                                            viewModel.deleteActiveMonth()
                                        } else {
                                            viewModel.setActiveMonth(month.id)
                                            viewModel.deleteActiveMonth()
                                        }
                                    },
                                    modifier = Modifier.testTag("delete_month_${month.id}")
                                ) {
                                    Icon(Icons.Filled.Delete, "Delete Month", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateMonthDialog) {
        CreateMonthDialog(
            currencySymbol = currencySymbol,
            onDismiss = { showCreateMonthDialog = false },
            onSave = { year, monthIndex, budget ->
                viewModel.createMonth(year, monthIndex, budget)
                showCreateMonthDialog = false
            }
        )
    }
}

// ==========================================
// SCREEN 4: SETTINGS SCREEN
// ==========================================
@Composable
fun SettingsScreen(viewModel: FinanceViewModel, currencySymbol: String) {
    val context = LocalContext.current
    val categories by viewModel.categoriesList.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showEditCategoryDialog by remember { mutableStateOf<Category?>(null) }
    var showDeleteCategoryConfirm by remember { mutableStateOf<Category?>(null) }

    // Launcher for SQLite Database Restorepicker
    val restoreDbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                viewModel.restoreDatabaseBackup(context, uri) {
                    Toast.makeText(context, "Database Restored Successfully! Please restart the application.", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("General Settings", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text("Customize Currency, Categorization & Backups", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // CURRENCY SELECTOR CARD
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("System Currency", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Select default currency mapping across spreadsheets and statements.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("₹", "$", "€", "£", "¥").forEach { sym ->
                            val isSelected = currencySymbol == sym
                            ElevatedButton(
                                onClick = { viewModel.setCurrency(sym) },
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("currency_select_$sym")
                            ) {
                                Text(sym, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // THEME MODE SELECTOR CARD
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("App Theme Mode", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Configure light, dark, or system matching theme overrides.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("SYSTEM", "LIGHT", "DARK").forEach { tMode ->
                            val isSelected = themeMode == tMode
                            ElevatedButton(
                                onClick = { viewModel.setTheme(tMode) },
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("theme_select_$tMode")
                            ) {
                                Text(tMode, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // DATABASE BACKUP & RESTORE SECTOR
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Backup & Local Restore", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Keep your money data completely safe using direct offline copies.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.launchDatabaseBackup(context) { file ->
                                    if (file != null) {
                                        val authority = "${context.packageName}.fileprovider"
                                        try {
                                            val backupUri: Uri = FileProvider.getUriForFile(context, authority, file)
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/octet-stream"
                                                putExtra(Intent.EXTRA_STREAM, backupUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share Database Backup").apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            })
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            Toast.makeText(context, "No sharing app found on this device", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("backup_db_button")
                        ) {
                            Icon(Icons.Filled.Backup, "Backup")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Backup DB", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                restoreDbLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("restore_db_button")
                        ) {
                            Icon(Icons.Filled.Restore, "Restore")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore DB", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // CUSTOM BUDGET CATEGORIES MANAGER
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Custom Categories", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = { showAddCategoryDialog = true },
                            modifier = Modifier.testTag("add_category_button")
                        ) {
                            Icon(Icons.Filled.AddCircle, "Add Custom Category", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text("Custom categories can be assigned to new entries.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    val chunkedCategories = remember(categories) { categories.chunked(2) }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        chunkedCategories.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { cat ->
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(safeParseColor(cat.colorHex))
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = cat.name,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Edit,
                                                "Edit Category",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { showEditCategoryDialog = cat }
                                            )
                                            Icon(
                                                Icons.Filled.Cancel,
                                                "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { showDeleteCategoryConfirm = cat }
                                            )
                                        }
                                    }
                                }
                                if (rowItems.size < 2) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECURE LOGOUT SESSION CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Session Security",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Wipe local in-memory session caches and safely lock account data.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.logout() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("logout_button")
                    ) {
                        Icon(Icons.Filled.Logout, "Logout")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Log Out Session",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            categories = categories,
            onDismiss = { showAddCategoryDialog = false },
            onSave = { name, hex ->
                viewModel.addCategory(name, hex)
                showAddCategoryDialog = false
            }
        )
    }

    if (showEditCategoryDialog != null) {
        EditCategoryDialog(
            category = showEditCategoryDialog!!,
            categories = categories,
            onDismiss = { showEditCategoryDialog = null },
            onSave = { updatedCat ->
                viewModel.updateCategory(updatedCat)
                showEditCategoryDialog = null
            }
        )
    }

    if (showDeleteCategoryConfirm != null) {
        DeleteCategoryConfirmDialog(
            category = showDeleteCategoryConfirm!!,
            onDismiss = { showDeleteCategoryConfirm = null },
            onConfirm = {
                viewModel.deleteCategory(showDeleteCategoryConfirm!!)
                showDeleteCategoryConfirm = null
            }
        )
    }
}

// ==========================================
// HELPER FORMS / DIALOG DESIGN SYSTEMS
// ==========================================

@Composable
fun EmptyMonthsDisclaimer(viewModel: FinanceViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.CalendarMonth, "Empty Calendar", modifier = Modifier.size(60.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(14.dp))
            Text("No Active Budget Statement", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Get started with Smart Money Manager by configuring your initial budget month index.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.createMonth(2026, 5, 25000.0) },
                modifier = Modifier.testTag("create_starter_month")
            ) {
                Text("Create Starter Month (May 2026)")
            }
        }
    }
}

// DIALOG: CREATE MONTH
@Composable
fun CreateMonthDialog(
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (year: Int, monthIdx: Int, budget: Double) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val safeDismiss = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onDismiss()
    }

    var yearStr by remember { mutableStateOf("2026") }
    var monthIndex by remember { mutableStateOf(5) } // May
    var budgetStr by remember { mutableStateOf("25000") }

    val monthsList = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    var dropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = safeDismiss,
        title = { Text("Configure Statement Month") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = yearStr,
                    onValueChange = { yearStr = it },
                    label = { Text("Year (e.g. 2026)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // Month dropdown spinner simulation
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Month: ${monthsList[monthIndex - 1]}")
                    }
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        monthsList.forEachIndexed { idx, name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    monthIndex = idx + 1
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = budgetStr,
                    onValueChange = { budgetStr = it },
                    label = { Text("Monthly Budget Limit ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    val y = yearStr.toIntOrNull() ?: 2026
                    val bud = budgetStr.toDoubleOrNull() ?: 25000.0
                    onSave(y, monthIndex, bud)
                },
                modifier = Modifier.testTag("save_month_dialog_button")
            ) {
                Text("Create Month")
            }
        },
        dismissButton = {
            TextButton(onClick = safeDismiss) {
                Text("Cancel")
            }
        }
    )
}

// DIALOG: TRANSACTION WRITER FORM
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionLoggerDialog(
    categories: List<Category>,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (day: Int, type: String, amount: Double, category: String, notes: String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val safeDismiss = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onDismiss()
    }

    var dayStr by remember { mutableStateOf("1") }
    var type by remember { mutableStateOf("EXPENSE") } // EXPENSE, INCOME
    var amountStr by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf(categories.firstOrNull()?.name ?: "Other") }
    var notes by remember { mutableStateOf("") }

    LaunchedEffect(categories) {
        if (categories.none { it.name == selectedCat }) {
            selectedCat = categories.firstOrNull()?.name ?: "Other"
        }
    }

    var catDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = safeDismiss,
        title = { Text("Log Transaction Entry") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Day and Type Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = dayStr,
                        onValueChange = { dayStr = it },
                        label = { Text("Day (1-31)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    // Transaction type switch
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("INCOME", "EXPENSE").forEach { item ->
                            val isSelected = type == item
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { type = item },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = item,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Amount cell
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_amount_input"),
                    singleLine = true
                )

                // Category select cell
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { catDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Category: $selectedCat")
                    }
                    DropdownMenu(
                        expanded = catDropdownExpanded,
                        onDismissRequest = { catDropdownExpanded = false }
                    ) {
                        categories.forEach { cate ->
                            DropdownMenuItem(
                                text = { Text(cate.name) },
                                onClick = {
                                    selectedCat = cate.name
                                    catDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Notes input field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (e.g. Grocery trip)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_notes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    val d = dayStr.toIntOrNull()?.coerceIn(1, 31) ?: 1
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    onSave(d, type, amt, selectedCat, notes)
                },
                modifier = Modifier.testTag("save_transaction_button")
            ) {
                Text("Add Entry")
            }
        },
        dismissButton = {
            TextButton(onClick = safeDismiss) {
                Text("Cancel")
            }
        }
    )
}

// EXPANDABLE CELL DETAIL ROW DRAWER / BOTTOM SHEET SIMULATOR
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailsModalSheet(
    day: Int,
    monthLabel: String,
    transactions: List<TransactionEntry>,
    categories: List<Category>,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onAddTx: (type: String, amt: Double, cat: String, notes: String) -> Unit,
    onDeleteTx: (TransactionEntry) -> Unit,
    onUpdateTx: (id: Int, type: String, amt: Double, cat: String, notes: String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val safeDismiss = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onDismiss()
    }

    // Local creation variables inside the day editor
    var isAddingMode by remember { mutableStateOf(false) }

    var type by remember { mutableStateOf("EXPENSE") }
    var amountStr by remember { mutableStateOf("") }
    var categorySelection by remember { mutableStateOf(categories.firstOrNull()?.name ?: "Other") }
    var notesStr by remember { mutableStateOf("") }

    LaunchedEffect(categories) {
        if (categories.none { it.name == categorySelection }) {
            categorySelection = categories.firstOrNull()?.name ?: "Other"
        }
    }

    var editingTx by remember { mutableStateOf<TransactionEntry?>(null) }

    ModalBottomSheet(
        onDismissRequest = safeDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Day $day Details",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Ledger list for day statement $monthLabel",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val isEditingOrAdding = isAddingMode || editingTx != null
                Button(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        if (isEditingOrAdding) {
                            isAddingMode = false
                            editingTx = null
                        } else {
                            isAddingMode = true
                            type = "EXPENSE"
                            amountStr = ""
                            categorySelection = categories.firstOrNull()?.name ?: "Other"
                            notesStr = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isEditingOrAdding) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isEditingOrAdding) "Show List" else "+ Append cell")
                }
            }

            val sheetMode = when {
                editingTx != null -> "EDIT"
                isAddingMode -> "ADD"
                else -> "LIST"
            }

            AnimatedContent(targetState = sheetMode, label = "SheetFormTransition") { currentMode ->
                when (currentMode) {
                    "ADD" -> {
                        // Quick Inline Add Component
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(10.dp)
                        ) {
                            Text("Add Sub Cell Entry", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                            // Choice type Selector Row
                            Row {
                                listOf("INCOME", "EXPENSE").forEach { entry ->
                                    val isSelected = type == entry
                                    ElevatedButton(
                                        onClick = { type = entry },
                                        colors = ButtonDefaults.elevatedButtonColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                            contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp)
                                    ) {
                                        Text(entry, fontSize = 11.sp)
                                    }
                                }
                            }

                            // Cash volume and notes inputs
                            OutlinedTextField(
                                value = amountStr,
                                onValueChange = { amountStr = it },
                                label = { Text("Amount ($currencySymbol)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Categories selector
                            var dropExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { dropExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Assign Category: $categorySelection")
                                }
                                DropdownMenu(expanded = dropExpanded, onDismissRequest = { dropExpanded = false }) {
                                    categories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat.name) },
                                            onClick = {
                                                categorySelection = cat.name
                                                dropExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = notesStr,
                                onValueChange = { notesStr = it },
                                label = { Text("Notes info") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    val amtVal = amountStr.toDoubleOrNull() ?: 100.0
                                    onAddTx(type, amtVal, categorySelection, notesStr)
                                    amountStr = ""
                                    notesStr = ""
                                    isAddingMode = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Cell")
                            }
                        }
                    }
                    "EDIT" -> {
                        // Safe Edit Component outside of LazyColumn loop
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(10.dp)
                        ) {
                            Text("Edit Sub Cell Entry", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                            Row {
                                listOf("INCOME", "EXPENSE").forEach { entry ->
                                    val isSelected = type == entry
                                    ElevatedButton(
                                        onClick = { type = entry },
                                        colors = ButtonDefaults.elevatedButtonColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                            contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp)
                                    ) {
                                        Text(entry, fontSize = 11.sp)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = amountStr,
                                onValueChange = { amountStr = it },
                                label = { Text("Amount ($currencySymbol)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )

                            var dropExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { dropExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Assign Category: $categorySelection")
                                }
                                DropdownMenu(expanded = dropExpanded, onDismissRequest = { dropExpanded = false }) {
                                    categories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat.name) },
                                            onClick = {
                                                categorySelection = cat.name
                                                dropExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = notesStr,
                                onValueChange = { notesStr = it },
                                label = { Text("Notes info") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        val amtVal = amountStr.toDoubleOrNull() ?: (editingTx?.amount ?: 0.0)
                                        editingTx?.let { et ->
                                            onUpdateTx(et.id, type, amtVal, categorySelection, notesStr)
                                        }
                                        editingTx = null
                                        amountStr = ""
                                        notesStr = ""
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Apply Changes")
                                }
                                OutlinedButton(
                                    onClick = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        editingTx = null
                                        amountStr = ""
                                        notesStr = ""
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                    else -> {
                        // "LIST" Mode
                        Column {
                            if (transactions.isEmpty()) {
                                Text("No items logged on Day $day. Tap '+ Append cell' to expand statement details.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
                            } else {
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 250.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(transactions) { tx ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Badge(
                                                            containerColor = if (tx.type == "INCOME") IncomeGreen else ExpenseRed
                                                        ) {
                                                            Text(tx.type, color = Color.White, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp))
                                                        }
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(tx.category, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(tx.notes, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "$currencySymbol${String.format("%.2f", tx.amount)}",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (tx.type == "INCOME") IncomeGreen else ExpenseRed,
                                                        modifier = Modifier.padding(end = 8.dp)
                                                    )

                                                    IconButton(onClick = {
                                                        editingTx = tx
                                                        type = tx.type
                                                        amountStr = tx.amount.toString()
                                                        categorySelection = tx.category
                                                        notesStr = tx.notes
                                                    }) {
                                                        Icon(Icons.Default.Edit, "Edit Entry", modifier = Modifier.size(16.dp))
                                                    }

                                                    IconButton(onClick = { onDeleteTx(tx) }) {
                                                        Icon(Icons.Default.Delete, "Delete Entry", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// DIALOG: ADD CUSTOM BUDGET CATEGORY IN SETTINGS (WITH ERROR VALIDATIONS)
@Composable
fun AddCategoryDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (name: String, colorHex: String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val safeDismiss = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onDismiss()
    }

    var catName by remember { mutableStateOf("") }
    var selectedColorIndex by remember { mutableStateOf(0) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val colorHexes = listOf("#E57373", "#81C784", "#64B5F6", "#F0D352", "#BA68C8", "#FFB74D", "#4DB6AC", "#90A4AE")

    AlertDialog(
        onDismissRequest = safeDismiss,
        title = { Text("New Custom Category", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = catName,
                    onValueChange = { 
                        catName = it
                        errorText = null // Clear error when typing
                    },
                    label = { Text("Category Name") },
                    isError = errorText != null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text("Assign Chart Color representation", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                // Grid of color chips selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    colorHexes.forEachIndexed { idx, hex ->
                        val isSel = idx == selectedColorIndex
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(safeParseColor(hex))
                                .border(
                                    2.dp,
                                    if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { selectedColorIndex = idx }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = catName.trim()
                    if (trimmed.isEmpty()) {
                        errorText = "Category name cannot be empty."
                    } else if (categories.any { it.name.trim().equals(trimmed, ignoreCase = true) }) {
                        errorText = "Category already exists."
                    } else {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onSave(trimmed, colorHexes[selectedColorIndex])
                    }
                },
                modifier = Modifier.testTag("save_category_dialog_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = safeDismiss) {
                Text("Cancel")
            }
        }
    )
}

// DIALOG: EDIT CUSTOM BUDGET CATEGORY IN SETTINGS (WITH ERROR VALIDATIONS)
@Composable
fun EditCategoryDialog(
    category: Category,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val safeDismiss = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onDismiss()
    }

    var catName by remember { mutableStateOf(category.name) }
    val colorHexes = listOf("#E57373", "#81C784", "#64B5F6", "#F0D352", "#BA68C8", "#FFB74D", "#4DB6AC", "#90A4AE")
    var selectedColorIndex by remember {
        val idx = colorHexes.indexOf(category.colorHex)
        mutableStateOf(if (idx != -1) idx else 0)
    }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = safeDismiss,
        title = { Text("Edit Custom Category", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = catName,
                    onValueChange = { 
                        catName = it
                        errorText = null // Clear error when typing
                    },
                    label = { Text("Category Name") },
                    isError = errorText != null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text("Assign Chart Color representation", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                // Grid of color chips selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    colorHexes.forEachIndexed { idx, hex ->
                        val isSel = idx == selectedColorIndex
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(safeParseColor(hex))
                                .border(
                                    2.dp,
                                    if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { selectedColorIndex = idx }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = catName.trim()
                    if (trimmed.isEmpty()) {
                        errorText = "Category name cannot be empty."
                    } else if (categories.any { it.id != category.id && it.name.trim().equals(trimmed, ignoreCase = true) }) {
                        errorText = "Category already exists."
                    } else {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onSave(category.copy(name = trimmed, colorHex = colorHexes[selectedColorIndex]))
                    }
                },
                modifier = Modifier.testTag("update_category_dialog_button")
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = safeDismiss) {
                Text("Cancel")
            }
        }
    )
}

// DIALOG: CONFIRM DELETE CATEGORY IN SETTINGS
@Composable
fun DeleteCategoryConfirmDialog(
    category: Category,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Category", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
        text = {
            Text("Are you sure you want to delete the category \"${category.name}\"? This action cannot be undone.")
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_delete_category_button")
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
