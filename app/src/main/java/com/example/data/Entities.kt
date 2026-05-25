package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "financial_months")
data class FinancialMonth(
    @PrimaryKey val id: String, // e.g., "2026-05"
    val year: Int,
    val month: Int, // 1 to 12
    val displayLabel: String,
    val budgetLimit: Double = 30000.0 // Default limit for overspending highlights
)

@Entity(
    tableName = "transaction_entries",
    foreignKeys = [
        ForeignKey(
            entity = FinancialMonth::class,
            parentColumns = ["id"],
            childColumns = ["monthId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["monthId"])]
)
data class TransactionEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val monthId: String,
    val day: Int, // 1 to 31
    val type: String, // "INCOME" or "EXPENSE"
    val amount: Double,
    val category: String,
    val notes: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val colorHex: String, // Hex string like "#FF5722" for charts
    val isSystem: Boolean = false
)

@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)
