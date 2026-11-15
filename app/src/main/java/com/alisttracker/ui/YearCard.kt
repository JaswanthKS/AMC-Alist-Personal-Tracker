package com.alisttracker.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size as CoilSize
import com.alisttracker.data.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ---------------------------------------------------------------------------------------------
// Year-end card. Every movie from January to December in date order, packed onto tall pages
// (2160 x 3840 design units) with as many posters per page as fit. Page 1 starts with the summary.
//
// The viewer draws the pages live, so text stays vector-sharp at any zoom and posters are loaded at
// 780 px. Sharing exports the same drawing as PNG files at 1.5x (3240 x 5760).
// ---------------------------------------------------------------------------------------------

private const val PW = 2160
private const val PH = 3840
private const val COLS = 6
private const val MARGIN = 90f
private const val GAP = 30f
private const val HEADER_H = 150f
private const val COVER_H = 1720f
private const val TOP = 110f
private const val FOOTER = 130f

private const val C_WHITE = 0xFFFFFFFF.toInt()
private const val C_SOFT = 0xFFD7D8E6.toInt()
private const val C_MUTED = 0xFF9A9CB0.toInt()
private const val C_GOLD = 0xFFFFC857.toInt()

private const val CELL_W = (PW - 2 * MARGIN - (COLS - 1) * GAP) / COLS
private const val K = CELL_W / 372f                 // text and spacing scale for this cell size
private const val ROW_H = CELL_W * 1.5f + 230f * K

private sealed interface YBlock { val h: Float }
private class YCover(val top: List<Movie>) : YBlock { override val h = COVER_H }
private class YHeader(val month: Int, val count: Int, val cont: Boolean) : YBlock { override val h = HEADER_H }
private class YRow(val movies: List<Movie>) : YBlock { override val h = ROW_H }
private class YPage(val ops: List<Pair<Float, YBlock>>)

private fun pageMovies(p: YPage): List<Movie> =
    p.ops.flatMap { (_, b) -> when (b) { is YRow -> b.movies; is YCover -> b.top; else -> emptyList() } }

/** Fills each page with as many rows as fit. A month that runs onto the next page gets a "(CONT.)" header there. */
private fun layoutPages(sorted: List<Movie>): List<YPage> {
    val pages = mutableListOf<MutableList<Pair<Float, YBlock>>>(mutableListOf())
    var y = TOP
    val limit = PH - FOOTER
    fun add(b: YBlock) { pages.last().add(y to b); y += b.h }
    fun newPage() { pages.add(mutableListOf()); y = TOP }

    val topRated = sorted.filter { it.rating > 0 }
        .sortedWith(compareByDescending<Movie> { it.rating }.thenBy { it.date })
        .distinctBy { normTitle(it.title) }.take(COLS)
    add(YCover(topRated))

    sorted.groupBy { it.date.substring(5, 7).toIntOrNull() ?: 0 }.toSortedMap().forEach { (month, movies) ->
        movies.chunked(COLS).forEachIndexed { i, chunk ->
            val row = YRow(chunk)
            if (i == 0) {
                if (y + HEADER_H + row.h > limit) newPage()
                add(YHeader(month, movies.size, false))
            } else if (y + row.h > limit) {
                newPage()
                add(YHeader(month, movies.size, true))
            }
            add(row)
        }
    }
    return pages.map { YPage(it) }
}

// ---------- drawing (used by both the viewer and the PNG export) ----------

private fun drawPage(
    c: Canvas, year: Int, sorted: List<Movie>, page: YPage, pageNo: Int, pageCount: Int,
    posters: Map<String, Bitmap>, gradTop: Int, gradBottom: Int
) {
    val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, PH.toFloat(), gradTop, gradBottom, Shader.TileMode.CLAMP) }
    c.drawRect(0f, 0f, PW.toFloat(), PH.toFloat(), bg)
    page.ops.forEach { (y, b) ->
        when (b) {
            is YCover -> drawCover(c, year, sorted, b, y, posters)
            is YHeader -> drawMonthHeader(c, b, y)
            is YRow -> b.movies.forEachIndexed { j, m -> drawCell(c, m, MARGIN + j * (CELL_W + GAP), y, posters) }
        }
    }
    c.drawText("$year · A-List Tracker", MARGIN, PH - 60f, ycPaint(30f, C_MUTED))
    c.drawText("$pageNo / $pageCount", PW - MARGIN, PH - 60f, ycPaint(30f, C_MUTED).apply { textAlign = Paint.Align.RIGHT })
}

