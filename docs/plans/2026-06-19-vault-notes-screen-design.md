# Vault Notes screen — design

**Date:** 2026-06-19

## Goal

A second screen for adding handwritten notes to *any* `.md` file in the vault,
not just the periodic daily note. No meeting agenda or calendar — instead a file
finder (tree explorer + filename search), then a handwriting canvas whose
converted Markdown bullets are spliced into the chosen file at a tapped line.

## Decisions (from brainstorming)

- **Input:** handwrite → MyScript/ML Kit convert (reuse existing pen pipeline).
- **Insert point:** at a tapped line in the file; defaults to end-of-file.
- **Insert format:** Markdown bullets (`formatAsNoteLines`).
- **File finder:** toggle between a collapsible folder **tree** and a
  filename-only **search** list. `.md` files only.
- **Layout:** file content and canvas in a 50/50 stylus-resizable split.
- **Navigation:** top-bar toggle between Calendar and Vault Notes.

## Components

### File I/O (JVM-testable, `vault/`)
- **`insertLines(content, atLine, newLines): String`** — pure line-splice.
  `atLine` coerced into `0..lineCount`; lines rejoined with `\n`.
- **`VaultFileRepository`** — `readFile(File): String?`,
  `insertLinesAt(file, atLine, lines): Boolean` (write-then-replace: `.tmp` +
  atomic rename, same discipline as `DailyNoteRepository`).
- **`VaultFileIndex(vaultRoot)`** — `childrenOf(dir)` (subfolders + `.md` files,
  for lazy tree expansion) and `search(query)` (recursive filename substring
  match over `.md` files, run on `Dispatchers.IO`).

### Recognition (shared, `memo/`)
- Extract `recognizeStrokes(context, engine, strokes, w, h): RecognitionOutcome`
  from `ConversionActions` so the new screen and the daily-note flow share one
  copy of the Onyx/ML Kit engine branching. `ConversionActions` refactored to
  call it.

### Stroke storage
- `StrokeStore` gains a parallel **string-keyed** API (keyed by file path) so
  vault-note ink never collides with calendar `(date, scope)` ink. Same store
  instance reused.

### UI (`vaultnotes/`)
- **`VaultNotesScreen`** — orchestrates picker ↔ editor.
- **Picker** — FilterChip toggles Tree vs Search; tapping a `.md` file opens it.
- **Editor** — 50/50 split: left = file lines with tappable insert points
  (selected line highlighted, default = end), reusing inline-markdown rendering;
  right = `MemoCanvas` + guideline toggle + Erase-all + **Convert**. Convert
  flushes the pen buffer → recognizes → `formatAsNoteLines` →
  `insertLinesAt(file, selectedLine, lines)` → clear canvas + re-read file.

## Testing

JVM unit tests for `insertLines` (start/middle/end, empty file, trailing
newline) and `VaultFileIndex` (`.md` filtering, recursive filename search) using
temp dirs. Canvas/HWR/Onyx remain device-only behind thin shims.
