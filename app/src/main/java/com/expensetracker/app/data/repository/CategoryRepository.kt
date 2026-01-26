package com.expensetracker.app.data.repository

import com.expensetracker.app.data.local.dao.CategoryDao
import com.expensetracker.app.data.mapper.toDomain
import com.expensetracker.app.data.mapper.toEntity
import com.expensetracker.app.domain.model.Category
import com.expensetracker.app.domain.model.DefaultCategories
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {

    fun getAllCategories(): Flow<List<Category>> =
        categoryDao.getAllCategories().map { entities ->
            entities.map { it.toDomain() }
        }

    fun getRootCategories(): Flow<List<Category>> =
        categoryDao.getRootCategories().map { entities ->
            entities.map { it.toDomain() }
        }

    fun getSubcategories(parentId: Long): Flow<List<Category>> =
        categoryDao.getSubcategories(parentId).map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun hasSubcategories(categoryId: Long): Boolean =
        categoryDao.hasSubcategories(categoryId)

    suspend fun getCategoryById(id: Long): Category? =
        categoryDao.getCategoryById(id)?.toDomain()

    suspend fun getCategoryCount(): Int =
        categoryDao.getCategoryCount()

    suspend fun insertCategory(category: Category): Long =
        categoryDao.insertCategory(category.toEntity())

    suspend fun updateCategory(category: Category) =
        categoryDao.updateCategory(category.toEntity())

    suspend fun deleteCategory(category: Category) =
        categoryDao.deleteCategory(category.toEntity())

    suspend fun deleteCategoryById(id: Long) =
        categoryDao.deleteCategoryById(id)

    suspend fun initializeDefaultCategories() {
        if (!categoryDao.hasCategories()) {
            categoryDao.insertCategories(DefaultCategories.map { it.toEntity() })
        }
    }

    suspend fun insertCategories(categories: List<Category>) =
        categoryDao.insertCategories(categories.map { it.toEntity() })
}
