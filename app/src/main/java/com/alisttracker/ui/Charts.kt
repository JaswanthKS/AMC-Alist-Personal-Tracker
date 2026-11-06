package com.alisttracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alisttracker.data.Movie
import com.alisttracker.data.SubPayment
import java.time.YearMonth
import java.util.Locale

private val monthLetters = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

/** Money charts for the Stats tab. Only AMC visits are counted, like the rest of Stats. */
@Composable
fun SpendingCharts(movies: List<Movie>, subs: List<SubPayment>) {
    val amc = remember(movies) { movies.filter { it.amc } }
    if (amc.isEmpty()) return

    val valueByMonth = remember(amc) {
        amc.filter { it.alist == "Yes" }
            .groupBy { runCatching { YearMonth.parse(it.date.take(7)) }.getOrNull() }
            .filterKeys { it != null }.mapKeys { it.key!! }
            .mapValues { (_, l) -> l.sumOf { it.total } }
    }
    val costByMonth = remember(subs) { subs.associate { YearMonth.of(it.year, it.month) to it.amount } }

    // ---- last 12 months: value vs membership ----
    val now = YearMonth.now()
    val last12 = (11 downTo 0).map { now.minusMonths(it.toLong()) }
    Section("Value vs membership · last 12 months", Green) {
        val vals = last12.map { valueByMonth[it] ?: 0.0 }
        val costs = last12.map { costByMonth[it] ?: 0.0 }
        val max = (vals + costs).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val slot = size.width / last12.size
            val bw = slot * 0.32f
            last12.indices.forEach { i ->
                val x = i * slot + slot / 2
                val hv = (vals[i] / max).toFloat() * size.height
                val hc = (costs[i] / max).toFloat() * size.height
                drawRoundRectBar(Green, x - bw - 1f, size.height - hv, bw, hv)
                drawRoundRectBar(Purple, x + 1f, size.height - hc, bw, hc)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            last12.forEach { Text(monthLetters[it.monthValue - 1], Modifier.weight(1f), fontSize = 11.sp, color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(Green, "A-List value"); LegendDot(Purple, "Membership")
        }
    }

    // ---- cumulative savings ----
    val firstMonth = (valueByMonth.keys + costByMonth.keys).minOrNull()
    if (firstMonth != null) {
        val months = generateSequence(firstMonth) { it.plusMonths(1) }.takeWhile { !it.isAfter(now) }.toList()
        var run = 0.0
        val cum = months.map { run += (valueByMonth[it] ?: 0.0) - (costByMonth[it] ?: 0.0); run }
        if (cum.size >= 2) Section("Total saved over time", Gold) {
            val lo = minOf(0.0, cum.minOrNull() ?: 0.0)
            val hi = maxOf(1.0, cum.maxOrNull() ?: 1.0)
            val span = (hi - lo).coerceAtLeast(1.0)
            Text(money(cum.last()), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (cum.last() >= 0) Green else Red)
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                fun px(i: Int) = i * size.width / (cum.size - 1)
                fun py(v: Double) = size.height - ((v - lo) / span).toFloat() * size.height
                val line = Path().apply { cum.forEachIndexed { i, v -> if (i == 0) moveTo(px(i), py(v)) else lineTo(px(i), py(v)) } }
                val fill = Path().apply {
                    addPath(line); lineTo(px(cum.size - 1), size.height); lineTo(0f, size.height); close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(Gold.copy(alpha = 0.35f), Color.Transparent)))
                val zeroY = py(0.0)
                drawLine(Muted.copy(alpha = 0.5f), Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.5f)
                drawPath(line, Gold, style = Stroke(width = 5f, cap = StrokeCap.Round))
                drawCircle(Gold, 8f, Offset(px(cum.size - 1), py(cum.last())))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(months.first().let { "${monthLetters[it.monthValue - 1]} ${it.year}" }, fontSize = 11.sp, color = Muted, modifier = Modifier.weight(1f))
                Text(months.last().let { "${monthLetters[it.monthValue - 1]} ${it.year}" }, fontSize = 11.sp, color = Muted)
            }
        }
    }

    // ---- spend by format ----
    Section("Ticket value by format", Blue) {
        val byFormat = amc.groupBy { it.format.ifBlank { "Standard" } }
            .map { (f, l) -> f to l.sumOf { it.total } }.filter { it.second > 0 }.sortedByDescending { it.second }
        val total = byFormat.sumOf { it.second }
        if (total <= 0) { Text("No ticket values yet.", color = Muted); return@Section }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(130.dp)) {
                val stroke = 30f
                var start = -90f
                byFormat.forEach { (f, v) ->
                    val sweep = (v / total).toFloat() * 360f
                    drawArc(formatColor(f), start, (sweep - 1.5f).coerceAtLeast(0.5f), false,
                        topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke))
                    start += sweep
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                byFormat.take(6).forEach { (f, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(formatColor(f)))
                        Spacer(Modifier.width(8.dp))
                        Text(f, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(String.format(Locale.US, "%.0f%%", v / total * 100), color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundRectBar(c: Color, x: Float, y: Float, w: Float, h: Float) {
    if (h <= 0f) return
    drawRoundRect(c, Offset(x, y), Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f))
}

@Composable
private fun LegendDot(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = Muted)
    }
}
