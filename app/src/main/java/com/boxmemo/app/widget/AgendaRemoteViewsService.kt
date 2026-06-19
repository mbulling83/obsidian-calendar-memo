package com.boxmemo.app.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.boxmemo.app.R
import com.boxmemo.app.settings.VaultSettingsStore
import com.boxmemo.app.vault.DailyNoteRepository
import com.boxmemo.app.vault.MeetingEntry
import com.boxmemo.app.vault.MeetingSectionParseResult
import com.boxmemo.app.vault.VaultSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/**
 * Backs the agenda widget's list. The factory runs in the launcher's process
 * via the widget host, re-reading the configured vault root and today's daily
 * note on every [onDataSetChanged], so the widget always reflects the current
 * file on disk without holding any in-memory state of its own.
 */
class AgendaRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        AgendaRemoteViewsFactory(applicationContext)
}

private class AgendaRemoteViewsFactory(
    private val context: Context,
) : RemoteViewsService.RemoteViewsFactory {

    private var meetings: List<MeetingEntry> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        meetings = loadMeetings()
    }

    private fun loadMeetings(): List<MeetingEntry> {
        val vaultRoot = runBlocking { VaultSettingsStore(context).vaultRoot.first() }
        if (vaultRoot.isNullOrBlank()) return emptyList()
        val repository = DailyNoteRepository(VaultSettings(vaultRoot))
        val result = repository.readMeetings(LocalDate.now())
        return (result as? MeetingSectionParseResult.Found)
            ?.entries
            ?.sortedBy { it.startTime }
            .orEmpty()
    }

    override fun onDestroy() {
        meetings = emptyList()
    }

    override fun getCount(): Int = meetings.size

    override fun getViewAt(position: Int): RemoteViews {
        val entry = meetings[position]
        return RemoteViews(context.packageName, R.layout.widget_agenda_item).apply {
            setTextViewText(R.id.item_time, "${entry.startTime} - ${entry.endTime}")
            setTextViewText(R.id.item_title, entry.title)
            // Every row opens the app via the template set on the collection.
            setOnClickFillInIntent(R.id.item_root, Intent())
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}
