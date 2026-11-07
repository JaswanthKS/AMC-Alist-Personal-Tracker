package com.alisttracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.input.nestedscroll.nestedScroll

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alisttracker.MainVM
import com.alisttracker.data.Movie
import com.alisttracker.data.OptType
import com.alisttracker.data.Option
import com.alisttracker.data.SubPayment
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- palette ----------
internal val Bg = Color(0xFF0B0B12)
internal val Surface1 = Color(0xFF161622)
internal val Surface2 = Color(0xFF1F1F2E)
internal val Red = Color(0xFFFF3B4E)
internal val Gold = Color(0xFFFFC857)
internal val Teal = Color(0xFF2EC4B6)
internal val Purple = Color(0xFF9B7BFF)
internal val Blue = Color(0xFF4D9DE0)
internal val Orange = Color(0xFFFF8A3D)
internal val Green = Color(0xFF3DDC84)
internal val Muted = Color(0xFF9A9CB0)
internal val Color3D = Color(0xFFE879F9)

internal val money = { v: Double -> String.format(Locale.US, "$%,.2f", v) }

internal fun formatColor(f: String): Color = when {
    f.equals("IMAX 70mm", true) -> Color(0xFF22D3EE)
    f.contains("70mm") -> Gold
    f.contains("IMAX") -> Blue
    f.contains("Dolby") -> Purple
    f.contains("Prime") -> Teal
    f.contains("XL") -> Orange
    f.contains("4DX") -> Green
    else -> Color(0xFFFF6B7A)
}

internal fun aListColor(a: String): Color = when (a) {
    "Yes" -> Green
    "Gift Card" -> Gold
    "Voucher" -> Teal
    else -> Red
}

private val dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

internal fun prettyDate(d: String) = runCatching { LocalDate.parse(d).format(dayFmt) }.getOrDefault(d)
internal fun prettyTime(t: String) = runCatching { LocalTime.parse(t).format(timeFmt) }.getOrDefault(t)
private fun monthKey(d: String) = runCatching { LocalDate.parse(d).format(monthFmt) }.getOrDefault(d)

