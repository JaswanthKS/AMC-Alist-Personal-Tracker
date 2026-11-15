package com.alisttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import java.util.Locale

private const val ALL_FORMATS = ""
private const val THREE_D = "3D"

private fun Movie.matchesFormat(f: String) = when (f) {
    ALL_FORMATS -> true
    THREE_D -> threeD
    else -> format == f
}

/** Titles compare ignoring case, spacing and punctuation so "Dune: Part Two" and "dune part two" match. */
internal fun normTitle(t: String) = t.lowercase(Locale.US).filter { it.isLetterOrDigit() }

/** Ids of visits that are a repeat: the same movie was already logged on an earlier visit. */
internal fun rewatchIds(movies: List<Movie>): Set<Long> =
    movies.groupBy { normTitle(it.title) }.values.flatMap { visits ->
        visits.sortedWith(compareBy<Movie>({ it.date }, { it.showtime }, { it.id })).drop(1).map { it.id }
    }.toSet()

/**
 * Year + format picker with a movie count, plus the movies seen more than once.
 * Year 0 = all time. Formats are kept exactly as logged (IMAX, IMAX 70mm and 70mm stay separate).
 */
@Composable
fun FormatYearSection(movies: List<Movie>, formatOptions: List<String>) {
    val amc = remember(movies) { movies.filter { it.amc } }
    var year by rememberSaveable { mutableIntStateOf(0) }
    var format by rememberSaveable { mutableStateOf(ALL_FORMATS) }
    var showList by rememberSaveable { mutableStateOf(false) }

    val years = remember(amc) { amc.mapNotNull { it.date.take(4).toIntOrNull() }.distinct().sortedDescending() }
    val formats = remember(amc, formatOptions) {
        (formatOptions + amc.map { it.format }).filter { it.isNotBlank() }.distinct()
    }
    val inYear = remember(amc, year) { if (year == 0) amc else amc.filter { it.date.startsWith("$year") } }
    val matches = remember(inYear, format) {
        inYear.filter { it.matchesFormat(format) }.sortedWith(compareBy<Movie>({ it.date }, { it.showtime }))
    }
    val unique = remember(matches) { matches.map { normTitle(it.title) }.distinct().size }
    val pct = if (inYear.isNotEmpty()) matches.size * 100 / inYear.size else 0
    val yearLabel = if (year == 0) "all time" else "$year"
    val formatLabel = if (format == ALL_FORMATS) "movies" else "$format movies"

    Section("Count by year & format", Blue) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Picker(if (year == 0) "All time" else "$year", Modifier.weight(1f), listOf("All time" to 0) + years.map { "$it" to it }) { year = it }
            Picker(
                if (format == ALL_FORMATS) "All formats" else format, Modifier.weight(1f),
                listOf("All formats" to ALL_FORMATS) + formats.map { it to it } + listOf(THREE_D to THREE_D)
            ) { format = it }
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.clickable { showList = !showList }) {
            Text("${matches.size}", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = if (format == ALL_FORMATS) Color.White else formatColor(format))
            Text(
                if (matches.isEmpty()) "No $formatLabel in $yearLabel" else "$formatLabel in $yearLabel",
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
            )
            if (matches.isNotEmpty()) {
                val extra = buildList {
                    if (unique != matches.size) add("$unique different movies")
                    if (format != ALL_FORMATS && inYear.isNotEmpty()) add("$pct% of $yearLabel's ${inYear.size}")
                }
                if (extra.isNotEmpty()) Text(extra.joinToString(" · "), color = Muted, fontSize = 12.sp)
                Text(if (showList) "Tap to hide list" else "Tap to see the list", color = Blue, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (showList && matches.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            matches.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(prettyDate(m.date), color = Muted, fontSize = 12.sp, modifier = Modifier.width(92.dp))
                    Text(m.title, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    if (format == ALL_FORMATS || format == THREE_D) {
                        Text(m.format.ifBlank { "Standard" }, color = formatColor(m.format), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Rewatches follow the same year and format choice.
    val rewatched = remember(matches) {
        matches.groupBy { normTitle(it.title) }.values.filter { it.size >= 2 }.sortedByDescending { it.size }
    }
    if (rewatched.isNotEmpty()) {
        Section("Watched more than once", Gold) {
            Text("${rewatched.size} movie${if (rewatched.size == 1) "" else "s"} · $yearLabel${if (format == ALL_FORMATS) "" else " · $format"}", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            rewatched.forEach { visits ->
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Row {
                        Text(visits.first().title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("×${visits.size}", color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                    Text(
                        visits.joinToString("  ·  ") { "${prettyDate(it.date)} (${it.format.ifBlank { "Standard" }})" },
                        color = Muted, fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
internal fun <T> Picker(label: String, modifier: Modifier, options: List<Pair<String, T>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surface2).clickable { open = true }
                .padding(start = 14.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Icon(Icons.Default.KeyboardArrowDown, null, tint = Muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, value) -> DropdownMenuItem(text = { Text(text) }, onClick = { onPick(value); open = false }) }
        }
    }
}
