package com.expensetracker.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.expensetracker.app.data.local.dao.AccountDao
import com.expensetracker.app.data.local.dao.CategoryDao
import com.expensetracker.app.data.local.dao.ExpenseDao
import com.expensetracker.app.data.local.entity.AccountEntity
import com.expensetracker.app.data.local.entity.CategoryEntity
import com.expensetracker.app.data.local.entity.ExpenseEntity

@Database(
    entities = [
        ExpenseEntity::class,
        CategoryEntity::class,
        AccountEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao

    companion object {
        const val DATABASE_NAME = "expense_tracker.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create accounts table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS accounts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        icon TEXT NOT NULL,
                        color INTEGER NOT NULL,
                        initialBalance REAL NOT NULL DEFAULT 0.0,
                        isDefault INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                """)

                // Insert default Cash account
                database.execSQL("""
                    INSERT INTO accounts (name, type, icon, color, initialBalance, isDefault, createdAt)
                    VALUES ('Cash', 'CASH', 'wallet', 4283215696, 0.0, 1, ${System.currentTimeMillis()})
                """)

                // Add accountId column to expenses table
                database.execSQL("ALTER TABLE expenses ADD COLUMN accountId INTEGER DEFAULT 1")

                // Create index for accountId
                database.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_accountId ON expenses(accountId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add parentCategoryId column to categories table for subcategories support
                database.execSQL("ALTER TABLE categories ADD COLUMN parentCategoryId INTEGER DEFAULT NULL")

                // Create index for parentCategoryId
                database.execSQL("CREATE INDEX IF NOT EXISTS index_categories_parentCategoryId ON categories(parentCategoryId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE expenses ADD COLUMN subcategoryId INTEGER DEFAULT NULL")
            }
        }
    }
}