private fun ycPaint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = size
    this.color = color
    typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
}

private fun ycLayout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build()

private fun ycDraw(c: Canvas, l: StaticLayout, x: Float, y: Float) { c.save(); c.translate(x, y); l.draw(c); c.restore() }

private fun drawCover(c: Canvas, year: Int, list: List<Movie>, cover: YCover, y: Float, posters: Map<String, Bitmap>) {
    c.drawText("$year in Review", MARGIN, y + 130f, ycPaint(130f, C_WHITE, true))
    val unique = list.map { normTitle(it.title) }.distinct().size
    val line = if (unique != list.size) "${list.size} visits · $unique different movies" else "${list.size} movies"
    c.drawText(line, MARGIN, y + 230f, ycPaint(66f, C_SOFT))

    val topFormat = list.groupingBy { it.format.ifBlank { "Standard" } }.eachCount().maxByOrNull { it.value }
    val topTheater = list.filter { it.theater.isNotBlank() }.groupingBy { it.theater }.eachCount().maxByOrNull { it.value }
    val busiest = list.groupingBy { it.date.substring(5, 7).toIntOrNull() ?: 0 }.eachCount().maxByOrNull { it.value }
    val rated = list.filter { it.rating > 0 }
    val stats = listOf(
        "TOP FORMAT" to (topFormat?.let { "${it.key} (${it.value})" } ?: "-"),
        "TOP THEATER" to (topTheater?.let { "${it.key} (${it.value})" } ?: "-"),
        "BUSIEST MONTH" to (busiest?.let { Month.of(it.key.coerceIn(1, 12)).getDisplayName(TextStyle.FULL, Locale.US) + " (${it.value})" } ?: "-"),
        "AVERAGE RATING" to (if (rated.isNotEmpty()) String.format(Locale.US, "%.1f ★", rated.map { it.rating }.average()) else "-")
    )
    val colW = (PW - 2 * MARGIN - GAP * 2) / 2
    stats.forEachIndexed { i, (label, value) ->
        val x = MARGIN + (i % 2) * (colW + GAP * 2)
        val top = y + 330f + (i / 2) * 230f
        c.drawText(label, x, top + 40f, ycPaint(38f, C_MUTED, true))
        ycDraw(c, ycLayout(value, ycPaint(74f, C_WHITE, true), colW.toInt(), 1), x, top + 56f)
    }
    c.drawLine(MARGIN, y + 840f, PW - MARGIN, y + 840f, Paint().apply { color = C_GOLD; strokeWidth = 6f })
    if (cover.top.isNotEmpty()) {
        c.drawText("YOUR TOP RATED", MARGIN, y + 960f, ycPaint(60f, C_GOLD, true))
        cover.top.forEachIndexed { j, m -> drawCell(c, m, MARGIN + j * (CELL_W + GAP), y + 1010f, posters) }
    }
}

private fun drawMonthHeader(c: Canvas, h: YHeader, y: Float) {
    val name = Month.of(h.month.coerceIn(1, 12)).getDisplayName(TextStyle.FULL, Locale.US).uppercase(Locale.US) + if (h.cont) " (CONT.)" else ""
    c.drawText(name, MARGIN, y + 90f, ycPaint(76f, C_GOLD, true))
    val right = ycPaint(44f, C_MUTED).apply { textAlign = Paint.Align.RIGHT }
    c.drawText("${h.count} movie${if (h.count == 1) "" else "s"}", PW - MARGIN, y + 88f, right)
    c.drawLine(MARGIN, y + 118f, PW - MARGIN, y + 118f, Paint().apply { color = 0x33FFFFFF; strokeWidth = 3f })
}

