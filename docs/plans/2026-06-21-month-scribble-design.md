# Month Scribble Calendar — Design

Date: 2026-06-21

## Goal

A standalone month-by-month calendar planner the user can handwrite ("scribble")
on top of, like a paper wall planner. Not linked to Obsidian — strokes are saved
within the app, on-device, and remain re-editable when a month is reopened.

Defaults to the current month; navigable to past and future months.

## Decisions

- **Ink model:** one freeform ink layer spanning the whole month grid (draw
  across day cells), not per-day canvases.
- **Persistence:** editable vector strokes saved per month on-device (internal
  `filesDir`), reappear and remain editable on reopen.
- **No Obsidian linkage / no event overlay.** Pure scribble planner. The grid
  shows weekday headers + day numbers only.
- **No handwriting→text recognition.** Ink stays ink (YAGNI).

## Architecture

New top-level screen `MonthScribbleScreen`, reachable from `AppTopBar` alongside
Calendar / Vault Notes / Settings.

New package `scribble/`:

- `MonthGridBackground` (Compose) — static month grid (weekday headers + day-number
  cells) for the displayed `YearMonth`. Sits *behind* the ink, never intercepts pen.
  Prev/Next month + "This month" controls.
- `MonthScribbleScreen` (Compose) — `Box` stacking `MemoCanvas` over
  `MonthGridBackground`, fills screen. Owns displayed-month state, eraser toggle
  (reuse existing eraser chip pattern), per-month Clear action.
- `MonthScribbleStore` — sole owner of `filesDir/month-scribbles/` I/O. One JSON
  file per month. Atomic write-then-replace (mirrors `vault/` repo discipline).

Reused unchanged: `OnyxInkSurfaceView`, `MemoCanvas`, `InkFlushHandle`,
`PenSettings`/`PenSettingsStore`, eraser interaction, `GuidelineStyle` (set to
none — the grid is the guide).

## Persistence format

`filesDir/month-scribbles/2026-07.json`:

```json
{
  "month": "2026-07",
  "captureWidth": 1404,
  "captureHeight": 1872,
  "strokes": [
    [[120.5, 88.0], [121.0, 90.2]],
    ...
  ]
}
```

`captureWidth/Height` is the canvas size strokes were drawn at. On load, if the
live canvas differs, points scale by `liveW/captureW`, `liveH/captureH`
(no-op on a fixed-orientation Boox, but keeps ink aligned if orientation changes).
Serialization via `org.json` — no new dependencies.

## Data flow

1. Open → `YearMonth.now()` → `MonthScribbleStore.load(month)` → strokes to `MemoCanvas`.
2. Stroke finishes → append in-memory + debounced save.
3. Erase → update list + debounced save.
4. Prev/Next month → flush + save current, then load target (canvas swaps stroke set).
5. `onPause` → force-flush save.

`MonthScribbleStore` is the single owner of `month-scribbles/` I/O.

## Tests (JVM)

- `MonthScribbleStore` save→load round-trip equality.
- Month-keyed isolation (one month's strokes never bleed into another).
- Scaling-on-load math.
- Atomic write-then-replace behavior.

Compose grid + ink stacking is thin device-verified glue (like `StrokeRenderer`).
