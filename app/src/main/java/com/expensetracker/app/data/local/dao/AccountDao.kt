package com.expensetracker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.expensetracker.app.data.local.entity.AccountEntity
import com.expensetracker.app.data.local.entity.AccountWithBalanceEntity
import com.expensetracker.app.data.local.entity.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY isDefault DESC, name ASC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultAccount(): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun getAccountCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccountById(id: Long)

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()

    @Query("SELECT * FROM accounts ORDER BY isDefault DESC, name ASC")
    suspend fun getAllAccountsSync(): List<AccountEntity>

    @Query("UPDATE accounts SET isDefault = 0")
    suspend fun clearAllDefaults()

    @Query("SELECT EXISTS(SELECT 1 FROM accounts LIMIT 1)")
    suspend fun hasAccounts(): Boolean

    @Query("""
        SELECT COALESCE(
            (SELECT initialBalance FROM accounts WHERE id = :accountId), 0.0
        ) +
        COALESCE(
            (SELECT SUM(
                CASE
                    WHEN type = 'INCOME' THEN amount
                    WHEN type = 'EXPENSE' THEN -amount
                    WHEN type = 'TRANSFER' THEN -amount
                    ELSE 0
                END
            ) FROM expenses WHERE accountId = :accountId), 0.0
        ) +
        COALESCE(
            (SELECT SUM(amount) FROM expenses WHERE toAccountId = :accountId AND type = 'TRANSFER'), 0.0
        )
    """)
    fun getAccountBalance(accountId: Long): Flow<Double>

    @Query("""
        SELECT SUM(
            COALESCE(a.initialBalance, 0.0) +
            COALESCE(
                (SELECT SUM(
                    CASE
                        WHEN e.type = 'INCOME' THEN e.amount
                        WHEN e.type = 'EXPENSE' THEN -e.amount
                        ELSE 0
                    END
                ) FROM expenses e WHERE e.accountId = a.id), 0.0
            )
        )
        FROM accounts a
    """)
    fun getTotalBalance(): Flow<Double?>

    @Query("""
        SELECT a.*,
            COALESCE(a.initialBalance, 0.0) +
            COALESCE(
                (SELECT SUM(
                    CASE
                        WHEN e.type = 'INCOME' THEN e.amount
                        WHEN e.type = 'EXPENSE' THEN -e.amount
                        WHEN e.type = 'TRANSFER' THEN -e.amount
                        ELSE 0
                    END
                ) FROM expenses e WHERE e.accountId = a.id), 0.0
            ) +
            COALESCE(
                (SELECT SUM(e.amount) FROM expenses e WHERE e.toAccountId = a.id AND e.type = 'TRANSFER'), 0.0
            ) as currentBalance
        FROM accounts a
        ORDER BY a.isDefault DESC, a.name ASC
    """)
    fun getAllAccountsWithBalances(): Flow<List<AccountWithBalanceEntity>>
}
