package com.alisttracker.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.alisttracker.data.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val stubDate = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)

/** A preview of a ticket-stub card for one visit, with a Share button that sends it as an image. */
@Composable
fun StubDialog(m: Movie, poster: String?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val accent = formatColor(m.format)
    val date = runCatching { LocalDate.parse(m.date).format(stubDate) }.getOrDefault(m.date)
    val time = m.showtime.takeIf { it.isNotBlank() }?.let { prettyTime(it) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // everything inside this box is what gets shared
            Box(
                Modifier.fillMaxWidth()
                    .drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }
                    .background(Bg).padding(18.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), Color(0xFF241638), Color(0xFF14141F))))
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.width(104.dp).height(156.dp).clip(RoundedCornerShape(12.dp)).background(Surface2), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Theaters, null, tint = accent)
                            if (!poster.isNullOrBlank()) AsyncImage("https://image.tmdb.org/t/p/w342$poster", m.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 3)
                            Spacer(Modifier.height(8.dp))
                            Text(date, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                            if (time != null) Text(time, fontSize = 14.sp, color = Color(0xFFD7D8E6))
                            Spacer(Modifier.height(6.dp))
                            Text(listOfNotNull(m.theater.ifBlank { null }, m.screen?.let { "Screen $it" }).joinToString(" · "), fontSize = 13.sp, color = Color(0xFFD7D8E6))
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Chip(m.format.ifBlank { "Standard" }, accent)
                                if (m.threeD) Chip("3D", Color3D)
                                if (m.language.isNotBlank() && m.language != "English") Chip(m.language, Orange)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            val bmp = layer.toImageBitmap().asAndroidBitmap()
                            val file = withContext(Dispatchers.IO) {
                                val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
                                File(dir, "stub_${m.id}.png").also { f -> f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
                            }
                            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(Intent.createChooser(send, "Share ticket stub"))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Share") }
                OutlinedButton(onClick = onDismiss) { Text("Close", color = Color.White) }
            }
        }
    }
}
