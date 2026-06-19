package com.boxmemo.app.memo

import java.time.LocalDate

/** A single stroke as a sequence of (x, y) points. */
typealias StrokePath = List<Pair<Float, Float>>

/**
 * In-memory strokes keyed by date and [CaptureScope], so switching between a
 * day's meetings (or days) never bleeds strokes from one capture into
 * another. Persistence to the vault happens via conversion (U9/U10), not
 * here — this store only needs to survive navigation within a session.
 */
class StrokeStore {
    private val strokesByKey = mutableMapOf<Pair<LocalDate, CaptureScope>, MutableList<StrokePath>>()
    // Parallel map for callers that key by an arbitrary string (the Vault Notes
    // screen keys by file path), kept separate so its keys never collide with
    // the calendar's (date, scope) keys.
    private val strokesByStringKey = mutableMapOf<String, MutableList<StrokePath>>()

    fun strokesFor(date: LocalDate, scope: CaptureScope): List<StrokePath> =
        strokesByKey[date to scope].orEmpty()

    fun addStroke(date: LocalDate, scope: CaptureScope, stroke: StrokePath) {
        strokesByKey.getOrPut(date to scope) { mutableListOf() }.add(stroke)
    }

    fun clear(date: LocalDate, scope: CaptureScope) {
        strokesByKey.remove(date to scope)
    }

    /** Replaces the full stroke list for a date+scope (used after erasing removes some strokes). */
    fun setStrokes(date: LocalDate, scope: CaptureScope, strokes: List<StrokePath>) {
        strokesByKey[date to scope] = strokes.toMutableList()
    }

    fun strokesFor(key: String): List<StrokePath> = strokesByStringKey[key].orEmpty()

    fun addStroke(key: String, stroke: StrokePath) {
        strokesByStringKey.getOrPut(key) { mutableListOf() }.add(stroke)
    }

    fun clear(key: String) {
        strokesByStringKey.remove(key)
    }

    fun setStrokes(key: String, strokes: List<StrokePath>) {
        strokesByStringKey[key] = strokes.toMutableList()
    }
}
