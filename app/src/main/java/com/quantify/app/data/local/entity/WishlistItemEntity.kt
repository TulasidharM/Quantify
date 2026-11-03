package com.quantify.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "wishlist_items")
data class WishlistItemEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val targetAmount: Double,
    val createdAt: Long = System.currentTimeMillis()
)