/** Latest watched first: by date, then by showtime, then by entry order. */
private fun List<Movie>.latestFirst() =
    sortedWith(compareByDescending<Movie> { it.date }.thenByDescending { it.showtime }.thenByDescending { it.id })

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainVM) {
    val scheme = darkColorScheme(
        primary = Red, onPrimary = Color.White, secondary = Gold,
        background = Bg, onBackground = Color.White,
        surface = Surface1, onSurface = Color.White,
        surfaceVariant = Surface2, onSurfaceVariant = Muted,
        surfaceContainerHigh = Surface2, surfaceContainer = Surface1
    )
    MaterialTheme(colorScheme = scheme) {
        val movies by vm.movies.collectAsState()
        val watchlist by vm.watchlist.collectAsState()
        var logging by remember { mutableStateOf<com.alisttracker.data.WatchItem?>(null) }
        val subs by vm.subs.collectAsState()
        val options by vm.options.collectAsState()
        var tab by remember { mutableIntStateOf(0) }
        var editing by remember { mutableStateOf<Movie?>(null) }
        var detailId by remember { mutableStateOf<Long?>(null) }
        var adding by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        val appCtx = LocalContext.current
        val prefs = remember { appCtx.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
        var gradIdx by remember { mutableIntStateOf(prefs.getInt("gradient", 0).coerceIn(0, GradientPresets.lastIndex)) }
        val grad = GradientPresets[gradIdx].colors
        val navColors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color.White, selectedTextColor = Color.White,
            indicatorColor = Red, unselectedIconColor = Muted, unselectedTextColor = Muted)

        val nav = remember { NavState() }
        val scrollWatcher = remember {
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                    nav.onScroll(available.y)
                    return androidx.compose.ui.geometry.Offset.Zero
                }
            }
        }
        val tabs = listOf("Log" to Icons.Default.Theaters, "Stats" to Icons.Default.Insights, "Rewind" to Icons.Default.History, "Lists" to Icons.Default.Tune)

        Box(Modifier.fillMaxSize()) {
            var refreshing by remember { mutableStateOf(false) }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    vm.refreshFromSheet { msg ->
                        refreshing = false
                        android.widget.Toast.makeText(appCtx, msg, android.widget.Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(grad)).nestedScroll(scrollWatcher).statusBarsPadding()
            ) {
                when (tab) {
                    0 -> LogScreen("Movie Log", "", movies, watchlist, vm, onLogWatch = { logging = it }) { detailId = it.id }
                    1 -> DashboardScreen(movies, subs, options, vm)
                    2 -> RewindScreen(movies, subs, vm, grad.take(2)) { detailId = it.id }
                    else -> ListsScreen(movies, subs, options, vm) { showSettings = true }
                }
            }
            FloatingChrome(nav, tab, tabs, onTab = { tab = it }, onAdd = { adding = true })

            val detail = detailId?.let { id -> movies.find { it.id == id } }
            if (detail != null) MovieDetailScreen(
                m = detail, vm = vm,
                onBack = { detailId = null },
                onEdit = { editing = detail },
                onDelete = { vm.delete(detail); detailId = null })
        }

        if (showSettings) SettingsScreen(
            vm = vm, selected = gradIdx, movieCount = movies.size,
            onSelect = { gradIdx = it; prefs.edit().putInt("gradient", it).apply() },
            onRefetch = { vm.refetchAll() },
            onBack = { showSettings = false })

        if (adding) MovieEditor(
            initial = null, existing = movies, options = options,
            onSave = { vm.save(it); adding = false },
            onDelete = null, onDismiss = { adding = false })
        logging?.let { w ->
            MovieEditor(
                initial = Movie(date = LocalDate.now().toString(), title = w.title, fee = 2.19, posterPath = w.posterPath,
                    theater = movies.firstOrNull()?.theater ?: ""),
                existing = movies, options = options,
                onSave = { vm.save(it); vm.removeWatch(w); logging = null; tab = 0 },
                onDelete = null, onDismiss = { logging = null })
        }
        editing?.let { m ->
            MovieEditor(
                initial = m, existing = movies, options = options,
                onSave = { vm.save(it); editing = null },
                onDelete = { vm.delete(m); editing = null },
                onDismiss = { editing = null })
        }
    }
}

