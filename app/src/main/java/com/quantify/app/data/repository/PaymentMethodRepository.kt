package com.quantify.app.data.repository

import com.quantify.app.data.local.dao.PaymentMethodDao
import com.quantify.app.data.local.entity.PaymentMethodEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentMethodRepository @Inject constructor(
    private val paymentMethodDao: PaymentMethodDao
) {
    fun getAll(): Flow<List<PaymentMethodEntity>> = paymentMethodDao.getAll()
    fun getDefault(): PaymentMethodEntity? = paymentMethodDao.getDefault()
    suspend fun setDefault(id: String) {
        paymentMethodDao.clearAllDefaults()
        paymentMethodDao.setDefault(id)
    }
    suspend fun insert(method: PaymentMethodEntity) = paymentMethodDao.insert(method)
    suspend fun delete(method: PaymentMethodEntity) = paymentMethodDao.delete(method)
    suspend fun deleteAll() = paymentMethodDao.deleteAll()
}
