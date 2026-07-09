package com.boxmemo.app.ui

import android.app.Activity
import android.graphics.Rect
import android.view.View
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.api.device.epd.UpdateMode

/**
 * Clears the e-ink ghost residue the Onyx system handwriting keyboard leaves
 * behind when it's dismissed.
 *
 * The Onyx HWR keyboard paints the strokes you write onto the panel via fast
 * partial refreshes; when it closes, e-ink doesn't fully repaint the vacated
 * region, so the ink stays ghosted on screen — e.g. over the calendar's
 * "New meeting" title field once the keyboard is put away. Watching the root
 * view's visible height for the keyboard's hide transition and forcing a full,
 * clean [UpdateMode.GC] refresh wipes the residue.
 *
 * Detection is by visible-frame height (which shrinks while any soft keyboard
 * is up, regardless of vendor) rather than `WindowInsets.ime()`, because
 * Onyx's handwriting input doesn't reliably report IME insets on this hardware.
 */
fun Activity.installKeyboardGhostRefresh() {
    val root = window.decorView
    var keyboardWasOpen = false
    root.viewTreeObserver.addOnGlobalLayoutListener {
        val visible = Rect().also { root.getWindowVisibleDisplayFrame(it) }
        // Baseline (no keyboard) diff is just the system bars; a soft keyboard
        // eats a large chunk, so >15% of screen height means it's up.
        val heightDiff = root.height - visible.height()
        val keyboardOpen = heightDiff > root.height * 0.15
        if (keyboardWasOpen && !keyboardOpen) {
            // Keyboard just dismissed — refresh once the panel has settled after
            // teardown; firing synchronously can re-latch the ghost mid-tear-down.
            root.postDelayed({ forceGcRefresh(root) }, 150)
        }
        keyboardWasOpen = keyboardOpen
    }
}

private fun forceGcRefresh(view: View) {
    try {
        EpdController.invalidate(view, UpdateMode.GC)
    } catch (_: Throwable) {
        // Not an Onyx device (or the SDK shape changed) — the refresh is a
        // display nicety, never load-bearing, so swallow any failure.
    }
}
