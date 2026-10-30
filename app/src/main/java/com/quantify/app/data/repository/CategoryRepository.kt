package com.quantify.app.data.repository

import com.quantify.app.data.local.dao.CategoryDao
import com.quantify.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {
    fun getByType(type: String): Flow<List<CategoryEntity>> = categoryDao.getByType(type)
    fun getAll(): Flow<List<CategoryEntity>> = categoryDao.getAll()
    suspend fun insert(category: CategoryEntity) = categoryDao.insert(category)
    suspend fun delete(category: CategoryEntity) = categoryDao.delete(category)
    suspend fun deleteAll() = categoryDao.deleteAll()
    suspend fun getByParentId(parentId: String) = categoryDao.getByParentId(parentId)
    suspend fun getByTypeAndIsDefault(type: String) = categoryDao.getByTypeAndIsDefault(type)
}
