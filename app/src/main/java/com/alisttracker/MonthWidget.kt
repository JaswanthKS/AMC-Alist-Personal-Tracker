package com.alisttracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.alisttracker.data.AppDb
import com.alisttracker.ui.money
import com.alisttracker.ui.monthSummary
import com.alisttracker.ui.visitsLast7Days
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.concurrent.thread

/** Home-screen widget: this month's A-List visits, free-ticket value and whether the membership paid off. */
class MonthWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) = refresh(context, appWidgetIds)

    companion object {
        /** Redraws every placed widget from the database. Safe to call from anywhere. */
        fun refresh(ctx: Context, ids: IntArray? = null) {
            val app = ctx.applicationContext
            val mgr = AppWidgetManager.getInstance(app)
            val widgetIds = ids ?: mgr.getAppWidgetIds(ComponentName(app, MonthWidget::class.java))
            if (widgetIds.isEmpty()) return
            thread {
                runCatching {
                    val dao = AppDb.get(app).dao()
                    val (movies, subs) = runBlocking { dao.allMovies() to dao.allSubs() }
                    val now = LocalDate.now()
                    val ms = monthSummary(movies, subs, now.year, now.monthValue)
                    val rv = RemoteViews(app.packageName, R.layout.widget_month)
                    rv.setTextViewText(R.id.w_visits, if (ms.visits == 1) "1 visit" else "${ms.visits} visits")
                    rv.setTextViewText(R.id.w_value, "${money(ms.value)} in free tickets")
                    rv.setTextViewText(R.id.w_status, when {
                        ms.cost <= 0 -> ""
                        ms.paidOff -> "Paid off · ${money(ms.net)} ahead"
                        else -> "${money(ms.cost - ms.value)} to cover membership"
                    })
                    rv.setTextViewText(R.id.w_week, "Last 7 days: ${visitsLast7Days(movies)} of 3")
                    val open = app.packageManager.getLaunchIntentForPackage(app.packageName)
                    if (open != null) rv.setOnClickPendingIntent(R.id.widget_root,
                        PendingIntent.getActivity(app, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                    widgetIds.forEach { mgr.updateAppWidget(it, rv) }
                }
            }
        }
    }
}
