package com.alisttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.alisttracker.MainVM
import com.alisttracker.data.Posters
import com.alisttracker.data.WatchItem
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val relFmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

private fun releaseLine(date: String): Pair<String, Color> {
    val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return "Release date unknown" to Muted
    val days = ChronoUnit.DAYS.between(LocalDate.now(), d)
    return when {
        days > 1 -> "Releases ${d.format(relFmt)} · in $days days" to Gold
        days == 1L -> "Releases tomorrow" to Gold
        days == 0L -> "In theaters today" to Green
        else -> "Released ${d.format(relFmt)}" to Muted
    }
}

/** Movies the user wants to see. Shown under the "Watchlist" chip in Movie Log. */
@Composable
fun WatchlistPane(items: List<WatchItem>, vm: MainVM, onLog: (WatchItem) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<WatchItem?>(null) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Button(
                onClick = { adding = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Surface1, contentColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) { Icon(Icons.Default.Add, null, tint = Red); Spacer(Modifier.width(6.dp)); Text("Add a movie to watch") }
        }
        if (items.isEmpty()) item {
            Text("Nothing here yet. Add upcoming movies, then tap \"Log visit\" when you go.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        }
        items(items, key = { it.tmdbId }) { w ->
            val (line, color) = releaseLine(w.releaseDate)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface1).height(IntrinsicSize.Min)
            ) {
                Box(Modifier.width(78.dp).fillMaxHeight().heightIn(min = 112.dp).background(Surface2), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Theaters, null, tint = Muted)
                    if (w.posterPath.isNotBlank()) AsyncImage(
                        "https://image.tmdb.org/t/p/w342${w.posterPath}", w.title,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(w.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White, maxLines = 2, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.Close, "Remove", tint = Muted, modifier = Modifier.size(20.dp).clickable { removing = w })
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(line, fontSize = 13.sp, color = color, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onLog(w) }, shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = Red),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                    ) { Icon(Icons.Default.ConfirmationNumber, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Log visit", fontSize = 13.sp) }
                }
            }
        }
    }

    if (adding) AddWatchDialog(vm, have = items.map { it.tmdbId }.toSet(), onDismiss = { adding = false })
    removing?.let { w ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove \"${w.title}\"?") },
            confirmButton = { TextButton(onClick = { vm.removeWatch(w); removing = null }) { Text("Remove", color = Red) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } })
    }
}

@Composable
private fun AddWatchDialog(vm: MainVM, have: Set<Int>, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Posters.Hit>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { results = emptyList(); searched = false; return@LaunchedEffect }
        delay(450)
        results = vm.searchTmdb(q)
        searched = true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to watchlist") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, label = { Text("Movie title") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                if (searched && results.isEmpty()) Text("No results. Check the spelling or your connection.", color = Muted, fontSize = 13.sp)
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(results, key = { it.id }) { h ->
                        val already = h.id in have
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !already) { vm.addWatch(h); onDismiss() }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.width(42.dp).height(62.dp).clip(RoundedCornerShape(6.dp)).background(Surface2)) {
                                if (h.poster.isNotBlank()) AsyncImage(
                                    "https://image.tmdb.org/t/p/w154${h.poster}", h.title,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(h.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                Text(h.release.take(4).ifBlank { "—" } + if (already) " · already added" else "", fontSize = 12.sp, color = Muted)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
