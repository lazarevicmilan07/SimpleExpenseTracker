package com.expensetracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["accountId"]),
        Index(value = ["date"]),
        Index(value = ["type"])
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val note: String,
    val categoryId: Long?,
    val accountId: Long?,
    val type: TransactionType,
    val date: Long,
    val subcategoryId: Long? = null,
    val toAccountId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER
}
