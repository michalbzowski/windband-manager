# Plan: US-7.2 — Link Compositions to Events (the "what's playing at this event?" question)

## Context / why it's the next step

The user's goal is "send specific PDF pages of a score to an invited member".
We just finished **US-7.1** which tags *which part belongs to which instrument + page range* on the *composition* side.
This plan (US-7.2) adds the missing connector: **which composition(s) are being performed at which event?**
Without this link we literally cannot answer "what pages does member A play on event day?" — so all the
PDF-extraction and email-sending work (future US-7.3/7.4) is blocked until this lands.

## Data model — what I'll add

A new `EventComposition` junction table:

```java
@Entity
@Table(name = "event_compositions")
public class EventComposition {
    @Id Long id;
    @ManyToOne BandEvent event;      // nullable=false
    @ManyToOne Composition composition; // nullable=false
    @Column(nullable = false) Integer orderInSet;  // position in the setlist (1-based)
}
```

Why `orderInSet`: a "concert" or "gala" event likely has a fixed setlist, and
the user wants to send pages *in setlist order* if they bulk-export. It's cheap to add
here and saves a migration later when that need surfaces.

`BandEvent` gets `@OneToMany ... List<EventComposition> compositions = new ...;`;
we do NOT cascade from the event side (composition is the owner of its parts).
A new `@Transactional` method `EventCommandService.assignComposition(eventId, compositionId, orderInSet)`
persists the row (or re-uses an existing one — update `orderInSet` / swap in if a re-order happens).

**Scope guard:** same `requireOwned` semantics as everywhere else — the
composition and event must share a band, otherwise refuse.

## UI impact — where it plugs in

`events/detail.html` already has:
- a header (event name / date / payment etc.)
- a "Podsumowanie – ile osób gra na każdym instrumencie" chip row
- the member table with their instrument picker

Right **after** the instrument-summary chips I'll add a new section:
- a heading "Skład repertuaru tego wydarzenia" (Repertoire of this event)
- one row per `EventComposition` linked to this event — showing composition.title,
  a short instrument count (from ScoreFileListQueryService), and the setlist position
- if no compositions are linked yet → an empty-state "Brak przypisanych utworów" + hint
- a `<button class="secondary">+ Dodaj utwór</button>` that opens a `dialog` with a dropdown
  of all band compositions (already fetched as `${bandCompositions}` from the controller — see next)

The existing member table does NOT change in this story. Future US-7.3 adds the
"which pages does THIS member play at THIS event" picker, and US-7.4 wires up
the PDF page-extraction + email attach.

## Controllers / services / tests

1. `CompositionQueryService.getAllForBand(bandId)` → new read-only query: all
   DRAFT/READY/ARCHIVED compositions of the band (we show archived too, so the
   user can re-add one that was once retired). Order by `updatedAt` desc.
2. `EventPageController.eventDetail(...)` — inject `bandCompositions` + `eventCompositions`
   into the model; add a new `assignCompositionToEvent(eventId, compositionId, orderInSet)`
   command to `EventCommandService`.
3. **HTTP shape** (keep it REST-ish and simple):
   - `POST /events/{id}/compositions?compositionId=X` → link (order auto-appended)
     returns 302 redirect back to the event detail
   - `DELETE /events/{id}/compositions/{compositionId}` → unlink
     returns 302 redirect back to the event detail
4. **Tests I'll write** (Selenium `EventCompositionUiTest`):
   - seed an event, a composition of the same band, + at least one participant
   - go to the event detail page → panel visible with empty state "Brak przypisanych utwórrów"
   - click "+ Dodaj utwór", choose composition from dropdown, save → row appears in setlist
   - refresh the page → the row persists (DB write verified via JdbcTemplate, mirroring the
     pattern used by `CompositionPageUiTest.addPart` tests)
   - same-event / cross-band guard: a composition of band #2 must not be addable to an event of band #1
5. `./mvnw clean verify` green before commit + push.

## Out of scope for this US

- The "which pages does member A play at event B?" table
  (that's the next US — it depends on THIS one landing)
- PDF page-extraction (needs a library like Apache PDFBox or similar → follow-up US)
- Email attachment + send (follow-up US, builds on extraction)
- Bulk "send to all" with part auto-mapping (future US, depends on everything above)

## Acceptance criteria — what makes it "done"

- ✅ A new `event_compositions` table exists in H2 (auto-DDL) and Postgres (flyway migration if
     present — I'll search for one; if none, just the entity + a Flyway SQL file)
- ✅ The event detail page shows a "Skład repertuaru" section with one row per linked composition
  + an "add" dialog listing all band compositions (not just this one band's)
- ✅ A `POST /events/{id}/compositions` endpoint creates the link, refuses cross-band composition,
  persists, and the row is visible after a page refresh
- ✅ A `DELETE /events/{id}/compositions/{cid}` endpoint unlinks; row disappears after refresh
- ✅ Selenium test(s) covering the happy path + cross-band refusal — passing on `./mvnw clean verify`
- ✅ `./mvnw clean verify` passes (all 521+ tests + new ones), commit + push

## Rollout order — what comes after

1. US-7.3: For each confirmed member in an event, show which part they play at this event
   (a second "Głosy przypisane" table on the event detail — one row per member, with
   instrumentRole + page_from + page_to)
2. US-7.4: Extract pages from the composition's score PDF (Apache PDFBox or similar)
3. US-7.5: Attach extracted pages to a member email and send them
4. US-7.6 (later): Bulk "send all members their parts" with auto-matched roles

