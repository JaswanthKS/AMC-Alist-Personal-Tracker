package com.alisttracker.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.alisttracker.MainVM
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class GradientPreset(val name: String, val colors: List<Color>)

/** Background gradients the user can pick in Settings (top → bottom). */
val GradientPresets = listOf(
    GradientPreset("Ocean Blue", listOf(Color(0xFF1E78C2), Color(0xFF0B2F52), Color(0xFF081426))),
    GradientPreset("Violet Night", listOf(Color(0xFF7B3FD6), Color(0xFF2D1759), Color(0xFF110A24))),
    GradientPreset("Teal Lagoon", listOf(Color(0xFF14A39A), Color(0xFF0B4A52), Color(0xFF07191F))),
    GradientPreset("Crimson Cinema", listOf(Color(0xFFD62F45), Color(0xFF5A1426), Color(0xFF1C0910))),
    GradientPreset("Emerald", listOf(Color(0xFF1FA463), Color(0xFF0D4A33), Color(0xFF07201A))),
    GradientPreset("Sunset", listOf(Color(0xFFF2784B), Color(0xFF8E2F5A), Color(0xFF2A1238))),
    GradientPreset("Midnight", listOf(Color(0xFF2B2F4A), Color(0xFF14172A), Color(0xFF0B0B12))),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainVM, selected: Int, movieCount: Int, onSelect: (Int) -> Unit, onRefetch: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var confirm by remember { mutableStateOf(false) }
    var dupList by remember { mutableStateOf<List<com.alisttracker.data.Movie>?>(null) }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val n = runCatching { vm.backupTo(uri) }.getOrNull()
            Toast.makeText(ctx, if (n != null) "Backed up $n movies" else "Backup failed", Toast.LENGTH_LONG).show()
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val n = runCatching { vm.exportMoviesCsv(uri) }.getOrNull()
            Toast.makeText(ctx, if (n != null) "Exported $n movies" else "Export failed", Toast.LENGTH_LONG).show()
        }
    }
    val otherCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val n = runCatching { vm.exportOtherCsv(uri) }.getOrNull()
            Toast.makeText(ctx, if (n != null) "Exported $n other-theater visits" else "Export failed", Toast.LENGTH_LONG).show()
        }
    }
    val subsCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val n = runCatching { vm.exportSubsCsv(uri) }.getOrNull()
            Toast.makeText(ctx, if (n != null) "Exported $n payments" else "Export failed", Toast.LENGTH_LONG).show()
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) pendingRestore = uri }
    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Section("App colors", Blue) {
                    Text("Pick the background gradient used on every tab.", fontSize = 13.sp, color = Muted)
                    Spacer(Modifier.height(8.dp))
                    GradientPresets.forEachIndexed { i, p ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp))
                                .border(BorderStroke(if (i == selected) 2.dp else 0.dp, Color.White), RoundedCornerShape(14.dp))
                                .background(Brush.horizontalGradient(p.colors))
                                .clickable { onSelect(i) }.padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(p.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            if (i == selected) Icon(Icons.Default.Check, null, tint = Color.White)
                        }
                    }
                }
                Section("Fix duplicates", Red) {
                    Text("Finds movies that were added twice with dates one day apart (from the sheet import mix-up) and removes the extra copies.", fontSize = 13.sp, color = Muted)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { scope.launch { dupList = vm.findDuplicates() } }) { Text("Find duplicate movies") }
                }
                Section("Backup & restore", Green) {
                    Text("Save all your movies, payments and lists to one file. In the file picker choose Google Drive (or Downloads) as the place. Restore reads that file back.", fontSize = 13.sp, color = Muted)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { backupLauncher.launch("alist-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json") },
                        colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) { Text("Back up now") }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Restore from a backup") }
                }
                Section("Google Sheets live sync", Teal) {
                    var url by remember { mutableStateOf(vm.syncUrl()) }
                    var secret by remember { mutableStateOf(vm.syncSecret()) }
                    var auto by remember { mutableStateOf(vm.syncAuto()) }
                    val status by vm.syncStatus.collectAsState()
                    Text("Keeps your Google Sheet up to date. One-time setup: add the small script I gave you to your sheet, deploy it as a web app, then paste the link and your secret code here.", fontSize = 13.sp, color = Muted)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(url, { url = it; vm.saveSync(it, secret, auto) }, label = { Text("Web app link") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(secret, { secret = it; vm.saveSync(url, it, auto) }, label = { Text("Secret code") }, singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Sync automatically after every change", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(auto, { auto = it; vm.saveSync(url, secret, it) })
                    }
                    Spacer(Modifier.height(6.dp))
                    var confirmRestore by remember { mutableStateOf(false) }
                    Button(onClick = { vm.saveSync(url, secret, auto); vm.syncNow() }, colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color.Black)) { Text("Sync now") }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { vm.saveSync(url, secret, auto); confirmRestore = true }) { Text("Restore from Sheet") }
                    Text("Restore replaces everything on this phone with the sheet's data. It never changes the sheet.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
                    if (confirmRestore) AlertDialog(
                        onDismissRequest = { confirmRestore = false },
                        title = { Text("Restore from Sheet?") },
                        text = { Text("All visits on this phone will be replaced with what is in your Google Sheet. The sheet itself is not changed.") },
                        confirmButton = { TextButton(onClick = { confirmRestore = false; vm.restoreFromSheet { } }) { Text("Restore") } },
                        dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } }
                    )
                    Text(status, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
                }
                Section("Export to spreadsheet", Gold) {
                    Text("Same columns as your spreadsheet tabs. Open the file in Excel or Google Sheets (File → Import). It's a copy; it does not change your sheet.", fontSize = 13.sp, color = Muted)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { csvLauncher.launch("alist-movies-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".csv") },
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)) { Text("Movie Log tab (.csv)") }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { otherCsvLauncher.launch("alist-other-theatres-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".csv") }) { Text("Other Theatres tab (.csv)") }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { subsCsvLauncher.launch("alist-payments-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".csv") }) { Text("Subscription costs grid (.csv)") }
                }
                Section("Movie data", Teal) {
                    Text("Posters, cast and synopsis come from TMDB. If something looks wrong everywhere, download it all again.", fontSize = 13.sp, color = Muted)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { confirm = true }, colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color.Black)) {
                        Text("Re-download all posters & details")
                    }
                }
                Section("About", Gold) {
                    Text("A-List Tracker · $movieCount movies logged", color = Color.White)
                    Text("Data is stored only on this phone.", fontSize = 12.sp, color = Muted)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    dupList?.let { d ->
        AlertDialog(
            onDismissRequest = { dupList = null },
            title = { Text(if (d.isEmpty()) "No duplicates found" else "Remove ${d.size} duplicates?") },
            text = {
                Text(if (d.isEmpty()) "Everything looks fine." else
                    "These extra copies will be deleted (the older original of each stays):\n\n" +
                    d.take(8).joinToString("\n") { "${it.date}  ${it.title}" } + if (d.size > 8) "\n…and ${d.size - 8} more" else "")
            },
            confirmButton = {
                if (d.isNotEmpty()) TextButton(onClick = {
                    dupList = null
                    vm.removeDuplicates { n -> Toast.makeText(ctx, "Removed $n duplicates", Toast.LENGTH_LONG).show() }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { dupList = null }) { Text(if (d.isEmpty()) "OK" else "Cancel") } }
        )
    }
    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore this backup?") },
            text = { Text("This replaces everything currently in the app (movies, payments and lists) with the backup's contents.") },
            confirmButton = { TextButton(onClick = {
                pendingRestore = null
                scope.launch {
                    val n = runCatching { vm.restoreFrom(uri) }.getOrDefault(-1)
                    Toast.makeText(ctx, if (n >= 0) "Restored $n movies" else "That file isn't an A-List backup", Toast.LENGTH_LONG).show()
                    if (n >= 0) vm.fetchPosters()
                }
            }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } }
        )
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Re-download everything?") },
        text = { Text("This clears saved posters and details for all $movieCount movies and fetches them again. It can take a minute.") },
        confirmButton = { TextButton(onClick = { onRefetch(); confirm = false }) { Text("Re-download") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
    )
}
