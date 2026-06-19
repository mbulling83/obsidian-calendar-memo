package com.boxmemo.app.hwr

/**
 * Which handwriting recognizer the Convert action uses. Selectable in Settings
 * so the two engines can be compared on the same handwriting:
 *
 * - [ONYX]: the Boox firmware's built-in MyScript engine (no model download,
 *   matches the native Notes app, but binds an undocumented firmware service).
 * - [ML_KIT]: Google ML Kit Digital Ink Recognition (on-device, offline after a
 *   one-time model download, no firmware internals).
 */
enum class HwrEngineType(val label: String) {
    ONYX("Onyx MyScript"),
    ML_KIT("Google ML Kit"),
}
