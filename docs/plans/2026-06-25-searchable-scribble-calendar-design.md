# Searchable Scribble Calendar — Design

**Date:** 2026-06-25

## Goal

Make the standalone scribble calendar searchable while keeping the
handwriting on the page exactly as drawn. The user writes over a month grid
like a paper wall planner; they want to find a word ("dentist") across months
without converting the ink to text on screen.

Search returns results at two granularities:

- **Specific day** — jump to the exact day cell where the word was written.
- **Whole month** — fall back to month-level matches for ink that does not sit
  cleanly in a day cell (page title, notes area, ink spilling across cells).

## Non-goals

- No change to how ink is captured, rendered, or stored (`.ink` files).
- No on-screen conversion of handwriting to text.
- No network calls; recognition stays on-device via the configured HWR engine.
- No new Android permissions.

## Data model & storage

The ink never changes. We add a searchable text **sidecar** next to each
month's `.ink` file.

### Per-day stroke assignment

A month is one freeform stroke layer with no day boundaries. To get day-level
results, recognition buckets each stroke into a grid cell by geometry, reusing
the cell math already in `MonthGridRenderer` (`leadingBlanks`,
`cellWidth`/`cellHeight`, `weekRowsFor`). A stroke is assigned to the cell
containing the **midpoint of its bounding box**, so a stroke that slightly
overflows a cell still lands on the right day. Strokes above the grid (title /
notes area) go into a month-level bucket.

### Recognition

Each non-empty cell bucket → `recognizeStrokes(...)` → that day's text. The
leftover / non-grid bucket → month-level text.

### Sidecar file

Alongside `2026-07.ink` we write `2026-07.idx` in the same
`filesDir/month-scribbles/` directory, using the same write-then-replace
discipline. Pure-text, version-prefixed, JVM-testable like the `.ink` format:

```
v1
month=2026-07
hash=<hash of .ink contents>
day=2026-07-03	dentist appointment 2pm
day=2026-07-11	call alice re budget
free=planning notes top of page
```

(`free=` carries the month-level / non-grid text; the `month=` header line
carries the `YearMonth` the index belongs to.)

The `hash` lets us skip re-recognising an unchanged month. Text is stored as
recognised and lowercased on search.

## Recognition flow & timing

### When it runs

At the existing **flush** moments in `MonthScribbleScreen` — navigating to
another month and leaving the screen (`saveCurrent(flush = true)`). Writing
stays at hardware latency; recognition only fires when the page is done.

### The indexer

A new Android-glue `scribble/ScribbleIndexer`:

1. Load the just-saved `MonthScribble`.
2. Compute a hash of the `.ink` text; skip if it matches the existing `.idx`
   hash (nothing changed).
3. Bucket strokes into day cells (pure function).
4. For each non-empty bucket, call `recognizeStrokes(context, engine, strokes)`
   with the configured HWR engine.
5. Write the `.idx` sidecar.

Runs off the main thread, fire-and-forget after flush. If recognition is
`Unavailable` (Onyx service unbound, ML Kit model missing), skip writing rather
than store empty text — the next flush retries.

### Pure, testable core

```kotlin
// scribble/ScribbleIndex.kt  (JVM-testable, no Android deps)
fun bucketStrokesByDay(
    scribble: MonthScribble,
    width: Int, height: Int,
): Map<LocalDate?, List<StrokePath>>   // null key = month-level / non-grid ink

data class ScribbleIndex(
    val month: YearMonth,
    val hash: String,
    val dayText: Map<LocalDate, String>,
    val monthText: String,
)
fun serializeIndex(index: ScribbleIndex): String
fun deserializeIndex(text: String): ScribbleIndex?
```

File I/O and the `recognizeStrokes` call stay in `ScribbleIndexer`, matching how
`MonthScribbleStore` keeps serialization pure and I/O separate.

### Backfill

Months written before this feature have no `.idx`. On first search (or screen
open) we index any `.ink` lacking a current `.idx`, using the same
skip-by-hash logic — a one-time cost per month.

## Search UI & navigation

### Search bar

An e-ink-styled text field at the top of `MonthScribbleScreen` (high contrast,
large tap target, no animation). Empty by default; the grid shows as normal.

### Searching

On **submit** (not per-keystroke — e-ink redraws are expensive), a pure
`ScribbleSearch` loads all `.idx` files and matches the query
case-insensitively against day and month entries:

```kotlin
data class ScribbleHit(val month: YearMonth, val day: LocalDate?, val snippet: String)
fun searchIndex(indexes: List<ScribbleIndex>, query: String): List<ScribbleHit>
```

Results render as a simple list below the bar, newest-first: each row shows the
date (or "July 2026" for month-level hits) and the matching snippet. Loading
`.idx` files and the backfill pass are the only Android glue.

### Navigation

Tapping a hit:

- Sets the screen's `month` to the hit's month (reuses existing month
  navigation, which flushes + reloads ink).
- If the hit has a day, passes that date to `MonthGridRenderer.drawMonthGrid` as
  a **highlighted day**, reusing the rounded-square emphasis the grid draws for
  "today" but with an **outline** instead of fill so it reads as distinct. The
  highlight clears when the user writes or changes month.

### Clearing

An ✕ in the search bar clears the query and results, returning to the normal
calendar.

## Testing

JVM unit tests (no Android runtime) cover the pure core:

- `bucketStrokesByDay` — correct cell→date assignment, midpoint rule, overflow
  tolerance, non-grid ink → null bucket, leading-blank offset, varying week
  counts.
- `serializeIndex` / `deserializeIndex` — round-trip, tab-delimited text with
  spaces preserved, version handling, malformed input → null.
- `searchIndex` — case-insensitive match, day vs month hits, ordering, no
  matches.

Android-dependent glue (`ScribbleIndexer` recognition + file I/O, the search bar
composable, the highlighted-day grid paint) is exercised manually on device,
consistent with the existing scribble test split.
