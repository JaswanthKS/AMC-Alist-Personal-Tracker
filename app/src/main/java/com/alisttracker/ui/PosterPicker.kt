package com.alisttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.alisttracker.MainVM
import com.alisttracker.data.Movie

/** Grid of every poster TMDB has for the film; tapping one makes it the poster for all visits of that title. */
@Composable
fun PosterPickerDialog(m: Movie, current: String?, vm: MainVM, onDismiss: () -> Unit) {
    var posters by remember(m.title) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(m.title) { posters = vm.posterChoices(m) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 40.dp).clip(RoundedCornerShape(24.dp))
                .background(Surface1).padding(16.dp)
        ) {
            Text("Choose a poster", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(m.title, fontSize = 13.sp, color = Muted, maxLines = 1)
            Spacer(Modifier.height(12.dp))
            val list = posters
            when {
                list == null -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Red) }
                list.isEmpty() -> Text("No other posters found. Check your connection and try again.", color = Muted, fontSize = 14.sp)
                else -> LazyVerticalGrid(
                    GridCells.Fixed(3), Modifier.weight(1f, fill = false).heightIn(max = 520.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list, key = { it }) { path ->
                        val on = path == current
                        AsyncImage(
                            "https://image.tmdb.org/t/p/w342$path", null, contentScale = ContentScale.Crop,
                            modifier = Modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Surface2)
                                .border(if (on) 3.dp else 0.dp, if (on) Red else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { vm.choosePoster(m.title, path); onDismiss() })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
        }
    }
}
