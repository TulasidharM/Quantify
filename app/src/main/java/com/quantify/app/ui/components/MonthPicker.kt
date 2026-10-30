package com.quantify.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.launch

private val MONTH_NAMES = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/**
 * Horizontal scrollable month picker. Scrolls to the selected month on first composition.
 */
@Composable
fun MonthPicker(
    selectedYear: Int,
    selectedMonth: Int,
    onMonthSelect: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tz = TimeZone.currentSystemDefault()
    val now = remember { Clock.System.now().toLocalDateTime(tz) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Generate months: 12 before current to 12 after current (25 months total)
    val months = remember(now.year, now.monthNumber) {
        buildList {
            for (offset in -12..12) {
                var year = now.year + (now.monthNumber - 1 + offset) / 12
                var month = (now.monthNumber - 1 + offset) % 12 + 1
                if (month < 1) { month += 12; year -= 1 }
                add(Pair(year, month))
            }
        }
    }

    LaunchedEffect(selectedYear, selectedMonth) {
        val idx = months.indexOfFirst { it.first == selectedYear && it.second == selectedMonth }
        if (idx >= 0) {
            scope.launch {
                listState.animateScrollToItem(idx)
            }
        }
    }

    val density = LocalDensity.current
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(months, key = { "${it.first}-${it.second}" }) { (year, month) ->
            val isSelected = year == selectedYear && month == selectedMonth
            val isCurrentMonth = year == now.year && month == now.monthNumber

            Card(
                modifier = Modifier
                    .clickable { onMonthSelect(year, month) }
                    .widthIn(min = 64.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        MONTH_NAMES[month - 1],
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        "$year",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    if (isCurrentMonth && !isSelected) {
                        Text(
                            "Current",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
