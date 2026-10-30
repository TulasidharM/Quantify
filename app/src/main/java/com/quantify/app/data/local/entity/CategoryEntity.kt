package com.quantify.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String, // "INCOME" or "EXPENSE"
    val color: Long = 0xFF000000L,
    val icon: String = "",
    val isDefault: Boolean = false,
    val parentId: String? = null // null = top-level category
)
