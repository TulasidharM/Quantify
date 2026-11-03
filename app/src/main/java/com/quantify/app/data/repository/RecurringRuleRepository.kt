package com.quantify.app.data.repository

import com.quantify.app.data.local.dao.RecurringRuleDao
import com.quantify.app.data.local.entity.RecurringRuleEntity
import com.quantify.app.data.local.entity.TransactionEntity
import com.quantify.app.data.local.dao.TransactionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecurringRuleRepository @Inject constructor(
    private val recurringRuleDao: RecurringRuleDao,
    private val transactionDao: TransactionDao
) {
    fun getAll(): Flow<List<RecurringRuleEntity>> = recurringRuleDao.getAll()
    fun getActive(): Flow<List<RecurringRuleEntity>> = recurringRuleDao.getActive()
    suspend fun insert(rule: RecurringRuleEntity) = recurringRuleDao.insert(rule)
    suspend fun update(rule: RecurringRuleEntity) = recurringRuleDao.update(rule)
    suspend fun delete(rule: RecurringRuleEntity) = recurringRuleDao.delete(rule)
    suspend fun deleteAll() = recurringRuleDao.deleteAll()

    suspend fun processDueRules() {
        val dueRules = recurringRuleDao.getDue()
        for (rule in dueRules) {
            val nextRunEpoch = computeNextRunEpoch(rule)

            // Generate transaction
            val transaction = TransactionEntity(
                amount = rule.amount,
                type = rule.type,
                categoryId = rule.categoryId,
                paymentMethodId = rule.paymentMethodId,
                description = rule.description,
                dateEpochSeconds = Clock.System.now().epochSeconds,
                recurringRuleId = rule.id
            )
            transactionDao.insert(transaction)

            // Update next run date
            val updatedRule = rule.copy(nextRunDateEpochSeconds = nextRunEpoch)
            recurringRuleDao.update(updatedRule)
        }
    }

    private fun computeNextRunEpoch(rule: RecurringRuleEntity): Long {
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val ld = now.toLocalDateTime(tz)
        return when (rule.frequency) {
            "DAILY" -> {
                LocalDateTime(ld.year, ld.monthNumber, ld.dayOfMonth + 1, 0, 0).toInstant(tz).epochSeconds
            }
            "WEEKLY" -> {
                val dayAdded = ld.dayOfMonth + 7
                val daysInCurrentMonth = daysInMonth(ld.year, ld.monthNumber)
                val (wYear, wMonth, wDay) = if (dayAdded > daysInCurrentMonth) {
                    val overflow = dayAdded - daysInCurrentMonth
                    val nextM = if (ld.monthNumber == 12) 1 else ld.monthNumber + 1
                    val nextY = if (ld.monthNumber == 12) ld.year + 1 else ld.year
                    Triple(nextY, nextM, overflow)
                } else {
                    Triple(ld.year, ld.monthNumber, dayAdded)
                }
                LocalDateTime(wYear, wMonth, wDay, 0, 0).toInstant(tz).epochSeconds
            }
            "MONTHLY" -> {
                val nextYear = if (ld.monthNumber == 12) ld.year + 1 else ld.year
                val nextMonth = if (ld.monthNumber == 12) 1 else ld.monthNumber + 1
                val day = (rule.dayOfMonth ?: ld.dayOfMonth).coerceIn(1, daysInMonth(nextYear, nextMonth))
                LocalDateTime(nextYear, nextMonth, day, 0, 0).toInstant(tz).epochSeconds
            }
            "YEARLY" -> {
                val year = ld.year + 1
                val month = ld.monthNumber
                val day = (rule.dayOfMonth ?: ld.dayOfMonth).coerceIn(1, daysInMonth(year, month))
                LocalDateTime(year, month, day, 0, 0).toInstant(tz).epochSeconds
            }
            else -> (now + 30.days).epochSeconds
        }
    }

    private fun daysInMonth(year: Int, month: Int): Int {
        return when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            else -> 30
        }
    }
}
