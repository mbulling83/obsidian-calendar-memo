---
date: 2026-06-18
topic: boox-obsidian-calendar-memo
---

# Boox Obsidian Calendar Memo

## Summary

A new Android app for Boox e-ink tablets that mirrors the native Boox Calendar+Memo layout but is backed by the user's Obsidian vault: it displays that day's meetings from the daily note plus a read-only Google Calendar overlay, lets the user add new meetings/notes via a quick form, and lets the user manually convert handwritten captures into Markdown bullets (via Boox's built-in handwriting recognition or AI vision through OpenRouter) and hand-drawn diagrams into an AI-recreated image or HTML diagram — writing all of it back into the day's Obsidian daily note file.

---

## Problem Frame

The user currently uses the built-in Boox Calendar+Memo app to see the day's commitments and jot handwritten notes underneath, but that app has no connection to Obsidian, which is where the user's actual meeting notes, structure, and knowledge graph live (`[[wiki links]]`, structured `# 👥 Meetings` sections, etc.). Today, getting a handwritten thought from the Boox device into a properly formatted Obsidian daily note is a manual, disconnected step — and there's no way to glance at the day's Obsidian-sourced commitments next to a writing surface the way the native app shows Google/Boox calendar events next to a memo pad.

Separately, the user already has Obsidian plugins in place that touch parts of this problem individually — `obsidian-livesync` (vault sync to other devices), `google-calendar-importer` (pulls Google Calendar into notes), and `tasknotes` (task management with its own calendar view) — but nothing ties calendar display, handwriting capture, and Obsidian's specific daily-note structure together on the Boox hardware itself.

---

## Key Flows

- F1. View the day
  - **Trigger:** User opens the app or navigates to a date
  - **Actors:** User
  - **Steps:** App reads the local daily note file for the selected date (materialized on-device via Obsidian mobile + LiveSync); parses the `# 👥 Meetings` section into a list of timed entries; fetches that day's Google Calendar events read-only; merges both lists into one day view, visually distinguishing Obsidian-sourced entries from Google Calendar entries; renders the handwriting memo canvas below, scoped to that date
  - **Outcome:** User sees a single day view combining Obsidian meetings, Google Calendar events, and a writing surface
  - **Covered by:** R1, R2, R3, R4

- F2. Add a new meeting or note via quick form
  - **Trigger:** User taps "add" on a day
  - **Actors:** User
  - **Steps:** User picks/types a start and end time and a title in a quick form; on save, the app appends a new `HH:MM - HH:MM: Title` line to the `# 👥 Meetings` section (or a plain bullet to `# 📝 Notes`, per the chosen target) of that day's local note file
  - **Outcome:** A new entry exists in the Obsidian daily note in the same format as entries created in Obsidian itself
  - **Covered by:** R5, R6

- F3. Convert a handwritten capture to text
  - **Trigger:** User finishes writing in the memo pane for a given meeting/note and manually triggers conversion
  - **Actors:** User
  - **Steps:** User chooses Boox's built-in handwriting recognition or AI vision OCR (via OpenRouter) for this capture; the app recognizes the strokes; the app formats the result as nested bullets matching the existing meeting note structure (matching the indentation/bullet style already used under meeting lines); the app writes the bullets into the daily note under the corresponding meeting or note entry
  - **Outcome:** Handwritten content appears as properly structured Markdown in the Obsidian daily note
  - **Covered by:** R7, R8, R9, R10

- F4. Convert a hand-drawn diagram
  - **Trigger:** User manually triggers diagram conversion on a capture containing a drawing
  - **Actors:** User
  - **Steps:** User chooses image recreation or HTML/diagram-code recreation for this specific diagram; the app sends the drawing to an AI model via OpenRouter; the app inserts the resulting image file or HTML/diagram code into the daily note at the relevant location
  - **Outcome:** A hand-drawn diagram is represented in the Obsidian note as a clean image or structured diagram, linked/embedded appropriately
  - **Covered by:** R11, R12, R13

---

## Requirements

**Calendar and day view**
- R1. The app displays a month/calendar view and a selected-day view modeled on the native Boox Calendar+Memo layout (month grid, day's event list, handwriting memo pane below).
- R2. The day view lists meetings parsed from that day's Obsidian daily note `# 👥 Meetings` section, in the existing `HH:MM - HH:MM: Title` format.
- R3. The day view also shows that day's Google Calendar events, fetched read-only, visually distinguished from Obsidian-sourced meetings. Calendars shown are a user-configured subset (e.g. work, personal, family, school) rather than every calendar on the Google account.
- R4. Google Calendar events are never written back to Google Calendar or to the Obsidian note by this app — they are display-only.

**Creating new entries**
- R5. The user can add a new meeting or note entry for a day via a quick form (time + title), not handwriting.
- R6. New entries are appended to the daily note's `# 👥 Meetings` or `# 📝 Notes` section in the same format as existing entries, so they're indistinguishable from entries created in Obsidian.

**Handwriting capture and text conversion**
- R7. The user can write by hand in a memo pane scoped to a specific date (and, where applicable, a specific meeting/note entry).
- R8. Handwriting-to-text conversion is manually triggered per capture — never automatic.
- R9. For each conversion, the user chooses between Boox's built-in handwriting recognition and AI vision OCR (via OpenRouter); the app remembers the last-used method as the default for the next capture, with an easy per-capture override.
- R10. Converted text is formatted as nested bullets matching the user's existing meeting-note structure (a top-level line plus indented detail bullets) before being written into the daily note.

**Diagram capture and conversion**
- R11. The user can draw diagrams by hand in the same memo pane.
- R12. Diagram-to-output conversion is manually triggered per diagram, with the user choosing image recreation or HTML/diagram-code recreation each time.
- R13. The chosen AI model (via OpenRouter) recreates the diagram in the selected format, and the result is inserted into the daily note.

**Vault access**
- R14. The app reads and writes the user's Obsidian daily note files directly from local on-device storage (the vault is materialized on the Boox device via Obsidian mobile running the LiveSync plugin); the app does not implement its own CouchDB/LiveSync client.
- R15. The app operates on one daily note per calendar day, matching the user's existing Periodic Notes daily-note path convention.

---

## Acceptance Examples

- AE1. **Covers R8, R9, R10.** Given a handwritten capture under a meeting entry, when the user taps "convert" and selects AI vision OCR, the app sends the capture to the AI provider, formats the result as indented bullets under that meeting's existing line, and writes it into the daily note file — without converting any other un-triggered captures on the page.
- AE2. **Covers R12, R13.** Given a hand-drawn flowchart, when the user taps "convert" and selects "HTML diagram," the app produces diagram code/markup (not a raster image) and inserts it into the note; if the user had instead selected "image," the app would produce and insert an image file instead.
- AE3. **Covers R3, R4.** Given a day with both an Obsidian meeting and a Google Calendar event at overlapping times, when the user views that day, both appear in the list with a visible distinction between them, and editing or deleting the Google Calendar entry from this app is not possible.
- AE4. **Covers R5, R6.** Given an empty day, when the user adds a new meeting via the quick form with a time and title, the daily note's `# 👥 Meetings` section gains a new `HH:MM - HH:MM: Title` line identical in format to one a user would type directly in Obsidian.

---

## Success Criteria

- The user can go through a full day on the Boox tablet — checking commitments, writing notes during meetings, and converting them — without opening a laptop or the desktop Obsidian app.
- Every entry the app creates or converts is plain Markdown matching the user's existing daily-note conventions closely enough that it requires no manual cleanup in Obsidian afterward.
- A downstream implementer can build the on-device file I/O, handwriting capture, and OpenRouter integration without needing to invent meeting-format parsing rules, conversion triggers, or Google Calendar display behavior — those are fully specified above.

---

## Scope Boundaries

### Deferred for later

- Displaying or editing TaskNotes tasks (due/scheduled items) in the calendar or day view.
- Batch/automatic conversion of handwriting or diagrams (e.g., converting everything on save) — v1 is manual, per-capture only.
- Two-way Google Calendar sync (creating or editing Google Calendar events from this app).

### Outside this product's identity

- General-purpose note-taking, file browsing, or notebook/folder organization on the device (the app is scoped to one day's note at a time, not a full vault browser).
- Implementing a custom CouchDB/LiveSync sync engine inside the app — vault materialization is delegated to Obsidian mobile + LiveSync already running on the device.

---

## Key Decisions

- **Build fresh rather than fork Aragonite**: the user wants a new Kotlin/Compose app, but it should follow Aragonite's proven patterns (Onyx HWR via AIDL, direct vault file writes, Jetpack Ink stroke rendering) rather than reinventing handwriting capture from scratch.
- **Vault access via local files, not direct CouchDB**: simpler and lower-risk than the app maintaining its own LiveSync/CouchDB client; relies on Obsidian mobile + LiveSync already running on the Boox device to materialize files locally.
- **Manual, per-capture conversion**: both handwriting-to-text and diagram-to-output are explicit user actions, not automatic background processing, to avoid OCR/AI errors silently entering the vault and to give the user control over cost/timing of AI calls.
- **OpenRouter as the AI gateway**: gives flexibility to choose/swap vision and diagram-generation models without hardcoding a single provider.
- **New meeting/note entries via quick form, not handwriting**: avoids OCR errors corrupting structured fields like times, which the daily note format depends on for parsing.

---

## Dependencies / Assumptions

- Obsidian mobile with the LiveSync plugin is expected to run on the Boox device to materialize the vault locally; this app assumes that process is already handling sync and conflict resolution, and does not duplicate it.
- Google Calendar read access requires OAuth credentials/setup; the vault already has a `google-calendar-importer` plugin with OAuth client credentials configured, which may be reusable as a reference or starting point, but the app will need its own Android-side OAuth flow.
- An OpenRouter API key/account is required for AI vision OCR and diagram conversion features.
- The daily note's `# 👥 Meetings` and `# 📝 Notes` headings and the `HH:MM - HH:MM: Title` meeting format are assumed stable conventions (confirmed from the user's actual templates and recent daily notes); if the user changes their daily note template, parsing/writing logic will need updating.

---

## Outstanding Questions

### Deferred to Planning

- [Affects R14][Needs research] Confirm exactly how Obsidian mobile + LiveSync exposes the vault's file path on Android (e.g., scoped storage permissions, SAF access) so the new app can read/write files reliably.
- [Affects R3][Technical] Decide whether Google Calendar read access uses a Google Calendar API OAuth flow directly in the new app, or reads via files already written by the existing `google-calendar-importer` Obsidian plugin.
- [Affects R9, R12][Needs research] Confirm Onyx HWR (MyScript) AIDL integration details and OpenRouter vision model selection during planning.
