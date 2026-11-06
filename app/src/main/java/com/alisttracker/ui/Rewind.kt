package com.alisttracker.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.alisttracker.MainVM
import com.alisttracker.data.Movie
import com.alisttracker.data.SubPayment
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun RewindScreen(movies: List<Movie>, subs: List<SubPayment>, vm: MainVM, grad: List<Color>, onOpen: (Movie) -> Unit) {
    val runtimes by vm.runtimes.collectAsState()
    val last = remember { LocalDate.now().minusMonths(1) }
    var year by remember { mutableIntStateOf(last.year) }
    var month by remember { mutableIntStateOf(last.monthValue) }
    val years = remember(movies) {
        (movies.mapNotNull { it.date.take(4).toIntOrNull() } + last.year).distinct().sortedDescending()
    }
    val prefix = if (month == 0) String.format(Locale.US, "%04d", year) else String.format(Locale.US, "%04d-%02d", year, month)
    val list = remember(movies, prefix) {
        movies.filter { it.amc && it.date.startsWith(prefix) }.sortedWith(compareBy<Movie>({ it.date }, { it.showtime }))
    }
    // make sure runtimes are known for this month's movies (fetched once, then saved)
    LaunchedEffect(prefix, list.size) {
        list.forEach { if (!runtimes.containsKey(it.title)) vm.loadInfo(it) }
    }

    val yearMode = month == 0
    val monthName = if (yearMode) "$year" else Month.of(month).getDisplayName(TextStyle.FULL, Locale.US)
    val subFee = subs.filter { it.year == year && (yearMode || it.month == month) }.sumOf { it.amount }
    val aListValue = list.filter { it.alist == "Yes" }.sumOf { it.total }
    val savings = aListValue - subFee
    val outOfPocket = list.filter { it.alist == "No" }.sumOf { it.total }
    val perMovie = if (list.isNotEmpty()) (subFee + outOfPocket) / list.size else 0.0
    val minutes = list.sumOf { runtimes[it.title] ?: 0 }
    val hours = String.format(Locale.US, "%.1f hr", minutes / 60.0)
    val plf = list.count { it.format.isNotBlank() && it.format != "Standard" }
    val theaters = list.map { it.theater }.filter { it.isNotBlank() }.distinct().size
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()

    BoxWithConstraints(Modifier.fillMaxSize()) {
    val minBody = maxHeight - 64.dp
    Column(
        Modifier.fillMaxSize()
            .background(grad.last())
            .verticalScroll(rememberScrollState())
    ) {
        // year picker + share
        Row(Modifier.fillMaxWidth().background(grad.first()).padding(top = 12.dp, start = 16.dp, end = 16.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            var open by remember { mutableStateOf(false) }
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f)).clickable { open = true }
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$year", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    years.forEach { y -> DropdownMenuItem(text = { Text("$y") }, onClick = { year = y; open = false }) }
                }
            }
            Spacer(Modifier.width(10.dp))
            IconButton(
                onClick = {
                    scope.launch {
                        val bmp = layer.toImageBitmap().asAndroidBitmap()
                        val file = withContext(Dispatchers.IO) {
                            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
                            File(dir, "rewind_${year}_${month}.png").also { f ->
                                f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                            }
                        }
                        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(Intent.createChooser(send, "Share rewind"))
                    }
                },
                modifier = Modifier.clip(CircleShape).background(Color(0xFF4DB3FF).copy(alpha = 0.9f))
            ) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
        }

        // everything below is what gets captured as the shared image
        Column(
            Modifier.fillMaxWidth().heightIn(min = minBody)
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
                .background(Brush.verticalGradient(grad))
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            for (mm in 0..12) {
                val sel = mm == month
                Text(
                    if (mm == 0) "Year" else Month.of(mm).getDisplayName(TextStyle.SHORT, Locale.US),
                    color = if (sel) Color.White else Color.White.copy(alpha = 0.6f),
                    fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Medium,
                    fontSize = 16.sp,
                    modifier = Modifier.clickable { month = mm }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(if (yearMode) "$year in Review" else "A-List Rewind", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            buildAnnotatedString {
                append("You saw ")
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = 40.sp)) { append("${list.size}") }
                append(if (yearMode) "\nmovies in " else "\nmovies in ")
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(monthName) }
                append(".")
            },
            color = Color.White.copy(alpha = 0.92f), fontSize = 26.sp, lineHeight = 34.sp
        )
        Spacer(Modifier.height(22.dp))

        if (list.isEmpty()) {
            Text("No movies logged in $monthName $year.", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
        } else if (yearMode) {
            val topFormat = list.groupingBy { it.format.ifBlank { "Standard" } }.eachCount().maxByOrNull { it.value }
            val topTheater = list.filter { it.theater.isNotBlank() }.groupingBy { it.theater }.eachCount().maxByOrNull { it.value }
            val busiest = list.groupingBy { it.date.substring(5, 7).toIntOrNull() ?: 0 }.eachCount().maxByOrNull { it.value }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    StatItem("Total Savings", money(savings))
                    StatItem("Hours Watched", hours)
                    StatItem("PLF Movies", "$plf")
                    StatItem("Favorite Format", topFormat?.key ?: "-")
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    StatItem("Cost per Movie", money(perMovie))
                    StatItem("Theaters Visited", "$theaters")
                    StatItem("Busiest Month", busiest?.let { Month.of(it.key.coerceIn(1, 12)).getDisplayName(TextStyle.FULL, Locale.US) + " (${it.value})" } ?: "-")
                    StatItem("Top Theater", topTheater?.key ?: "-")
                }
            }
            Spacer(Modifier.height(22.dp))
            list.chunked(5).forEach { row ->
                Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { m -> PosterCell(m, Modifier.weight(1f)) { onOpen(m) } }
                    repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(0.42f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    StatItem("Total Savings", money(savings))
                    StatItem("Cost per Movie", money(perMovie))
                    StatItem("Hours Watched", hours)
                    StatItem("PLF Movies", "$plf")
                    StatItem("Theaters Visited", "$theaters")
                }
                Column(Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { m -> PosterCell(m, Modifier.weight(1f)) { onOpen(m) } }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        }
        Spacer(Modifier.height(110.dp))
    }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = Color.White, fontSize = if (value.length > 10) 18.sp else 26.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun PosterCell(m: Movie, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(m.title, color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(4.dp))
        if (m.posterPath.isNotBlank() && m.posterPath != "-") {
            AsyncImage("https://image.tmdb.org/t/p/w185${m.posterPath}", m.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