private val shortDate = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/** One movie: poster, title, rating (or "Rating unavailable"), then date and format. */
private fun drawCell(c: Canvas, m: Movie, x: Float, y: Float, posters: Map<String, Bitmap>) {
    val posterH = CELL_W * 1.5f
    drawPosterInto(c, posters[m.posterPath], RectF(x, y, x + CELL_W, y + posterH), 18f * K)
    ycDraw(c, ycLayout(m.title, ycPaint(34f * K, C_WHITE, true), CELL_W.toInt(), 2), x, y + posterH + 14f * K)
    val ratingBase = y + posterH + (14f + 82f + 42f) * K
    if (m.rating > 0) {
        val r = m.rating.coerceIn(1, 5)
        val gold = ycPaint(38f * K, C_GOLD)
        val on = "★".repeat(r)
        c.drawText(on, x, ratingBase, gold)
        c.drawText("★".repeat(5 - r), x + gold.measureText(on), ratingBase, ycPaint(38f * K, 0x44FFFFFF))
    } else {
        val tag = ycPaint(24f * K, C_MUTED)
        val label = "Rating unavailable"
        val w = tag.measureText(label) + 28f * K
        val box = RectF(x, ratingBase - 34f * K, x + w, ratingBase + 8f * K)
        c.drawRoundRect(box, 21f * K, 21f * K, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF })
        c.drawText(label, x + 14f * K, ratingBase - 4f * K, tag)
    }
    val date = runCatching { LocalDate.parse(m.date).format(shortDate) }.getOrDefault(m.date)
    val datePaint = ycPaint(28f * K, C_MUTED)
    val metaBase = ratingBase + 40f * K
    c.drawText("$date · ", x, metaBase, datePaint)
    val fmt = m.format.ifBlank { "Standard" } + if (m.threeD) " 3D" else ""
    c.drawText(fmt, x + datePaint.measureText("$date · "), metaBase, ycPaint(28f * K, formatColor(m.format).toArgb(), true))
}

/** Center-crops the poster into [rect]; a plain block when there is no poster (yet). */
private fun drawPosterInto(c: Canvas, poster: Bitmap?, rect: RectF, radius: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    c.save()
    c.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
    if (poster == null) {
        paint.color = 0xFF1F1F2E.toInt()
        c.drawRect(rect, paint)
    } else {
        val srcAspect = poster.width.toFloat() / poster.height
        val dstAspect = rect.width() / rect.height()
        val src = if (srcAspect > dstAspect) {
            val w = poster.height * dstAspect
            Rect(((poster.width - w) / 2).toInt(), 0, ((poster.width + w) / 2).toInt(), poster.height)
        } else {
            val h = poster.width / dstAspect
            Rect(0, ((poster.height - h) / 2).toInt(), poster.width, ((poster.height + h) / 2).toInt())
        }
        c.drawBitmap(poster, src, rect, paint)
    }
    c.restore()
}

// ---------- posters and export ----------

private suspend fun loadPosters(ctx: Context, movies: List<Movie>, w: Int, h: Int): Map<String, Bitmap> = coroutineScope {
    movies.map { it.posterPath }.filter { it.isNotBlank() && it != "-" }.distinct().map { p ->
        async {
            val req = ImageRequest.Builder(ctx).data("https://image.tmdb.org/t/p/w780$p")
                .allowHardware(false).size(CoilSize(w, h)).build()
            val res = ctx.imageLoader.execute(req)
            val bmp = if (res is SuccessResult) runCatching { res.drawable.toBitmap() }.getOrNull() else null
            if (bmp != null) p to bmp else null
        }
    }.awaitAll().filterNotNull().toMap()
}

