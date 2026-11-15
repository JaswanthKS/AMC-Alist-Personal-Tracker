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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alisttracker.data.Movie
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayWithYear = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)
private fun niceDay(d: String) = runCatching { LocalDate.parse(d).format(dayWithYear) }.getOrDefault(d)

/**
 * "When did I last watch 2 / 3 / 4 movies in one day?" Pick a number and see every day that had exactly that
 * many visits, newest first. Counts every logged visit (all theaters); a repeat of the same movie counts again.
 */
@Composable
fun MultiMovieDaysSection(movies: List<Movie>) {
    val days = remember(movies) {
        movies.groupBy { it.date }.filter { it.value.size >= 2 }
            .map { (d, l) -> d to l.sortedWith(compareBy<Movie>({ it.showtime }, { it.id })) }
            .sortedByDescending { it.first }
    }
    if (days.isEmpty()) return
    val counts = remember(days) { days.map { it.second.size }.distinct().sorted() }
    var picked by rememberSaveable { mutableIntStateOf(0) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val sel = if (picked in counts) picked else counts.last()
    val matching = remember(days, sel) { days.filter { it.second.size == sel } }
    val shown = if (showAll) matching else matching.take(8)

    Section("Multiple movies in a day", Orange) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            counts.forEach { n ->
                val on = n == sel
                Text(
                    "$n movies", color = if (on) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Orange else Surface2)
                        .clickable { picked = n; showAll = false }.padding(horizontal = 16.dp, vertical = 9.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Last time you watched $sel in one day", color = Muted, fontSize = 12.sp)
        Text(niceDay(matching.first().first), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "${matching.size} day${if (matching.size == 1) "" else "s"} with exactly $sel movies",
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.height(10.dp))
        shown.forEach { (date, visits) ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(niceDay(date), color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                visits.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                        Text(m.title, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(
                            listOfNotNull(m.showtime.ifBlank { null }?.let { prettyTime(it) }, m.format.ifBlank { "Standard" }).joinToString(" · "),
                            color = formatColor(m.format), fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        if (matching.size > 8) {
            Text(
                if (showAll) "Show fewer" else "Show all ${matching.size} days", color = Blue, fontSize = 13.sp,
                modifier = Modifier.clickable { showAll = !showAll }.padding(top = 6.dp)
            )
        }
    }
}
