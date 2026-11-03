package com.quantify.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    @PrimaryKey
    val id: String,
    val amount: Double,
    val type: String, // "INCOME" or "EXPENSE"
    val categoryId: String,
    val paymentMethodId: String?,
    val description: String?,
    val frequency: String, // "DAILY", "WEEKLY", "MONTHLY", "YEARLY"
    val dayOfMonth: Int? = null, // for monthly/yearly
    val dayOfWeek: Int? = null, // for weekly (0=Sunday..6=Saturday)
    val nextRunDateEpochSeconds: Long,
    val isActive: Boolean = true,
    val createdAtEpochSeconds: Long = Clock.System.now().epochSeconds
) {
    val nextRunDate: Instant
        get() = Instant.fromEpochSeconds(nextRunDateEpochSeconds)

    val createdAt: Instant
        get() = Instant.fromEpochSeconds(createdAtEpochSeconds)
}
