package com.alisttracker.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
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
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import com.alisttracker.data.Movie
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The looks a shared ticket card can have. */
enum class ShareStyle(val label: String) { CLASSIC("Classic"), POSTER("Poster"), PAPER("Paper") }

// The card is laid out on a 1080 x 1350 canvas (4:5, good for Instagram and messages) and drawn at any scale,
// so text and shapes stay sharp at 2x. The poster is fetched at its largest size.
private const val W = 1080f
private const val H = 1350f

private val longDate = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)

/** Downloads the poster at full size (falls back to a smaller one). Null when there is none. */
suspend fun loadPosterBitmap(ctx: Context, poster: String?): Bitmap? {
    if (poster.isNullOrBlank()) return null
    for (size in listOf("original", "w780")) {
        val req = ImageRequest.Builder(ctx).data("https://image.tmdb.org/t/p/$size$poster").allowHardware(false).size(Size.ORIGINAL).build()
        val res = ctx.imageLoader.execute(req)
        if (res is SuccessResult) return runCatching { res.drawable.toBitmap() }.getOrNull()
    }
    return null
}

fun renderShareCard(m: Movie, poster: Bitmap?, style: ShareStyle, scale: Float = 1f): Bitmap {
    val bmp = Bitmap.createBitmap((W * scale).toInt(), (H * scale).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(scale, scale)
    when (style) {
        ShareStyle.CLASSIC -> drawClassic(c, m, poster)
        ShareStyle.POSTER -> drawPosterStyle(c, m, poster)
        ShareStyle.PAPER -> drawPaper(c, m, poster)
    }
    return bmp
}

// ---------- shared helpers ----------

private const val WHITE = 0xFFFFFFFF.toInt()
private const val SOFT = 0xFFD7D8E6.toInt()
private const val GOLD = 0xFFFFC857.toInt()

private fun tp(size: Float, color: Int, bold: Boolean = false, face: Typeface? = null) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = size
    this.color = color
    typeface = face ?: if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
}

private fun withAlpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

private fun layoutText(text: String, paint: TextPaint, width: Int, maxLines: Int, align: Layout.Alignment): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(align).setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build()

private fun drawLayout(c: Canvas, l: StaticLayout, x: Float, y: Float) { c.save(); c.translate(x, y); l.draw(c); c.restore() }

private fun dateLine(m: Movie) = runCatching { LocalDate.parse(m.date).format(longDate) }.getOrDefault(m.date)
private fun timeLine(m: Movie) = m.showtime.takeIf { it.isNotBlank() }?.let { prettyTime(it) } ?: ""
private fun venueLine(m: Movie) = listOfNotNull(m.theater.ifBlank { null }, m.screen?.let { "Screen $it" }).joinToString(" · ")
private fun starsLine(m: Movie) = "★".repeat(m.rating) + "☆".repeat(5 - m.rating)

private class Pill(val text: String, val color: Int)

private fun pillsFor(m: Movie): List<Pill> = buildList {
    add(Pill(m.format.ifBlank { "Standard" }, formatColor(m.format).toArgb()))
    if (m.threeD) add(Pill("3D", Color3D.toArgb()))
    if (m.language.isNotBlank() && m.language != "English") add(Pill(m.language, Orange.toArgb()))
}

private const val PILL_PAD_X = 26f
private const val PILL_H = 60f
private const val PILL_GAP = 14f

private fun pillsWidth(pills: List<Pill>, p: TextPaint) = pills.sumOf { (p.measureText(it.text) + 2 * PILL_PAD_X).toDouble() }.toFloat() + PILL_GAP * (pills.size - 1)

/** Draws the pills starting at [startX]; [outline] draws just a coloured border (for the paper look). */
private fun drawPills(c: Canvas, pills: List<Pill>, startX: Float, top: Float, p: TextPaint, outline: Boolean, textOverride: Int? = null) {
    var x = startX
    val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    pills.forEach { pill ->
        val w = p.measureText(pill.text) + 2 * PILL_PAD_X
        val r = RectF(x, top, x + w, top + PILL_H)
        if (outline) { bg.style = Paint.Style.STROKE; bg.strokeWidth = 4f; bg.color = pill.color } else { bg.style = Paint.Style.FILL; bg.color = withAlpha(pill.color, 0.25f) }
        c.drawRoundRect(r, PILL_H / 2, PILL_H / 2, bg)
        p.color = textOverride ?: pill.color
        val base = top + PILL_H / 2 - (p.descent() + p.ascent()) / 2
        c.drawText(pill.text, x + PILL_PAD_X, base, p)
        x += w + PILL_GAP
    }
}

