package com.expensetracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [Index(value = ["parentCategoryId"])]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val icon: String,
    val color: Long,
    val isDefault: Boolean = false,
    val parentCategoryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
