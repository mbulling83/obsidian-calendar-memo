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
}
