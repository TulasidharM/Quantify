package com.quantify.app.data.local.dao

import androidx.room.*
import com.quantify.app.data.local.entity.PaymentMethodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentMethodDao {
    @Query("SELECT * FROM payment_methods ORDER BY name")
    fun getAll(): Flow<List<PaymentMethodEntity>>

    @Query("SELECT * FROM payment_methods WHERE isDefault = 1 LIMIT 1")
    fun getDefault(): PaymentMethodEntity?

    @Query("UPDATE payment_methods SET isDefault = 0")
    suspend fun clearAllDefaults()

    @Query("UPDATE payment_methods SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(method: PaymentMethodEntity)

    @Delete
    suspend fun delete(method: PaymentMethodEntity)

    @Query("DELETE FROM payment_methods")
    suspend fun deleteAll()
}