private suspend fun exportPages(
    ctx: Context, year: Int, sorted: List<Movie>, pages: List<YPage>, gradTop: Int, gradBottom: Int, onProgress: (Int) -> Unit
): List<File> = withContext(Dispatchers.Default) {
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    dir.listFiles()?.filter { it.name.startsWith("yearcard_") }?.forEach { it.delete() }
    val scale = 1.5f
    pages.mapIndexed { i, page ->
        onProgress(i + 1)
        val posters = loadPosters(ctx, pageMovies(page), 560, 840)
        val bmp = Bitmap.createBitmap((PW * scale).toInt(), (PH * scale).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        drawPage(c, year, sorted, page, i + 1, pages.size, posters, gradTop, gradBottom)
        val f = File(dir, "yearcard_${year}_page_${i + 1}.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()   // poster bitmaps are left alone: Coil owns them
        f
    }
}

// ---------- viewer: swipe between pages, pinch or double-tap to zoom, share ----------

@Composable
fun YearCardDialog(year: Int, list: List<Movie>, gradTop: Int, gradBottom: Int, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sorted = remember(list) { list.sortedWith(compareBy<Movie>({ it.date }, { it.showtime }, { it.id })) }
    val pages = remember(sorted) { layoutPages(sorted) }
    var sharing by remember { mutableStateOf<Int?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0B14))) {
            val pager = rememberPagerState(pageCount = { pages.size })
            var zoomed by remember { mutableStateOf(false) }
            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed) { i ->
                ZoomablePage(year, sorted, pages[i], i + 1, pages.size, gradTop, gradBottom) { zoomed = it }
            }
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 28.dp, start = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    sharing?.let { "Preparing page $it of ${pages.size}…" } ?: "${pager.currentPage + 1} / ${pages.size}",
                    color = Color.White, modifier = Modifier.padding(start = 12.dp).weight(1f)
                )
                IconButton(enabled = sharing == null, onClick = {
                    scope.launch {
                        val files = runCatching { exportPages(ctx, year, sorted, pages, gradTop, gradBottom) { sharing = it } }.getOrNull()
                        sharing = null
                        if (!files.isNullOrEmpty()) {
                            val uris = ArrayList(files.map { FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", it) })
                            val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "image/png"
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(Intent.createChooser(send, "Share year card"))
                        }
                    }
                }) { Icon(Icons.Default.Share, "Share all pages", tint = Color.White) }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            }
        }
    }
}

@Composable
private fun ZoomablePage(
    year: Int, sorted: List<Movie>, page: YPage, pageNo: Int, pageCount: Int, gradTop: Int, gradBottom: Int,
    onZoomed: (Boolean) -> Unit
) {
    val ctx = LocalContext.current
    var posters by remember(page) { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    LaunchedEffect(page) { posters = loadPosters(ctx, pageMovies(page), 780, 1170) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    fun clamp(o: Offset, s: Float): Offset {
        val mx = box.width * (s - 1f) / 2f
        val my = box.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }
    Box(
        Modifier.fillMaxSize().clipToBounds().onSizeChanged { box = it }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = if (scale > 1f) 1f else 3f
                    offset = Offset.Zero
                    onZoomed(scale > 1f)
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        val multi = ev.changes.count { it.pressed } >= 2
                        if (multi || scale > 1f) {
                            val z = ev.calculateZoom()
                            val pan = ev.calculatePan()
                            val ns = (scale * z).coerceIn(1f, 12f)
                            scale = ns
                            offset = if (ns <= 1f) Offset.Zero else clamp(offset + pan, ns)
                            onZoomed(ns > 1f)
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            }
    ) {
        // The page is drawn live (not as a flat picture), so zooming re-draws text and posters sharply.
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offset.x; translationY = offset.y
                }
                .drawBehind {
                    drawIntoCanvas { canvas ->
                        val nc = canvas.nativeCanvas
                        val s = minOf(size.width / PW, size.height / PH)
                        nc.save()
                        nc.translate((size.width - PW * s) / 2f, (size.height - PH * s) / 2f)
                        nc.scale(s, s)
                        drawPage(nc, year, sorted, page, pageNo, pageCount, posters, gradTop, gradBottom)
                        nc.restore()
                    }
                }
        )
    }
}
