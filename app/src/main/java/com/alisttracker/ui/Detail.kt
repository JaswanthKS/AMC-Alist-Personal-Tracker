package com.alisttracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.alisttracker.MainVM
import com.alisttracker.data.Movie
import org.json.JSONObject
import java.util.Locale

private const val IMG = "https://image.tmdb.org/t/p/"

@Composable
fun MovieDetailScreen(m: Movie, vm: MainVM, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var infoJson by remember(m.title) { mutableStateOf<String?>(null) }
    var loading by remember(m.title) { mutableStateOf(true) }
    var tick by remember(m.title) { mutableIntStateOf(0) }
    LaunchedEffect(m.title, tick) {
        loading = true
        infoJson = if (tick == 0) vm.loadInfo(m) else vm.refreshInfo(m)
        loading = false
    }
    val info = remember(infoJson) { infoJson?.let { runCatching { JSONObject(it) }.getOrNull() } }
    var confirmDelete by remember { mutableStateOf(false) }
    var showStub by remember { mutableStateOf(false) }
    var showPosters by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    val accent = formatColor(m.format)
    val poster = m.posterPath.takeIf { it.isNotBlank() && it != "-" } ?: info?.optString("poster", "")?.takeIf { it.isNotBlank() }
    val backdrop = info?.optString("backdrop", "").orEmpty()

    val rawTint = rememberPosterTint(poster?.let { "${IMG}w185$it" })
    val tint by animateColorAsState(rawTint ?: Color(0xFF2A2140), tween(700), label = "tint")
    val pageTop = lerpColor(Bg, tint, 0.55f)
    val pageMid = lerpColor(Bg, tint, 0.22f)
    val heroEnd = lerpColor(pageTop, pageMid, 0.5f)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(pageTop, pageMid, Bg)))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // ---- hero: backdrop, poster, title ----
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(240.dp)) {
                    if (backdrop.isNotBlank()) {
                        AsyncImage("${IMG}w780$backdrop", null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent.copy(alpha = 0.7f), Color(0xFF3A1D5C), Bg))))
                    }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, heroEnd))))
                }
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 150.dp), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.width(116.dp).height(174.dp).clip(RoundedCornerShape(14.dp)).background(Surface2).clickable { showPosters = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Theaters, null, tint = accent)
                        if (poster != null) AsyncImage("${IMG}w342$poster", m.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(5.dp)) {
                            Icon(Icons.Default.Edit, "Change poster", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 3)
                        Spacer(Modifier.height(4.dp))
                        val year = info?.optString("release", "").orEmpty().take(4)
                        val runtime = info?.optInt("runtime", 0) ?: 0
                        val rating = info?.optDouble("rating", 0.0) ?: 0.0
                        val facts = listOfNotNull(
                            year.ifBlank { null },
                            if (runtime > 0) "${runtime / 60}h ${runtime % 60}m" else null,
                            if (rating > 0) "★ " + String.format(Locale.US, "%.1f", rating) else null
                        ).joinToString("  ·  ")
                        if (facts.isNotBlank()) Text(facts, fontSize = 14.sp, color = Gold, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip(m.format.ifBlank { "Standard" }, accent)
                            if (m.threeD) Chip("3D", Color3D)
                            if (m.language.isNotBlank() && m.language != "English") Chip(m.language, Orange)
                        }
                    }
                }
                IconButton(
                    onClick = { tick++ },
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                ) { Icon(Icons.Default.Refresh, "Fetch details again", tint = Color.White) }
                IconButton(
                    onClick = { showStub = true },
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                ) { Icon(Icons.Default.ConfirmationNumber, "Ticket stub", tint = Color.White) }
                IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
            }

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // genres
                val genres = info?.optJSONArray("genres")
                if (genres != null && genres.length() > 0) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in 0 until genres.length()) Chip(genres.getString(i), Purple)
                    }
                }
                val tagline = info?.optString("tagline", "").orEmpty()
                if (tagline.isNotBlank()) Text("“$tagline”", fontStyle = FontStyle.Italic, color = Muted, fontSize = 14.sp)

                // synopsis
                Section("Synopsis", Red) {
                    val overview = info?.optString("overview", "").orEmpty()
                    Text(
                        when {
                            overview.isNotBlank() -> overview
                            loading -> "Loading details…"
                            else -> "No online details found. Check your internet connection and the TMDB key."
                        },
                        color = Color(0xFFD7D8E6), fontSize = 14.sp, lineHeight = 21.sp)
                    val director = info?.optString("director", "").orEmpty()
                    if (director.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text("DIRECTED BY", fontSize = 11.sp, color = Muted, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                        Text(director, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }

                // cast
                val cast = info?.optJSONArray("cast")
                if (cast != null && cast.length() > 0) {
                    Section("Cast", Blue) {
                        val people = (0 until cast.length()).map { cast.getJSONObject(it) }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(people) { p ->
                                Column(Modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(72.dp).clip(CircleShape).background(Surface2), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, null, tint = Muted)
                                        val photo = p.optString("photo", "")
                                        if (photo.isNotBlank() && photo != "null") {
                                            AsyncImage("${IMG}w185$photo", p.optString("name"), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(p.optString("name"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                    Text(p.optString("character"), color = Muted, fontSize = 11.sp, maxLines = 2)
                                }
                            }
                        }
                    }
                }

                // your rating
                Section("Your rating", Gold) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StarRating(m.rating, size = 38.dp) { r -> vm.save(m.copy(rating = if (r == m.rating) 0 else r)) }
                        Spacer(Modifier.width(12.dp))
                        Text(if (m.rating > 0) "${m.rating} / 5" else "Tap to rate", color = Muted, fontSize = 13.sp)
                    }
                }

                // your visit (the spreadsheet data)
                Section("Your visit", accent) {
                    val whenText = listOfNotNull(prettyDate(m.date), m.showtime.ifBlank { null }?.let { prettyTime(it) }).joinToString(" · ")
                    DetailRow("Watched", whenText)
                    DetailRow("Theater", m.theater)
                    DetailRow("Screen", m.screen?.toString().orEmpty())
                    DetailRow("Format", if (m.threeD) "${m.format} · 3D" else m.format, accent)
                    DetailRow("Language", m.language)
                    DetailRow("Ticket", money(m.ticket))
                    DetailRow("Convenience fee", money(m.fee))
                    DetailRow("Total value", money(m.total))
                    DetailRow("A-List", if (m.alist == "No") "Paid" else m.alist, aListColor(m.alist))
                    DetailRow("Swag", m.swag)
                    if (m.concessions > 0) DetailRow("Concessions", money(m.concessions) + if (m.concessionsPay.isNotBlank()) " · ${m.concessionsPay}" else "")
                    DetailRow("Notes", m.notes)
                    if (!m.amc) DetailRow("Counts toward A-List", "No (other theater)")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onEdit, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Red)) {
                        Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text("Edit")
                    }
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Delete, null, tint = Red); Spacer(Modifier.width(6.dp)); Text("Delete", color = Red)
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(16.dp))
            }
        }
    }

    if (showPosters) PosterPickerDialog(m, poster, vm, onDismiss = { showPosters = false })

    if (showStub) StubDialog(m, poster, onDismiss = { showStub = false })

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete \"${m.title}\"?") },
        text = { Text("This removes the visit from your log.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = Red) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
    )
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = Color.White) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.width(130.dp))
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

/** Tappable 1-5 star row. Tapping the current rating again clears it (the caller decides). */
@Composable
fun StarRating(rating: Int, size: androidx.compose.ui.unit.Dp = 32.dp, onChange: (Int) -> Unit) {
    Row {
        for (i in 1..5) {
            Icon(
                if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder, "$i stars",
                tint = if (i <= rating) Gold else Muted,
                modifier = Modifier.size(size).clip(CircleShape).clickable { onChange(if (i == rating) 0 else i) }.padding(2.dp))
        }
    }
}
