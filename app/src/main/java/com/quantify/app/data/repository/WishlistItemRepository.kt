package com.quantify.app.data.repository

import com.quantify.app.data.local.dao.WishlistItemDao
import com.quantify.app.data.local.entity.WishlistItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WishlistItemRepository @Inject constructor(
    private val dao: WishlistItemDao
) {
    fun getAll(): Flow<List<WishlistItemEntity>> = dao.getAll()
    suspend fun insert(item: WishlistItemEntity) = dao.insert(item)
    suspend fun delete(item: WishlistItemEntity) = dao.delete(item)
    suspend fun deleteAll() = dao.deleteAll()
}
