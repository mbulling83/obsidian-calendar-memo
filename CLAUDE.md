# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

A Kotlin/Jetpack Compose Android app for Boox e-ink tablets. It displays the user's Obsidian daily note meetings alongside a read-only Google Calendar overlay, provides a low-latency handwriting canvas, and manually converts handwriting to Markdown bullets (via Onyx's built-in MyScript HWR) — writing results back into the daily note file on-device.

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
| `vault/` | All file I/O: `DailyNoteRepository` (single read/write owner for the daily note), `VaultFileRepository` + `insertLines` (read/line-splice/write for arbitrary vault files), `VaultFileIndex` (folder tree + filename search), `VaultSettings` (path resolution), `MeetingSectionParser`, `NotesSectionParser` |
| `memo/` | Handwriting surface: `OnyxInkSurfaceView` (raw pen input), `MemoCanvas` (Compose wrapper), `StrokeStore`, `ConversionActions` (triggers recognition and writes back), `recognizeStrokes` (shared Onyx/ML Kit recognition helper), `renderInlineMarkdown` |
| `vaultnotes/` | `VaultNotesScreen`: pick any `.md` file (tree or filename search), then handwrite and convert bullets spliced into the file at a tapped line. Reuses `MemoCanvas` + `recognizeStrokes`; writes via `VaultFileRepository` |
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

**Handwriting → text recognition** goes entirely through the Onyx built-in MyScript engine (`OnyxHWREngine`). There are no cloud/AI recognition paths — the app makes no network calls.

**Daily note path** follows the user's Periodic Notes convention: `Periodic Notes/Daily Notes/{year}/{MM - Month}/{yyyy-MM-dd}.md` — see `VaultSettings.DEFAULT_TEMPLATE`.

**Daily note section format**: meetings live under `# 👥 Meetings` as `HH:MM - HH:MM: Title` lines; notes live under `# 📝 Notes` as `- bullet` lines. The section-aware parsers in `vault/` operate only within the target section's line range — they never touch Dataview/DataviewJS blocks elsewhere in the file.

### Google Calendar

GCal OAuth is **deferred** — `NoOpGoogleCalendarRepository` is wired in `MainActivity` to keep the merged day view working with Obsidian-only data until OAuth is implemented.

### Tests

JVM unit tests (no Android runtime needed) cover parsers, formatters, `StrokeStore`, `EraseHitTest`, and `DailyNoteRepository`.

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
