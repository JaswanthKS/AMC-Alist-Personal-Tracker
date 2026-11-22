package com.alisttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alisttracker.data.Movie
import com.alisttracker.data.SubPayment
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val shortDay = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
private fun d(s: String) = runCatching { LocalDate.parse(s) }.getOrNull()
private fun nice(s: String) = d(s)?.format(shortDay) ?: s

/** Savings by year: what the free A-List tickets were worth vs what the membership cost, month by month. */
@Composable
fun YearSavingsSection(movies: List<Movie>, subs: List<SubPayment>) {
    val years = remember(movies, subs) {
        (movies.filter { it.amc }.mapNotNull { it.date.take(4).toIntOrNull() } + subs.map { it.year }).distinct().sortedDescending()
    }
    if (years.isEmpty()) return
    var year by rememberSaveable { mutableIntStateOf(0) }
    val sel = if (year in years) year else years.first()
    val rows = remember(movies, subs, sel) { (1..12).map { monthSummary(movies, subs, sel, it) } }
    val value = rows.sumOf { it.value }
    val cost = rows.sumOf { it.cost }
    val net = value - cost
    val paidMonths = subs.count { it.year == sel && it.amount > 0 }
    val wonMonths = rows.count { it.paidOff }
    val maxVal = rows.maxOfOrNull { maxOf(it.value, it.cost) }?.coerceAtLeast(1.0) ?: 1.0
    val best = rows.withIndex().filter { it.value.visits > 0 }.maxByOrNull { it.value.net }

    Section("Savings by year", Green) {
        Picker("$sel", Modifier.fillMaxWidth(), years.map { "$it" to it }) { year = it }
        Spacer(Modifier.height(12.dp))
        Text(if (net >= 0) "Saved in $sel" else "Behind in $sel", color = Muted, fontSize = 12.sp)
        Text(money(net), fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = if (net >= 0) Green else Red)
        Text(
            "${money(value)} of free tickets for ${money(cost)} in membership" +
                if (cost > 0) String.format(Locale.US, " · %.1f× return", value / cost) else "",
            color = Color.White, fontSize = 13.sp
        )
        if (paidMonths > 0) Text("$wonMonths of $paidMonths paid months earned back the membership", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        if (best != null) Text("Best month: ${monthNames[best.index]} (+${money(best.value.net)})", color = Gold, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(10.dp))
        rows.forEachIndexed { i, r ->
            if (r.visits == 0 && r.cost <= 0) return@forEachIndexed
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(monthNames[i], color = Muted, fontSize = 12.sp, modifier = Modifier.width(34.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Surface2)) {
                        Box(Modifier.fillMaxWidth((r.value / maxVal).toFloat().coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Green))
                    }
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(Surface2)) {
                        Box(Modifier.fillMaxWidth((r.cost / maxVal).toFloat().coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(4.dp)).background(Purple))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.width(78.dp), horizontalAlignment = Alignment.End) {
                    Text(
                        if (r.cost > 0) (if (r.net >= 0) "+" else "-") + money(kotlin.math.abs(r.net)) else money(r.value),
                        color = if (r.cost <= 0) Color.White else if (r.net >= 0) Green else Red, fontWeight = FontWeight.Bold, fontSize = 13.sp
                    )
                    Text("${r.visits} visit${if (r.visits == 1) "" else "s"}", color = Muted, fontSize = 10.sp)
                }
            }
        }
        Text("Green bar: free tickets · purple bar: membership paid", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

private fun weekStart(date: LocalDate) = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Streaks, longest gap and visit-count milestones. */
@Composable
fun StreaksSection(movies: List<Movie>) {
    val dates = remember(movies) { movies.mapNotNull { d(it.date) }.sorted() }
    if (dates.isEmpty()) return
    val info = remember(dates) {
        val weeks = dates.map { weekStart(it) }.distinct().sorted()
        // longest run of consecutive weeks
        var bestLen = 1; var bestEnd = weeks.first(); var run = 1
        for (i in 1 until weeks.size) {
            run = if (ChronoUnit.WEEKS.between(weeks[i - 1], weeks[i]) == 1L) run + 1 else 1
            if (run > bestLen) { bestLen = run; bestEnd = weeks[i] }
        }
        // current run: counts back from this week (or last week if nothing yet this week)
        val thisWeek = weekStart(LocalDate.now())
        var cur = 0
        var w = if (weeks.last() == thisWeek) thisWeek else thisWeek.minusWeeks(1)
        val set = weeks.toSet()
        while (w in set) { cur++; w = w.minusWeeks(1) }
        // longest gap between consecutive distinct days
        val days = dates.distinct()
        var gap = 0L; var gapFrom = days.first(); var gapTo = days.first()
        for (i in 1 until days.size) {
            val g = ChronoUnit.DAYS.between(days[i - 1], days[i])
            if (g > gap) { gap = g; gapFrom = days[i - 1]; gapTo = days[i] }
        }
        val sinceLast = ChronoUnit.DAYS.between(days.last(), LocalDate.now()).coerceAtLeast(0)
        listOf(cur, bestLen, bestEnd, gap, gapFrom, gapTo, sinceLast)
    }
    val cur = info[0] as Int; val bestLen = info[1] as Int; val bestEnd = info[2] as LocalDate
    val gap = info[3] as Long; val gapFrom = info[4] as LocalDate; val gapTo = info[5] as LocalDate
    val sinceLast = info[6] as Long
    val total = dates.size
    val marks = listOf(1, 10, 25, 50, 75, 100, 150, 200, 250, 300, 400, 500, 750, 1000)
    val reached = marks.filter { it <= total }
    val next = marks.firstOrNull { it > total }

    Section("Streaks & milestones", Gold) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Week streak", "$cur", Gold, Modifier.weight(1f))
            Tile("Best streak", "$bestLen", Orange, Modifier.weight(1f))
        }
        Text(
            if (cur > 0) "$cur week${if (cur == 1) "" else "s"} in a row with a movie" else "No movie this week yet — start a new streak",
            color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "Best: $bestLen week${if (bestLen == 1) "" else "s"} in a row, ending the week of ${nice(bestEnd.toString())}",
            color = Muted, fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        Text("Longest gap between movies", color = Muted, fontSize = 12.sp)
        Text("$gap days", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        if (gap > 0) Text("${nice(gapFrom.toString())} → ${nice(gapTo.toString())}", color = Muted, fontSize = 12.sp)
        Text("Days since your last movie: $sinceLast", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        Text("Milestones", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        reached.reversed().forEach { n ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (n == 1) "First movie" else "Movie #$n", color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(110.dp))
                Text(nice(dates[n - 1].toString()), color = Muted, fontSize = 12.sp)
            }
        }
        if (next != null) Text("Next: movie #$next — ${next - total} to go", color = Blue, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** "AMC Empire 25" has 25 screens: the number at the end of the theater name. */
private fun screenCountFromName(theater: String): Int? =
    Regex("""(\d{1,2})\s*$""").find(theater.trim())?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 2..40 }

/** Per-theater breakdown: pick a theater to see its visits, formats, screens, spend and ratings. */
@Composable
fun TheaterStatsSection(movies: List<Movie>) {
    val theaters = remember(movies) {
        movies.filter { it.theater.isNotBlank() }.groupBy { it.theater }.map { it.key to it.value.size }.sortedByDescending { it.second }
    }
    if (theaters.isEmpty()) return
    var picked by rememberSaveable { mutableStateOf("") }
    var openScreen by remember { mutableStateOf<Int?>(null) }
    val sel = if (theaters.any { it.first == picked }) picked else theaters.first().first
    val list = remember(movies, sel) { movies.filter { it.theater == sel }.sortedBy { it.date } }
    val formats = remember(list) { list.groupBy { it.format.ifBlank { "Standard" } }.map { it.key to it.value.size }.sortedByDescending { it.second } }
    val screens = remember(list) { list.mapNotNull { it.screen }.groupBy { it }.map { it.key to it.value.size }.sortedByDescending { it.second }.take(5) }
    val rated = list.filter { it.rating > 0 }
    val screenVisits: Map<Int, Int> = remember(list) { list.mapNotNull { it.screen }.filter { it > 0 }.groupingBy { it }.eachCount() }
    val namedScreens = screenCountFromName(sel)
    val totalScreens = maxOf(namedScreens ?: 0, screenVisits.keys.maxOrNull() ?: 0)
    val avgTicket = list.map { it.ticket }.average()
    val years = remember(list) { list.groupBy { it.date.take(4) }.map { it.key to it.value.size }.sortedBy { p -> p.first } }

    Section("Theater stats", Red) {
        Picker(sel, Modifier.fillMaxWidth(), theaters.map { "${it.first} (${it.second})" to it.first }) { picked = it }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Visits", "${list.size}", Red, Modifier.weight(1f))
            Tile("Avg ticket", money(avgTicket), Gold, Modifier.weight(1f))
        }
        Text("First visit ${nice(list.first().date)} · latest ${nice(list.last().date)}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        if (rated.isNotEmpty()) Text(
            String.format(Locale.US, "Average rating here: %.1f ★ (%d rated)", rated.map { it.rating }.average(), rated.size),
            color = Gold, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text("Formats here", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        val fmax = formats.maxOfOrNull { it.second } ?: 1
        formats.forEach { BarRow(it.first, it.second, fmax, formatColor(it.first)) }
        if (totalScreens in 1..60) {
            val missing = (1..totalScreens).filter { (screenVisits[it] ?: 0) == 0 }
            val smax = (screenVisits.values.maxOrNull() ?: 1).coerceAtLeast(1)
            Spacer(Modifier.height(10.dp))
            Text("Screens", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Visited ${totalScreens - missing.size} of $totalScreens screens", color = Muted, fontSize = 12.sp)
            (1..totalScreens).chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { n ->
                        val c = screenVisits[n] ?: 0
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (c > 0) androidx.compose.ui.graphics.lerp(Color(0xFF1E3A5F), Blue, c.toFloat() / smax) else Surface2)
                                .clickable(enabled = c > 0) { openScreen = n }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("$n", color = if (c > 0) Color.White else Muted, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            Text(if (c > 0) "$c×" else "—", color = if (c > 0) Color.White else Muted, fontSize = 11.sp)
                        }
                    }
                    repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (missing.isEmpty()) Text("You have been in every screen here", color = Green, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            else Text("Not visited yet: ${missing.joinToString(", ")}", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (namedScreens == null) Text("Screen count taken from the highest screen you logged here", color = Muted, fontSize = 11.sp)
            val noScreen = list.count { it.screen == null }
            if (noScreen > 0) Text("$noScreen visit${if (noScreen == 1) " has" else "s have"} no screen saved", color = Muted, fontSize = 11.sp)
        }
        if (years.size > 1) {
            Spacer(Modifier.height(10.dp))
            Text("By year", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            val ymax = years.maxOf { it.second }
            years.forEach { BarRow(it.first, it.second, ymax, Orange) }
        }
        openScreen?.let { n ->
            val seen = list.filter { it.screen == n }.sortedByDescending { it.date }
            AlertDialog(
                onDismissRequest = { openScreen = null },
                title = { Text("Screen $n · ${seen.size} visit${if (seen.size == 1) "" else "s"}") },
                text = {
                    LazyColumn {
                        items(seen, key = { it.id }) { m ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(nice(m.date), color = Muted, fontSize = 13.sp, modifier = Modifier.width(96.dp))
                                Text(m.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { openScreen = null }) { Text("Close") } }
            )
        }
    }
}