@Composable
fun LogScreen(title: String, subtitle: String, movies: List<Movie>, watch: List<com.alisttracker.data.WatchItem>, vm: MainVM, onLogWatch: (com.alisttracker.data.WatchItem) -> Unit, onClick: (Movie) -> Unit) {
    var query by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(0) }   // 0 = AMC, 1 = other theaters, 2 = watchlist
    var fFormat by remember { mutableStateOf<String?>(null) }
    var fYear by remember { mutableStateOf<String?>(null) }
    var f3d by remember { mutableStateOf(false) }
    var fRating by remember { mutableStateOf<Int?>(null) }   // null = any, 0 = unrated, 1..5 = exactly that many stars
    var fLang by remember { mutableStateOf<String?>(null) }
    var fAlist by remember { mutableStateOf<String?>(null) }   // Yes / Gift Card / Voucher / No
    var showFilters by remember { mutableStateOf(false) }
    val amcCount = movies.count { it.amc }
    val scoped = remember(movies, scope) { movies.filter { it.amc == (scope == 0) } }
    val sorted = remember(scoped) { scoped.latestFirst() }
    val exactFormat = remember(sorted, query) { sorted.map { it.format }.firstOrNull { it.equals(query.trim(), true) } }
    val formatChoices = remember(sorted) { sorted.groupingBy { it.format.ifBlank { "Standard" } }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
    val langChoices = remember(sorted) { sorted.groupingBy { it.language.ifBlank { "English" } }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
    val yearChoices = remember(sorted) { sorted.map { it.date.take(4) }.distinct().sortedDescending() }
    val filterCount = listOfNotNull(fFormat, fYear, fRating, fLang, fAlist).size + (if (f3d) 1 else 0)
    val filtersOn = filterCount > 0
    val list = sorted.filter {
        (fFormat == null || it.format.ifBlank { "Standard" } == fFormat) && (fYear == null || it.date.startsWith(fYear!!)) &&
        (!f3d || it.threeD) && (fRating == null || it.rating == fRating) &&
        (fLang == null || it.language.ifBlank { "English" } == fLang) && (fAlist == null || it.alist == fAlist)
    }.filter {
        query.isBlank() || (it.threeD && query.trim().equals("3d", true)) || it.title.contains(query, true) || it.theater.contains(query, true) ||
            (if (exactFormat != null) it.format.equals(exactFormat, true) else it.format.contains(query, true))
    }
    val grouped = list.groupBy { monthKey(it.date) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(when (scope) { 0 -> "$amcCount AMC visits"; 1 -> "${movies.size - amcCount} at other theaters"; else -> "${watch.size} to watch" }, fontSize = 13.sp, color = Muted)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("AMC", "Other theaters", "Watchlist").forEachIndexed { i, label ->
                    val on = scope == i
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else Muted,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Red else Surface1)
                            .clickable { scope = i }.padding(horizontal = 14.dp, vertical = 7.dp))
                }
            }
            if (scope != 2) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                query, { query = it }, singleLine = true,
                placeholder = { Text("Search title, theater or format", color = Muted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Surface1, unfocusedContainerColor = Surface1,
                    focusedBorderColor = Red, unfocusedBorderColor = Surface2),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterTag(if (filtersOn) "Filters · $filterCount  ▾" else "Filters  ▾", filtersOn) { showFilters = true }
                if (filtersOn) Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { fFormat = null; fYear = null; f3d = false; fRating = null; fLang = null; fAlist = null }.padding(horizontal = 8.dp, vertical = 6.dp))
            }
            if (filtersOn || query.isNotBlank()) Text("${list.size} of ${sorted.size} shown", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (showFilters) FiltersDialog(fRating, yearChoices, fYear, formatChoices, fFormat, f3d, langChoices, fLang, fAlist,
            { fRating = it }, { fYear = it }, { fFormat = it }, { f3d = it }, { fLang = it }, { fAlist = it }, { fFormat = null; fYear = null; f3d = false; fRating = null; fLang = null; fAlist = null }, { showFilters = false })
        if (scope == 2) WatchlistPane(watch, vm, onLogWatch) else
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            grouped.forEach { (month, items) ->
                item(key = "h$month") {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(month.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Gold, letterSpacing = 1.sp)
                        Spacer(Modifier.weight(1f))
                        Text("${items.size} movie${if (items.size == 1) "" else "s"}", fontSize = 12.sp, color = Muted)
                    }
                }
                items(items, key = { it.id }) { m -> MovieRow(m) { onClick(m) } }
            }
        }
    }
}

