package com.expensetracker.app.di

import android.content.Context
import androidx.room.Room
import com.expensetracker.app.data.local.ExpenseDatabase
import com.expensetracker.app.data.local.dao.AccountDao
import com.expensetracker.app.data.local.dao.CategoryDao
import com.expensetracker.app.data.local.dao.ExpenseDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideExpenseDatabase(
        @ApplicationContext context: Context
    ): ExpenseDatabase {
        return Room.databaseBuilder(
            context,
            ExpenseDatabase::class.java,
            ExpenseDatabase.DATABASE_NAME
        )
            .addMigrations(
                ExpenseDatabase.MIGRATION_1_2,
                ExpenseDatabase.MIGRATION_2_3
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideExpenseDao(database: ExpenseDatabase): ExpenseDao {
        return database.expenseDao()
    }

    @Provides
    @Singleton
    fun provideCategoryDao(database: ExpenseDatabase): CategoryDao {
        return database.categoryDao()
    }

    @Provides
    @Singleton
    fun provideAccountDao(database: ExpenseDatabase): AccountDao {
        return database.accountDao()
    }
}
