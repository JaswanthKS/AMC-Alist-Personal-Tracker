package com.alisttracker.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.alisttracker.data.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Preview of a shareable ticket card with a choice of looks. Sharing renders the card at 2x for a sharp image. */
@Composable
fun StubDialog(m: Movie, poster: String?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var style by remember { mutableStateOf(ShareStyle.CLASSIC) }
    var posterBmp by remember { mutableStateOf<Bitmap?>(null) }
    var posterReady by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(poster) {
        posterBmp = withContext(Dispatchers.IO) { runCatching { loadPosterBitmap(ctx, poster) }.getOrNull() }
        posterReady = true
    }
    LaunchedEffect(style, posterReady, m) {
        if (posterReady) preview = withContext(Dispatchers.Default) { runCatching { renderShareCard(m, posterBmp, style, 1f) }.getOrNull() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 5f).clip(RoundedCornerShape(16.dp)).background(Surface1), contentAlignment = Alignment.Center) {
                val p = preview
                if (p != null) Image(p.asImageBitmap(), "Ticket card preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                else CircularProgressIndicator(color = Red)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShareStyle.values().forEach { s ->
                    val on = s == style
                    Text(s.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) androidx.compose.ui.graphics.Color.White else Muted,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Red else Surface2).clickable { style = s }.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    enabled = posterReady && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            val file = withContext(Dispatchers.IO) {
                                runCatching {
                                    val big = renderShareCard(m, posterBmp, style, 2f)
                                    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
                                    File(dir, "stub_${m.id}_${style.name.lowercase()}.png").also { f ->
                                        f.outputStream().use { big.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                        big.recycle()
                                    }
                                }.getOrNull()
                            }
                            busy = false
                            if (file != null) {
                                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                ctx.startActivity(Intent.createChooser(send, "Share ticket stub"))
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "Preparing…" else "Share") }
                OutlinedButton(onClick = onDismiss) { Text("Close", color = androidx.compose.ui.graphics.Color.White) }
            }
        }
    }
}
