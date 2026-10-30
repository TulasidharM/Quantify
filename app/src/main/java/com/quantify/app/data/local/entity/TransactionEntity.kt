package com.quantify.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val id: String = generateId(),
    val amount: Double,
    val type: String, // "INCOME" or "EXPENSE"
    val categoryId: String,
    val paymentMethodId: String?,
    val description: String?,
    val dateEpochSeconds: Long,
    val createdAtEpochSeconds: Long = Clock.System.now().epochSeconds,
    val tags: String = "", // comma-separated tag IDs
    val recurringRuleId: String? = null
) {
    val date: Instant
        get() = Instant.fromEpochSeconds(dateEpochSeconds)

    val createdAt: Instant
        get() = Instant.fromEpochSeconds(createdAtEpochSeconds)

    companion object {
        private var counter = 0
        @Synchronized
        fun generateId(): String {
            return "${Clock.System.now().epochSeconds}_${counter++}"
        }
    }
}
