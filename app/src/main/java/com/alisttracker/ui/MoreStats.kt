package com.alisttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alisttracker.MainVM
import com.alisttracker.data.FilmInfo
import com.alisttracker.data.Movie
import com.alisttracker.data.SubPayment
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val longMonth = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
private val dayFull = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
private fun ld(s: String): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()
private fun lt(s: String): LocalTime? = runCatching { LocalTime.parse(s) }.getOrNull()
private fun dayText(s: String): String = ld(s)?.format(dayFull) ?: s
private fun yearsOf(movies: List<Movie>): List<Int> = movies.mapNotNull { it.date.take(4).toIntOrNull() }.distinct().sortedDescending()
private fun List<Movie>.inYear(y: Int): List<Movie> = if (y == 0) this else filter { it.date.startsWith("$y-") }
private fun plural(n: Int, word: String): String = if (n == 1) "1 $word" else "$n ${word}s"
private fun hm(min: Int): String = if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"

@Composable
private fun YearChips(years: List<Int>, sel: Int, accent: Color, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf(0) + years).forEach { y ->
            val on = y == sel
            Text(
                if (y == 0) "All time" else "$y", color = if (on) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) accent else Surface2)
                    .clickable { onPick(y) }.padding(horizontal = 16.dp, vertical = 9.dp)
            )
        }
    }
}

@Composable
private fun SubHead(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
}

