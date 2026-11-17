package com.quantify.app.ui.screens.csvimport

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class BankStatementPdfResult(
    val rows: List<ParsedCsvRow>,
    val usedOcr: Boolean
)

/** Extracts selectable text first, then uses bundled on-device OCR for scanned pages. */
suspend fun parseBankStatementPdf(
    context: Context,
    uri: Uri,
    password: String
): BankStatementPdfResult = withContext(Dispatchers.IO) {
    PDFBoxResourceLoader.init(context.applicationContext)
    val resolver = context.contentResolver
    val fileSize = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
    require(fileSize <= 30L * 1024 * 1024 || fileSize < 0) {
        "This PDF is over 30 MB. Choose a smaller statement PDF."
    }
    val input = resolver.openInputStream(uri) ?: throw IOException("Could not open the selected PDF.")
    val document = input.use { PDDocument.load(it, password) }
    document.use { pdf ->
        require(pdf.numberOfPages in 1..80) { "Choose a PDF with 1 to 80 pages." }
        if (pdf.isEncrypted && !pdf.currentAccessPermission.canExtractContent()) {
            throw IOException("This PDF does not allow text extraction. Try downloading an unlocked statement from your bank.")
        }

        val stripper = PositionedPdfTextStripper().apply { sortByPosition = true }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
        var usedOcr = false
        val pageLines = (1..pdf.numberOfPages).map { page ->
            stripper.startPage = page
            stripper.endPage = page
            stripper.lines.clear()
            val extracted = stripper.getText(pdf)
            if (extracted.trim().length >= 24) {
                stripper.lines.toList()
            } else {
                usedOcr = true
                val bitmap = PDFRenderer(pdf).renderImage(page - 1, 2.0f, ImageType.RGB)
                try {
                    val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                    result.textBlocks.flatMap { block -> block.lines }.map { line ->
                        val positions = MutableList(line.text.length) { line.boundingBox?.centerX()?.toFloat() ?: 0f }
                        var searchFrom = 0
                        line.elements.forEach { element ->
                            val start = line.text.indexOf(element.text, searchFrom).takeIf { it >= 0 } ?: searchFrom
                            val end = (start + element.text.length).coerceAtMost(positions.size)
                            val centerX = element.boundingBox?.centerX()?.toFloat() ?: 0f
                            for (i in start until end) positions[i] = centerX
                            searchFrom = end
                        }
                        StatementLine(line.text, positions)
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        }
            val rows = parseBankStatementPages(pageLines)
            require(rows.isNotEmpty()) {
                if (usedOcr) "We couldn't identify transaction rows in this PDF. Try a text-based statement or a different statement layout."
                else "This PDF contains no recognizable transaction rows. If it is password-protected, check the password and try again."
            }
            BankStatementPdfResult(rows, usedOcr)
        } finally {
            recognizer.close()
        }
    }
}

private data class StatementLine(val text: String, val characterX: List<Float> = emptyList())

private class PositionedPdfTextStripper : PDFTextStripper() {
    val lines = mutableListOf<StatementLine>()

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        lines += StatementLine(text, textPositions.map { it.xDirAdj })
        super.writeString(text, textPositions)
    }
}

private data class MoneyToken(val start: Int, val end: Int, val amount: Double, val marker: String, val centerX: Float?, val context: String)
private data class StatementColumns(val debitX: Float, val creditX: Float, val balanceX: Float?)
private data class StatementEntry(val lines: MutableList<StatementLine>)

private val statementDateRegex = Regex(
    "(?i)\\b(?:\\d{4}[./-]\\d{1,2}[./-]\\d{1,2}|\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}|\\d{1,2}[./ -](?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[./ ,-]+\\d{2,4}|(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[ -]+\\d{1,2},?[ -]+\\d{2,4})\\b"
)
private val timeRegex = Regex("(?i)\\b(\\d{1,2}):(\\d{2})(?::\\d{2})?\\s*(am|pm)?\\b")
private val moneyRegex = Regex("(?i)(?:(?:₹|Rs\\.?|INR)\\s*)?[-(]?\\d[\\d,]*(?:\\.\\d{1,2})?\\s*(?:(?:CR|DR|C|D)\\b)?[)]?")
private val debitHeaderRegex = Regex("(?i)\\b(?:DEBIT|WITHDRAWALS?|WITHDRAWN|PAID|DR)\\b")
private val creditHeaderRegex = Regex("(?i)\\b(?:CREDIT|DEPOSITS?|RECEIVED|CR)\\b")
private val balanceHeaderRegex = Regex("(?i)\\b(?:RUNNING\\s+)?BALANCE\\b")
private val monthNames = mapOf(
    "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
    "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12
)

/**
 * Bank statements differ by bank and statement version. When available, column positions
 * are matched against the statement's debit, credit/deposit, and balance headings. Rows
 * without a usable column layout fall back to DR/CR markers and transaction wording.
 * Every result is shown for review before it can be saved.
 */
fun parseBankStatementText(text: String): List<ParsedCsvRow> {
    return parseBankStatementPages(text.lineSequence().map { StatementLine(it.trim()) }.toList().let(::listOf))
}

private fun parseBankStatementPages(pages: List<List<StatementLine>>): List<ParsedCsvRow> =
    pages.flatMap { pageLines ->
        val columns = findStatementColumns(pageLines)
        val entries = mutableListOf<StatementEntry>()
        pageLines.map { it.copy(text = it.text.replace('\u00a0', ' ').trim()) }
            .filter { it.text.isNotBlank() }
            .forEach { line ->
                if (statementDateRegex.containsMatchIn(line.text)) {
                    entries += StatementEntry(mutableListOf(line))
                } else if (entries.isNotEmpty() && line.text.length < 220 && !isStatementFooter(line.text)) {
                    entries.last().lines += line
                }
            }

        entries.mapNotNull { entry ->
        val raw = entry.lines.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ").trim()
        val rowLine = entry.lines.first()
        val dateMatch = statementDateRegex.find(rowLine.text) ?: return@mapNotNull null
        val date = parseStatementDate(dateMatch.value) ?: return@mapNotNull null
        val tokens = entry.lines.flatMapIndexed { lineIndex, amountLine ->
            val amountText = if (lineIndex == 0) amountLine.text.removeRange(dateMatch.range) else amountLine.text
            val positionsAfterDate = if (lineIndex == 0) {
                amountLine.characterX.drop((dateMatch.range.last + 1).coerceAtMost(amountLine.characterX.size))
            } else amountLine.characterX
            moneyRegex.findAll(amountText).mapNotNull { match ->
            val valueText = match.value.trim()
            val marker = valueText.uppercase(Locale.ROOT)
            val looksLikeMoney = valueText.contains('.') || valueText.contains(',') || valueText.contains('₹') ||
                valueText.contains("Rs", ignoreCase = true) || valueText.contains("INR", ignoreCase = true) ||
                Regex("(?i)\\b(?:CR|DR|C|D)\\b").containsMatchIn(valueText) ||
                (columns != null && positionsAfterDate.size == amountText.length)
            if (!looksLikeMoney) return@mapNotNull null
            val numeric = valueText.replace(Regex("(?i)CR|DR|\\bC\\b|\\bD\\b|INR|RS\\.?|₹|[(),\\s]"), "").replace(",", "")
            val amount = numeric.toDoubleOrNull()?.let { kotlin.math.abs(it) } ?: return@mapNotNull null
            val centerX = positionsAfterDate.takeIf { it.size == amountText.length }
                ?.subList(match.range.first, (match.range.last + 1).coerceAtMost(positionsAfterDate.size))
                ?.takeIf { it.isNotEmpty() }
                ?.average()?.toFloat()
            if (amount == 0.0) null else MoneyToken(match.range.first, match.range.last + 1, amount, marker, centerX, amountText)
            }
        }
        if (tokens.isEmpty()) return@mapNotNull null

        val amountToken = chooseTransactionAmount(tokens, columns)
        val amountContext = amountToken.context
        val vicinityStart = (amountToken.start - 36).coerceAtLeast(0)
        val vicinityEnd = (amountToken.end + 16).coerceAtMost(amountContext.length)
        val vicinity = amountContext.substring(vicinityStart, vicinityEnd).uppercase(Locale.ROOT)
        val whole = raw.uppercase(Locale.ROOT)
        val direction = directionFromColumn(amountToken, columns) ?: inferDirection(whole, vicinity, amountToken.marker)
        val time = parseStatementTime(raw) ?: LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, 0, 0)
        val cleanedDescription = raw.removeRange(dateMatch.range)
            .replace(timeRegex, " ")
            .replace(moneyRegex, " ")
            .replace(Regex("(?i)\\b(?:DEBIT|CREDIT|WITHDRAWAL|DEPOSIT|BALANCE|VALUE DATE|TRANSACTION DATE|DR|CR)\\b"), " ")
            .replace(Regex("[|:;\\t]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '|', ':', '.')
        val vendor = cleanedDescription.ifBlank { "Bank transaction" }.take(140)
        ParsedCsvRow(
            index = 0,
            date = date,
            time = time,
            vendor = vendor,
            amount = amountToken.amount,
            direction = direction,
            rawDetail = raw.take(500)
        )
        }.toList()
    }.mapIndexed { index, row -> row.copy(index = index) }

private fun findStatementColumns(lines: List<StatementLine>): StatementColumns? {
    for (line in lines) {
        if (!Regex("(?i)\\b(?:DATE|PARTICULARS?|NARRATION|DESCRIPTION|TRANSACTION)\\b").containsMatchIn(line.text)) continue
        val debit = debitHeaderRegex.find(line.text) ?: continue
        val credit = creditHeaderRegex.find(line.text) ?: continue
        val debitX = line.xAt(debit.range) ?: debit.range.first.toFloat()
        val creditX = line.xAt(credit.range) ?: credit.range.first.toFloat()
        if (kotlin.math.abs(debitX - creditX) < 1f) continue
        val balance = balanceHeaderRegex.find(line.text)
        val balanceX = balance?.let { line.xAt(it.range) ?: it.range.first.toFloat() }
        return StatementColumns(debitX, creditX, balanceX)
    }
    return null
}

private fun StatementLine.xAt(range: IntRange): Float? =
    characterX.takeIf { it.size == text.length && range.last < it.size }
        ?.subList(range.first, range.last + 1)
        ?.takeIf { it.isNotEmpty() }
        ?.average()?.toFloat()

private fun chooseTransactionAmount(tokens: List<MoneyToken>, columns: StatementColumns?): MoneyToken {
    if (columns == null) return tokens.first()
    val positioned = tokens.filter { it.centerX != null }
    if (positioned.isEmpty()) return tokens.first()
    return positioned.minByOrNull { token ->
        val debitDistance = kotlin.math.abs(token.centerX!! - columns.debitX)
        val creditDistance = kotlin.math.abs(token.centerX - columns.creditX)
        val transactionColumnDistance = minOf(debitDistance, creditDistance)
        val balanceDistance = columns.balanceX?.let { kotlin.math.abs(token.centerX - it) } ?: Float.MAX_VALUE
        if (balanceDistance < transactionColumnDistance) 10_000f + balanceDistance else transactionColumnDistance
    } ?: tokens.first()
}

private fun directionFromColumn(token: MoneyToken, columns: StatementColumns?): Direction? {
    val x = token.centerX ?: return null
    columns ?: return null
    val debitDistance = kotlin.math.abs(x - columns.debitX)
    val creditDistance = kotlin.math.abs(x - columns.creditX)
    val balanceDistance = columns.balanceX?.let { kotlin.math.abs(x - it) }
    if (balanceDistance != null && balanceDistance < minOf(debitDistance, creditDistance)) return null
    return if (debitDistance < creditDistance) Direction.PAID else Direction.RECEIVED
}

private fun inferDirection(line: String, vicinity: String, amountMarker: String): Direction {
    if ("DR" in amountMarker || "(" in amountMarker || "-" in amountMarker) return Direction.PAID
    if ("CR" in amountMarker) return Direction.RECEIVED
    val debit = Regex("\\b(DEBIT|DR|WITHDRAWAL|WITHDRAW|PAID|PURCHASE|CHARGES|ATM|POS)\\b").containsMatchIn(vicinity)
    val credit = Regex("\\b(CREDIT|CR|DEPOSIT|RECEIVED|REFUND|SALARY|INTEREST|CASHBACK)\\b").containsMatchIn(vicinity)
    if (debit.xor(credit)) return if (debit) Direction.PAID else Direction.RECEIVED
    val lineDebit = Regex("\\b(DEBIT|WITHDRAWAL|WITHDRAW|PAID|PURCHASE|CHARGES|ATM|POS)\\b").containsMatchIn(line)
    val lineCredit = Regex("\\b(CREDIT|DEPOSIT|RECEIVED|REFUND|SALARY|INTEREST|CASHBACK)\\b").containsMatchIn(line)
    return if (lineCredit && !lineDebit) Direction.RECEIVED else Direction.PAID
}

private fun parseStatementDate(value: String): LocalDate? {
    val normalized = value.trim().replace(Regex("\\s+"), " ").replace(',', ' ')
    val formats = listOf("d/M/uuuu", "d-M-uuuu", "d.M.uuuu", "uuuu-M-d", "uuuu/M/d", "d MMM uuuu", "d-MMM-uuuu", "MMM d uuuu", "MMMM d uuuu", "d/M/yy", "d-M-yy", "d MMM yy", "d-MMM-yy")
    for (format in formats) {
        try {
            val parsed = java.time.LocalDate.parse(normalized, DateTimeFormatter.ofPattern(format, Locale.ENGLISH))
            return LocalDate(parsed.year, parsed.monthValue, parsed.dayOfMonth)
        } catch (_: Exception) { }
    }
    val parts = normalized.split(Regex("[./ -]+"))
    if (parts.size == 3) {
        val month = monthNames[parts[1].lowercase(Locale.ROOT).take(4)] ?: monthNames[parts[1].lowercase(Locale.ROOT).take(3)]
        if (month != null) {
            val year = parts[2].toIntOrNull()?.let { if (it < 100) 2000 + it else it } ?: return null
            val day = parts[0].toIntOrNull() ?: return null
            return runCatching { LocalDate(year, month, day) }.getOrNull()
        }
    }
    return null
}

private fun parseStatementTime(value: String): LocalDateTime? {
    val match = timeRegex.find(value) ?: return null
    val hourRaw = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    val meridiem = match.groupValues[3].lowercase(Locale.ROOT)
    val hour = when (meridiem) {
        "pm" -> if (hourRaw == 12) 12 else hourRaw + 12
        "am" -> if (hourRaw == 12) 0 else hourRaw
        else -> hourRaw
    }
    if (hour !in 0..23 || minute !in 0..59) return null
    val date = parseStatementDate(statementDateRegex.find(value)?.value ?: return null) ?: return null
    return LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute)
}

private fun isStatementFooter(line: String) = Regex("(?i)^(page\\s+\\d+|continued|total|opening balance|closing balance|statement of account|account summary)\\b").containsMatchIn(line)

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
    addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
