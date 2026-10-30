package com.quantify.app.data.local.dao

import androidx.room.*
import com.quantify.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE type = :type ORDER BY name")
    fun getByType(type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name")
    fun getAll(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Query("SELECT * FROM categories WHERE parentId = :parentId")
    suspend fun getByParentId(parentId: String): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE type = :type AND isDefault = 1 AND parentId IS NULL LIMIT 1")
    suspend fun getByTypeAndIsDefault(type: String): CategoryEntity?
}