/** Movies per language, for all time or one year. */
@Composable
fun LanguageSection(movies: List<Movie>) {
    if (movies.isEmpty()) return
    val years = remember(movies) { yearsOf(movies) }
    var year by rememberSaveable { mutableIntStateOf(0) }
    val sel = if (year in years) year else 0
    val list = remember(movies, sel) { movies.inYear(sel) }
    val langs: List<Pair<String, Int>> = remember(list) {
        list.groupBy { it.language.trim().ifBlank { "Not set" } }.map { e -> e.key to e.value.size }.sortedByDescending { p -> p.second }
    }
    Section("Languages", Teal) {
        YearChips(years, sel, Teal) { year = it }
        Spacer(Modifier.height(12.dp))
        if (langs.isEmpty()) {
            Text("No movies in $sel", color = Muted, fontSize = 13.sp)
        } else {
            val max = langs.first().second
            langs.forEach { p -> BarRow("${p.first} · ${p.second * 100 / list.size}%", p.second, max, Teal) }
            if (sel == 0 && years.size > 1) {
                SubHead("By year")
                years.forEach { y ->
                    val line = movies.inYear(y).groupBy { it.language.trim().ifBlank { "Not set" } }.map { e -> e.key to e.value.size }
                        .sortedByDescending { p -> p.second }.joinToString(" · ") { p -> "${p.first} ${p.second}" }
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text("$y", color = Muted, fontSize = 12.sp, modifier = Modifier.width(44.dp))
                        Text(line, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private val SLOTS = listOf("Morning (before noon)", "Afternoon (12–5 PM)", "Evening (5–9 PM)", "Late night (after 9 PM)")
private fun slotOf(t: LocalTime): Int = when {
    t.hour < 4 -> 3
    t.hour < 12 -> 0
    t.hour < 17 -> 1
    t.hour < 21 -> 2
    else -> 3
}

/** Which weekdays and showtimes you go for. */
@Composable
fun HabitsSection(movies: List<Movie>) {
    if (movies.isEmpty()) return
    val years = remember(movies) { yearsOf(movies) }
    var year by rememberSaveable { mutableIntStateOf(0) }
    val sel = if (year in years) year else 0
    val list = remember(movies, sel) { movies.inYear(sel) }
    val byDay: IntArray = remember(list) {
        IntArray(7).also { a -> list.forEach { m -> ld(m.date)?.let { a[it.dayOfWeek.value - 1]++ } } }
    }
    val bySlot: IntArray = remember(list) {
        IntArray(4).also { a -> list.forEach { m -> lt(m.showtime)?.let { a[slotOf(it)]++ } } }
    }
    val dayTotal = byDay.sum()
    val slotTotal = bySlot.sum()
    Section("Watching habits", Purple) {
        YearChips(years, sel, Purple) { year = it }
        Spacer(Modifier.height(12.dp))
        if (dayTotal == 0) {
            Text("No movies in $sel", color = Muted, fontSize = 13.sp)
        } else {
            val topDay = byDay.indices.maxByOrNull { byDay[it] } ?: 0
            Text("Favorite day", color = Muted, fontSize = 12.sp)
            Text(
                DayOfWeek.of(topDay + 1).getDisplayName(TextStyle.FULL, Locale.US) + " · ${byDay[topDay] * 100 / dayTotal}%",
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold
            )
            val weekend = byDay[4] + byDay[5] + byDay[6]
            Text("Fri–Sun: ${weekend * 100 / dayTotal}% of visits · Mon–Thu: ${100 - weekend * 100 / dayTotal}%", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            val dmax = byDay.maxOrNull() ?: 1
            for (i in 0 until 7) BarRow(DayOfWeek.of(i + 1).getDisplayName(TextStyle.FULL, Locale.US), byDay[i], dmax, Purple)
            if (slotTotal > 0) {
                val topSlot = bySlot.indices.maxByOrNull { bySlot[it] } ?: 0
                Spacer(Modifier.height(12.dp))
                Text("Favorite showtime", color = Muted, fontSize = 12.sp)
                Text(SLOTS[topSlot] + " · ${bySlot[topSlot] * 100 / slotTotal}%", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                val smax = bySlot.maxOrNull() ?: 1
                for (i in 0 until 4) BarRow(SLOTS[i], bySlot[i], smax, Blue)
                if (slotTotal < dayTotal) Text("${dayTotal - slotTotal} visits have no showtime saved", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

private class Record(val label: String, val value: String, val sub: String)

private fun lateKey(t: LocalTime): Int = (if (t.hour < 4) t.hour + 24 else t.hour) * 60 + t.minute

private fun buildRecords(movies: List<Movie>): List<Record> {
    val out = ArrayList<Record>()
    val dated = movies.filter { ld(it.date) != null }
    if (dated.isEmpty()) return out
    dated.groupBy { it.date.take(7) }.maxByOrNull { it.value.size }?.let { e ->
        val label = runCatching { YearMonth.parse(e.key).format(longMonth) }.getOrDefault(e.key)
        out.add(Record("Biggest month", plural(e.value.size, "movie"), label))
    }
    dated.groupBy { ld(it.date)!!.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.maxByOrNull { it.value.size }?.let { e ->
        out.add(Record("Biggest week", plural(e.value.size, "movie"), "Week of ${e.key.format(dayFull)} (Mon–Sun)"))
    }
    dated.groupBy { it.date }.maxByOrNull { it.value.size }?.let { e ->
        if (e.value.size >= 2) out.add(Record("Most in one day", plural(e.value.size, "movie"), dayText(e.key)))
    }
    dated.groupBy { normTitle(it.title) }.maxByOrNull { it.value.size }?.let { e ->
        if (e.value.size >= 2) out.add(Record("Most-watched movie", "${e.value.size} times", e.value.first().title))
    }
    dated.filter { it.total > 0 }.maxByOrNull { it.total }?.let { m ->
        out.add(Record("Most expensive ticket", money(m.total), "${m.title} · ${m.format.ifBlank { "Standard" }} · ${dayText(m.date)}"))
    }
    dated.filter { it.amc && it.alist == "Yes" }.groupBy { it.date.take(7) }.maxByOrNull { e -> e.value.sumOf { it.total } }?.let { e ->
        val label = runCatching { YearMonth.parse(e.key).format(longMonth) }.getOrDefault(e.key)
        out.add(Record("Best month for free tickets", money(e.value.sumOf { it.total }), "$label · ${plural(e.value.size, "A-List visit")}"))
    }
    val timed = dated.mapNotNull { m -> lt(m.showtime)?.let { m to it } }
    timed.minByOrNull { lateKey(it.second) }?.let { p ->
        out.add(Record("Earliest showtime", prettyTime(p.first.showtime), "${p.first.title} · ${dayText(p.first.date)}"))
    }
    timed.maxByOrNull { lateKey(it.second) }?.let { p ->
        out.add(Record("Latest showtime", prettyTime(p.first.showtime), "${p.first.title} · ${dayText(p.first.date)}"))
    }
    dated.map { m -> m to m.concItems().sumOf { it.price } }.filter { it.second > 0 }.maxByOrNull { it.second }?.let { p ->
        out.add(Record("Biggest concession run", money(p.second), "${p.first.title} · ${dayText(p.first.date)}"))
    }
    return out
}

/** Personal bests across everything logged. */
@Composable
fun RecordsSection(movies: List<Movie>) {
    val records = remember(movies) { runCatching { buildRecords(movies) }.getOrDefault(emptyList()) }
    if (records.isEmpty()) return
    Section("Personal records", Gold) {
        records.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(r.label, color = Muted, fontSize = 12.sp)
                    Text(r.sub, color = Color.White, fontSize = 13.sp, maxLines = 2)
                }
                Spacer(Modifier.width(10.dp))
                Text(r.value, color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}

private class Fact(val label: String, val text: String, val num: Double?)

private fun yearFacts(y: Int, movies: List<Movie>, subs: List<SubPayment>, infos: Map<String, FilmInfo>): List<Fact> {
    val all = movies.inYear(y)
    val yes = all.filter { it.amc && it.alist == "Yes" }
    val value = yes.sumOf { it.total }
    val cost = subs.filter { it.year == y }.sumOf { it.amount }
    val rated = all.filter { it.rating > 0 }
    val conc = all.sumOf { m -> m.concItems().sumOf { it.price } }
    val minutes = all.sumOf { infos[it.title]?.runtime ?: 0 }
    val topFormat = all.groupBy { it.format.ifBlank { "Standard" } }.maxByOrNull { it.value.size }
    val topTheater = all.filter { it.theater.isNotBlank() }.groupBy { it.theater }.maxByOrNull { it.value.size }
    val topMonth = all.groupBy { it.date.substring(5, 7.coerceAtMost(it.date.length)) }.maxByOrNull { it.value.size }
    val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return listOf(
        Fact("Movies", "${all.size}", all.size.toDouble()),
        Fact("Different movies", "${all.map { normTitle(it.title) }.distinct().size}", all.map { normTitle(it.title) }.distinct().size.toDouble()),
        Fact("A-List visits", "${yes.size}", yes.size.toDouble()),
        Fact("Free tickets", money(value), value),
        Fact("Membership paid", money(cost), null),
        Fact("Saved", money(value - cost), value - cost),
        Fact("Avg rating", if (rated.isEmpty()) "—" else String.format(Locale.US, "%.1f ★", rated.map { it.rating }.average()), if (rated.isEmpty()) null else rated.map { it.rating }.average()),
        Fact("Hours watched", if (minutes == 0) "—" else "${minutes / 60} h", if (minutes == 0) null else minutes.toDouble()),
        Fact("Concessions", money(conc), null),
        Fact("Top format", topFormat?.let { "${it.key} (${it.value.size})" } ?: "—", null),
        Fact("Top theater", topTheater?.let { "${it.key} (${it.value.size})" } ?: "—", null),
        Fact("Busiest month", topMonth?.let { e -> (e.key.toIntOrNull()?.let { monthNames.getOrNull(it - 1) } ?: e.key) + " (${e.value.size})" } ?: "—", null)
    )
}

/** Two years side by side. */
@Composable
fun YearCompareSection(movies: List<Movie>, subs: List<SubPayment>, vm: MainVM) {
    val years = remember(movies) { yearsOf(movies.filter { it.date.length >= 7 }) }
    if (years.size < 2) return
    val infos by vm.infos.collectAsState()
    var a by rememberSaveable { mutableIntStateOf(0) }
    var b by rememberSaveable { mutableIntStateOf(0) }
    val ya = if (a in years) a else years[0]
    val yb = if (b in years) b else years[1]
    val fa = remember(movies, subs, infos, ya) { runCatching { yearFacts(ya, movies, subs, infos) }.getOrDefault(emptyList()) }
    val fb = remember(movies, subs, infos, yb) { runCatching { yearFacts(yb, movies, subs, infos) }.getOrDefault(emptyList()) }
    Section("Year vs year", Blue) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Picker("$ya", Modifier.weight(1f), years.map { "$it" to it }) { a = it }
            Picker("$yb", Modifier.weight(1f), years.map { "$it" to it }) { b = it }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Spacer(Modifier.weight(1.1f))
            Text("$ya", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text("$yb", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        for (i in 0 until minOf(fa.size, fb.size)) {
            val x = fa[i]; val y = fb[i]
            val xn = x.num; val yn = y.num
            val xWins = xn != null && yn != null && xn > yn
            val yWins = xn != null && yn != null && yn > xn
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(x.label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1.1f))
                Text(x.text, color = if (xWins) Green else Color.White, fontWeight = if (xWins) FontWeight.ExtraBold else FontWeight.Normal,
                    fontSize = 13.sp, textAlign = TextAlign.End, maxLines = 2, modifier = Modifier.weight(1f))
                Text(y.text, color = if (yWins) Green else Color.White, fontWeight = if (yWins) FontWeight.ExtraBold else FontWeight.Normal,
                    fontSize = 13.sp, textAlign = TextAlign.End, maxLines = 2, modifier = Modifier.weight(1f))
            }
        }
        Text("Green marks the higher number", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Hours in the theater, favorite genres, directors and actors — from the TMDB details of each movie. */
@Composable
fun FilmStatsSection(movies: List<Movie>, vm: MainVM) {
    if (movies.isEmpty()) return
    val infos by vm.infos.collectAsState()
    val progress by vm.infoProgress.collectAsState()
    LaunchedEffect(Unit) { vm.fillMissingInfo() }
    val years = remember(movies) { yearsOf(movies) }
    var year by rememberSaveable { mutableIntStateOf(0) }
    val sel = if (year in years) year else 0
    val list = remember(movies, sel) { movies.inYear(sel) }
    val known = remember(list, infos) { list.filter { infos[it.title] != null } }
    val uniq = remember(known) { known.distinctBy { it.title } }
    val uniqAll = remember(list) { list.distinctBy { it.title }.size }
    val minutes = known.sumOf { infos[it.title]?.runtime ?: 0 }
    val timedVisits = known.count { (infos[it.title]?.runtime ?: 0) > 0 }
    val genres: List<Pair<String, Int>> = remember(known, infos) {
        known.flatMap { infos[it.title]?.genres ?: emptyList() }.groupBy { it }.map { e -> e.key to e.value.size }.sortedByDescending { p -> p.second }.take(6)
    }
    val directors: List<Pair<String, Int>> = remember(uniq, infos) {
        uniq.flatMap { infos[it.title]?.directors ?: emptyList() }.groupBy { it }.map { e -> e.key to e.value.size }.sortedByDescending { p -> p.second }.take(5)
    }
    val actors: List<Pair<String, Int>> = remember(uniq, infos) {
        uniq.flatMap { infos[it.title]?.cast ?: emptyList() }.groupBy { it }.map { e -> e.key to e.value.size }
            .filter { p -> p.second >= 2 }.sortedByDescending { p -> p.second }.take(8)
    }
    val withTime = uniq.filter { (infos[it.title]?.runtime ?: 0) > 0 }
    val longest = withTime.maxByOrNull { infos[it.title]?.runtime ?: 0 }
    val shortest = withTime.minByOrNull { infos[it.title]?.runtime ?: 0 }

    Section("Hours, genres & people", Teal) {
        YearChips(years, sel, Teal) { year = it }
        Spacer(Modifier.height(12.dp))
        if (known.isEmpty()) {
            Text(if (progress != null) "Looking up movie details…" else "No movie details yet for this period. They load when you are online.", color = Muted, fontSize = 13.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Hours watched", String.format(Locale.US, "%.0f", minutes / 60.0), Teal, Modifier.weight(1f))
                Tile("Avg length", if (timedVisits > 0) hm(minutes / timedVisits) else "—", Blue, Modifier.weight(1f))
            }
            if (minutes > 0) Text(String.format(Locale.US, "That is %.1f full days in a theater seat", minutes / 1440.0), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            if (longest != null) Text("Longest: ${longest.title} · ${hm(infos[longest.title]?.runtime ?: 0)}", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            if (shortest != null && shortest.title != longest?.title) Text("Shortest: ${shortest.title} · ${hm(infos[shortest.title]?.runtime ?: 0)}", color = Color.White, fontSize = 13.sp)
            if (genres.isNotEmpty()) {
                SubHead("Top genres (visits)")
                val max = genres.first().second
                genres.forEach { p -> BarRow(p.first, p.second, max, Teal) }
            }
            if (directors.isNotEmpty()) {
                SubHead("Most-watched directors (movies)")
                val max = directors.first().second
                directors.forEach { p -> BarRow(p.first, p.second, max, Gold) }
            }
            if (actors.isNotEmpty()) {
                SubHead("Actors you see most (movies)")
                val max = actors.first().second
                actors.forEach { p -> BarRow(p.first, p.second, max, Purple) }
            }
        }
        val pr = progress
        Text(
            if (pr != null) "Looking up details… ${pr.first} of ${pr.second}" else "Details found for ${uniq.size} of $uniqAll movies",
            color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)
        )
    }
}
