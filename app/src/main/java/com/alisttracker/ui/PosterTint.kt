package com.alisttracker.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Dominant vivid color of a poster URL (null until loaded / on failure). */
@Composable
fun rememberPosterTint(url: String?): Color? {
    val ctx = LocalContext.current
    var tint by remember(url) { mutableStateOf<Color?>(null) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        tint = withContext(Dispatchers.Default) {
            runCatching {
                val req = ImageRequest.Builder(ctx).data(url).allowHardware(false).size(96).build()
                val d = (ctx.imageLoader.execute(req) as? SuccessResult)?.drawable as? BitmapDrawable
                d?.bitmap?.let { dominant(it) }
            }.getOrNull()
        }
    }
    return tint
}

private fun dominant(src: android.graphics.Bitmap): Color {
    val bmp = android.graphics.Bitmap.createScaledBitmap(src, 32, 48, true)
    val hsv = FloatArray(3)
    val w = FloatArray(12); val sumS = FloatArray(12); val sumV = FloatArray(12)
    var avgV = 0f; var n = 0
    for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
        android.graphics.Color.colorToHSV(bmp.getPixel(x, y), hsv)
        avgV += hsv[2]; n++
        if (hsv[2] < 0.15f || hsv[1] < 0.18f) continue
        val wt = hsv[1] * hsv[2]
        val b = ((hsv[0] / 30f).toInt()).coerceIn(0, 11)
        w[b] += wt; sumS[b] += hsv[1] * wt; sumV[b] += hsv[2] * wt
    }
    var best = 0
    for (i in 1 until 12) if (w[i] > w[best]) best = i
    if (w[best] < 0.5f) {
        // black-and-white / muted poster: soft slate
        val v = (avgV / n).coerceIn(0.25f, 0.5f)
        return Color(android.graphics.Color.HSVToColor(floatArrayOf(220f, 0.15f, v + 0.1f)))
    }
    // refine hue as weighted average inside the winning bucket
    var hx = 0f; var hy = 0f
    for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
        android.graphics.Color.colorToHSV(bmp.getPixel(x, y), hsv)
        if (hsv[2] < 0.15f || hsv[1] < 0.18f) continue
        if ((hsv[0] / 30f).toInt().coerceIn(0, 11) != best) continue
        val wt = hsv[1] * hsv[2]
        hx += Math.cos(Math.toRadians(hsv[0].toDouble())).toFloat() * wt
        hy += Math.sin(Math.toRadians(hsv[0].toDouble())).toFloat() * wt
    }
    var hue = Math.toDegrees(Math.atan2(hy.toDouble(), hx.toDouble())).toFloat()
    if (hue < 0) hue += 360f
    val s = (sumS[best] / w[best]).coerceIn(0.5f, 0.9f)
    val v = (sumV[best] / w[best]).coerceIn(0.55f, 0.85f)
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, s, v)))
}

fun lerpColor(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t)
