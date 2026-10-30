package com.quantify.app.ui.screens.csvimport

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/**
 * Serializable snapshot of a CSV import session — persisted to DataStore
 * so the user doesn't lose progress on process death or accidental dismiss.
 */
@Serializable
data class CsvImportSession(
    val rows: List<CsvRowSnapshot>,
    val classifications: List<RowClassificationSnapshot>,
    val currentRow: Int
)

@Serializable
data class CsvRowSnapshot(
    val dateEpochSeconds: Long,
    val vendor: String,
    val amount: Double,
    val direction: String,  // "PAID", "RECEIVED", "REFUND"
    val rawDetail: String
)

@Serializable
data class RowClassificationSnapshot(
    val categoryId: String? = null,
    val paymentMethodId: String? = null,
    val label: String = "",
    val ignored: Boolean = false
)

fun RowClassification.toSnapshot() = RowClassificationSnapshot(
    categoryId = categoryId,
    paymentMethodId = paymentMethodId,
    label = label,
    ignored = ignored
)

fun RowClassificationSnapshot.toClassification() = RowClassification(
    categoryId = categoryId,
    paymentMethodId = paymentMethodId,
    label = label,
    ignored = ignored
)

fun ParsedCsvRow.toSnapshot() = CsvRowSnapshot(
    dateEpochSeconds = epochSeconds,
    vendor = vendor,
    amount = amount,
    direction = direction.name,
    rawDetail = rawDetail
)

fun CsvRowSnapshot.fromSnapshot(index: Int): ParsedCsvRow {
    val tz = TimeZone.currentSystemDefault()
    val instant = kotlinx.datetime.Instant.fromEpochSeconds(this.dateEpochSeconds)
    val ldt = instant.toLocalDateTime(tz)
    return ParsedCsvRow(
        index = index,
        date = LocalDate(ldt.year, ldt.monthNumber, ldt.dayOfMonth),
        time = LocalDateTime(ldt.year, ldt.monthNumber, ldt.dayOfMonth, ldt.hour, ldt.minute),
        vendor = vendor,
        amount = amount,
        direction = Direction.valueOf(direction),
        rawDetail = rawDetail
    )
}
