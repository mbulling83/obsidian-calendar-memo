# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

A Kotlin/Jetpack Compose Android app for Boox e-ink tablets. It displays the user's Obsidian daily note meetings alongside a read-only Google Calendar overlay, provides a low-latency handwriting canvas, and manually converts handwriting to Markdown bullets (via Onyx's built-in MyScript HWR) — writing results back into the daily note file on-device. It also includes a standalone scribble calendar — a month grid you handwrite over freely, saved on-device (not in Obsidian).

Target device: Boox U7 (and compatible Onyx Boox hardware running Android 10+, minSdk 29).

## Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug

# Run JVM unit tests (no emulator needed)
./gradlew test

# Run a single test class
./gradlew test --tests "com.boxmemo.app.vault.MeetingSectionParserTest"

# Run all tests in a package
./gradlew test --tests "com.boxmemo.app.vault.*"
```

## Architecture

### Package map

| Package | Responsibility |
|---|---|
| `calendar/` | Month grid + day view UI (`CalendarScreen`, `CalendarView`, `DayView`); `DayViewModel` owns merged Obsidian+GCal state |
| `vault/` | All file I/O: `DailyNoteRepository` (single read/write owner for the daily note), `VaultFileRepository` + `insertLines` (read/line-splice/write for arbitrary vault files), `DiagramRepository` + `DiagramPaths` (write diagram PNGs to `attachments/Diagrams/<year>/W<week>`; pure naming/folder/collision helpers), `VaultFileIndex` (folder tree + filename search), `VaultSettings` (path resolution), `MeetingSectionParser`, `NotesSectionParser` |
| `memo/` | Handwriting surface: `OnyxInkSurfaceView` (raw pen input; optional `backgroundRenderer` to paint under the ink on the surface's own buffer), `MemoCanvas` (Compose wrapper), `StrokeStore`, `ConversionActions` (triggers recognition and writes back), `DiagramSaveAction` + `StrokeRenderer` (export strokes to a PNG and insert an Obsidian `![[…]]` image bullet), `recognizeStrokes` (shared Onyx/ML Kit recognition helper), `renderInlineMarkdown` |
| `vaultnotes/` | `VaultNotesScreen`: pick any `.md` file (tree or filename search), then handwrite and convert bullets — or save the canvas as a diagram PNG — spliced into the file at a tapped line. Reuses `MemoCanvas` + `recognizeStrokes`; writes via `VaultFileRepository` / `DiagramRepository` |
| `scribble/` | Standalone scribble calendar (not Obsidian-linked): `MonthScribbleScreen` (single freeform ink layer over a month grid), `MonthScribbleStore` (per-month on-device ink persistence in `filesDir/month-scribbles/`; pure-text serialize/deserialize + `scaleStrokes`), `MonthGridRenderer` (`drawMonthGrid` paints the grid into the ink surface via `MemoCanvas`'s `backgroundRenderer`; `weekRowsFor` row math) |
| `hwr/` | Recognition: `OnyxHWREngine` (AIDL to firmware MyScript service), `BulletFormatter` |
| `gcal/` | `GoogleCalendarRepository` interface + `NoOpGoogleCalendarRepository` (GCal OAuth deferred) |
| `quickadd/` | Quick-add form composables for adding meetings/notes via text |
| `settings/` | `SettingsScreen`, `VaultSettingsStore` (DataStore), `PenSettingsStore` (DataStore), vault permission helpers |
| `ui/` | Shared `AppTopBar`, typography |

### Key architectural decisions

**Vault file access** uses `MANAGE_EXTERNAL_STORAGE` + direct `java.io.File` against a user-configured absolute path — not Storage Access Framework. This matches the confirmed-working pattern from jdkruzr/aragonite on Onyx hardware.

**Write-then-replace**: `DailyNoteRepository.writeNote()` writes to a `.tmp` sibling file then atomically renames over the original. This ensures LiveSync (running concurrently on-device) never observes a partial write.

**`DailyNoteRepository` is the single owner of daily-note file I/O** — the calendar view, conversion engines, and quick-add form all go through it. The Vault Notes screen uses `VaultFileRepository` for arbitrary files (same write-then-replace discipline). Nothing in `memo/`, `hwr/`, `calendar/`, or `vaultnotes/` touches the filesystem directly — it routes through a repository in `vault/`.

**Onyx HWR binding** (`OnyxHWREngine`) binds to the undocumented firmware AIDL service `com.onyx.android.ksync / KHwrService`. The AIDL stubs live under `com/onyx/android/sdk/hwr/service/`. The wire format is a hand-rolled protobuf (adapted from aragonite). Pressure defaults to 0.5 and timestamps are synthesized at 10ms intervals — adequate for recognition, not for replay.

**`OnyxInkSurfaceView`** uses Onyx Pen SDK `TouchHelper` / raw-drawing mode: the e-ink controller renders ink at hardware latency, bypassing Compose's recomposition pipeline. Strokes are received *after* completion for persistence. Erasing: hardware (stylus side button → `onRawErasingTouchPointListReceived`) and UI eraser chip both remove strokes and trigger a full canvas redraw (Onyx firmware handles raw drawing visually but not erasing).

**Scribble calendar** (`scribble/`) is deliberately *not* linked to Obsidian: it's a month grid you handwrite over like a paper wall planner, with one freeform ink layer spanning all cells. The grid is drawn into the ink surface's own white buffer (via `MemoCanvas`'s `backgroundRenderer`), not as a Compose layer behind it — the `OnyxInkSurfaceView` is opaque and would hide anything composed behind it. Ink persists per month under `filesDir/month-scribbles/<YearMonth>.ink` (write-then-replace), serialized as a compact pure-text format (not `org.json`, so the round-trip is JVM-testable). Strokes record the canvas size they were drawn at and rescale on load (`scaleStrokes`). It opens on the current month and navigates to any past/future month.

**Handwriting → text recognition** goes entirely through the Onyx built-in MyScript engine (`OnyxHWREngine`). There are no cloud/AI recognition paths — the app makes no network calls.

**Handwriting → diagram** is the alternative to recognition: `StrokeRenderer.renderStrokesToBitmap()` rasterises the strokes (cropped to the ink bounds, white background, no guidelines) to a PNG, which `DiagramRepository` saves under `attachments/Diagrams/<week-based-year>/W<week>/` (write-then-replace, like the note repos). The note then gets an Obsidian `![[filename.png]]` embed bullet — a meeting detail line under a meeting, or a plain bullet for the Notes section / a Vault Notes file. Filenames are date + time + meeting/note name (see `DiagramPaths`), sanitized and collision-suffixed. Saving does **not** clear the canvas.

**Daily note path** follows the user's Periodic Notes convention: `Periodic Notes/Daily Notes/{year}/{MM - Month}/{yyyy-MM-dd}.md` — see `VaultSettings.DEFAULT_TEMPLATE`.

**Daily note section format**: meetings live under `# 👥 Meetings` as `HH:MM - HH:MM: Title` lines; notes live under `# 📝 Notes` as `- bullet` lines. The section-aware parsers in `vault/` operate only within the target section's line range — they never touch Dataview/DataviewJS blocks elsewhere in the file.

### Google Calendar

GCal OAuth is **deferred** — `NoOpGoogleCalendarRepository` is wired in `MainActivity` to keep the merged day view working with Obsidian-only data until OAuth is implemented.

### Tests

JVM unit tests (no Android runtime needed) cover parsers, formatters, `StrokeStore`, `EraseHitTest`, `DailyNoteRepository`, `DiagramPaths` (naming/folder/collision rules), `MonthScribbleStore` (serialization round-trip, month isolation, atomic replace, `scaleStrokes`), and `weekRowsFor` (grid row math). The bitmap render (`StrokeRenderer`), image I/O (`DiagramRepository`), and the grid paint (`drawMonthGrid`) are thin Android-dependent glue, exercised manually on device.

## Daily Note Format Reference

The parsers and writers depend on this section structure in the daily note:

```markdown
# 👥 Meetings
09:00 - 10:00: Standup
	- some detail bullet
10:30 - 11:00: 1:1 with [[Person]]

# 📝 Notes
- a note bullet
```

Meetings section: each entry is `HH:MM - HH:MM: Title`, optionally followed by indented `\t- bullet` detail lines. New meetings are inserted in chronological order. New detail bullets are inserted under the matching start-time entry.
