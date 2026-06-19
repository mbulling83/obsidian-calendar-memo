package com.boxmemo.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.boxmemo.app.MainActivity
import com.boxmemo.app.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Full-width home-screen widget showing today's meeting agenda from the
 * Obsidian daily note. The list itself is populated by
 * [AgendaRemoteViewsService]; tapping anywhere on the widget opens the app.
 */
class AgendaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, AgendaWidgetProvider::class.java),
            )
            // Force the collection to re-read the note, then redraw the header.
            manager.notifyAppWidgetViewDataChanged(ids, R.id.agenda_list)
            onUpdate(context, manager, ids)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.boxmemo.app.widget.ACTION_REFRESH"

        private val TITLE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

        /** Asks every placed agenda widget to re-read the daily note and redraw. */
        fun refresh(context: Context) {
            val intent = Intent(context, AgendaWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            context.sendBroadcast(intent)
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val today = LocalDate.now()
            val views = RemoteViews(context.packageName, R.layout.widget_agenda).apply {
                setTextViewText(R.id.widget_title, today.format(TITLE_FORMAT))

                // List adapter, backed by the RemoteViewsService.
                val serviceIntent = Intent(context, AgendaRemoteViewsService::class.java).apply {
                    // Make the intent unique per widget so the host keeps adapters distinct.
                    data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                setRemoteAdapter(R.id.agenda_list, serviceIntent)
                setEmptyView(R.id.agenda_list, R.id.agenda_empty)

                // Tapping the header or a row opens the app.
                val openApp = openAppPendingIntent(context)
                setOnClickPendingIntent(R.id.widget_title, openApp)
                setPendingIntentTemplate(R.id.agenda_list, openApp)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.agenda_list)
        }

        private fun openAppPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            // MUTABLE so the collection can merge per-row fill-in intents into it.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            return PendingIntent.getActivity(context, 0, intent, flags)
        }
    }
}
