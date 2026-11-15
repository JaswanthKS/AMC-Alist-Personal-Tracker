package com.alisttracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.alisttracker.data.AppDb
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/** Notifications for watchlist movies: one the day before they open and one on opening day. */
object ReleaseReminders {
    private const val CHANNEL = "releases"
    private const val WORK = "release-check"

    fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    /** Checks twice a day in the background. Safe to call on every app start. */
    fun schedule(ctx: Context) {
        runCatching {
            val req = PeriodicWorkRequestBuilder<ReleaseWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx.applicationContext).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }

    /** Sends any reminder that is due and has not been sent yet. Does nothing when notifications are off. */
    suspend fun check(context: Context) {
        val ctx = context.applicationContext
        if (!canNotify(ctx)) return
        val items = AppDb.get(ctx).dao().allWatch()
        if (items.isEmpty()) return
        val prefs = ctx.getSharedPreferences("release_reminders", Context.MODE_PRIVATE)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Watchlist releases", NotificationManager.IMPORTANCE_DEFAULT))
        val today = LocalDate.now()
        for (w in items) {
            val d = runCatching { LocalDate.parse(w.releaseDate) }.getOrNull() ?: continue
            val days = ChronoUnit.DAYS.between(today, d)
            val kind: String
            val text: String
            when {
                days == 1L -> { kind = "pre"; text = "Opens tomorrow · on your watchlist" }
                days == 0L -> { kind = "day"; text = "In theaters today · on your watchlist" }
                days in -2L..-1L -> { kind = "day"; text = "Now in theaters · on your watchlist" }
                else -> continue
            }
            // Added on or after opening day: you already know it is out.
            val added = Instant.ofEpochMilli(w.addedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            if (kind == "day" && !added.isBefore(d)) continue
            val key = "${w.tmdbId}:$kind:${w.releaseDate}"
            if (prefs.getBoolean(key, false)) continue
            val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            val pi = if (open == null) null else
                PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(w.title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            runCatching { nm.notify(w.tmdbId, n) }.onSuccess { prefs.edit().putBoolean(key, true).apply() }
        }
    }
}

class ReleaseWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runCatching { ReleaseReminders.check(applicationContext) }
        return Result.success()
    }
}
