# The Daily

A Kotlin/Jetpack Compose Android app for **Boox e-ink tablets** that pairs your Obsidian daily-note agenda with a low-latency handwriting canvas. Write notes by hand, convert them to Markdown bullets, and have them written straight back into your daily note file on-device — alongside a read-only Google Calendar overlay.

> **Target device:** Boox U7 (and compatible Onyx Boox hardware running Android 10+, `minSdk 29`).

## What it does

- **Merged day view** — your Obsidian daily-note meetings rendered together with an (optional) read-only Google Calendar overlay.
- **Low-latency ink** — a handwriting surface driven by the Onyx Pen SDK raw-drawing mode, so the e-ink controller renders strokes at hardware latency rather than through Compose recomposition.
- **Handwriting → Markdown** — manual conversion of ink to Markdown bullets via either Onyx's built-in MyScript recognizer (on-device AIDL) or OpenRouter AI vision OCR.
- **Writes back to your vault** — converted notes are written into the daily note file using an atomic write-then-replace so a concurrently running LiveSync never sees a partial file.
- **Quick add** — add meetings and notes by text without picking up the pen.
- **`[[wiki link]]` rendering** and a guideline picker for the canvas.

## Architecture

### Package map

| Package | Responsibility |
|---|---|
| `calendar/` | Month grid + day view UI (`CalendarScreen`, `CalendarView`, `DayView`); `DayViewModel` owns merged Obsidian + GCal state |
| `vault/` | All file I/O: `DailyNoteRepository` (single read/write owner), `VaultSettings` (path resolution), `MeetingSectionParser`, `NotesSectionParser` |
| `memo/` | Handwriting surface: `OnyxInkSurfaceView` (raw pen input), `MemoCanvas` (Compose wrapper), `StrokeStore`, `ConversionActions` |
| `hwr/` | Recognition engines: `OnyxHWREngine` (AIDL to firmware MyScript), `VisionOcrClient` (OpenRouter vision), `TextEnhancementClient`, `BulletFormatter` |
| `gcal/` | `GoogleCalendarRepository` interface + `NoOpGoogleCalendarRepository` (GCal OAuth deferred) |
| `quickadd/` | Quick-add form composables for meetings/notes via text |
| `settings/` | `SettingsScreen`, `VaultSettingsStore` / `PenSettingsStore` (DataStore), vault permission helpers |
| `ui/` | Shared `AppTopBar`, typography |
| `diagram/` | `DiagramConversionClient` (OpenRouter for diagram → image/HTML) |

### Key decisions

- **Vault file access** uses `MANAGE_EXTERNAL_STORAGE` + direct `java.io.File` against a user-configured absolute path — not the Storage Access Framework. This matches the confirmed-working pattern from [jdkruzr/aragonite](https://github.com/jdkruzr/aragonite) on Onyx hardware.
- **`DailyNoteRepository` is the single owner of file I/O.** Nothing in `memo/`, `hwr/`, or `calendar/` touches the filesystem directly. `writeNote()` writes to a `.tmp` sibling then atomically renames over the original.
- **Onyx HWR binding** (`OnyxHWREngine`) binds to the undocumented firmware AIDL service `com.onyx.android.ksync / KHwrService`. The wire format is a hand-rolled protobuf adapted from aragonite.
- **`OnyxInkSurfaceView`** uses the Onyx Pen SDK `TouchHelper` raw-drawing mode. Strokes are received after completion for persistence; erasing (hardware side-button or UI chip) removes strokes and triggers a full canvas redraw.
- **OpenRouter** is used as a thin REST client (no vendor SDK) at `https://openrouter.ai/api/v1/chat/completions` for both vision OCR and text enhancement. Default model: `google/gemini-2.0-flash-001`.

### Google Calendar

GCal OAuth is **deferred** — `NoOpGoogleCalendarRepository` is wired in `MainActivity` so the merged day view works with Obsidian-only data until OAuth is implemented.

## Daily note format

The parsers and writers depend on this section structure:

```markdown
# 👥 Meetings
09:00 - 10:00: Standup
	- some detail bullet
10:30 - 11:00: 1:1 with [[Person]]

# 📝 Notes
- a note bullet
```

- **Meetings** live under `# 👥 Meetings` as `HH:MM - HH:MM: Title` lines, optionally followed by indented `\t- bullet` detail lines. New meetings are inserted in chronological order.
- **Notes** live under `# 📝 Notes` as `- bullet` lines.
- The section-aware parsers operate only within the target section's line range — they never touch Dataview/DataviewJS blocks elsewhere in the file.

The default daily-note path follows the Periodic Notes convention: `Periodic Notes/Daily Notes/{year}/{MM - Month}/{yyyy-MM-dd}.md` (see `VaultSettings.DEFAULT_TEMPLATE`).

## Build & run

```bash
# Build debug APK
./gradlew assembleDebug

# Install to a connected device
./gradlew installDebug

# Run JVM unit tests (no emulator needed)
./gradlew test

# Run a single test class
./gradlew test --tests "com.boxmemo.app.vault.MeetingSectionParserTest"
```

The Onyx SDK (`onyxsdk-device` / `-pen` / `-base`) is published only on Boox's own Maven repo (`repo.boox.com`), which is wired into `settings.gradle.kts`.

### Secrets

`local.properties` (gitignored) holds:

```properties
openrouter.apiKey=<your key>
```

This is injected into `BuildConfig.OPENROUTER_API_KEY` at build time — never hardcode it.

## Tests

JVM unit tests (no Android runtime) cover the parsers, formatters, `StrokeStore`, `EraseHitTest`, and `DailyNoteRepository`. `org.json:json` is on the test classpath so `JSONObject` works on the JVM (the Android stub jar throws).

## Tech stack

Kotlin · Jetpack Compose (Material 3) · AndroidX Lifecycle/DataStore · Jetpack Ink · Onyx Boox SDK (device/pen/base) · OpenRouter REST API.
