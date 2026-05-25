package com.example.ui.screens

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TransactionEntry

@Composable
fun CategoryPieChart(
    transactions: List<TransactionEntry>,
    currencySymbol: String,
    modifier: Modifier = Modifier
) {
    val expenses = transactions.filter { it.type == "EXPENSE" }
    val totalExpense = expenses.sumOf { it.amount }

    if (totalExpense <= 0.0) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No expenses recorded to chart budget categories.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
        return
    }

    // Grouping & computing slice proportions
    val grouped = expenses.groupBy { it.category }
        .mapValues { it.value.sumOf { tx -> tx.amount } }

    val colorsMap = mapOf(
        "Food" to Color(0xFFE57373),
        "Travel" to Color(0xFF64B5F6),
        "Bills" to Color(0xFFBA68C8),
        "Shopping" to Color(0xFF4DB6AC),
        "Other" to Color(0xFF90A4AE)
    )

    // Order segments
    val segments = grouped.map { (cat, amt) ->
        val pct = amt / totalExpense
        val color = colorsMap[cat] ?: Color(0xFF81C784)
        PieSegment(cat, amt, pct.toFloat(), color)
    }.sortedByDescending { it.amount }

    var animationTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animationTriggered = true
    }
    val animatedProgress by animateFloatAsState(
        targetValue = if (animationTriggered) 1f else 0f,
        animationSpec = tween(durationMillis = 1000)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pie/Donut Chart canvas
        Box(
            modifier = Modifier
                .size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(150.dp)) {
                var startAngle = -90f
                val arcWidth = (size.width - 32f).coerceAtLeast(1f)
                val arcHeight = (size.height - 32f).coerceAtLeast(1f)
                for (segment in segments) {
                    val sweepAngle = segment.percentage * 360f * animatedProgress
                    drawArc(
                        color = segment.color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 32f, cap = StrokeCap.Round),
                        size = Size(arcWidth, arcHeight),
                        topLeft = Offset(16f, 16f)
                    )
                    startAngle += sweepAngle
                }
            }

            // Central balance text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Total Spent",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$currencySymbol${String.format("%.0f", totalExpense)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Legend details
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            for (segment in segments) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(segment.color)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = segment.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$currencySymbol${String.format("%.2f", segment.amount)} (${String.format("%.1f", segment.percentage * 100)}%)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

data class PieSegment(
    val name: String,
    val amount: Double,
    val percentage: Float,
    val color: Color
)

