package com.expensetracker.app.domain.model

import androidx.compose.ui.graphics.Color

data class Category(
    val id: Long = 0,
    val name: String,
    val icon: String,
    val color: Color,
    val isDefault: Boolean = false,
    val parentCategoryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

val DefaultCategories = listOf(
    Category(name = "Food & Dining", icon = "restaurant", color = Color(0xFFFF6B6B), isDefault = true),
    Category(name = "Transportation", icon = "directions_car", color = Color(0xFF4ECDC4), isDefault = true),
    Category(name = "Shopping", icon = "shopping_bag", color = Color(0xFFFFE66D), isDefault = true),
    Category(name = "Entertainment", icon = "movie", color = Color(0xFF95E1D3), isDefault = true),
    Category(name = "Bills & Utilities", icon = "receipt_long", color = Color(0xFFA8E6CF), isDefault = true),
    Category(name = "Health", icon = "medical_services", color = Color(0xFFDDA0DD), isDefault = true),
    Category(name = "Education", icon = "school", color = Color(0xFF87CEEB), isDefault = true),
    Category(name = "Salary", icon = "payments", color = Color(0xFF98D8C8), isDefault = true),
    Category(name = "Investment", icon = "trending_up", color = Color(0xFFB4A7D6), isDefault = true),
    Category(name = "Other", icon = "more_horiz", color = Color(0xFFBDBDBD), isDefault = true)
)