@Composable
fun Chip(text: String, color: Color) {
    Text(
        text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
fun MovieRow(m: Movie, onClick: () -> Unit) {
    val accent = formatColor(m.format)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.22f), Surface1, Surface1))).height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(accent))
            Box(Modifier.width(72.dp).fillMaxHeight().heightIn(min = 108.dp).background(accent.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Theaters, null, tint = accent.copy(alpha = 0.7f))
                if (m.posterPath.isNotBlank() && m.posterPath != "-") {
                    AsyncImage(
                        model = "https://image.tmdb.org/t/p/w342${m.posterPath}",
                        contentDescription = m.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize())
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(m.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White, maxLines = 2)
                Spacer(Modifier.height(2.dp))
                val whenLine = listOfNotNull(prettyDate(m.date), m.showtime.ifBlank { null }?.let { prettyTime(it) }).joinToString(" · ")
                Text(whenLine, fontSize = 13.sp, color = Color(0xFFD7D8E6))
                val where = listOfNotNull(m.theater.ifBlank { null }, m.screen?.let { "Screen $it" }).joinToString(" · ")
                if (where.isNotBlank()) Text(where, fontSize = 12.sp, color = Muted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(m.format.ifBlank { "Standard" }, accent)
                    if (m.threeD) Chip("3D", Color3D)
                    if (m.language.isNotBlank() && m.language != "English") Chip(m.language, Orange)
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 8.dp).drawBehind {
                drawLine(Color(0xFF3A3A4F), Offset(0f, 0f), Offset(0f, size.height),
                    strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            })
            Column(Modifier.width(92.dp).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(money(m.total), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White)
                if (m.rating > 0) Text("★".repeat(m.rating), fontSize = 12.sp, color = Gold, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun Gauge(progress: Float, top: String, bottom: String, gaugeSize: Dp = 124.dp) {
    Box(Modifier.size(gaugeSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val arc = Size(size.width - stroke, size.height - stroke)
            val tl = Offset(stroke / 2, stroke / 2)
            drawArc(Color.White.copy(alpha = 0.22f), 135f, 270f, false, topLeft = tl, size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Color.White, 135f, 270f * progress.coerceIn(0f, 1f), false, topLeft = tl, size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(top, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(bottom, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun DashboardScreen(movies: List<Movie>, subs: List<SubPayment>, options: List<Option>, vm: MainVM) {
    var addSub by remember { mutableStateOf(false) }
    val s = remember(movies, subs, options) {
        computeStats(movies, subs, options.filter { it.type == OptType.THEATER }.map { it.value }, options.filter { it.type == OptType.FORMAT }.map { it.value })
    }
    val multiple = if (s.subsPaid > 0) s.aListValue / s.subsPaid else 0.0
    val savedPct = if (s.aListValue > 0) (s.savings / s.aListValue).toFloat() else 0f
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Your A-List", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)

        // Hero card with gauge
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFF3B4E), Color(0xFFB4236F), Color(0xFF5B2A9E))))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("TOTAL SAVED", fontSize = 12.sp, letterSpacing = 1.5.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                Text(money(s.savings), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text("${money(s.aListValue)} of tickets for ${money(s.subsPaid)} in membership", fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
            }
            Gauge(savedPct, String.format(Locale.US, "%.1f×", multiple), "return")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Movies", s.totalMovies.toString(), Red, Modifier.weight(1f))
            Tile("Per \$1 spent", String.format(Locale.US, "$%.2f", s.valuePerDollar), Gold, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("A-List value", money(s.aListValue), Green, Modifier.weight(1f))
            Tile("Membership paid", money(s.subsPaid), Purple, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Out of pocket", money(s.outOfPocket), Orange, Modifier.weight(1f))
            Tile("Gift card + voucher", "${s.giftCardMovies + s.voucherMovies}", Teal, Modifier.weight(1f))
        }

        val cs = remember(movies) { concessionStats(movies) }
        if (cs.visits > 0) {
            Section("Concessions & true cost", Orange) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Popcorn & drinks", money(cs.total), Orange, Modifier.weight(1f))
                    Tile("Avg per visit", money(cs.total / cs.visits), Gold, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                cs.byPay.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(k, color = Color.White, modifier = Modifier.weight(1f))
                        Text(money(v), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                val trueCost = s.subsPaid + s.outOfPocket + cs.cashOut
                Text("True total cost: ${money(trueCost)}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text("Membership + tickets you paid for + concessions paid with cash or card (rewards and gift cards left out).", color = Muted, fontSize = 12.sp)
            }
        }

        run {
            val now = LocalDate.now()
            val ms = monthSummary(movies, subs, now.year, now.monthValue)
            val (won, total) = monthsPaidOff(movies, subs)
            Section("This month", Green) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("A-List visits", ms.visits.toString(), Red, Modifier.weight(1f))
                    Tile("Free tickets", money(ms.value), Green, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        ms.cost <= 0 -> "Add this month's membership payment to see if it paid off."
                        ms.paidOff -> "Paid for itself — ${money(ms.net)} ahead of the ${money(ms.cost)} membership."
                        else -> "${money(ms.cost - ms.value)} of tickets to go to cover the ${money(ms.cost)} membership."
                    }, color = if (ms.paidOff) Green else Color(0xFFD7D8E6), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (total > 0) Text("$won of $total paid months earned back their membership", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                val wk = visitsLast7Days(movies)
                Text("Last 7 days: $wk of 3 A-List movies used", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }

        val other = movies.filter { !it.amc }
        if (other.isNotEmpty()) {
            Section("Other theaters (not counted above)", Gold) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Movies", other.size.toString(), Gold, Modifier.weight(1f))
                    Tile("Total spent", money(other.sumOf { it.total }), Orange, Modifier.weight(1f))
                }
            }
        }

        SpendingCharts(movies, subs)

        Section("Theaters", Red) {
            val max = s.byTheater.maxOfOrNull { it.second } ?: 1
            s.byTheater.forEach { BarRow(it.first.ifBlank { "—" }, it.second, max, Red) }
        }
        Section("Formats", Blue) {
            val max = s.byFormat.maxOfOrNull { it.second } ?: 1
            s.byFormat.forEach { BarRow(it.first.ifBlank { "—" }, it.second, max, formatColor(it.first)) }
        }
        Section("Average ticket by format", Gold) {
            s.avgTicketByFormat.forEach { (f, avg) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(formatColor(f)))
                    Spacer(Modifier.width(8.dp))
                    Text(f.ifBlank { "—" }, color = Color.White, modifier = Modifier.weight(1f))
                    Text(money(avg), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        Section("Movies per month", Orange) {
            val months = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
            Row { Spacer(Modifier.width(44.dp)); months.forEach { Text(it, Modifier.weight(1f), fontSize = 11.sp, color = Muted) } }
            s.perMonth.forEach { (y, arr) ->
                val max = (arr.maxOrNull() ?: 1).coerceAtLeast(1)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("$y", Modifier.width(44.dp), fontSize = 12.sp, color = Muted)
                    arr.forEach { c ->
                        val color = if (c == 0) Surface2 else lerp(Color(0xFF7A2A3A), Gold, c.toFloat() / max)
                        Box(Modifier.weight(1f).height(26.dp).padding(1.5.dp).clip(RoundedCornerShape(5.dp)).background(color), contentAlignment = Alignment.Center) {
                            if (c > 0) Text("$c", fontSize = 10.sp, color = if (c * 2 > max) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Section("Membership payments", Purple) {
            subs.groupBy { it.year }.forEach { (y, l) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text("$y · ${l.size} months", color = Color.White, modifier = Modifier.weight(1f))
                    Text(money(l.sumOf { it.amount }), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Button(onClick = { addSub = true }, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Add / edit payment") }
        }
        Spacer(Modifier.height(110.dp))
    }
    if (addSub) SubDialog(onSave = { vm.saveSub(it); addSub = false }, onDismiss = { addSub = false })
}

@Composable
fun Tile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.30f), Surface1)))
            .padding(14.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.height(8.dp))
        Text(value, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
        Text(label, fontSize = 12.sp, color = Muted)
    }
}

@Composable
fun Section(title: String, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface1).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Spacer(Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
fun BarRow(label: String, count: Int, max: Int, color: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("$count", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Surface2)) {
            Box(Modifier.fillMaxWidth(count.toFloat() / max.coerceAtLeast(1)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}

@Composable
fun ListsScreen(movies: List<Movie>, subs: List<SubPayment>, options: List<Option>, vm: MainVM, onSettings: () -> Unit) {
    var pendingDelete by remember { mutableStateOf<Option?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Lists", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) }
        }
        Text("Add a new theater, format, language or swag item here. It shows up in the movie form and on Stats.", fontSize = 13.sp, color = Muted)
        OptionSection("Theaters", Red, OptType.THEATER, options, { v -> movies.count { it.theater == v } }, { vm.addOption(OptType.THEATER, it) }) { pendingDelete = it }
        OptionSection("Formats", Blue, OptType.FORMAT, options, { v -> movies.count { it.format == v } }, { vm.addOption(OptType.FORMAT, it) }) { pendingDelete = it }
        OptionSection("Languages", Teal, OptType.LANGUAGE, options, { v -> movies.count { it.language == v } }, { vm.addOption(OptType.LANGUAGE, it) }) { pendingDelete = it }
        OptionSection("Swag / freebies", Gold, OptType.SWAG, options, { v -> movies.count { it.swag == v } }, { vm.addOption(OptType.SWAG, it) }) { pendingDelete = it }
        Section("A-List types", Green) {
            listOf("Yes", "No", "Gift Card", "Voucher").forEach {
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(aListColor(it)))
                    Spacer(Modifier.width(8.dp))
                    Text(it, color = Color.White)
                }
            }
            Text("Fixed, because the savings math depends on them.", fontSize = 12.sp, color = Muted)
        }
        Spacer(Modifier.height(110.dp))
    }
    pendingDelete?.let { o ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove \"${o.value}\"?") },
            text = { Text("It disappears from the dropdowns. Movies already logged with it keep their value.") },
            confirmButton = { TextButton(onClick = { vm.deleteOption(o); pendingDelete = null }) { Text("Remove", color = Red) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun OptionSection(
    title: String, accent: Color, type: String, options: List<Option>,
    usage: (String) -> Int, onAdd: (String) -> Unit, onDelete: (Option) -> Unit
) {
    val items = options.filter { it.type == type }
    var text by remember { mutableStateOf("") }
    Section(title, accent) {
        items.forEach { o ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(o.value, color = Color.White, modifier = Modifier.weight(1f))
                Text("${usage(o.value)} used", fontSize = 12.sp, color = Muted)
                IconButton(onClick = { onDelete(o) }) { Icon(Icons.Default.Close, "Remove", tint = Muted) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text, { text = it }, singleLine = true,
                placeholder = { Text("Add new…", color = Muted) },
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { if (text.isNotBlank()) { onAdd(text.trim()); text = "" } },
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
            ) { Text("Add") }
        }
    }
}

@Composable
fun SubDialog(onSave: (SubPayment) -> Unit, onDismiss: () -> Unit) {
    val now = LocalDate.now()
    var y by remember { mutableStateOf(now.year.toString()) }
    var m by remember { mutableStateOf(now.monthValue.toString()) }
    var a by remember { mutableStateOf("30.00") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subscription payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(y, { y = it }, label = { Text("Year") }, singleLine = true)
                OutlinedTextField(m, { m = it }, label = { Text("Month (1-12)") }, singleLine = true)
                OutlinedTextField(a, { a = it }, label = { Text("Amount") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val yy = y.toIntOrNull(); val mm = m.toIntOrNull(); val aa = a.toDoubleOrNull()
                if (yy != null && mm != null && mm in 1..12 && aa != null) onSave(SubPayment(yy, mm, aa))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieEditor(initial: Movie?, existing: List<Movie>, options: List<Option>, onSave: (Movie) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now().toString()) }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var ticket by remember { mutableStateOf(initial?.ticket?.toString() ?: "") }
    var fee by remember { mutableStateOf(initial?.fee?.toString() ?: "2.19") }
    var format by remember { mutableStateOf(initial?.format ?: "Standard") }
    var language by remember { mutableStateOf(initial?.language ?: "English") }
    var theater by remember { mutableStateOf(initial?.theater ?: existing.firstOrNull()?.theater ?: "") }
    var screen by remember { mutableStateOf(initial?.screen?.toString() ?: "") }
    var showtime by remember { mutableStateOf(initial?.showtime ?: "") }
    var alist by remember { mutableStateOf(initial?.alist ?: "Yes") }
    var threeD by remember { mutableStateOf(initial?.threeD ?: false) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var swag by remember { mutableStateOf(initial?.swag ?: "") }
    var amc by remember { mutableStateOf(initial?.amc ?: true) }
    var rating by remember { mutableStateOf(initial?.rating ?: 0) }
    var concessions by remember { mutableStateOf(initial?.concessions?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.2f", it) } ?: "") }
    var concPay by remember { mutableStateOf(initial?.concessionsPay ?: "") }
    var showPicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    fun opt(t: String, extra: List<String>) =
        (options.filter { it.type == t }.map { it.value } + extra).filter { it.isNotBlank() }.distinct()
    val formats = opt(OptType.FORMAT, existing.map { it.format })
    val languages = opt(OptType.LANGUAGE, existing.map { it.language })
    val theaters = opt(OptType.THEATER, existing.map { it.theater })
    val swagOpts = opt(OptType.SWAG, existing.map { it.swag })

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Log a movie" else "Edit movie") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(date, { date = it }, label = { Text("Date (yyyy-MM-dd)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { IconButton({ showPicker = true }) { Icon(Icons.Default.DateRange, "Pick date") } })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(ticket, { ticket = it }, label = { Text("Ticket $") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(fee, { fee = it }, label = { Text("Fee $") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Choice("Format", format, formats) { format = it }
                Choice("Language", language, languages) { language = it }
                Choice("Theater", theater, theaters) { theater = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(screen, { screen = it }, label = { Text("Screen") }, singleLine = true, modifier = Modifier.weight(1f))
                    Box(Modifier.weight(1f)) {
                        OutlinedTextField(
                            if (showtime.isBlank()) "" else prettyTime(showtime), {}, readOnly = true,
                            label = { Text("Showtime") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { Icon(Icons.Default.Schedule, "Pick time") })
                        Box(Modifier.matchParentSize().clickable { showTimePicker = true })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(threeD, { threeD = it }); Text("Watched in 3D")
                }
                Choice("A-List", alist, listOf("Yes", "No", "Gift Card", "Voucher")) { alist = it }
                Choice("Swag / freebies", swag, swagOpts) { swag = it }
                OutlinedTextField(concessions, { concessions = it }, label = { Text("Concessions \$ (popcorn, drinks)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if ((concessions.toDoubleOrNull() ?: 0.0) > 0) Choice("Paid with", concPay, listOf("Cash", "Credit Card", "Rewards", "Gift Card", "Other")) { concPay = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rating", color = Muted, fontSize = 14.sp, modifier = Modifier.width(64.dp))
                    StarRating(rating, size = 30.dp) { rating = it }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(amc, { amc = it }); Text("AMC theater (counts toward A-List stats)")
                }
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete this movie", color = Red) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) return@TextButton
                runCatching { LocalDate.parse(date) }.getOrNull() ?: return@TextButton
                onSave(Movie(
                    id = initial?.id ?: 0, date = date, title = title.trim(),
                    ticket = ticket.toDoubleOrNull() ?: 0.0, fee = fee.toDoubleOrNull() ?: 0.0,
                    format = format, language = language, theater = theater,
                    screen = screen.toIntOrNull(), showtime = showtime, alist = alist, threeD = threeD,
                    notes = notes, swag = swag, amc = amc, rating = rating,
                    concessions = (concessions.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                    concessionsPay = if ((concessions.toDoubleOrNull() ?: 0.0) > 0) concPay else "",
                    posterPath = if (initial != null && initial.title == title.trim()) initial.posterPath else ""))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showTimePicker) {
        val init = runCatching { java.time.LocalTime.parse(showtime) }.getOrNull() ?: java.time.LocalTime.of(19, 0)
        val ts = rememberTimePickerState(initialHour = init.hour, initialMinute = init.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Showtime") },
            text = { TimePicker(state = ts) },
            confirmButton = { TextButton(onClick = { showtime = String.format(java.util.Locale.US, "%02d:%02d", ts.hour, ts.minute); showTimePicker = false }) { Text("OK") } },
            dismissButton = {
                Row {
                    if (showtime.isNotBlank()) TextButton(onClick = { showtime = ""; showTimePicker = false }) { Text("Clear") }
                    TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                }
            }
        )
    }

    if (showPicker) {
        val st = rememberDatePickerState(
            initialSelectedDateMillis = runCatching { LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE) }
                    showPicker = false
                }) { Text("OK") }
            }
        ) { DatePicker(state = st) }
    }
}

/** Free-text field with a dropdown of existing options, so new values can be typed in. */
@Composable
fun Choice(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            trailingIcon = { IconButton({ open = true }) { Icon(Icons.Default.ArrowDropDown, null) } })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onChange(o); open = false }) }
        }
    }
}

class NavState {
    var expanded by mutableStateOf(true)
    private var acc = 0f
    /** Collapse after a decent scroll down, expand after a scroll up (ignores tiny jitter). */
    fun onScroll(dy: Float) {
        if (dy == 0f) return
        if ((acc > 0 && dy < 0) || (acc < 0 && dy > 0)) acc = 0f
        acc += dy
        if (acc < -90f && expanded) { expanded = false; acc = 0f }
        else if (acc > 50f && !expanded) { expanded = true; acc = 0f }
    }
}

@Composable
fun BoxScope.FloatingChrome(nav: NavState, tab: Int, tabs: List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>, onTab: (Int) -> Unit, onAdd: () -> Unit) {
    val expanded = nav.expanded
    val fabBottom by androidx.compose.animation.core.animateDpAsState(if (expanded) 92.dp else 20.dp, label = "fab")
    if (tab == 0) FloatingActionButton(
                onClick = onAdd, containerColor = Red, contentColor = Color.White, shape = CircleShape,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = fabBottom)
            ) { Icon(Icons.Default.Add, "Add movie") }

            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                AnimatedContent(
                    targetState = expanded,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "nav"
                ) { open ->
                    if (open) {
                        Row(
                            Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(36.dp)).clip(RoundedCornerShape(36.dp))
                                .background(Surface1.copy(alpha = 0.97f)).padding(6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
                        ) {
                            tabs.forEachIndexed { i, (label, icon) ->
                                val sel = i == tab
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(28.dp))
                                        .background(if (sel) Red else Color.Transparent)
                                        .clickable { onTab(i) }.padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(icon, label, tint = if (sel) Color.White else Muted)
                                    Text(label, fontSize = 11.sp, color = if (sel) Color.White else Muted, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                                }
                            }
                        }
                    } else {
                        Box(
                            Modifier.size(58.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(Red)
                                .clickable { nav.expanded = true },
                            contentAlignment = Alignment.Center
                        ) { Icon(tabs[tab].second, tabs[tab].first, tint = Color.White) }
                    }
                }
            }


}

@Composable
private fun FilterDropdown(label: String, value: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 14.sp, modifier = Modifier.width(76.dp))
        Box(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface2).clickable { open = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(value, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowDown, null, tint = Muted)
            }
            DropdownMenu(open, { open = false }) {
                options.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(o) }, onClick = { onPick(i); open = false }) }
            }
        }
    }
}

@Composable
private fun FiltersDialog(
    ratingNow: Int?, years: List<String>, yearNow: String?, formats: List<String>, formatNow: String?, threeD: Boolean,
    langs: List<String>, langNow: String?, alistNow: String?,
    onRating: (Int?) -> Unit, onYear: (String?) -> Unit, onFormat: (String?) -> Unit, on3D: (Boolean) -> Unit, onLang: (String?) -> Unit, onAlist: (String?) -> Unit, onClear: () -> Unit, onDone: () -> Unit
) {
    val ratingOpts = listOf("Any rating", "0 · Unrated", "1 ★", "2 ★★", "3 ★★★", "4 ★★★★", "5 ★★★★★")
    val yearOpts = listOf("All years") + years
    val formatOpts = listOf("All formats") + formats
    val langOpts = listOf("All languages") + langs
    val alistValues = listOf("Yes", "Gift Card", "Voucher", "No")
    val alistOpts = listOf("All tickets", "A-List", "Gift Card", "Voucher", "No A-List")
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Filters") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterDropdown("Rating", ratingOpts[if (ratingNow == null) 0 else ratingNow + 1], ratingOpts) { onRating(if (it == 0) null else it - 1) }
                FilterDropdown("Year", yearNow ?: yearOpts[0], yearOpts) { onYear(if (it == 0) null else years[it - 1]) }
                FilterDropdown("Format", formatNow ?: formatOpts[0], formatOpts) { onFormat(if (it == 0) null else formats[it - 1]) }
                FilterDropdown("Language", langNow ?: langOpts[0], langOpts) { onLang(if (it == 0) null else langs[it - 1]) }
                FilterDropdown("Ticket", alistNow?.let { alistOpts[alistValues.indexOf(it) + 1] } ?: alistOpts[0], alistOpts) { onAlist(if (it == 0) null else alistValues[it - 1]) }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(threeD, on3D); Text("Watched in 3D") }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
        dismissButton = { TextButton(onClick = onClear) { Text("Clear all", color = Red) } }
    )
}

@Composable
private fun FilterTag(label: String, on: Boolean, onClick: () -> Unit) {
    Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else Color(0xFFD7D8E6),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Red else Surface2).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp))
}
