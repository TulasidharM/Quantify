package com.quantify.app.ui.screens.csvimport

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * One row parsed from a PhonePe CSV. The user will classify these into transactions.
 */
data class ParsedCsvRow(
    val index: Int,
    val date: LocalDate,
    val time: LocalDateTime,
    val vendor: String,
    val amount: Double,
    val direction: Direction,
    val rawDetail: String
) {
    val epochSeconds: Long
        get() = time.toInstant(TimeZone.currentSystemDefault()).epochSeconds
}

enum class Direction {
    PAID,       // "Paid to X" → EXPENSE
    RECEIVED,   // "Received from X" → INCOME
    REFUND      // "Refund from X" → INCOME
}

/**
 * Parses a PhonePe statement CSV. Handles the header rows, quoted fields, and the 8-column layout.
 */
fun parsePhonePeCsv(text: String): List<ParsedCsvRow> {
    val lines = text.lines()
    val headerLineIndex = lines.indexOfFirst { it.trim().startsWith("Date,Time,Transaction Details") }
    if (headerLineIndex < 0) return emptyList()

    val rows = mutableListOf<ParsedCsvRow>()
    for (i in headerLineIndex + 1 until lines.size) {
        val row = splitCsvLine(lines[i])
        if (row.size < 8) continue

        val date = parseDate(row[0].trim()) ?: continue
        val time = parseTime(row[1].trim(), date) ?: continue
        val detail = row[2].trim()
        val direction = parseDirection(detail) ?: continue
        val vendor = parseVendor(detail)
        val amount = row[7].trim().toDoubleOrNull() ?: continue

        rows.add(
            ParsedCsvRow(
                index = rows.size,
                date = date,
                time = time,
                vendor = vendor,
                amount = amount,
                direction = direction,
                rawDetail = detail
            )
        )
    }
    return rows
}

/**
 * Split a CSV line respecting quoted fields (fields wrapped in "..." may contain commas).
 */
private fun splitCsvLine(line: String): List<String> {
    val fields = mutableListOf<String>()
    var i = 0
    val s = line.trim()
    while (i < s.length) {
        if (s[i] == '"') {
            val sb = StringBuilder()
            i++ // skip opening quote
            while (i < s.length) {
                if (s[i] == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') {
                        sb.append('"')
                        i += 2
                    } else {
                        i++ // skip closing quote
                        break
                    }
                } else {
                    sb.append(s[i])
                    i++
                }
            }
            if (i < s.length && s[i] == ',') i++
            fields.add(sb.toString())
        } else {
            val comma = s.indexOf(',', i)
            val end = if (comma >= 0) comma else s.length
            fields.add(s.substring(i, end).trim())
            i = if (comma >= 0) comma + 1 else end
        }
    }
    return fields
}

private val MONTHS = mapOf(
    "Jan" to 1, "Feb" to 2, "Mar" to 3, "Apr" to 4,
    "May" to 5, "Jun" to 6, "Jul" to 7, "Aug" to 8,
    "Sep" to 9, "Sept" to 9, "Oct" to 10, "Nov" to 11, "Dec" to 12
)

private val DATE_PATTERN = Regex("""(\w+)\s+(\d{1,2}),?\s+(\d{4})""")
private val TIME_PATTERN = Regex("""(\d{1,2}):(\d{2})\s*(am|pm)""", RegexOption.IGNORE_CASE)

/** Parses "Oct 28, 2026" → LocalDate */
private fun parseDate(s: String): LocalDate? {
    val m = DATE_PATTERN.find(s) ?: return null
    val groups = m.groupValues
    val month = MONTHS[groups[1]] ?: return null
    val day = groups[2].toInt()
    val year = groups[3].toInt()
    return LocalDate(year, month, day)
}

/** Parses "12:42 pm" → LocalDateTime (combined with the date) */
private fun parseTime(s: String, date: LocalDate): LocalDateTime? {
    val m = TIME_PATTERN.find(s) ?: return null
    val groups = m.groupValues
    var hour = groups[1].toInt()
    val min = groups[2].toInt()
    val ampm = groups[3].lowercase()

    if (ampm == "pm" && hour != 12) hour += 12
    if (ampm == "am" && hour == 12) hour = 0

    return LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, min)
}

private fun parseDirection(detail: String): Direction? {
    val lower = detail.lowercase()
    return when {
        lower.startsWith("refund from") -> Direction.REFUND
        lower.startsWith("received from") -> Direction.RECEIVED
        lower.startsWith("paid to") -> Direction.PAID
        else -> null
    }
}

private fun parseVendor(detail: String): String {
    return detail.removePrefix("Paid to ").removePrefix("Received from ").removePrefix("Refund from ").trim()
}
