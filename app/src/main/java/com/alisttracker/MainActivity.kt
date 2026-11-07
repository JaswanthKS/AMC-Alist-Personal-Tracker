package com.alisttracker

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alisttracker.data.*
import com.alisttracker.ui.AppRoot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainVM(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    val movies = dao.movies().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val subs = dao.subs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** title -> runtime in minutes, from the saved TMDB details (used by Rewind's "hours watched"). */
    val runtimes = dao.allInfo()
        .map { list -> list.associate { it.title to (runCatching { org.json.JSONObject(it.json).optInt("runtime", 0) }.getOrDefault(0)) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val watchlist = dao.watchlist().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    suspend fun searchTmdb(q: String): List<Posters.Hit> {
        val token = BuildConfig.TMDB_TOKEN
        return if (token.isBlank()) emptyList() else Posters.searchMany(token, q)
    }
    fun addWatch(h: Posters.Hit) = viewModelScope.launch {
        dao.addWatch(com.alisttracker.data.WatchItem(h.id, h.title, h.release, h.poster, System.currentTimeMillis()))
    }
    fun removeWatch(w: com.alisttracker.data.WatchItem) = viewModelScope.launch { dao.deleteWatch(w) }

    val options = dao.options().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        watchForSync()
        viewModelScope.launch {
            seedIfEmpty(app, dao)
            seedOptionsIfEmpty(dao)
            fetchPosters()
        }
    }

    // ---- poster the user picked for a title (kept in settings so refreshes never replace it) ----
    private fun customPosters(): org.json.JSONObject =
        runCatching { org.json.JSONObject(sheetPrefs.getString("custom_posters", "{}") ?: "{}") }.getOrDefault(org.json.JSONObject())
    private fun customPoster(title: String): String? = customPosters().optString(title.trim().lowercase(), "").takeIf { it.isNotBlank() }

    suspend fun posterChoices(m: Movie): List<String> {
        val token = BuildConfig.TMDB_TOKEN
        return if (token.isBlank()) emptyList() else Posters.posterOptions(token, m.title, m.date.take(4).toIntOrNull() ?: 0)
    }
    fun choosePoster(title: String, path: String) = viewModelScope.launch {
        sheetPrefs.edit().putString("custom_posters", customPosters().put(title.trim().lowercase(), path).toString()).apply()
        dao.forcePoster(title, path)
    }

    /** Fills in missing posters from TMDB when a key is set in local.properties. */
    fun fetchPosters() = viewModelScope.launch {
        val token = BuildConfig.TMDB_TOKEN
        if (token.isBlank()) return@launch
        for (td in dao.titlesWithoutPoster()) {
            val p = customPoster(td.title) ?: Posters.lookup(token, td.title, td.date.take(4).toIntOrNull() ?: 0) ?: continue
            dao.setPoster(td.title, p)
            delay(120)
        }
    }

    /** Writes every movie, payment and list item as JSON to a file the user picked (Drive, Downloads...). */
    suspend fun backupTo(uri: android.net.Uri): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val all = dao.allMovies()
        val root = org.json.JSONObject()
        root.put("app", "alist-tracker")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("movies", org.json.JSONArray().also { arr -> all.forEach { m ->
            arr.put(org.json.JSONObject().apply {
                put("id", m.id); put("date", m.date); put("title", m.title); put("ticket", m.ticket); put("fee", m.fee)
                put("format", m.format); put("threeD", m.threeD); put("language", m.language); put("theater", m.theater)
                put("screen", m.screen ?: org.json.JSONObject.NULL); put("showtime", m.showtime); put("alist", m.alist)
                put("notes", m.notes); put("swag", m.swag); put("amc", m.amc); put("posterPath", m.posterPath); put("rating", m.rating)
                put("concessions", m.concessions); put("concessionsPay", m.concessionsPay)
            }) } })
        root.put("subs", org.json.JSONArray().also { arr -> subs.value.forEach { s ->
            arr.put(org.json.JSONObject().apply { put("year", s.year); put("month", s.month); put("amount", s.amount) }) } })
        root.put("options", org.json.JSONArray().also { arr -> options.value.forEach { o ->
            arr.put(org.json.JSONObject().apply { put("type", o.type); put("value", o.value); put("sortOrder", o.sortOrder) }) } })
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(root.toString(2).toByteArray()) }
        all.size
    }

    private fun csvCell(v: Any?): String {
        val t = v?.toString() ?: ""
        return if (t.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + t.replace("\"", "\"\"") + "\"" else t
    }

    private fun writeCsv(uri: android.net.Uri, header: List<String>, rows: List<List<Any?>>) {
        val sb = StringBuilder("\uFEFF")   // BOM so Excel/Sheets read it as UTF-8
        sb.append(header.joinToString(",") { csvCell(it) }).append("\r\n")
        rows.forEach { r -> sb.append(r.joinToString(",") { csvCell(it) }).append("\r\n") }
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
    }

    private val prettyDateFmt = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.US)
    private fun sheetDate(d: String) = runCatching { java.time.LocalDate.parse(d).format(prettyDateFmt) }.getOrDefault(d)
    private fun sheetDay(d: String) = runCatching { java.time.LocalDate.parse(d).dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US) }.getOrDefault("")
    private fun sheetTime(t: String) = runCatching { java.time.LocalTime.parse(t).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US)) }.getOrDefault(t)
    private fun money2(v: Double) = String.format(java.util.Locale.US, "%.2f", v)

    /** AMC movie log, same columns as the "Movie Log" tab of the spreadsheet. */
    suspend fun exportMoviesCsv(uri: android.net.Uri): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val all = dao.allMovies().filter { it.amc }.sortedWith(compareBy({ it.date }, { it.showtime }))
        writeCsv(uri,
            listOf("Date", "Day", "Movie Title", "Ticket Value", "Conv. Fee", "Total Value", "Format", "Language", "Theater", "Screen", "Showtime", "A-List", "Notes", "Swag / Freebies", "3D"),
            all.map { m -> listOf(sheetDate(m.date), sheetDay(m.date), m.title, money2(m.ticket), money2(m.fee), money2(m.total),
                m.format, m.language, m.theater, m.screen, sheetTime(m.showtime), m.alist, m.notes, m.swag, if (m.threeD) "TRUE" else "FALSE") })
        all.size
    }

    /** Non-AMC visits, same columns as the "Other Theatres" tab. */
    suspend fun exportOtherCsv(uri: android.net.Uri): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val all = dao.allMovies().filter { !it.amc }.sortedBy { it.date }
        writeCsv(uri, listOf("Date", "Day", "Movie Name", "Ticket Price", "Format", "Location", "Notes"),
            all.map { m -> listOf(sheetDate(m.date), sheetDay(m.date), m.title, money2(m.total), sheetFmt(m), m.theater, m.notes) })
        all.size
    }

    /** Membership payments laid out like the Dashboard grid (one row per year, Jan..Dec). */
    suspend fun exportSubsCsv(uri: android.net.Uri): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val all = subs.value
        val years = all.map { it.year }.distinct().sorted()
        writeCsv(uri, listOf("Year", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
            years.map { y -> listOf<Any?>(y) + (1..12).map { m -> all.find { it.year == y && it.month == m }?.let { money2(it.amount) } ?: "" } })
        all.size
    }

    // ---------- Google Sheets live sync (through a small Apps Script the user adds to their sheet) ----------
    private val sheetPrefs = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    val syncStatus = kotlinx.coroutines.flow.MutableStateFlow(sheetPrefs.getString("sync_status", "Not synced yet") ?: "")
    fun syncUrl() = sheetPrefs.getString("sync_url", "") ?: ""
    fun syncSecret() = sheetPrefs.getString("sync_secret", "") ?: ""
    fun syncAuto() = sheetPrefs.getBoolean("sync_auto", false)
    fun saveSync(url: String, secret: String, auto: Boolean) {
        sheetPrefs.edit().putString("sync_url", url.trim()).putString("sync_secret", secret.trim()).putBoolean("sync_auto", auto).apply()
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun watchForSync() = viewModelScope.launch {
        kotlinx.coroutines.flow.combine(movies, subs) { m, s -> m to s }
            .debounce(4000)
            .collect { (m, _) -> if (syncAuto() && syncUrl().isNotBlank() && m.isNotEmpty()) pushToSheet() }
    }

    fun syncNow() = viewModelScope.launch { pushToSheet() }

    // ---------- Two-way sync: pull changes made in the sheet, merge safely, then push ----------
    /** The sheet has no 3D column: a 3D visit is written as "IMAX 3D" in the Format column. */
    private fun sheetFmt(m: Movie) = if (m.threeD) "${m.format} 3D" else m.format
    private fun is3dText(s: String) = Regex("(?i)3d\\s*$").containsMatchIn(s.trim())
    private fun baseFmt(s: String) = s.trim().replace(Regex("(?i)\\s*3d\\s*$"), "").trim().ifBlank { "Standard" }
    private fun m2s(v: Double) = String.format(java.util.Locale.US, "%.2f", v)
    private fun normTime(t: String): String {
        val x = Regex("^(\\d{1,2}):(\\d{2})").find(t.trim()) ?: return t.trim()
        return String.format(java.util.Locale.US, "%02d:%s", x.groupValues[1].toInt(), x.groupValues[2])
    }
    /** True when the app already has this same movie (same title/theater/format/price/time) within 10 days: a shifted copy, not a new visit. */
    private fun looksLikeCopy(m: Movie, existing: List<Movie>): Boolean {
        val d = runCatching { java.time.LocalDate.parse(m.date) }.getOrNull() ?: return false
        return existing.any { e ->
            e.amc == m.amc && e.title.trim().equals(m.title.trim(), true) && e.theater.trim() == m.theater.trim() &&
            e.format.trim() == m.format.trim() && e.threeD == m.threeD && m2s(e.ticket) == m2s(m.ticket) && normTime(e.showtime) == normTime(m.showtime) &&
            runCatching { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(e.date), d)) <= 10 }.getOrDefault(false)
        }
    }

    private fun keyOf(m: Movie) =
        if (m.amc) "A|${m.date}|${m.title.trim().lowercase()}|${normTime(m.showtime)}" else "O|${m.date}|${m.title.trim().lowercase()}"
    private fun canon(m: Movie) =
        (if (m.amc) listOf(m.date, m.title.trim(), m2s(m.ticket), m2s(m.fee), m.format.trim(), if (m.threeD) "3D" else "", m.language.trim(), m.theater.trim(),
            m.screen?.toString() ?: "", normTime(m.showtime), m.alist.trim(), m.notes.trim(), m.swag.trim()) + (if (m.rating > 0) listOf("r${m.rating}") else emptyList()) +
            (if (m.concessions > 0) listOf("c${m2s(m.concessions)}${m.concessionsPay.trim()}") else emptyList())
         else listOf(m.date, m.title.trim(), m2s(m.total), sheetFmt(m).trim(), m.theater.trim(), m.notes.trim())).joinToString("\u0001")

    private fun loadSnapshot(): Map<String, String> {
        val j = runCatching { org.json.JSONObject(sheetPrefs.getString("sync_snapshot", "{}") ?: "{}") }.getOrNull() ?: return emptyMap()
        return j.keys().asSequence().associateWith { j.getString(it) }
    }
    private fun saveSnapshot(all: List<Movie>) {
        val j = org.json.JSONObject()
        all.forEach { j.put(keyOf(it), canon(it)) }
        sheetPrefs.edit().putString("sync_snapshot", j.toString()).apply()
    }

    private fun postJson(url: String, text: String): String {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 20000; c.readTimeout = 60000
        c.setRequestProperty("Content-Type", "text/plain;charset=utf-8")
        c.outputStream.use { it.write(text.toByteArray()) }
        return (if (c.responseCode in 200..299) c.inputStream else c.errorStream).bufferedReader().readText()
    }

    /** Rows that look like copies made by a date-shifted import: same movie one day earlier than an older row. */
    suspend fun findDuplicates(): List<Movie> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val all = dao.allMovies()
        val groups = all.groupBy { listOf(it.title.trim().lowercase(), it.theater, it.format, it.threeD, m2s(it.ticket), m2s(it.fee), normTime(it.showtime), it.amc) }
        val dups = mutableListOf<Movie>()
        for (g in groups.values) {
            val sorted = g.mapNotNull { m -> runCatching { java.time.LocalDate.parse(m.date) }.getOrNull()?.let { it to m } }
                .sortedWith(compareBy({ it.first }, { it.second.id }))
            var keep: java.time.LocalDate? = null
            for ((d, m) in sorted) {
                val k = keep
                if (k != null && java.time.temporal.ChronoUnit.DAYS.between(k, d) in 0..10) dups += m else keep = d
            }
        }
        dups
    }

    fun removeDuplicates(onDone: (Int) -> Unit) = viewModelScope.launch {
        val d = findDuplicates()
        d.forEach { dao.delete(it) }
        if (syncUrl().isNotBlank()) pushToSheet()
        onDone(d.size)
    }

    /** One-way: the sheet replaces everything on this phone. Never writes to the sheet. */
    fun restoreFromSheet(onDone: (String) -> Unit) = viewModelScope.launch {
        val msg = runCatching { pullFromSheet(replace = true) }.getOrElse { "Couldn't restore: ${it.message ?: "error"}" }
        fetchPosters()
        onDone(msg)
    }

    /** True when the sheet already holds visits (used to stop a fresh install from overwriting it). */
    private fun sheetHasData(url: String): Boolean = runCatching {
        val body = postJson(url, org.json.JSONObject().put("token", syncSecret()).put("action", "pull").toString())
        val root = org.json.JSONObject(body)
        (root.optJSONArray("movies")?.length() ?: 0) + (root.optJSONArray("other")?.length() ?: 0) > 0
    }.getOrDefault(true)

    /** Pull-to-refresh: bring in what changed in the sheet, then push the merged result back. */
    fun refreshFromSheet(onDone: (String) -> Unit) = viewModelScope.launch {
        val msg = runCatching { pullFromSheet() }.getOrElse { "Couldn't refresh: ${it.message ?: "error"}" }
        fetchPosters()
        onDone(msg)
    }

    private suspend fun pullFromSheet(replace: Boolean = false): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val url = syncUrl()
        if (url.isBlank()) return@withContext "Set up Google Sheets sync in Settings first"
        val body = postJson(url, org.json.JSONObject().put("token", syncSecret()).put("action", "pull").toString())
        if (body.contains("bad token")) return@withContext "Wrong secret code"
        val root = runCatching { org.json.JSONObject(body) }.getOrNull()
        if (root == null || !root.optBoolean("ok") || !root.has("movies"))
            return@withContext "Update your sheet script to the new version first (it can't send data yet)"

        fun rows(name: String, amc: Boolean): List<Movie> {
            val a = root.optJSONArray(name) ?: return emptyList()
            return (0 until a.length()).mapNotNull { i ->
                val j = a.getJSONObject(i)
                val date = j.optString("date"); val title = j.optString("title").trim()
                if (title.isEmpty() || runCatching { java.time.LocalDate.parse(date) }.isFailure) return@mapNotNull null
                if (amc) Movie(date = date, title = title, ticket = j.optDouble("ticket", 0.0), fee = j.optDouble("fee", 0.0),
                    format = baseFmt(j.optString("format")), threeD = j.optBoolean("threeD", false) || is3dText(j.optString("format")), language = j.optString("language").ifBlank { "English" },
                    theater = j.optString("theater"), screen = j.optString("screen").toDoubleOrNull()?.toInt(),
                    showtime = normTime(j.optString("showtime")), alist = j.optString("alist").ifBlank { "No" },
                    notes = j.optString("notes"), swag = j.optString("swag"), amc = true, rating = j.optInt("rating", 0).coerceIn(0, 5),
                    concessions = j.optDouble("concessions", 0.0).coerceAtLeast(0.0), concessionsPay = j.optString("concessionsPay"))
                else Movie(date = date, title = title, ticket = j.optDouble("price", 0.0), fee = 0.0,
                    format = baseFmt(j.optString("format")), threeD = is3dText(j.optString("format")), theater = j.optString("theater"),
                    notes = j.optString("notes"), alist = "No", amc = false)
            }
        }
        val sheetHasRating = root.optBoolean("hasRating", false)   // false = the sheet script is the older version without a Rating column
        val sheetRows = rows("movies", true) + rows("other", false)
        if (replace) {
            if (sheetRows.isEmpty()) return@withContext "The sheet returned no movies, so nothing was changed"
            val keepExtra = dao.allMovies().filter { it.rating > 0 || it.concessions > 0 }.associateBy { keyOf(it) }
            dao.deleteAllMovies(); dao.upsertAll(sheetRows.map { r ->
                val k = keepExtra[keyOf(r)]
                if (k == null || (r.amc && sheetHasRating)) r else r.copy(rating = k.rating, concessions = k.concessions, concessionsPay = k.concessionsPay)   // old sheet script: keep what is on the phone
            })
            val ss = root.optJSONArray("subs")
            if (ss != null) {
                dao.deleteAllSubs()
                dao.upsertSubs((0 until ss.length()).map { ss.getJSONObject(it) }
                    .filter { it.optDouble("amount", 0.0) > 0 }
                    .map { SubPayment(it.getInt("year"), it.getInt("month"), it.getDouble("amount")) })
            }
            saveSnapshot(dao.allMovies())
            sheetPrefs.edit().putString("sync_status", "Restored ${sheetRows.size} movies from the sheet").apply()
            syncStatus.value = "Restored ${sheetRows.size} movies from the sheet"
            return@withContext "Restored ${sheetRows.size} movies from the sheet"
        }
        val appAll = dao.allMovies()
        if (sheetRows.isEmpty() && appAll.size >= 5) return@withContext "The sheet returned no movies, so nothing was changed"

        val appMap = appAll.associateBy { keyOf(it) }
        val sheetMap = sheetRows.associateBy { keyOf(it) }
        val snap = loadSnapshot()
        val toAdd = mutableListOf<Movie>(); val toUpdate = mutableListOf<Movie>(); val toDelete = mutableListOf<Movie>()
        for ((k, sm0) in sheetMap) {
            val am = appMap[k]
            val sm = if (!sheetHasRating && am != null) sm0.copy(rating = am.rating, concessions = am.concessions, concessionsPay = am.concessionsPay) else sm0
            if (am != null) {
                if (canon(am) != canon(sm) && snap[k] == canon(am))   // app untouched since last sync -> sheet wins
                    toUpdate += if (am.amc) sm.copy(id = am.id, posterPath = am.posterPath)
                                else am.copy(ticket = sm.ticket, fee = 0.0, format = sm.format, threeD = sm.threeD, theater = sm.theater, notes = sm.notes)
            } else if (snap[k] == null && !looksLikeCopy(sm, appAll)) toAdd += sm   // brand new in the sheet
        }
        for ((k, am) in appMap) if (k !in sheetMap && snap[k] != null && snap[k] == canon(am)) toDelete += am

        var note = ""
        val addCap = maxOf(5, appAll.size / 10)
        if (toAdd.size > addCap) {
            val n = toAdd.size
            return@withContext "The sheet has $n movies the app doesn't. Nothing was imported, to be safe. Check the sheet's dates, then try again"
        }
        val cap = maxOf(3, appAll.size / 10)
        if (toDelete.size > cap) { note = " (skipped ${toDelete.size} removals to be safe)"; toDelete.clear() }
        dao.upsertAll(toAdd + toUpdate)
        toDelete.forEach { dao.delete(it) }

        // membership payments: only fill in months the app doesn't have yet
        val sheetSubs = root.optJSONArray("subs")
        if (sheetSubs != null) {
            val have = subs.value.map { it.year * 100 + it.month }.toSet()
            val add = (0 until sheetSubs.length()).map { sheetSubs.getJSONObject(it) }
                .filter { it.getInt("year") * 100 + it.getInt("month") !in have && it.optDouble("amount", 0.0) > 0 }
                .map { SubPayment(it.getInt("year"), it.getInt("month"), it.getDouble("amount")) }
            if (add.isNotEmpty()) dao.upsertSubs(add)
        }

        pushToSheet(force = true)   // make the sheet match the merged result and remember it as the new baseline
        val parts = listOfNotNull(
            if (toAdd.isNotEmpty()) "${toAdd.size} added" else null,
            if (toUpdate.isNotEmpty()) "${toUpdate.size} updated" else null,
            if (toDelete.isNotEmpty()) "${toDelete.size} removed" else null)
        (if (parts.isEmpty()) "Already up to date" else parts.joinToString(", ")) + note
    }


    private suspend fun pushToSheet(force: Boolean = false) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val url = syncUrl()
        if (url.isBlank()) { syncStatus.value = "Add your web app link first"; return@withContext }
        // Never synced on this phone yet: don't overwrite a sheet that already has data.
        if (!force && loadSnapshot().isEmpty() && sheetHasData(url)) {
            val m = "Your sheet already has data. Tap \"Restore from Sheet\" to load it onto this phone. Nothing was sent."
            syncStatus.value = m; sheetPrefs.edit().putString("sync_status", m).apply(); return@withContext
        }
        val all = dao.allMovies().sortedWith(compareBy({ it.date }, { it.showtime }))
        val root = org.json.JSONObject()
        root.put("token", syncSecret())
        root.put("movies", org.json.JSONArray().also { arr -> all.filter { it.amc }.forEach { m ->
            arr.put(org.json.JSONObject().apply {
                put("date", m.date); put("title", m.title); put("ticket", m.ticket); put("fee", m.fee)
                put("format", m.format); put("threeD", m.threeD); put("language", m.language); put("theater", m.theater)
                put("screen", m.screen ?: ""); put("showtime", m.showtime); put("alist", m.alist)
                put("notes", m.notes); put("swag", m.swag); put("rating", m.rating); put("concessions", m.concessions); put("concessionsPay", m.concessionsPay)
            }) } })
        root.put("other", org.json.JSONArray().also { arr -> all.filter { !it.amc }.forEach { m ->
            arr.put(org.json.JSONObject().apply {
                put("date", m.date); put("title", m.title); put("price", m.total); put("format", sheetFmt(m))
                put("theater", m.theater); put("notes", m.notes)
            }) } })
        root.put("subs", org.json.JSONArray().also { arr -> subs.value.forEach { s ->
            arr.put(org.json.JSONObject().apply { put("year", s.year); put("month", s.month); put("amount", s.amount) }) } })
        syncStatus.value = "Syncing..."
        val result = runCatching {
            val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 20000; c.readTimeout = 60000
            c.setRequestProperty("Content-Type", "text/plain;charset=utf-8")
            c.outputStream.use { it.write(root.toString().toByteArray()) }
            val body = (if (c.responseCode in 200..299) c.inputStream else c.errorStream).bufferedReader().readText()
            body
        }
        val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(java.util.Date())
        val msg = result.fold(
            { body -> if (body.contains("\"ok\":true")) "Synced ${all.size} movies at $time"
                      else if (body.contains("bad token")) "Wrong secret code"
                      else "Sheet said: " + body.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim().take(160) },
            { "No connection: ${it.message ?: "error"}" })
        syncStatus.value = msg
        sheetPrefs.edit().putString("sync_status", msg).apply()
        if (msg.startsWith("Synced")) saveSnapshot(all)
    }

    /** Replaces everything in the app with the contents of a backup file. Returns the movie count, or -1 if the file is not a backup. */
    suspend fun restoreFrom(uri: android.net.Uri): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val text = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }
        val root = runCatching { org.json.JSONObject(text) }.getOrNull()
        if (root == null || root.optString("app") != "alist-tracker") return@withContext -1
        val ms = root.getJSONArray("movies").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { j ->
            Movie(
                id = j.optLong("id"), date = j.getString("date"), title = j.getString("title"),
                ticket = j.optDouble("ticket", 0.0), fee = j.optDouble("fee", 0.0),
                format = j.optString("format", "Standard"), threeD = j.optBoolean("threeD", false), language = j.optString("language", "English"),
                theater = j.optString("theater", ""), screen = if (j.isNull("screen")) null else j.optInt("screen"),
                showtime = j.optString("showtime", ""), alist = j.optString("alist", "Yes"),
                notes = j.optString("notes", ""), swag = j.optString("swag", ""), amc = j.optBoolean("amc", true),
                posterPath = j.optString("posterPath", ""), rating = j.optInt("rating", 0),
                concessions = j.optDouble("concessions", 0.0), concessionsPay = j.optString("concessionsPay", ""))
        } }
        val ss = root.getJSONArray("subs").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { SubPayment(it.getInt("year"), it.getInt("month"), it.getDouble("amount")) } }
        val os = root.getJSONArray("options").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { Option(it.getString("type"), it.getString("value"), it.optInt("sortOrder")) } }
        dao.deleteAllMovies(); dao.deleteAllSubs(); dao.deleteAllOptions()
        dao.upsertAll(ms); dao.upsertSubs(ss); dao.addOptions(os)
        ms.size
    }

    /** Settings → forget every saved poster/detail and download them again. */
    fun refetchAll() = viewModelScope.launch {
        dao.clearAllInfo()
        dao.clearAllPosters()
        fetchPosters()
    }

    /** Forget the saved details/poster for this title and look them up again. */
    suspend fun refreshInfo(m: Movie): String? {
        dao.deleteInfo(m.title)
        dao.clearPoster(m.title)
        val json = loadInfo(m)
        fetchPosters()
        return json
    }

    /** Cast, synopsis and other details for the movie screen (cached after the first look-up). */
    suspend fun loadInfo(m: Movie): String? {
        dao.info(m.title)?.let { return it }
        val token = BuildConfig.TMDB_TOKEN
        if (token.isBlank()) return null
        val json = Posters.details(token, m.title, m.date.take(4).toIntOrNull() ?: 0) ?: return null
        dao.putInfo(MovieInfo(m.title, json, System.currentTimeMillis()))
        if (customPoster(m.title) == null)
            org.json.JSONObject(json).optString("poster", "").takeIf { it.isNotBlank() }?.let { dao.forcePoster(m.title, it) }
        return json
    }

    fun save(m: Movie) = viewModelScope.launch {
        dao.upsert(m)
        MonthWidget.refresh(getApplication())
        // anything newly typed in the form becomes part of the lists
        listOf(OptType.THEATER to m.theater, OptType.FORMAT to m.format, OptType.LANGUAGE to m.language, OptType.SWAG to m.swag)
            .filter { it.second.isNotBlank() }
            .forEach { dao.addOption(Option(it.first, it.second, 999)) }
        fetchPosters()
    }
    fun addOption(type: String, value: String) = viewModelScope.launch { dao.addOption(Option(type, value, 999)) }
    fun deleteOption(o: Option) = viewModelScope.launch { dao.deleteOption(o) }
    fun delete(m: Movie) = viewModelScope.launch { dao.delete(m); MonthWidget.refresh(getApplication()) }
    fun saveSub(s: SubPayment) = viewModelScope.launch { dao.upsertSub(s); MonthWidget.refresh(getApplication()) }
}

class MainActivity : ComponentActivity() {
    private val vm: MainVM by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { AppRoot(vm) }
    }
}