/** Center-crops the poster into [rect]; draws a plain block if there is no poster. */
private fun drawPosterInto(c: Canvas, poster: Bitmap?, rect: RectF, radius: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    c.save()
    if (radius > 0f) c.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
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

// ---------- Classic: dark ticket with the format colour ----------

private fun drawClassic(c: Canvas, m: Movie, poster: Bitmap?) {
    val accent = formatColor(m.format).toArgb()
    c.drawColor(0xFF0B0B12.toInt())
    val card = RectF(40f, 40f, W - 40f, H - 40f)
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(card.left, card.top, card.right, card.bottom,
            intArrayOf(withAlpha(accent, 0.55f), 0xFF241638.toInt(), 0xFF14141F.toInt()), null, Shader.TileMode.CLAMP)
    }
    c.drawRoundRect(card, 56f, 56f, bg)

    val posterW = 400f; val posterH = 600f
    val titleLay = layoutText(m.title, tp(68f, WHITE, true), 880, 2, Layout.Alignment.ALIGN_CENTER)
    val lines = listOf(dateLine(m), timeLine(m), venueLine(m)).filter { it.isNotBlank() }
    val pills = pillsFor(m)
    val pillP = tp(30f, WHITE, true)
    val stars = m.rating > 0
    val total = posterH + 44f + titleLay.height + 22f + lines.size * 50f + 26f + PILL_H + (if (stars) 24f + 64f else 0f)
    var y = 60f + (1180f - total) / 2f

    val px = (W - posterW) / 2f
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
    c.drawRoundRect(RectF(px + 8f, y + 14f, px + posterW + 8f, y + posterH + 14f), 28f, 28f, shadow)
    drawPosterInto(c, poster, RectF(px, y, px + posterW, y + posterH), 28f)
    y += posterH + 44f

    drawLayout(c, titleLay, (W - 880f) / 2f, y)
    y += titleLay.height + 22f

    val center = W / 2f
    lines.forEachIndexed { i, t ->
        val p = tp(if (i == 0) 40f else 36f, if (i == 0) WHITE else SOFT, i == 0)
        p.textAlign = Paint.Align.CENTER
        c.drawText(t, center, y + 38f, p)
        y += 50f
    }
    y += 26f
    drawPills(c, pills, (W - pillsWidth(pills, pillP)) / 2f, y, pillP, outline = false)
    y += PILL_H
    if (stars) {
        val sp = tp(60f, GOLD); sp.textAlign = Paint.Align.CENTER
        c.drawText(starsLine(m), center, y + 24f + 52f, sp)
    }
    val fp = tp(26f, 0x73FFFFFF, true); fp.textAlign = Paint.Align.CENTER; fp.letterSpacing = 0.1f
    c.drawText("A-List Tracker", center, H - 72f, fp)
}

// ---------- Poster: the poster fills the card, text sits over a dark fade ----------

