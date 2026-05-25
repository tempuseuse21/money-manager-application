package com.example.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Dao
interface FinancialMonthDao {
    @Query("SELECT * FROM financial_months ORDER BY year DESC, month DESC")
    fun getAllMonths(): Flow<List<FinancialMonth>>

    @Query("SELECT * FROM financial_months ORDER BY year DESC, month DESC")
    suspend fun getAllMonthsDirect(): List<FinancialMonth>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMonth(month: FinancialMonth)

    @Delete
    suspend fun deleteMonth(month: FinancialMonth)

    @Update
    suspend fun updateMonth(month: FinancialMonth)
}

@Dao
interface TransactionEntryDao {
    @Query("SELECT * FROM transaction_entries WHERE monthId = :monthId ORDER BY day ASC, id ASC")
    fun getEntriesForMonth(monthId: String): Flow<List<TransactionEntry>>

    @Query("SELECT * FROM transaction_entries WHERE monthId = :monthId ORDER BY day ASC, id ASC")
    suspend fun getEntriesForMonthDirect(monthId: String): List<TransactionEntry>

    @Query("SELECT * FROM transaction_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<TransactionEntry>>

    @Query("SELECT * FROM transaction_entries ORDER BY timestamp DESC")
    suspend fun getAllEntriesDirect(): List<TransactionEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: TransactionEntry): Long

    @Update
    suspend fun updateEntry(entry: TransactionEntry)

    @Delete
    suspend fun deleteEntry(entry: TransactionEntry)

    @Query("DELETE FROM transaction_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Int)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY id ASC")
    suspend fun getAllCategoriesDirect(): List<Category>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)
}

@Dao
interface AppSettingDao {
    @Query("SELECT * FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): AppSetting?

    @Query("SELECT * FROM app_settings")
    fun getAllSettings(): Flow<List<AppSetting>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: AppSetting)
}

@Database(
    entities = [
        FinancialMonth::class,
        TransactionEntry::class,
        Category::class,
        AppSetting::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun financialMonthDao(): FinancialMonthDao
    abstract fun transactionEntryDao(): TransactionEntryDao
    abstract fun categoryDao(): CategoryDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_money_manager_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
