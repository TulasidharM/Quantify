package com.quantify.app.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantify.app.ui.screens.dashboard.formatCurrency
import com.quantify.app.ui.theme.categoryColor
import com.quantify.app.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val parentCategorySpending by viewModel.parentCategorySpending.collectAsState()
    val yearlyTrend by viewModel.yearlyTrend.collectAsState()
    val income by viewModel.monthlyIncome.collectAsState()
    val expense by viewModel.monthlyExpense.collectAsState()
    val selectedYear by viewModel.selectedYearFlow.collectAsState()
    val selectedMonth by viewModel.selectedMonthFlow.collectAsState()

    val monthNames = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val currentMonthName = monthNames[selectedMonth - 1]

    var selectedParent by remember { mutableStateOf<DashboardViewModel.ParentCategorySpending?>(null) }
    var subData by remember { mutableStateOf<List<DashboardViewModel.CategorySpending>?>(null) }

    // Fetch subcategory data
    LaunchedEffect(selectedParent?.parentCategory?.id) {
        subData = null
        if (selectedParent != null && selectedParent!!.subCategories.isNotEmpty()) {
            viewModel.subcategorySpendingFor(selectedParent!!.parentCategory.id)
                .collect { subData = it }
        } else {
            subData = emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats") }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
        item {
            com.quantify.app.ui.components.MonthPicker(
                selectedYear = selectedYear,
                selectedMonth = selectedMonth,
                onMonthSelect = { year, month -> viewModel.selectMonth(year, month) }
            )
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Monthly Trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))
            TrendChart(data = yearlyTrend.takeLast(6))
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (parentCategorySpending.isNotEmpty()) {
            item {
                Text("Spending by Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                val total = parentCategorySpending.sumOf { it.amount }
                val slicesForChart = parentCategorySpending.map { DashboardViewModel.CategorySpending(it.parentCategory, it.amount) }
                PieChart(
                    data = slicesForChart,
                    total = total,
                    onSliceClicked = { idx ->
                        if (idx in parentCategorySpending.indices) {
                            selectedParent = parentCategorySpending[idx]
                        }
                    }
                )

                // Legend
                LazyRow(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(slicesForChart, key = { cs -> cs.category.id }) { cs ->
                        val paletteIdx = slicesForChart.indexOf(cs)
                        val color = categoryColor(paletteIdx)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Canvas(modifier = Modifier.size(10.dp), onDraw = { drawCircle(color) })
                            Text(cs.category.name, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text("$currentMonthName Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Income", style = MaterialTheme.typography.labelMedium)
                        Text(formatCurrency(income), style = MaterialTheme.typography.headlineSmall)
                    }
                }
                Card(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Expense", style = MaterialTheme.typography.labelMedium)
                        Text(formatCurrency(expense), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                Card(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Savings", style = MaterialTheme.typography.labelMedium)
                        Text(formatCurrency(income - expense), style = MaterialTheme.typography.headlineSmall, color = if (income >= expense) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
        } // LazyColumn
    } // Scaffold

    // Subcategory drill-down bottom sheet
    selectedParent?.let { parent ->
        if (parent.amount > 0 && parent.subCategories.isNotEmpty()) {
            val data = subData
            val subTotal = (data ?: emptyList()).sumOf { it.amount }
            ModalBottomSheet(
                onDismissRequest = { selectedParent = null },
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(parent.parentCategory.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(formatCurrency(parent.amount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(16.dp))
                    if (data == null) {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (data.isNotEmpty()) {
                        PieChart(
                            data = data,
                            total = subTotal,
                            onSliceClicked = {}
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        data.forEachIndexed { idx, cs ->
                            val color = categoryColor(idx)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Canvas(modifier = Modifier.size(12.dp), onDraw = { drawCircle(color) })
                                    Text(cs.category.name, style = MaterialTheme.typography.bodyMedium)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(formatCurrency(cs.amount), style = MaterialTheme.typography.bodyMedium)
                                    val pct = if (subTotal > 0) String.format("%.0f%%", (cs.amount / subTotal) * 100) else "0%"
                                    Text(pct, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }
                        }
                    } else {
                        Text("No subcategory breakdown — all spending is directly on this category", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 16.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun TrendChart(data: List<DashboardViewModel.MonthlyData>) {
    if (data.isEmpty()) return
    val incomeColor = MaterialTheme.colorScheme.primary
    val expenseColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    Box(modifier = Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 16.dp)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val maxVal = (data.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1.0).toFloat()
            val padding = 30f
            val chartWidth = size.width - padding * 2
            val chartHeight = size.height - padding * 2
            val stepX = if (data.size > 1) chartWidth / (data.size - 1) else chartWidth / 2

            repeat(5) { i ->
                val y = padding + (chartHeight / 4) * i
                drawLine(gridColor, Offset(padding, y), Offset(size.width - padding, y), strokeWidth = 1f)
            }

            data.forEachIndexed { i, d ->
                val x = (padding + i * stepX).toFloat()
                val y = (padding + chartHeight - (d.income / maxVal) * chartHeight).toFloat()
                if (i > 0) {
                    val prev = data[i - 1]
                    val prevX = (padding + (i - 1) * stepX).toFloat()
                    val prevY = (padding + chartHeight - (prev.income / maxVal) * chartHeight).toFloat()
                    drawLine(incomeColor, Offset(prevX, prevY), Offset(x, y), strokeWidth = 2f)
                }
                drawCircle(incomeColor, center = Offset(x, y), radius = 4f)
            }

            data.forEachIndexed { i, d ->
                val x = (padding + i * stepX).toFloat()
                val y = (padding + chartHeight - (d.expense / maxVal) * chartHeight).toFloat()
                if (i > 0) {
                    val prev = data[i - 1]
                    val prevX = (padding + (i - 1) * stepX).toFloat()
                    val prevY = (padding + chartHeight - (prev.expense / maxVal) * chartHeight).toFloat()
                    drawLine(expenseColor, Offset(prevX, prevY), Offset(x, y), strokeWidth = 2f)
                }
                drawCircle(expenseColor, center = Offset(x, y), radius = 4f)
            }
        }
    }
}

private data class PieSlice(
    val color: Color,
    val label: String,
    val startAngle: Float,
    val sweepAngle: Float,
    val percentage: Float
)

@Composable
fun PieChart(
    data: List<DashboardViewModel.CategorySpending>,
    total: Double,
    onSliceClicked: (Int) -> Unit
) {
    if (data.isEmpty() || total == 0.0) return
    val density = LocalDensity.current.density
    val textColor = MaterialTheme.colorScheme.onSurface
    val colors = data.mapIndexed { i, _ -> categoryColor(i) }
    val slices = buildSlicesWithColors(data, total, colors)
    val hasLabels = slices.any { it.sweepAngle > 10f }
    val pieRadiusFraction = 0.44f  // 44% of half-width

    // Estimate chart height: pie diameter + label column height on each side
    val labelCountLeft = slices.count { it.sweepAngle >= 10f && kotlin.math.cos((it.startAngle + it.sweepAngle / 2f) * kotlin.math.PI / 180.0).toFloat() < 0 }
    val labelCountRight = slices.count { it.sweepAngle >= 10f && kotlin.math.cos((it.startAngle + it.sweepAngle / 2f) * kotlin.math.PI / 180.0).toFloat() >= 0 }
    val maxLabels = maxOf(labelCountLeft, labelCountRight)
    val labelColumnHeight = (maxLabels * 32).dp  // ~32dp per label block
    // Pie diameter = canvasWidth * 0.44 ≈ (screenWidth - 16dp) * 0.44 ≈ 166dp on typical screen
    // But the canvas height also includes label area below/above the pie.
    // Content height = bottomExtent - topExtent, where bottomExtent includes label bottoms from simulation.
    // Estimate: pie_diameter + max_label_column_height (labels extend below pie on one side)
    val pieDiameterEstimate = 170.dp
    val chartHeight = if (hasLabels) pieDiameterEstimate + labelColumnHeight else 260.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(chartHeight)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(slices) {
                    detectTapGestures { offset: Offset ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = (minOf(size.width, size.height) / 2f) * pieRadiusFraction
                        val dx = offset.x - c.x
                        val dy = offset.y - c.y
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy).toFloat()
                        if (dist <= r && dist > 5f) {
                            val tapAngle = (kotlin.math.atan2(dy.toDouble(), dx.toDouble()) * (180.0 / kotlin.math.PI)).toFloat()
                            var found = -1
                            var cumulative = -90f
                            for (i in slices.indices) {
                                val sweep = slices[i].sweepAngle
                                val rel = ((tapAngle - cumulative) % 360f + 360f) % 360f
                                if (rel < sweep) { found = i; break }
                                cumulative = cumulative + sweep
                            }
                            if (found >= 0) onSliceClicked(found)
                        }
                    }
                }
        ) {
            val pieRadius = (minOf(size.width, size.height) / 2f) * pieRadiusFraction

            // Compute content extent first, then center the pie vertically within the canvas
            val centerY: Float
            if (hasLabels) {
                val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = (11 * density)
                }
                val fontMetrics = textPaint.fontMetrics
                val lineHeight = (fontMetrics.descent - fontMetrics.ascent)
                val labelBlock = lineHeight * 2 + 6f
                val labelTopY = -fontMetrics.ascent

                // Find the furthest label bottom on each side to know content extent
                data class SimLabelInfo(val slice: PieSlice, val edgeX: Float, val edgeY: Float, val isRight: Boolean)
                val tempCenter = Offset(size.width / 2f, 0f)  // Y=0 for simulation
                val tempLeft = mutableListOf<SimLabelInfo>()
                val tempRight = mutableListOf<SimLabelInfo>()
                slices.forEach { slice ->
                    if (slice.sweepAngle < 10f) return@forEach
                    val midAngle = slice.startAngle + slice.sweepAngle / 2f
                    val cosA = kotlin.math.cos(midAngle * kotlin.math.PI / 180.0).toFloat()
                    val sinA = kotlin.math.sin(midAngle * kotlin.math.PI / 180.0).toFloat()
                    val edgeX = tempCenter.x + cosA * pieRadius
                    val edgeY = tempCenter.y + sinA * pieRadius
                    if (cosA >= 0) tempRight.add(SimLabelInfo(slice, edgeX, edgeY, true))
                    else tempLeft.add(SimLabelInfo(slice, edgeX, edgeY, false))
                }
                tempLeft.sortBy { it.edgeY }
                tempRight.sortBy { it.edgeY }

                var prevBottom = -999f
                var maxLeftBottom = 0f
                tempLeft.forEach { info ->
                    val desiredBottom = info.edgeY + labelTopY
                    val actualBottom = maxOf(desiredBottom, prevBottom + labelBlock)
                    maxLeftBottom = maxOf(maxLeftBottom, actualBottom + lineHeight)
                    prevBottom = actualBottom
                }

                prevBottom = -999f
                var maxRightBottom = 0f
                tempRight.forEach { info ->
                    val desiredBottom = info.edgeY + labelTopY
                    val actualBottom = maxOf(desiredBottom, prevBottom + labelBlock)
                    maxRightBottom = maxOf(maxRightBottom, actualBottom + lineHeight)
                    prevBottom = actualBottom
                }

                val maxBottom = maxOf(maxLeftBottom, maxRightBottom)
                val bottomExtent = maxOf(pieRadius, maxBottom)
                val topExtent = -pieRadius  // top of pie relative to center
                val contentHeight = bottomExtent - topExtent
                val offsetY = (size.height - contentHeight) / 2f
                centerY = offsetY - topExtent  // = offsetY + pieRadius
            } else {
                centerY = size.height / 2f
            }
            val center = Offset(size.width / 2f, centerY)

            // Draw pie slices
            slices.forEach { slice ->
                val slicePath = Path()
                slicePath.moveTo(center.x, center.y)
                val sliceRect = Rect(
                    Offset(center.x - pieRadius, center.y - pieRadius),
                    Size(pieRadius * 2f, pieRadius * 2f)
                )
                slicePath.arcTo(sliceRect, slice.startAngle, slice.sweepAngle, false)
                slicePath.close()
                drawPath(slicePath, slice.color)
            }

            if (hasLabels) {
                val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.textSize = (11 * density)
                }
                val fontMetrics = textPaint.fontMetrics
                val lineHeight = (fontMetrics.descent - fontMetrics.ascent)
                val labelBlock = lineHeight * 2 + 6f  // two lines + gap
                val labelBottomY = -fontMetrics.ascent  // baseline offset from anchor

                val argb = ((textColor.alpha * 255).toInt() shl 24) or
                    ((textColor.red * 255).toInt() shl 16) or
                    ((textColor.green * 255).toInt() shl 8) or
                    ((textColor.blue * 255).toInt())

                data class LabelInfo(val slice: PieSlice, val edgeX: Float, val edgeY: Float, val isRight: Boolean)
                val leftList = mutableListOf<LabelInfo>()
                val rightList = mutableListOf<LabelInfo>()

                slices.forEach { slice ->
                    if (slice.sweepAngle < 10f) return@forEach
                    val midAngle = slice.startAngle + slice.sweepAngle / 2f
                    val cosA = kotlin.math.cos(midAngle * kotlin.math.PI / 180.0).toFloat()
                    val sinA = kotlin.math.sin(midAngle * kotlin.math.PI / 180.0).toFloat()
                    val edgeX = center.x + cosA * pieRadius
                    val edgeY = center.y + sinA * pieRadius
                    if (cosA >= 0) rightList.add(LabelInfo(slice, edgeX, edgeY, true))
                    else leftList.add(LabelInfo(slice, edgeX, edgeY, false))
                }
                leftList.sortBy { it.edgeY }
                rightList.sortBy { it.edgeY }

                // Label X: fixed distance beyond the pie edge
                val labelGap = 20f * density
                val leftLabelX = center.x - pieRadius - labelGap
                val rightLabelX = center.x + pieRadius + labelGap

                textPaint.color = argb

                // Left labels — right-aligned, stack from edge Y with overlap prevention
                var prevLeftBottom = -999f
                leftList.forEach { info ->
                    val desiredBottom = info.edgeY + labelBottomY
                    val actualBottom = maxOf(desiredBottom, prevLeftBottom + labelBlock)
                    val baseline = actualBottom - lineHeight

                    // Leader line: straight from edge to a point halfway to the label, then horizontal
                    val midX = info.edgeX - (info.edgeX - leftLabelX) * 0.5f
                    drawLine(info.slice.color, Offset(info.edgeX, info.edgeY), Offset(midX, info.edgeY), strokeWidth = 1.5f)
                    drawLine(info.slice.color, Offset(midX, info.edgeY), Offset(midX, baseline), strokeWidth = 1.5f)
                    drawLine(info.slice.color, Offset(midX, baseline), Offset(leftLabelX, baseline), strokeWidth = 1.5f)

                    textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    drawContext.canvas.nativeCanvas.apply {
                        drawText(info.slice.label, leftLabelX, baseline, textPaint)
                        drawText("${info.slice.percentage.toInt()}%", leftLabelX, baseline + lineHeight, textPaint)
                    }
                    prevLeftBottom = actualBottom
                }

                // Right labels — left-aligned
                var prevRightBottom = -999f
                rightList.forEach { info ->
                    val desiredBottom = info.edgeY + labelBottomY
                    val actualBottom = maxOf(desiredBottom, prevRightBottom + labelBlock)
                    val baseline = actualBottom - lineHeight

                    val midX = info.edgeX + (rightLabelX - info.edgeX) * 0.5f
                    drawLine(info.slice.color, Offset(info.edgeX, info.edgeY), Offset(midX, info.edgeY), strokeWidth = 1.5f)
                    drawLine(info.slice.color, Offset(midX, info.edgeY), Offset(midX, baseline), strokeWidth = 1.5f)
                    drawLine(info.slice.color, Offset(midX, baseline), Offset(rightLabelX, baseline), strokeWidth = 1.5f)

                    textPaint.textAlign = android.graphics.Paint.Align.LEFT
                    drawContext.canvas.nativeCanvas.apply {
                        drawText(info.slice.label, rightLabelX, baseline, textPaint)
                        drawText("${info.slice.percentage.toInt()}%", rightLabelX, baseline + lineHeight, textPaint)
                    }
                    prevRightBottom = actualBottom
                }
            }
        }
    }
}

private fun buildSlicesWithColors(
    data: List<DashboardViewModel.CategorySpending>,
    total: Double,
    colors: List<Color>
): List<PieSlice> {
    val slices = mutableListOf<PieSlice>()
    var startAngle = -90f
    data.forEachIndexed { i, cs ->
        val sweep = ((cs.amount.toFloat() / total.toFloat()) * 360f)
        val pct = ((cs.amount.toFloat() / total.toFloat()) * 100f)
        slices.add(PieSlice(
            color = colors.getOrNull(i) ?: Color.Gray,
            label = cs.category.name,
            startAngle = startAngle,
            sweepAngle = sweep,
            percentage = pct
        ))
        startAngle += sweep
    }
    return slices
}
