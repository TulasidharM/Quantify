package com.quantify.app.data.local.dao

import androidx.room.*
import com.quantify.app.data.local.entity.WishlistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WishlistItemDao {
    @Query("SELECT * FROM wishlist_items ORDER BY createdAt DESC")
    fun getAll(): Flow<List<WishlistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WishlistItemEntity): Long

    @Delete
    suspend fun delete(item: WishlistItemEntity)

    @Query("DELETE FROM wishlist_items")
    suspend fun deleteAll()
}