private fun drawPosterStyle(c: Canvas, m: Movie, poster: Bitmap?) {
    val accent = formatColor(m.format).toArgb()
    c.drawColor(0xFF0B0B12.toInt())
    if (poster != null) drawPosterInto(c, poster, RectF(0f, 0f, W, H), 0f)
    else c.drawRect(0f, 0f, W, H, Paint().apply { shader = LinearGradient(0f, 0f, W, H, withAlpha(accent, 0.7f), 0xFF14141F.toInt(), Shader.TileMode.CLAMP) })

    val fade = Paint().apply {
        shader = LinearGradient(0f, H * 0.34f, 0f, H, intArrayOf(0x00000000, 0xB8000000.toInt(), 0xF5000000.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }
    c.drawRect(0f, H * 0.34f, W, H, fade)
    val topFade = Paint().apply { shader = LinearGradient(0f, 0f, 0f, 220f, 0x88000000.toInt(), 0x00000000, Shader.TileMode.CLAMP) }
    c.drawRect(0f, 0f, W, 220f, topFade)

    val brand = tp(28f, 0xCCFFFFFF.toInt(), true); brand.textAlign = Paint.Align.RIGHT; brand.letterSpacing = 0.1f
    c.drawText("A-List Tracker", W - 64f, 84f, brand)

    val left = 70f; val width = 940
    val titleLay = layoutText(m.title, tp(92f, WHITE, true), width, 3, Layout.Alignment.ALIGN_NORMAL)
    val lines = listOf(dateLine(m), timeLine(m), venueLine(m)).filter { it.isNotBlank() }
    val pills = pillsFor(m)
    val pillP = tp(30f, WHITE, true)
    val stars = m.rating > 0
    val total = titleLay.height + 20f + lines.size * 50f + 24f + PILL_H + (if (stars) 20f + 64f else 0f)
    var y = H - 90f - total

    drawLayout(c, titleLay, left, y)
    y += titleLay.height + 20f
    lines.forEachIndexed { i, t ->
        val p = tp(if (i == 0) 40f else 36f, if (i == 0) WHITE else SOFT, i == 0)
        c.drawText(t, left, y + 38f, p)
        y += 50f
    }
    y += 24f
    drawPills(c, pills, left, y, pillP, outline = false)
    y += PILL_H
    if (stars) c.drawText(starsLine(m), left, y + 20f + 52f, tp(60f, GOLD))
}

// ---------- Paper: a cream paper ticket with a perforated stub ----------

private fun drawPaper(c: Canvas, m: Movie, poster: Bitmap?) {
    val ink = 0xFF2A2118.toInt()
    val faint = 0xFF8A7B60.toInt()
    val outer = 0xFF1B1620.toInt()
    c.drawColor(outer)
    val paper = RectF(50f, 50f, W - 50f, H - 50f)
    val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF4EAD5.toInt() }
    c.drawRoundRect(paper, 40f, 40f, paperPaint)

    // red header band, clipped to the paper's rounded top
    c.save()
    c.clipPath(Path().apply { addRoundRect(paper, 40f, 40f, Path.Direction.CW) })
    c.drawRect(paper.left, paper.top, paper.right, paper.top + 170f, Paint().apply { color = 0xFFC21F3A.toInt() })
    c.restore()
    val serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    val mono = Typeface.MONOSPACE
    val head = tp(64f, WHITE, face = serifBold); head.letterSpacing = 0.14f
    c.drawText("ADMIT ONE", 100f, 160f, head)
    val tag = tp(34f, 0xFFFFD7DC.toInt(), face = serifBold); tag.textAlign = Paint.Align.RIGHT; tag.letterSpacing = 0.2f
    c.drawText("A-LIST", W - 100f, 158f, tag)

    // poster on the left with a thin ink border
    val pr = RectF(100f, 250f, 500f, 850f)
    drawPosterInto(c, poster, pr, 8f)
    c.drawRoundRect(pr, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = ink })

    // details on the right
    val x = 545f; val w = 440
    var y = 250f
    val titleLay = layoutText(m.title, tp(62f, ink, face = serifBold), w, 4, Layout.Alignment.ALIGN_NORMAL)
    drawLayout(c, titleLay, x, y)
    y += titleLay.height + 24f
    listOf(dateLine(m), timeLine(m)).filter { it.isNotBlank() }.forEach {
        val l = layoutText(it, tp(33f, ink, face = mono), w, 2, Layout.Alignment.ALIGN_NORMAL)
        drawLayout(c, l, x, y); y += l.height + 8f
    }
    venueLine(m).takeIf { it.isNotBlank() }?.let {
        val l = layoutText(it, tp(31f, faint, face = mono), w, 3, Layout.Alignment.ALIGN_NORMAL)
        drawLayout(c, l, x, y); y += l.height + 8f
    }
    y += 18f
    val pills = pillsFor(m)
    val pillP = tp(28f, ink, true)
    // wrap the pills onto a second row if they would run off the page
    var row = mutableListOf<Pill>(); var rowW = 0f
    val rows = mutableListOf<List<Pill>>()
    pills.forEach { pill ->
        val pw = pillP.measureText(pill.text) + 2 * PILL_PAD_X + PILL_GAP
        if (rowW + pw > w + PILL_GAP && row.isNotEmpty()) { rows.add(row); row = mutableListOf(); rowW = 0f }
        row.add(pill); rowW += pw
    }
    if (row.isNotEmpty()) rows.add(row)
    rows.forEach { r -> drawPills(c, r, x, y, pillP, outline = true, textOverride = ink); y += PILL_H + 12f }

    // perforation + notches
    val perfY = 960f
    val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5f; color = 0xFF9C8F76.toInt(); pathEffect = DashPathEffect(floatArrayOf(20f, 14f), 0f)
    }
    c.drawLine(paper.left + 50f, perfY, paper.right - 50f, perfY, dash)
    val notch = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = outer }
    c.drawCircle(paper.left, perfY, 36f, notch)
    c.drawCircle(paper.right, perfY, 36f, notch)

    // stub
    val center = W / 2f
    val stubLabel = tp(34f, faint, face = mono); stubLabel.textAlign = Paint.Align.CENTER; stubLabel.letterSpacing = 0.25f
    c.drawText("ADMIT ONE  ·  A-LIST", center, 1060f, stubLabel)
    if (m.rating > 0) {
        val sp = tp(80f, 0xFFC28A00.toInt()); sp.textAlign = Paint.Align.CENTER
        c.drawText(starsLine(m), center, 1170f, sp)
    }
    val fp = tp(26f, faint, true); fp.textAlign = Paint.Align.CENTER; fp.letterSpacing = 0.1f
    c.drawText("A-List Tracker", center, H - 84f, fp)
}
