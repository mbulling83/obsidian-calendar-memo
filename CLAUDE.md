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
| `vault/` | All file I/O: `DailyNoteRepository` (single read/write owner for the daily note — including `createNote`, which fills a missing note from the user's Templater template), `TemplaterRenderer` + `MomentFormat` (pure native render of a Templater template's date/title tags), `VaultFileRepository` + `insertLines` (read/line-splice/write for arbitrary vault files), `DiagramRepository` + `DiagramPaths` (write diagram PNGs to `attachments/Diagrams/<year>/W<week>`; pure naming/folder/collision helpers), `VaultFileIndex` (folder tree + filename search), `VaultSettings` (path resolution + configurable folder template/headings), `MeetingSectionParser`, `NotesSectionParser`, `SectionHeading` (forgiving heading matching), `VaultDiagnostics` (pure analysis of sampled notes → `VaultDiagnosis`) + `VaultScanner` (Android: samples recent notes / walks the tree) |
| `memo/` | Handwriting surface: `OnyxInkSurfaceView` (raw pen input; optional `backgroundRenderer` to paint under the ink on the surface's own buffer), `MemoCanvas` (Compose wrapper), `StrokeStore`, `ConversionActions` (triggers recognition and writes back), `DiagramSaveAction` + `StrokeRenderer` (export strokes to a PNG and insert an Obsidian `![[…]]` image bullet), `recognizeStrokes` (shared Onyx/ML Kit recognition helper), `renderInlineMarkdown` |
| `vaultnotes/` | `VaultNotesScreen`: pick any `.md` file (tree or filename search), then handwrite and convert bullets — or save the canvas as a diagram PNG — spliced into the file at a tapped line. Reuses `MemoCanvas` + `recognizeStrokes`; writes via `VaultFileRepository` / `DiagramRepository` |
| `scribble/` | Standalone scribble calendar (not Obsidian-linked): `MonthScribbleScreen` (single freeform ink layer over a month grid), `MonthScribbleStore` (per-month on-device ink persistence in `filesDir/month-scribbles/`; pure-text serialize/deserialize + `scaleStrokes`), `MonthGridRenderer` (`drawMonthGrid` paints the grid into the ink surface via `MemoCanvas`'s `backgroundRenderer`; `weekRowsFor` row math) |
| `hwr/` | Recognition: `OnyxHWREngine` (AIDL to firmware MyScript service), `BulletFormatter` |
| `gcal/` | `GoogleCalendarRepository` interface + `NoOpGoogleCalendarRepository` (GCal OAuth deferred) |
| `quickadd/` | Quick-add form composables for adding meetings/notes via text |
| `settings/` | `SettingsScreen` (e-ink card-grouped settings page), `DailyNoteControls` (reusable folder-structure + Templater-template + auto-create controls, shared with onboarding), `VaultSettingsStore` (DataStore: vault root, folder template, section headings, template path, auto-create flag), `PenSettingsStore`/`HwrSettingsStore`/`OnboardingSettingsStore` (DataStore), vault permission helpers |
| `onboarding/` | `OnboardingScreen`: first-run welcome tour (file access → vault path → daily-note location → Templater template → feature highlights), shown once and re-openable from Settings; the daily-note location and template steps reuse `settings/DailyNoteControls` |
| `vaultcheck/` | `VaultCheckScreen` (diagnosis + one-tap fixes) and `VaultHealthBanner` (Calendar warning when no meetings can be read) — driven by `vault/VaultScanner` |
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

**Daily note path** defaults to the user's Periodic Notes convention: `Periodic Notes/Daily Notes/{year}/{MM - Month}/{yyyy-MM-dd}.md` — see `VaultSettings.DEFAULT_TEMPLATE` — but the template is **user-configurable** (Settings → Daily note folder structure; persisted in `VaultSettingsStore`) with `{year}`/`{monthFolder}`/`{isoDate}` tokens, so friends whose notes live elsewhere are supported.

**Creating a missing daily note from a Templater template**: when the day's note doesn't exist, the Calendar's day view offers a "Create note" button (`DayViewModel.createDailyNote` → `DailyNoteRepository.createNote`). Templater can't actually run — it lives in Obsidian's JS runtime, and the app writes files directly — so the app *replicates* the template instead: `TemplaterRenderer` reads the user-chosen template file and natively evaluates the subset of `<% tp... %>` tags it can (`tp.date.now/tomorrow/yesterday`, `tp.file.title`), with `MomentFormat` translating moment.js format tokens (which differ from `java.time`'s) for the **note's own date** as reference. Anything dynamic it can't evaluate (prompts, user scripts, queries) is **stripped**, not guessed. The template file is chosen in Settings (→ Daily note template; stored in `VaultSettingsStore.dailyNoteTemplatePath`, relative to the vault root or absolute); with none configured, `createNote` falls back to `VaultSettings.defaultNoteScaffold()` — just the two configured section headings. Note creation uses the same write-then-replace + `mkdirs` discipline as every other write and refuses to overwrite an existing note. Because the render is best-effort, **auto-creation is opt-in**: a Settings switch (`VaultSettingsStore.autoCreateMissingNotes`, default off) controls whether a write to a missing note — quick-add, conversion, diagram save — creates it first (via `DailyNoteRepository.withNoteContent`) rather than returning `NoteMissing`; the manual "Create note" button works regardless of the switch. `TemplaterRenderer`/`MomentFormat` are pure and JVM-tested (`TemplaterRendererTest`); `createNote` is covered in `DailyNoteRepositoryTest`.

**Vault health diagnostics**: `VaultScanner` samples the most recent resolved daily notes (looking back up to 120 days) and runs the pure `VaultDiagnostics.analyzeSamples` to produce a `VaultDiagnosis`: `Healthy`, `HeadingMismatch` (notes resolve but the configured meetings heading isn't present — recommends the heading where `HH:MM - HH:MM:` lines actually live), or `NoNotesFound` (nothing resolved — walks the vault for a date-named `.md` and recommends a corrected folder template via `inferTemplate`). The Calendar shows `VaultHealthBanner` for problem diagnoses; `VaultCheckScreen` (also from Settings) applies fixes with one tap. The scan runs off the main thread on vault-config change.

**Daily note section format**: meetings live under `# 👥 Meetings` as `HH:MM - HH:MM: Title` lines; notes live under `# 📝 Notes` as `- bullet` lines. The two section headings are **user-configurable** (`VaultSettingsStore` → `VaultSettings.meetingsHeading`/`notesHeading`, defaulting to the emoji headings) and matched forgivingly via `vault/SectionHeading` — heading level (`#` vs `##`), surrounding whitespace, and case are all ignored, so a friend's `## Meetings` still resolves a configured `Meetings`. The section-aware parsers in `vault/` take the configured heading and operate only within that section's line range (heading → next ATX heading or `---`) — they never touch Dataview/DataviewJS blocks elsewhere in the file.

### Google Calendar

GCal OAuth is **deferred** — `NoOpGoogleCalendarRepository` is wired in `MainActivity` to keep the merged day view working with Obsidian-only data until OAuth is implemented.

### Tests

JVM unit tests (no Android runtime needed) cover parsers, formatters, `StrokeStore`, `EraseHitTest`, `DailyNoteRepository`, `DiagramPaths` (naming/folder/collision rules), `MonthScribbleStore` (serialization round-trip, month isolation, atomic replace, `scaleStrokes`), `weekRowsFor` (grid row math), `SectionHeading` (forgiving matching), `VaultDiagnostics` (heading/template detection), and `TemplaterRenderer`/`MomentFormat` (native rendering of Templater date/title tags). The bitmap render (`StrokeRenderer`), image I/O (`DiagramRepository`), the grid paint (`drawMonthGrid`), and the `VaultScanner` filesystem walk are thin Android-dependent glue, exercised manually on device.

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