@Composable
fun DailySpendingBarChart(
    transactions: List<TransactionEntry>,
    currencySymbol: String,
    budgetLimit: Double,
    modifier: Modifier = Modifier
) {
    val dayExpenses = transactions.filter { it.type == "EXPENSE" }
        .groupBy { it.day }
        .mapValues { it.value.sumOf { tx -> tx.amount } }

    val daysInMonth = 31
    // Fill all days
    val dailyValues = FloatArray(daysInMonth) { dayIdx ->
        (dayExpenses[dayIdx + 1] ?: 0.0).toFloat()
    }

    val maxVal = dailyValues.maxOrNull()?.coerceAtLeast(100f) ?: 100f
    val highlightThreshold = (budgetLimit / daysInMonth).toFloat() // Recommended daily average budget limit

    var animationTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animationTriggered = true
    }
    val animProgress by animateFloatAsState(
        targetValue = if (animationTriggered) 1f else 0f,
        animationSpec = tween(durationMillis = 1000)
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Daily Expenses & Warning Threshold",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val gridLineColor = MaterialTheme.colorScheme.outlineVariant
        val textCol = MaterialTheme.colorScheme.onSurfaceVariant
        val primaryColor = MaterialTheme.colorScheme.primary
        val warningColor = Color(0xFFEF6C00) // Deep Warning Orange

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val paddingLeft = 45f
                val paddingRight = 15f
                val paddingTop = 20f
                val paddingBottom = 40f

                val chartWidth = (size.width - paddingLeft - paddingRight).coerceAtLeast(1f)
                val chartHeight = (size.height - paddingTop - paddingBottom).coerceAtLeast(1f)

                // Draw Y-axis Guideline levels (0, 50%, 100%)
                val levels = listOf(0f, 0.5f, 1f)
                val paint = Paint()
                for (level in levels) {
                    val y = paddingTop + chartHeight * (1f - level)
                    // Draw dashed horizontal line matching grid guidelines
                    drawLine(
                        color = gridLineColor,
                        start = Offset(paddingLeft, y),
                        end = Offset(size.width - paddingRight, y),
                        strokeWidth = 1f
                    )
                }

                // Draw X-axis line
                drawLine(
                    color = textCol,
                    start = Offset(paddingLeft, paddingTop + chartHeight),
                    end = Offset(size.width - paddingRight, paddingTop + chartHeight),
                    strokeWidth = 2f
                )

                // Render critical Average Daily Budget Guide Line
                val budgetY = paddingTop + chartHeight * (1f - (highlightThreshold / maxVal).coerceAtMost(1f))
                drawLine(
                    color = Color.Red.copy(alpha = 0.6f),
                    start = Offset(paddingLeft, budgetY),
                    end = Offset(size.width - paddingRight, budgetY),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )

                // Draw Bars (1 to 31)
                val barSpacing = 2f
                val totalBarsWidth = (chartWidth - (barSpacing * (daysInMonth - 1))).coerceAtLeast(1f)
                val barWidth = (totalBarsWidth / daysInMonth).coerceAtLeast(1f)

                for (dayIdx in 0 until daysInMonth) {
                    val value = dailyValues[dayIdx]
                    val barHeight = ((value / maxVal) * chartHeight * animProgress).coerceAtLeast(0f)
                    val x = paddingLeft + dayIdx * (barWidth + barSpacing)
                    val y = paddingTop + chartHeight - barHeight

                    val isHigh = value > highlightThreshold
                    val barColor = if (isHigh) warningColor else primaryColor

                    if (barHeight > 0) {
                        drawRect(
                            color = barColor,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight)
                        )
                    }

                    // Label occasional X coordinates (e.g. Day 1, 5, 10, 15, 20, 25, 31)
                    val day = dayIdx + 1
                    if (day == 1 || day == 5 || day == 10 || day == 15 || day == 20 || day == 25 || day == 31) {
                        // We will let text rendering handle labels nicely below canvas or draw lines
                        drawLine(
                            color = textCol,
                            start = Offset(x + barWidth / 2f, paddingTop + chartHeight),
                            end = Offset(x + barWidth / 2f, paddingTop + chartHeight + 6f),
                            strokeWidth = 1.5f
                        )
                    }
                }
            }

            // Draw text values overlaid for visual indicators (0, Max value)
            Text(
                text = "$currencySymbol${maxVal.toInt()}",
                fontSize = 10.sp,
                color = textCol,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp)
            )
            Text(
                text = "${currencySymbol}0",
                fontSize = 10.sp,
                color = textCol,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 2.dp, bottom = 28.dp)
            )
        }

        // Custom X label row with Compose
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 45.dp, end = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Day 1", fontSize = 10.sp, color = textCol)
            Text("Day 10", fontSize = 10.sp, color = textCol)
            Text("Day 20", fontSize = 10.sp, color = textCol)
            Text("Day 31", fontSize = 10.sp, color = textCol)
        }

        // Legend detail description
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.size(10.dp).background(primaryColor))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Normal Spend", fontSize = 11.sp, color = textCol)

            Spacer(modifier = Modifier.width(16.dp))

            Box(modifier = Modifier.size(10.dp).background(warningColor))
            Spacer(modifier = Modifier.width(4.dp))
            Text("High Spend (> $currencySymbol${String.format("%.0f", highlightThreshold)}/day)", fontSize = 11.sp, color = textCol)

            Spacer(modifier = Modifier.width(16.dp))

            Box(modifier = Modifier.height(2.dp).width(20.dp).background(Color.Red))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Budget Warning Limit", fontSize = 11.sp, color = textCol)
        }
    }
}
