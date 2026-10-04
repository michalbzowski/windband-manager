# 📚 Biblioteka Utworów (Compositions Library) — User Stories

> **Status audytu:** stan repozytorium `michalbzowski/windband-manager`, branch `main`, HEAD `1dc9159` (2026-09-18). Wszystkie statusy poniżej zostały zweryfikowane w kodzie (produkcyjnym i testowym), migracjach Flyway, szablonach Thymeleaf oraz historii PR (do #207).

## 🎯 Product Vision
Create a team-scoped compositions library where band members can catalog pieces, upload scores (PDF/ZIP), map instruments to pages/files via AI-assisted analysis with mandatory human verification, assign compositions to events, and distribute individual parts to musicians based on their instrument tags.

---

## 🏗️ Architecture Principles (from windband-manager)
- **Domain-Driven Design** with Ports & Adapters
- **CQRS-light**: separate Command/Query services
- **Multi-tenant isolation**: every entity has `band_id`, repositories filter by `band_id`
- **Human-in-the-loop** for AI operations
- **Flyway migrations** for schema changes
- **Thymeleaf + HTMX + PicoCSS** for UI

---

## 📋 Epic Breakdown

| Epic | Stories | Focus | Status |
|------|---------|-------|--------|
| **Epic 1: Domain Model & Persistence** | 1.1 – 1.6 | Core entities, repositories, migrations | ✅ All done (2026-09) |
| **Epic 2: File Upload & Storage** | 2.1 – 2.5 | Secure upload, storage strategy, validation | ✅ All done |
| **Epic 3: Composition CRUD (Manual)** | 3.1 – 3.5 + 3.03 | Create/read/update/delete without AI | ✅ All done |
| **Epic 4: AI-Assisted Score Analysis** | 4.1 – 4.7 | PDF/ZIP analysis, preview, verification | 🔶 US-4.1 + US-4.3 done · 4.2/4.4/4.5/4.6/4.7 not started |
| **Epic 5: Instrument Alias Mapping** | 5.1 – 5.3 | Tag-to-role resolution for distribution | 🔶 US-5.1 ✅ PR #251 (resolution service); US-5.2/5.3 ⬜ |
| **Epic 6: Event Integration & Distribution** | 6.1 – 6.6 | Assign to event, generate parts, send | ✅ FULLY DELIVERED — US-6.3 (PR #254) + US-6.4 (PR #255) + US-6.5 (PR #256) + **US-6.6 delivery audit trail (PR #257)**; setlist binding 500 hotfixed (PR #260, 2026-10-04) |
| **Epic 7: UI & UX** | 7.0 – 7.16 | Thymeleaf templates, HTMX interactions | 🔶 7.0/7.1/7.3/7.9/7.10/7.11/7.14/7.15 + **7.16 verify-gate (PR #261)** done · open: 7.2b setlist-remove button, 7.4–7.8 |

> **Status legend:** ✅ done · 🔶 partial / open items listed below · ⬜ not started · ❌ deliberately deferred
>
> **Uwaga numeracyjna:** oryginalny plan nie rozdzielał story "7.1" / "7.2" od Epic 6 — implementacja w repo traktuje link utworu do wydarzenia (setlist) jako **US-7.2**, panel głosów na stronach nut jako **US-7.1**, a verify-gate jako **US-3.03** (wcześniej cytowany w kodzie jako "Epic 7.0"). W dalszej części tego dokumentu używamy etykiet faktycznie występujących w kodzie (`US-2.3`, `US-4.1`, `US-4.3`, `US-7.1`, `US-7.2`).

---

## 🟢 Epic 1: Domain Model & Persistence (Foundation) — ✅ COMPLETE

All six stories are implemented and tested. Remaining open work per story is noted in *Open items*.

### **US-1.1: Composition Entity & Repository** ✅
> **As a** system
> **I want** a `Composition` aggregate root with band-scoped persistence
> **So that** compositions are isolated per team and support full CRUD

**Acceptance Criteria:**
- [x] `Composition` entity in `domain/composition/` with fields: `id`, `title`, `description`, `composer`, `arranger`, `band` (ManyToOne, not null), `status` (DRAFT/READY/ARCHIVED), `createdAt`, `updatedAt` — ⚠️ title max length is **200** (not 255 as originally specified)
- [x] `CompositionRepository` interface in `domain/composition/` with methods: `save`, `findByIdAndBandId`, `findAllByBand`, `findAllByBandAndStatus`, `search(bandId, term)`, `existsByIdAndBandId`, `delete` — method names differ from original spec (actual API is band-aware object-based, not primitive-id based)
- [x] Spring Data adapter in `adapter/out/persistence/CompositionRepositoryAdapter` + `SpringDataCompositionRepository` (flat `persistence/` tree, not a `composition/` sub-package as originally sketched)
- [x] Flyway migration: actual file is **`V32__create_compositions.sql`** (doc's suggested `V18` was already occupied by the system_admin migration)
- [x] Integration tests: `CompositionIT`, `CompositionRepositoryIT`, `CompositionQueryIT`, `CompositionTest` (unit), `CompositionCommandServiceTest` (Mockito)

**Open items:** none — story is complete.

**Story Points:** 3
**Dependencies:** None

---

### **US-1.2: Instrument Entity Enhancement** ✅
> **As a** developer
> **I want** the existing `Instrument` entity extended with `aliasOf` self-reference
> **So that** instrument hierarchies (e.g., Kornet → Trąbka) support alias resolution

**Acceptance Criteria:**
- [x] Nullable `aliasOf` (ManyToOne self-ref) added to `Instrument` — plus guards: no self-alias, no cross-band alias, root-target only. Write path: `InstrumentCommandService.updateAliasOf(Long, Long, Long)`; domain mutator `Instrument.setAliasOf(...)`.
- [x] `band_id` NOT NULL enforcement already in place via **V23__add_band_id_to_instruments.sql** and **V28__enforce_band_isolation_on_instruments.sql** (pre-dates this story; no new isolation migration needed)
- [x] `InstrumentRepository` updated with `findByAliasOf(...)` and `findRootInstrumentsByBandId(Long)` — adapter: `InstrumentRepositoryAdapter`
- [x] Migration: actual file is **`V37__add_alias_of_to_instrument.sql`** (doc's suggested `V19` already taken by member-groups). Adds nullable `alias_of_id`, self-FK with `ON DELETE CASCADE`, index `(band_id, alias_of_id)`.
- [x] Tests: `InstrumentAliasIT`, `InstrumentCommandServiceAliasTest`

**Open items:**
- 🔶 **Epic 5 (US-5.1–5.3)**: consuming the `aliasOf` hierarchy to resolve member instrument tags → composition roles ("Kornet" tag matches "Kornet 1", which is an alias of "Trąbka"). Resolution service **landed in US-5.1 (PR #251)** — `"Kornet"` now resolves to `{Trąbka 1, Trąbka 2, Kornet 1}` via `InstrumentRoleResolutionQueryService`; what remains is US-5.2 (cache/invalidation seam) and US-5.3 (distribution-list consumption).

**Story Points:** 2
**Dependencies:** US-1.1 (band isolation pre-existed since V23/V28)

---

### **US-1.3: CompositionInstrument Link Entity** ✅
> **As a** system
> **I want** a `CompositionInstrument` entity linking compositions to instruments with page/file mapping
> **So that** each part knows its source pages (PDF) or files (ZIP)

**Acceptance Criteria:**
- [x] Entity in `domain/composition/CompositeInstrument` — all specified fields present: `id`, `composition`, `instrument`, `instrumentRole`, `pageFrom`, `pageTo`, `fileRef`, `source` (enum `PartSource`: AI / MANUAL / HYBRID), `confidenceScore` (0.0–1.0 enforced), `verifiedBy`, `verifiedAt`, timestamps. ⚠️ **No denormalised `band_id` column** in the entity (the original spec asked for one) — band isolation is instead enforced by the factory: `CompositionInstrument.forComposition(...)` throws IAE when the two entities disagree on band; SQL FKs (`ON DELETE CASCADE` to composition, `ON DELETE RESTRICT` to instrument) are the net.
- [x] Unique constraint per (composition, role) — **case-insensitive**: `uq_composition_instruments_role` on `(composition_id, lower(instrument_role))`. "Flet 1" / "FLET 1" collide; "Flet 1" + "Flet 2" are two legal rows.
- [x] `CompositionInstrumentRepository` with band-scoped queries — adapter: `SpringDataCompositionInstrumentRepository` + `CompositionInstrumentRepositoryAdapter`. Methods include `findAllByComposition(Composition)`.
- [x] Migrations: **`V36__create_composition_instrument.sql`** (disabled no-op, comment-only placeholder) and **`V38__create_composition_instruments.sql`** (the corrected, idempotent DDL). Table in DB: `composition_instruments`.
- [x] Cascade delete when composition removed — via JPA (`CascadeType.ALL` + `orphanRemoval`) on `Composition.parts` **and** DB-level `ON DELETE CASCADE` from `composition_instruments.composition_id → compositions.id`. Both paths apply.

**Open items:** none — story is complete.

> **Pitfall for future readers:** the first attempt to ship this entity as `V36__create_composition_instrument.sql` used a PostgreSQL-invalid `CHECK (col1 >= 1, col2 >= 1)` syntax (comma-separated predicates inside one CHECK). Flyway stamped v36=FAILED with a stale checksum, so the corrected DDL had to be re-homed in **V38**, and V36 was preserved as a comment-only file. Do not edit V36's body or rename V38 — both are load-bearing for shared (Railway/DB) Flyway history.

**Story Points:** 3
**Dependencies:** US-1.1, US-1.2

---

### **US-1.4: InstrumentRoleMap (Tag-to-Role Mapping)** ✅ (persistence + domain unit test) / ⬜ (admin UI → Epic 7)
> **As a** band manager
> **I want** configurable mapping from member instrument tags to composition roles
> **So that** "Trąbka" tag automatically matches "Trąbka 1", "Trąbka 2", "Kornet 1"

**Acceptance Criteria:**
- [x] Entity `InstrumentRoleMap` in `domain/composition/` with: `id`, `band` (ManyToOne → `Band` entity, not a raw `band_id` column), `sourceTag`, `targetRolePattern`, `description`, `createdAt`, `updatedAt`. Factory `InstrumentRoleMap.forBand(...)`.
- [x] Repository with `findByBandIdAndSourceTag(Long, String)` and `findByBandIdAndSourceTagAndTargetRolePattern(...)` in the domain port `InstrumentRoleMapRepository` (adapter: `SpringDataInstrumentRoleMapRepository`).
- [x] Migration: actual file is **`V39__create_instrument_role_map.sql`** (doc's suggested `V21` already taken). Idempotent DDL with unique index `(band_id, lower(source_tag), target_role_pattern)`, covering index on `(band_id, lower(source_tag))`.
- [x] **Seed mappings for the "default" band (id=1)** in the migration: Trąbka→Trąbka 1 & 2, Flet→Flet 1, Waltornia→Waltornia 1, Puzon→Puzon 1, Saksofon→Saksofon 1. Idempotent under repeated replay via `INSERT ... WHERE NOT EXISTS`.
- [x] Domain behavior test: `InstrumentRoleMapTest` (unit).
- [ ] **Admin UI for managing mappings** — ⬜ NOT YET. Belongs deliberately in **Epic 7 (UI)** per original spec "later story". The persistence layer is ready for it.

**Open items:**
- ⬜ Admin UI for managing `InstrumentRoleMap` — tracked as part of **Epic 7**, not Epic 1.
- ⬜ Read path for US-5.x resolution: a service that consumes the map when distributing parts (no such service exists in `application/query/composition/` yet).

**Story Points:** 2
**Dependencies:** US-1.3

---

### **US-1.5: ScoreFile Entity (Stored Files Metadata)** ✅ (persistence) / ✅ (write path via US-2.1) / ⬜ (read paths via US-2.4/2.5, done)
> **As a** system
> **I want** track uploaded score files with storage metadata
> **So that** files are retrievable, secure, and auditable

> Naming note: the entity is named **`ScoreFile`** (not `CompositionFile` as originally sketched). Table in DB: **`score_files`**. Deliberate rename — "score file" is clearer than "composition file" (a composition can hold several score files over time).

**Acceptance Criteria:**
- [x] Entity `ScoreFile` in `domain/composition/` with: `id`, `composition` (ManyToOne, not null), `mimeType`, `sizeBytes`, **`sha256`** (integrity + dedup hook — stronger than the original spec's plain metadata), `storagePath` (absolute path, nullable until US-2.1 populated it — now always set on write), `originalName` (display-only, nullable), `createdAt`.
  - ⚠️ Field deltas vs. original spec: adds `sha256`; drops `fileType` enum (MIME type is the discriminator); drops `pageCount` (later re-added by US-2.2 — see below); drops `uploadedBy`/`uploadedAt` in favour of a single immutable `createdAt` (authed-uploader audit columns were never added; Epic 2 shipped without them).
- [x] Added fields: `pageCount` (**US-2.2**, migration **V40__add_scorefile_page_count.sql**), `parentFileId` (**US-2.3**, migration **V41__add_scorefile_parent_file_id.sql** — self-FK with `ON DELETE CASCADE`, so deleting a ZIP row removes its extracted children).
- [x] Repository with band-scoped queries: `ScoreFileRepository` port + Spring Data adapter (`SpringDataScoreFileRepository` / `ScoreFileRepositoryAdapter`).
- [ ] **Storage path convention `/mnt/sda1/media/windband-scores/{bandId}/compositions/{compositionId}/{uuid}_{originalName}.ext`** — ✅ implemented in **US-2.1** via `ScoreFileStorage` (root configurable through `ScoresConfig`, two-phase write: temp file → hash → atomic rename).
- [x] Tests: `ScoreFileIT`, plus the full US-2.x integration suite (see Epic 2 below).

**Open items:** none — entity layer is stable and consumed by US-2.1–2.5, US-4.x, and US-7.x write paths.

**Story Points:** 2
**Dependencies:** US-1.1

---

### **US-1.6: Composition Command & Query Services** ✅ (all pieces now landed)
> **As a** developer
> **I want** `CompositionCommandService` and `CompositionQueryService` in `application/command/composition/` and `application/query/composition/`
> **So that** use cases are encapsulated following CQRS pattern

**Acceptance Criteria:**
- [x] `CompositionCommandService` (in `pl.michalbzowski.windband.application.command.composition`) with: `create(cmd, bandId)`, `update(id, cmd, bandId)`, `archive(id, bandId)`, `restore(id, bandId)`, `deleteComposition(id, bandId)`.
  - ✅ **`verifyCompositionParts(id, bandId, verifier)` is now implemented** (US-3.03, commit `549d293`) — single legal entry point for DRAFT → READY; see Epic 3.
  - ✅ New in US-7.1: `addPart(compositionId, instrumentId, role, pageFrom, pageTo, confidence, bandId)` — manual part-mapping write path (see Epic 7).
- [x] `CompositionQueryService` (in `pl.michalbzowski.windband.application.query.composition`) with: `get(id, bandId)`, `listByBand(bandId, statusFilter)` (+ paginated overload used by the US-3.2 list page), `search(bandId, term)`, `getCompositionWithParts(id, bandId)`.
- [x] Command DTOs: `CreateCompositionCommand`, `UpdateCompositionCommand` in `application/command/composition/`.
- [x] Query DTOs: `CompositionDto`, `CompositionWithPartsDto`, `CompositionInstrumentDto` in `application/dto/composition/`.
- [x] Band isolation enforced via `BandQueryService.getRequiredBand(bandId)` on both sides; mutating paths via the shared fail-closed `requireOwned(id, bandId)` helper (`IllegalStateException` → HTTP 409).
- [x] New query side classes supporting the US-2.3–2.4/2.5 and US-7.x pages: `ScoreFileListQueryService` (file list + parts panel data), `ScoreFileDownloadQueryService` (+ `ScoreFileDownloadMetadata` record).
- [x] Unit tests with Mockito: `CompositionCommandServiceTest`; integration-style: `CompositionQueryServiceIT`; page-level UI hook: `CompositionPageUiTest`.

**Open items:** none — all deferred Epic 1 items have since landed (READY-gate via US-3.03; manual part writes via US-7.1).

**Story Points:** 5
**Dependencies:** US-1.1, US-1.3, US-1.5

---

### Epic 1 status summary (post-audit)

| Story | Status | Notes |
|-------|--------|-------|
| US-1.1 Composition + repository | ✅ done | Migration V32; consumed by every list/detail/CRUD page |
| US-1.2 Instrument `aliasOf` | ✅ done | Migration V37; alias mutator ready for Epic 5 resolution logic |
| US-1.3 CompositionInstrument | ✅ done | Migrations V36 (disabled) + V38 (corrected); cascade via JPA and DB FK |
| US-1.4 InstrumentRoleMap | 🔶 persistence done, UI open | Migration V39 with seed maps; admin UI → Epic 7 |
| US-1.5 ScoreFile | ✅ done (incl. US-2.x columns) | Entity `ScoreFile`, migrations V35 + V40 (`page_count`) + V41 (`parent_file_id`); download/delete implemented in US-2.4/2.5 |
| US-1.6 Command + Query services | ✅ done | `verifyCompositionParts` (US-3.03) and `addPart` (US-7.1) both landed in the command service |

**Migration numbering reality (vs. the original plan):** the doc suggested V18/V19/V20/V21/V22 — all were already occupied by unrelated features (system_admin, member groups, event invitations, event participation-instrument). The score-library migrations actually landed as:

| Version | File | Story |
|---------|------|-------|
| **V32** | `V32__create_compositions.sql` | US-1.1 |
| V35 | `V35__create_scorefile.sql` | US-1.5 |
| V36 | `V36__create_composition_instrument.sql` | disabled placeholder (US-1.3) |
| **V37** | `V37__add_alias_of_to_instrument.sql` | US-1.2 |
| **V38** | `V38__create_composition_instruments.sql` | US-1.3 (corrected DDL) |
| **V39** | `V39__create_instrument_role_map.sql` | US-1.4 |
| V40 | `V40__add_scorefile_page_count.sql` | US-2.2 |
| V41 | `V41__add_scorefile_parent_file_id.sql` | US-2.3 |
| V42 | `V42__score_analysis_table.sql` | US-4.1 |
| V43 | `V43__create_event_compositions.sql` | US-7.2 (event-setlist link) |

Future migrations for Epic 5 (alias resolution) and the rest of Epic 6/7 should use **V44 and up**.

---

## 🟢 Epic 2: File Upload & Storage — ✅ COMPLETE

### **US-2.1: Secure File Upload Pipeline** ✅ (PR #200, merged 2026-09-16)
> **As a** band member
> **I want** to upload a score file (PDF or ZIP) for a composition
> **So that** it's stored securely, validated, and retrievable

**Acceptance Criteria:**
- [x] `POST /bands/{bandId}/compositions/{compositionId}/files` — multipart `file` field → `201` + `ScoreFileDto`. Lives in adapter layer (`ScoreFileUploadRestController`); application-layer DTO is Spring Web-free (enforced by `ArchitectureTest`)
- [x] MIME allow-list: PDF / ZIP / JPEG / PNG. Disallowed types rejected with **415** (via `UploadValidator.UploadRejectedException` + `GlobalExceptionHandler`)
- [x] Size cap (per file and per ZIP): configured via `windband.scores.{max-file-size-bytes,max-zip-file-size-bytes}` in `ScoresConfig`; over-cap → **413**
- [x] **ZIP-slip protection**: entries containing `..`, absolute paths, drive letters (`C:`), or backslashes are rejected with **422** (`UploadValidatorZipSlipTest`, 6 unit cases)
- [x] SHA-256 computed once and stored in the `score_files.sha256` row (integrity + dedup hook from US-1.5)
- [x] Two-phase disk storage: write to temp file, compute hash, atomic rename via `Files.move` into final location `root/{bandId}/compositions/{compositionId}/{uuid}_{sanitisedName}` (original-name column remains for display only) — implemented in `ScoreFileStorage` with shared `TEMP_FILE_PREFIX`/`TEMP_FILE_SUFFIX` constants so the writer and the US-2.5 cleaner never diverge
- [x] Band isolation enforced in `ScoreFileCommandService`: a band can only attach files to compositions whose `band.id == caller band` (`CompositionRepository.findByIdAndBandId`, fail-closed)
- [x] No Spring Web types anywhere under `pl..application..` (enforced by ArchUnit rule)

**Files touched:** `ScoreFileUploadRequest`, `UploadedFileAssembler`, `UploadValidator` (+ `UploadRejectedException`), `ScoreFileStorage`, `ScoreFileCommandService`, `ScoresConfig`, `ScoreFileDto`, `ScoreFileUploadRestController`; wired into `GlobalExceptionHandler`.
**Tests:** `ScoreFileCommandServiceIT` (Testcontainers PostgreSQL), `UploadValidatorZipSlipTest`.

**Story Points:** 8
**Dependencies:** US-1.5 (ScoreFile entity already in place from Epic 1).

---

### **US-2.2: PDF Page Count Extraction** ✅ (PR #200, merged 2026-09-16)
> **As a** band member or admin
> **I want to know** how many pages my uploaded score PDF has
> **So that** I can plan assignments and see page ranges in the UI (US-7.1 done; AI page-mapping Epic 4 later)

**Acceptance Criteria:**
- [x] `ScoreFile.pageCount` column: nullable `Integer`, NULL = "not applicable" (ZIP / image uploads keep NULL naturally); > 0 = extracted
- [x] Migration **V40__add_scorefile_page_count.sql**: `ALTER TABLE score_files ADD COLUMN IF NOT EXISTS page_count INT;` — idempotent, non-breaking (backfills left NULL)
- [x] `PdfPageCounter.extract(byte[])`: uses Apache PDFBox 3.0.7 `Loader.loadPDF`; graceful on invalid / password-locked / truncated input (returns `null`, never throws)
- [x] Wired into `ScoreFileCommandService.upload(...)`: after MIME validation, if `contentType == "application/pdf"` → count pages and pass to the factory; ZIP / non-PDF uploads keep `pageCount = null` in the DB
- [x] Exposed on the response: `ScoreFileDto.pageCount` (nullable) so the UI can hide the field when N/A
- [x] Domain factory updated: `ScoreFile.forComposition(..., Integer pageCount)` — 7-arg variant; constructor validates `pageCount >= 0 or null`; zero-page PDFs are preserved as 0

**Files touched:** `ScoreFile` (+1 column, +1 factory arg), `PdfPageCounter` (new class, app layer — no Spring Web deps), `ScoreFileCommandService.upload(...)` updated to extract, `pom.xml` (+PDFBox 3.0.7).
**Tests:** `ScoreFileIT` (updated factory signature); `PdfPageCounterTest` (5 unit tests incl. real 1-page + 5-page PDF round-trip through PDFBox, via `TestPdfBuilder.generate(int pages)` — no binary fixtures in repo); `ScoreFileCommandServiceIT.uploadPdf_recordsPageCount` and `.uploadZip_staysPageCountNull`.

**Story Points:** 3
**Dependencies:** US-2.1 (pipeline already in place).

---

### **US-2.3: ZIP Content Enumeration** ✅ (PR #201, merged 2026-09-16 — **previously under-reported as "not started"**)
> **As a** band member
> **I want** the ZIP to be unpacked and its inner files listed individually
> **So that** I can map parts (pages or files) to instruments (Epic 4)

**Real implementation (verified in code):**
- [x] `POST /bands/{bandId}/compositions/{compositionId}/files/{fileId}/expand` → expands all entries of a previously uploaded ZIP into individual `score_files` rows (controller: `ScoreFileUploadRestController`; service: `ScoreFileCommandService.expandZip(fileId, compositionId, bandId)`)
- [x] Each entry gets its own disk write + SHA-256 via the same `ScoreFileStorage` two-phase path; each extracted PDF gets a `pageCount` (`PdfPageCounter` reuse); ZIP parents and non-PDF entries keep it NULL
- [x] Parent linkage: `score_files.parent_file_id` (migration **V41__add_scorefile_parent_file_id.sql**) self-references the parent ZIP row with **`ON DELETE CASCADE`** — deleting a parent ZIP removes all extracted children; the US-2.5 delete service relies on this to cascade child rows explicitly as well
- [x] ZIP-slip protection re-applied at extraction time (not just upload) via `UploadValidator.requireZipContentSafe(...)` inside `ZipEntryExtractor.extract(...)` — exactly as the original spec required
- [x] The parent ZIP row remains untouched as the setlist/parent anchor after expansion; `ZipEntryDto` list is returned for the UI
- [x] Non-ZIP input rejected with 422 (`UploadRejectedException`, "nie jest archiwum ZIP"); foreign-band / unknown composition fail closed via the standard `requireOwned` path
- [x] Tests: `ZipEntryExtractorTest`, `ScoreFileExpandZipIT`

**Story Points:** 5 (retroactive; original estimate was open)
**Dependencies:** US-2.1, US-2.2 (PDF page count per extracted entry).

---

### **US-2.4: File Download** ✅ done (PR #202 merged 2026-09-17 — **verified, previously shown as "PR pending"**)
> **As a** band member
> **I want** to download a file I uploaded
> **So that** I can view the full score or extract parts locally

Real API: `GET /bands/{bandId}/compositions/{compositionId}/files/{fileId}` (controller `ScoreFileDownloadRestController`, service `ScoreFileDownloadQueryService` + `ScoreFileDownloadMetadata`) — streams with `Content-Disposition` (inline for PDF, JPEG, PNG; attachment for ZIPs and other binaries), real MIME type from `score_files.mime_type`, `Content-Length` from recorded byte size. Band isolation: layer 1 via band resolution → `IllegalArgumentException` (→ HTTP 400); layer 2 file's composition `.bandId` must equal the requested band and URL composition id must equal file's composition, else `IllegalStateException` (→ HTTP 409). Missing on disk / unreadable → mapped as HTTP 410 Gone. **Tests:** `ScoreFileDownloadIT`, `ScoreFileDownloadQueryServiceTest`, `ScoreFileDownloadRestControllerTest`.

---

### **US-2.5: File Delete + Scheduled Temp Cleanup** ✅ (PR #202 merged, same branch/PR family)
> **As a** band manager
> **I want** to delete a file and any orphaned rows it left behind
> **So that** I don't keep stale scores on disk and in the DB

Real API: `DELETE /bands/{bandId}/compositions/{compositionId}/files/{fileId}` → `204 No Content` on success (controller `ScoreFileDeleteRestController`, service `ScoreFileDeleteCommandService`). Band isolation follows the same two-layer contract as US-2.4 — unknown band → 400; unknown file id or composition mismatch or cross-band ownership → 409; no row or on-disk bytes touched in any failure path (proven by the unit suite). ZIP-parent delete removes extracted children via the V41 `parent_file_id` `ON DELETE CASCADE` **and** an explicit app-layer cascade pass so that both paths converge. `ScoreFileTempCleanupScheduler` (`@Scheduled(fixedDelay = 1h)`) sweeps the scores root for stale `upload-*.tmp` files older than `windband.scores.temp-cleanup-hours` (default 24 h) — safety net for crashed uploads; uses the shared `ScoreFileStorage.TEMP_FILE_*` constants so it can never drift from the writer's naming.

**Files touched:** `ScoreFileDeleteCommandService` (app layer), `ScoreFileDeleteRestController` (adapter), `ScoreFileTempCleanupScheduler`, `ScoresConfig` (+`tempCleanupHours`), `ScoreFileStorage` (+public temp-file constants, +`effectiveRootPath()`).
**Tests:** `ScoreFileDeleteCommandServiceTest` (7 unit tests), `ScoreFileTempCleanupSchedulerTest` (4 pure-JVM tests over a real temp dir), `ScoreFileDeleteRestControllerTest` (4 MockMvc web-slice tests pinning 204 / 400 / 409 through the real `GlobalExceptionHandler`).

**Story Points:** 5
**Dependencies:** US-2.1, US-2.3 (cascade semantics depend on `parent_file_id`).

---

**Epic 2 status (post-audit):** ✅ **Complete.** All five user stories are implemented, tested (unit + integration + web-slice), and merged. Migration trail: V35 → V40 → V41. Follow-up work that builds on top of this pipeline: US-4.x AI analysis (partially done, see Epic 4) and the part-mapping UI / distribution logic in Epics 5–7. `ZipEntryExtractor` + US-2.3 also unlock per-entry analysis as the next step for the AI pipeline.

---

## 🟢 Epic 3: Composition CRUD (Manual) — ✅ COMPLETE

> **Scope note:** the original breakdown had "US-3.03" in an ambiguous position; the implementation shipped it as an explicit story **US-3.03 (verify-gate)**, and completed the remaining CRUD stories as **US-3.1** (create), **US-3.2** (list/browse), **US-3.4** (edit metadata) and **US-3.5** (archive/restore/delete). There is no separate "US-3.05" story in code; the commit message `ac39840 "Epic 3.05?"` was a review-fix pass over US-3.5, not a new US.

### **US-3.1: Create composition via UI/endpoint** ✅
> **As a** band manager or member
> **I want to add a new title (title, description, composer, arranger) under my team
> **So that** we have a base row into which files and parts can eventually be added

**Real implementation:**
- [x] `GET /bands/{bandId}/compositions/new` → rendered form (`templates/compositions/form.html`), HTMX-aware (swaps `#compositions-content`) — `CompositionPageController#createForm`
- [x] `POST /bands/{bandId}/compositions` → creates a DRAFT row and 302's to the detail page; binding errors re-render the form with the first Polish validation message (`#composition-form-errors`, e.g. "Tytuł jest wymagany") — `CompositionPageController#create`
- [x] Writes through `CompositionCommandService.create(cmd, bandId)` → `Composition.create(...)` factory → `CompositionRepository.save`; band isolation via `BandQueryService.getRequiredBand(bandId)` + the standard `requireOwned` pattern on any subsequent read (US-1.6 service contract enforced by ArchUnit rule)
- [x] UI test: `CompositionPageUiTest.shouldListSeededCompositionsAndCreateANewOne` (end-to-end form fill → persistence assertion in PostgreSQL via Testcontainers); `shouldShowServerValidationForBlankTitle` pins the blank-title rejection path
- [x] Navigation entry point added by US-7.0: "Utwory" in top-nav + hamburger menu (commit `020ce81`)

**Story Points:** 2 (retroactive).
**Dependencies:** US-1.6 (command service), US-4.2 is NOT required — create flow has no AI dependency by design.

---

### **US-3.2: List & browse per band (no cross-band leakage through the web layer)** ✅
> **As a** band manager
> **I want to see only my team's titles, with pagination and status badge
> **So that** members of other teams never leak into our list

**Real implementation:**
- [x] `GET /bands/{bandId}/compositions` → paginated (Spring Data `PageRequest`, default 20) listing sorted by most recent, via `CompositionQueryService.listByBand(bandId, null, pageable)` — `CompositionPageController#list`; HTMX-aware fragment swap (`:: #compositions-content`) for lazy/in-place refresh without a full page reload
- [x] Band isolation: `requireBandAccess(oidcUser, bandId)` throws `IllegalStateException` (→ 409) for any caller who does not belong to the team; query is additionally band-scoped at the repository level (`SpringDataCompositionRepository.findAllByBand`, US-1.1) — double net
- [x] Status badge rendered per row via `templates/compositions/fragments/status-badge.html` ("Szkic" / "Gotowy" / "Zarchiwizowany")
- [x] UI test: the list assertion in `CompositionPageUiTest.shouldListSeededCompositionsAndCreateANewOne` (2 seeded rows render, correct titles) + `CompositionPageUiTest.shouldRestoreArchivedComposition_toDraft_andReappearInList` proves a restored row is band-scoped and reappears (not merely soft-hidden)

**Story Points:** 1 (retroactive).
**Dependencies:** US-1.1 (repository band scope), US-1.6 (query service).

---

### **US-3.4: Update metadata via UI/endpoint** ✅ (PR #206 "pr-206-us-3.4-edit-metadata", merged; follow-up review fixes in PR #207)
> **As a** band manager
> **I want to edit the title / description / composer / arranger of an existing composition
> **So that** errors caught during rehearsals or later research can be corrected without recreating the row

**Real implementation:**
- [x] `GET /bands/{bandId}/compositions/{id}/edit` → edit form pre-populated from `CompositionQueryService.get(id, bandId)` — `CompositionPageController#editForm`; template `templates/compositions/edit.html`
- [x] `POST /bands/{bandId}/compositions/{id}` (HTTP PATCH-equivalent via Spring's model binding; the controller comment refers to it as "PATCH /compositions/{id}") → validates with `@Valid @ModelAttribute UpdateCompositionCommand`, on success calls `CompositionCommandService.update(id, cmd, bandId)` (`updateTexts` on the entity) and 302's back to detail — `CompositionPageController#update`
- [x] Domain validation (blank title on update) surfaces as a Polish error message re-rendered above the form (`#composition-edit-form-errors`, "Tytuł jest wymagany"); DB row is unchanged in this case (pinned by test)
- [x] Cross-band or unknown composition ids fail closed via `requireOwned` → `IllegalStateException` → 409; no side effects
- [x] UI tests: `CompositionPageUiTest.shouldEditMetadata_onEditPage_andPersistAllFields` (all three text round-trips, DB assertion) and `shouldRejectBlankTitle_onEditPage_andRenderValidationError` (server-side rejection + unchanged row in DB)

**Story Points:** 2.
**Dependencies:** US-1.6 (`update` on the command service), US-3.1 (a composition must exist to be edited).

---

### **US-3.5: Archive / Restore / Delete (with confirmation UX)** ✅ (PR #207 merged commit `9b1b8ba`; fix pass `61f4802`)
> **As a** band manager
> **I want to archive a finished piece, restore it if needed, or delete it permanently — always with an explicit confirmation dialog
> **So that** the library stays tidy, but an accidental keystroke never destroys data

**Real implementation:**
- [x] `POST /bands/{bandId}/compositions/{id}/archive` → status → ARCHIVED via `CompositionCommandService.archive(id, bandId)`; idempotent (re-click is a no-op that still 302's back) — `CompositionPageController#archive`
- [x] `POST /bands/{bandId}/compositions/{id}/restore` → sets status unconditionally to DRAFT (even if called from READY — safe down-grade, forces re-verification via US-3.03 before re-promotion is possible) — `CompositionPageController#restore`
- [x] `POST /bands/{bandId}/compositions/{id}/delete` → hard delete through `CompositionCommandService.deleteComposition(id, bandId)`; dependent rows (`composition_instruments`, `score_files`) cascade via V35/V38 FKs — `CompositionPageController#delete`
- [x] Shared confirmation dialog (`window.openLifecycleDialog`, `lifecycle-confirm-btn` in `detail.html`) guards all three actions on the client side before any POST fires
- [x] Band isolation / fail-closed: unknown or foreign ids → `IllegalStateException` → 409; no row in another band's space is ever read or touched
- [x] UI tests (`CompositionPageUiTest`, Selenium + DB assertions): `shouldArchiveComposition_andShowArchivedStatus_badge`, `shouldRestoreArchivedComposition_toDraft_andReappearInList` (full archive→restore round-trip, DB row flfrom READY → ARCHIVED → DRAFT), `shouldDeleteComposition_andRemoveFromBandList_persistently` (row gone from DB by primary key **and** by title, subsequent GET 4xx)

**Story Points:** 3.
**Dependencies:** US-1.1, US-1.6 (command service lifecycle methods; `deleteComposition` was part of US-1.6's AC list).

---

### **US-3.03: Manual part verification + ready-gate** ✅ (commit `549d293`)
> **As a** band manager or librarian
> **I want to explicitly verify the instrument-parts mapping of a composition before it can be marked READY
> **So that** distribution (Epic 6) and AI-assisted review (Epic 4) only ever operate on parts a human has confirmed

This is the "verify-gate" that US-1.6 deliberately deferred to Epic 3. It is also what US-4.5 ("AI preview accept", future story) will call through instead of bypassing the READY transition.

**Real implementation:**
- [x] `CompositionCommandService.verifyCompositionParts(Long id, Long bandId, String verifier)` — single legal entry point for DRAFT → READY and for auditing part-row ownership at the service boundary. Blank verifier → `IllegalArgumentException` (HTTP 400); unknown or foreign-band composition → `IllegalStateException` (HTTP 409 fail-closed).
- [x] Reuses the per-row audit primitive already in US-1.3 (`CompositionInstrument.verify(verifier, instant)` — idempotent, first-writer wins) and the entity lifecycle method `Composition.markReady()`.
- [x] Idempotent / non-destructive: re-running after a partial earlier verification leaves existing `verifiedBy`/`verifiedAt` untouched; only unfilled rows are touched, and promotion to READY happens **only** when zero parts remain unverified.
- [x] Band isolation via the shared `requireOwned` helper (same as every other mutating method in this service).
- [x] Integration tests (4 new cases in `CompositionCommandServiceTest`, shared Testcontainers PostgreSQL): happy-path promotion; blank-verifier rejection with zero side effects; cross-band rejection (fail-closed, no row touched); audit-pair freeze + partial-prior-verification (first-writer preserved verbatim, new verifier fills only missing rows, READY reached exactly once all are covered).

**Files touched:** `CompositionCommandService` (+autowired `CompositionInstrumentRepository`, +imports, +1 new method `verifyCompositionParts`); tests: `CompositionCommandServiceTest` (+4 test methods, +2 small seed helpers `seedComposition` / `instrumentInBand`).
**Regression gate used at close-out:** `./mvnw test -Dtest='CompositionCommandServiceTest,CompositionInstrumentIT,CompositionQueryServiceIT,CompositionTest'` — all green (39 tests).

**Story Points:** 3.
**Dependencies:** US-1.3 (parts + audit primitive), US-1.6 (command service + `requireOwned` pattern both reused, not re-invented).

---

### Epic 3 status (post-audit)

| Story | Status | Notes |
|-------|--------|-------|
| US-3.03 verify-gate | ✅ done, merged | Ready-gate reachable via a legal, audited path (`verifyCompositionParts`) |
| US-3.1 create | ✅ done, merged | `/new` form + `POST .../compositions`, Selenium UI tests |
| US-3.2 list/browse | ✅ done, merged | Paginated band-scoped list (Spring Data), HTMX fragment swap, seeded-data + restore-reappear UI tests pin the band-scope guarantee |
| US-3.4 edit metadata | ✅ done, merged (PR #206 + review fixes PR #207) | `GET /{id}/edit` + `POST /{id}`, full-field round-trip + blank-title rejection tested end-to-end |
| US-3.5 archive/restore/delete | ✅ done, merged (PR #207) | Shared confirmation dialog + 3 POST endpoints; DB-level assertions for all three states + hard-delete persistence |

**Open items for follow-on work (not blocking Epic 6):**
- No HTTP/HTMX surface for `verifyCompositionParts` yet — the method is testable at the service level but **no UI button calls it**. Wiring a "Oznacz jako gotowy" action on the detail page (and a parts verification checklist) is an open UX item that belongs to Epic 7 / early Epic 6, not Epic 3.
- Archive / Restore / Delete are exposed as raw browser-side form POSTs inside `detail.html`; a more discoverable location (e.g. a kebab menu on the list rows) is a follow-on UX refinement tracked by the team.

---

## 🟡 Epic 4: AI-Assisted Score Analysis — 🔶 PARTIAL (US-4.1 + US-4.3 done; US-4.2/4.4–4.7 not started)

> **Architecture note:** the AI work itself lives in an **external** pipeline (the `windband-ai` Ollama+music21 repo, per the project's own skill documentation). The windband-manager side is a *thin* orchestration + artefact store: it launches the runner, tracks lifecycle via `score_analysis`, and exposes the artefacts (JSON / MusicXML / MIDI / validation.txt) to the UI. The actual page-mapping quality of the result is a property of that external pipeline, not of this repo — so "AI analysis" in this document means "the manager-side orchestration seam", not a local NLP model.

### **US-4.1: AI analysis pipeline (start + poll + latest)** ✅ (commit `c4088d0`, 2026-09-18)
> **As a** band manager
> **I want to trigger an AI analysis of an uploaded score PDF
> **So that** the external windband-ai pipeline can propose part→page mappings I can then review against US-4.5

**Real implementation:**
- [x] Domain: `ScoreAnalysis` aggregate root (state machine `PENDING → RUNNING → {SUCCEEDED | FAILED}`, immutable transitions — every mutator returns a fresh instance; illegal transition throws `IllegalStateException`). Artefacts are stored as absolute paths, not bytes: `arrangementJsonPath`, `arrangementMusicxmlPath`, `arrangementMidPath`, `validationTxtPath`, plus `runnerRef` and `errorMessage`.
- [x] Migration **V42__score_analysis_table.sql** with CHECK constraints: terminal rows must have `finished_at`; SUCCEEDED requires all four artefact paths; FAILED requires a non-blank `error_message`. Dominant read path indexed (`composition_id, created_at DESC`).
- [x] Application layer (**Spring Web-free**, enforced by ArchUnit rule): `ScoreAnalysisCommandService` (start + poll), `AiAnalysisRunner` port + `StubAiAnalysisRunner` default bean (so the app boots without a real pipeline configured — returns `RunnerNotConfiguredException`, surfaced as 501), `AiArtifactsLayout` (deterministic output dir from analysis id), `ScoreAnalysisQueryService.latestFor(compositionId, bandId)` for the "latest" snapshot.
- [x] Adapter layer: `StartScoreAnalysisRestController` — `POST /bands/{bandId}/compositions/{compositionId}/score-files/{fileId}/analyze`; `GetLatestScoreAnalysisRestController` — `GET /bands/{bandId}/compositions/{compositionId}/analysis/latest`. Both enforce band isolation through the composition FK (a foreign-band score file can never be "borrowed" as a US-4.1 input).
- [x] Scope: standalone PDFs only (ZIP parents are rejected with a Polish `IllegalArgumentException` → 422 path; the MVP is single-PDF). Cross-band or unknown inputs fail closed (`IllegalStateException` → 409); missing runner → `RunnerNotConfiguredException` → 501 with a DB-visible `errorMessage`.
- [x] Tests: `ScoreAnalysisCommandServiceIT` (start/poll/latest + band-isolation + fail-closed paths over Testcontainers PG via the existing `BaseIntegrationTest`), `StartScoreAnalysisRestControllerTest` (web-slice, MockMvc), plus the domain-level `ScoreAnalysisTest` for the state-machine invariants.

**Story Points:** 8.
**Dependencies:** US-1.5 (`score_files.storagePath` is the input to the runner), US-2.1 (a file must be uploaded first).

---

### **US-4.3: "Analizuj utwór" modal on detail page** ✅ (commit `5b10a7f`, 2026-09-18)
> **As a** band member
> **I want to see which analyses ran on this composition, their current phase, and be able to start a new one — all from the detail page
> **So that** I don't have to know IDs or endpoints to drive the AI pipeline

**Real implementation:**
- [x] `CompositionPageController#detail` (US-4.3) now injects into the Thymeleaf context: `scoreFiles` (the uploaded files, via `ScoreFileListQueryService.listByComposition(id, bandId)` — read inside the same transaction to avoid the classic `LazyInitializationException` after session close), and a **latest analysis** snapshot from `ScoreAnalysisQueryService.latestFor(id, bandId)`: `latestAnalysisId`, `latestPhase`, `latestRunnerRef`, `latestErrorMessage`, the four artefact paths, `latestStartedAt`, `latestFinishedAt`
- [x] Template `compositions/detail.html` renders an "Analizuj utwór" dialog with a file-selector (only PDFs show as options — ZIPs and images are filtered client-side), a start button wired to the US-4.1 endpoint, and a read-only section showing the latest phase / error / artefacts; HTMX-aware so the panel refits on the same content id (`#compositions-content`) without a page reload
- [x] UI test: `CompositionDetailPageUiTest.detailPage_rendersAnalyzeModalStructure` — pins that the dialog, file picker and "Analizuj" button all exist on the rendered detail page for a composition that has ≥1 uploaded PDF (Selenium + DB seed)

**Story Points:** 3.
**Dependencies:** US-4.1 (the endpoint it calls), US-2.1/2.2 (files must exist to be selectable), US-7.0 (detail page is reachable from nav).

---

### **US-4.4: AI preview — page mapping shown for user review** ⬜ Not started
> **As a** band manager or librarian
> **I want the AI's part→page proposal rendered as an editable table I can adjust before accepting
> **So that** I can catch wrong page ranges (the pipeline is heuristic) before they become "trusted" data

Not yet implemented. The `score_analysis` artefacts (`arrangement_json_path`, `validation_txt_path`) are the natural input for this screen; US-4.5's accept/verify flow gates the READY transition (see US-3.03) so US-4.4 can focus purely on rendering + editing, not on promotion semantics.

---

### **US-4.5: AI preview accept — writes to part rows under a human audit trail** ⬜ Not started
> **As a** band manager or librarian
> **I want the accepted proposal to land in `composition_instruments` with `PartSource.AI` (or `.HYBRID`) recorded per row, and every write to carry my identity
> **So that** US-3.03's verify-gate can distinguish AI-suggested rows from human-authored ones, and an audit query can always answer "who, when, what"

Not yet implemented. The target entities (`CompositionInstrument`, `PartSource`, verifiedBy/verifiedAt) already exist from US-1.3; the domain `verify()` primitive is already ready (US-3.03's acceptance criteria describe the exact contract this story must respect — "US-4.5 (AI preview accept)" is named explicitly as a dependent in `CompositionInstrument`'s javadoc). What's missing is a command service method that reads the JSON artefact, applies each row to `composition_instruments`, and stamps `PartSource.AI` / `.HYBRID` — plus a UI button on the US-4.4 preview screen.

---

### **US-4.6: Re-run / cancel an analysis** ⬜ Not started
> **As a** band manager
> **I want to re-run a failed or stale analysis with one click, and (when supported) kill a runaway process
> **So that** I don't have to delete + re-upload the file just to retry

Not yet implemented. The `ScoreAnalysis` state machine already forbids illegal transitions (`requirePhase`), so a cancel/re-run path will need either a new `CANCELED` phase (schema change — would be V44) or a "new row supersedes old" rule at the query layer (no schema change; `latestFor` already picks by `created_at DESC`, which is compatible). The `runnerRef` column is ready to hold a PID/job id for a future kill path.

---

### **US-4.7: Zip-parent analysis (multi-page PDF set)** ⬜ Not started
> **As a** band manager
> **I want to analyze each PDF *inside* a ZIP of parts, not only standalone uploads
> **So that** a "whole concert" ZIP upload is usable end-to-end

Not yet implemented. US-2.3 has already exploded the ZIP into per-entry `score_files` rows (with their own `sha256`, `pageCount`, and `parent_file_id`), so the input surface for this story is simple: change US-4.1's PDF-only gate to "PDF **or** a PDF child of a ZIP parent", then run the pipeline per child and write one `score_analysis` row per child (or a new parent-level aggregate — design call). US-2.3's cascade semantics (parent delete → children cascade) must be respected, since a "cancel" in the middle would leave orphan rows otherwise.

---

**Epic 4 status (post-audit):** 🔶 **US-4.1 + US-4.3 done and merged** (external-runner orchestration + detail-page modal). **US-4.4 / US-4.5 / US-4.6 / US-4.7 not started.** The domain, migration (V42), command/query services, and REST surface are all in place and test-covered — the remaining work is purely about consuming/authoring the part rows from AI artefacts, plus the UX for re-run/cancel.

---

## 🔶 Epic 5: Instrument Alias Mapping — Partial (US-5.1 ✅; US-5.2/5.3 ⬜)

> **Status note:** the *persistence* layer (US-1.2 — `aliasOf` self-reference on `Instrument`, guards against self/cross-band aliasing, root-target only), the domain unit test, **and now the resolution service (US-5.1)** are in place: `"Kornet"` tag → `{Trąbka 1, Trąbka 2, Kornet 1}` via `InstrumentRoleResolutionQueryService` (PR #251). What's missing is the cache/invalidation layer (US-5.2) and the consumption UI in the distribution flow (US-5.3 → US-6.2/6.3).

### **US-5.1: Resolution algorithm (tag → role via alias chain)** ✅ in PR #251 (2026-09-28)
> **As a** band manager or librarian
> **I want a function that, given a member's instrument tag and my team's `InstrumentRoleMap` rows, returns the matching composition role(s)
> **So that** "Kornet" (alias of "Trąbka") can legitimately match "Trąbka 1", "Trąbka 2", **and** "Kornet 1"

**Implemented:** `InstrumentRoleResolutionQueryService` (`application/query/composition/`) — pure application-layer read service, no UI (that's US-5.3's territory):
- [x] Widen the input tag to its **alias family**: itself (always kept, so hand-typed map rows stay reachable), its canonical root when it is an alias, and every direct alias of that root — matching "Kornet" ⇒ {Trąbka 1, Trąbka 2, Kornet 1} exactly as the acceptance story requires
- [x] Case-insensitive tag matching (folded with `Locale.ROOT`), mirroring V39's `lower(source_tag)` unique index so app and DB never disagree; role labels keep original spelling in results
- [x] Deterministic output: deduped on folded role label, sorted by role then source tag — stable regardless of DB row order
- [x] Two entry points: `resolveRoles(bandId, tag)` → `List<ResolvedInstrumentRole>` (role + matched `source_tag` provenance) and `resolveRoleNames(bandId, tag)` → flat `List<String>` for US-5.3's distribution list
- [x] Fail-closed: blank/null tag → empty list (no port touched); unknown band → `IllegalArgumentException` from `getRequiredBand` (→ 400); band with no matching rows → empty list (valid state)
- [x] Termination is structural — only one band's instruments + one band's role-map rows are ever loaded, and the write path forbids self/cross-band/chained aliases (`Instrument.setAliasOf`), so a 1-step family is bounded; the read still guards an `aliasOf` pointer that resolves to nothing
- [x] Tests: 10 unit (Mockito contract) + 9 integration (real PG/H2: acceptance example, root/alias symmetry, second alias, provenance, hand-typed unknown tag, blank input, fail-closed band, unrelated roots, cross-band isolation). Full `./mvnw clean verify` BUILD SUCCESS — 639 tests green, Checkstyle + SpotBugs clean

**Design deviations from the "Proposed design" above (recorded for US-5.2/5.3):**
1. The walk is **symmetric family matching**, not a per-row `aliasOf` chain: the whole alias family (root + all its direct aliases) is treated as equivalent names of one musical role, so *both* "Kornet" and "Trąbka" resolve to the union of roles mapped under either label. This is what makes the story's example work for root players too.
2. No in-memory cache yet — that is deliberately left to **US-5.2** (cache/invalidation). The current read loads at most one band's instruments and role-map rows; the US-5.2 cache layer can sit directly in front of `resolveRoles` without changing any caller.

### **US-5.2: Cache / invalidation** ⬜
> **As a** system
> **I want resolution to be fast (~µs) and correct after any alias/tag edit
> **So that** I don't re-walk the chain on every distribution click

Design call: in-memory per-band cache keyed by (bandId, sourceTag) with an explicit `@Transactional`-aware invalidation hook on `InstrumentCommandService.updateAliasOf(...)` and any future `InstrumentRoleMap` write path. No code exists yet for this seam; the US-3.x CRUD story should add it as its last step rather than leaving it to Epic 5 to retrofit.

### **US-5.3: Distribution list builder** ⬜
> **As a** band manager
> **I want, given a READY composition and my team's active members, a list of (member → instrument → page range / extracted file) ready to hand to US-6.1 for event assignment
> **So that** "who plays what" is answerable from the app without manual lookup

Depends on US-3.03's READY gate (the `verifiedBy`/`verifiedAt` audit pair must be populated before this story reads rows) and on US-4.5 / US-7.1 (part rows must exist, either AI or manually authored). No code yet.

---

## ✅ Epic 6: Event Integration & Distribution — ✅ FULLY DELIVERED (setlist link, part-list generation, delivery, batch ordering, stale-voice demotion, delivery audit trail — PRs #252–#257)

> **Important ordering fact discovered during the audit:** the *event assignment* story (linking a composition to an event's setlist) shipped as **US-7.2** in this repo, even though it is logically Epic 6/7 hybrid — because its UI surface (the setlist panel on the event detail page) landed through the same Thymeleaf workstream as US-7.1. The original plan's "Epic 6.1: assign to event" work is therefore **already done** below; what remains in Epic 6 is the parts-generation + e-mail/delivery side, which is genuinely new work.

### **US-7.2 (≈ Epic 6.1): Assign compositions to an event setlist** ✅ (commit `9c7bbbd`, 2026-09-18)
> **As a** band manager
> **I want to add / remove a specific composition from a specific event's setlist, in order
> **So that** "send all parts in concert order" (future US-6.3) has a stable, ordered input

**Real implementation:**
- [x] Domain: `EventComposition` junction entity (`domain/event/`), `@OnDelete(action = OnDeleteAction.CASCADE)` on both sides, `orderInSet` ≥ 1, unique constraint `(event_id, composition_id)` — factory `link(event, composition, orderInSet)` enforces the invariants before the instance is handed out
- [x] Migration **V43__create_event_compositions.sql** (idempotent; `uq_event_compositions`, index on `event_id`). Both FKs are `ON DELETE CASCADE` at the DB level — an event deletion removes all its links, a composition deletion removes every event that listed it
- [x] Band-scope invariant (the junction keeps no direct `band` FK): enforced in `EventCommandService.assignComposition` by resolving **both** bands and refusing cross-band writes — same pattern as `CompositionInstrument.forComposition`. Cross-band attempts are pinned by the test suite to leave the table untouched (0 rows inserted, 0 status codes other than 4xx)
- [x] Adapter: `EventPageController#assignComposition` (`POST /events/{id}/compositions`) and `#unassignComposition` (`DELETE /events/{id}/compositions/{cid}`) — both in the existing `/events` controller (no new controller needed); read path `EventCommandService.getEventCompositions(eventId)` + a setlist panel rendered into `templates/events/detail.html` (auto-append order = next `orderInSet`)
- [x] Tests: `EventCompositionLinkTest` (3 cases: two-same-band items land in order; cross-band refused with no row; unassign idempotent and removes the row) and `EventCompositionUiTest` (2 Selenium cases: add-persist-shows-in-list end-to-end; cross-band rejected with no UI error dialog leaking foreign data)

**Story Points:** 5.
**Dependencies:** US-3.x (a composition must exist), US-1.1 + US-2.x (band-isolation pattern reused, not re-invented).

---

### **US-6.2: Generate the concrete part list for an event × member** ✅ (PR #252, merged 2026-09-28, commit `0e9bfb4`)
> **As a** band manager
> **I want, for a given READY composition and a chosen member, the exact page range (or extracted ZIP entry) they should play
> **So that** I can print / share it to them individually

**Real implementation (verified in code):**
- [x] `EventPartDistributionDto` (`application/dto/event/`) — nested read model: compositions in setlist order → parts (role, `pageFrom`/`pageTo`, `fileRef`) → matched musicians (member + the instrument tag that produced the match)
- [x] `EventCompositionPartsQueryService` (`application/query/event/`) — band-scoped generator: walks US-7.2's ordered `event_compositions` rows, loads each composition's part rows, and resolves who plays each part through US-5.1's `InstrumentRoleResolutionQueryService` (tag → alias family → role set). Fail-closed band isolation: unknown band → `IllegalArgumentException` (400), cross-band probe → `IllegalStateException` (409)
- [x] UI: "Rozdanie głosów" panel in `templates/events/detail.html` (composition → part → musician, empty state when the setlist has no parts); wired through `EventPageController` model attribute `partsDistribution`
- [x] `CompositionInstrumentRepository` + adapter gained the band-scoped batch read the generator needs
- [x] Tests: 13 service-level tests (`EventCompositionPartsQueryServiceTest` — alias-matched resolution, cross-band isolation, empty-setlist and no-matching-musician empty states) + canary UI test on `/events/{id}` (no regression)

**Story Points:** 5.
**Dependencies:** US-7.2 (ordered setlist), US-7.1 / US-4.5 (part rows with page ranges), US-2.3 (per-entry `score_files`), US-5.1 (alias resolution — consumed, not re-implemented).

---

### **US-6.3: Deliver parts to musicians (e-mail or app)** ✅ (PR #254, merged 2026-10-02)
> **As a** band manager
> **I want one click to "send this event's parts" and every member gets their page range / file via the channel they prefer
> **So that** I don't hand-copy each PDF

**Real implementation (verified in code — merged 2026-10-02):**
- [x] `EventPartDeliveryCommandService` + `PartDeliveryResult` (`application/command/event/`) — the single delivery policy: builds the per-composition recipient lists from US-6.2's distribution, **mints a share token per part via US-7.11's `PartShareTokenCommandService.tokenFor`** (no new token scheme), renders the e-mail through the existing `EmailSender` port + Thymeleaf (`templates/email/event-part.html`), and returns an honest split of `sent / skipped-no-consent / errors`
- [x] **Consent is a hard gate** — driven by US-6.2's distribution, every non-consenting member for that part is *skipped silently* (named in the result) rather than failing the whole send; members without a valid address are likewise bucketed into `errors`, so one bad row never blocks the rest
- [x] Adapter: `EventPageController#deliverParts` (`POST /events/{id}/parts-delivery`) — resolves the acting principal, adds `partDeliveryResult` / `partDeliveryError` to flash, and redirects back; the "Rozdanie głosów" section in `templates/events/detail.html` renders the send button only when a US-6.2 distribution exists, with a confirm modal (using the layout's `openAppModal(id)` convention) and the post-return result banner
- [x] **Defects found by its own tests and fixed** (each surfaced by a real run, not assumed): (1) `@PathVariable eventId` vs URI `{id}` → `MissingPathVariableException` on *every* POST (hard 500 blocker); (2) non-existent Thymeleaf `#numbers.toString()` truncated the part table mid-render; (3) LAZY `EventComposition.composition` read outside an open session (`open-in-view: false`) → `LazyInitializationException`, closed with a `JOIN FETCH` repository read `findAllWithCompositionByEventId`; (4) `openAppModal(element)` vs `openAppModal(id)` so the confirm modal opens
- [x] Tests: 8 service-level unit tests (`EventPartDeliveryCommandServiceTest` — happy path, consent gating, no-covering-file, deactivated/no-consent exclusion, token minting, error bucketing), integration tests over Testcontainers PG (`EventPartDeliveryIntegrationTest` — real bean + hermetic `@Primary` mailer: exactly one token per part, e-mail carries band name + working token link, non-consenting members get no mail and are reported skipped), and a Selenium UI test (`EventPartDeliveryUiTest`) asserting the button renders only with a distribution, "brak zgody" flagged before send, the modal opens, the honest banner reports sent + skipped counts, exactly one captured envelope goes to the consenting musician, and exactly one token row exists in `part_share_tokens`

**Story Points:** 8.
**Dependencies:** US-6.2 (distribution lists), US-7.2 (ordered setlist), US-7.11 (share token mint), US-7.1 / US-4.5 (part rows with page ranges), V25 `email_consent` (consent gate). The sender port/adapter are reused, not re-implemented (event invitations, welcome emails).

---

### **US-6.4 / US-6.5 — delivered; US-6.6 ✅ (branch ready, PR pending build)** ⬜

- **US-6.4:** "Send *all* parts for the event, in concert order" (batch version of US-6.3 over the full setlist built in US-7.2's `orderInSet` walk). ✅
- **US-6.5:** "Regenerate on fly when a composer edits part ranges" (invalidation hook on US-7.1 / US-4.5 writes — the same invalidation *concept* as US-5.2's cache seam, but at the *composition* level and, critically, for the verification/ready-gate that Epic 6's distribution reads are built on). ✅

### **US-6.5: Regenerate on when a composer edits part ranges (invalidation on write)** ✅

> **As a** band manager or librarian
> **I want any change to a voice (page range, role, instrument, bound score file) to instantly invalidate its "verified" state and re-enter the US-3.03 ready-gate — live, not on a rebuild or cache flush
> **So that** Epic 6 never distributes, presents-with-a-working-link, or reports-as-ready a mapping that a human only *previously* approved

**Why it was broken (root cause):** `CompositionInstrument.updateMapping` replaced the range/role/instrument but deliberately left the frozen `verifiedBy`/`verifiedAt` audit pair in place. Because `verify()` freezes on first call and never touches an already-set pair, a READY composition kept reporting its edited voice as verified for every downstream read — US-6.2's distribution `verified` flag (`part.getVerifiedAt() != null`) and the ready-gate both trusted stale data. Editing was effectively invisible to the "human-approved" model the whole Epic 6 hand-off depends on.

**Real implementation (this change):**
- [x] **Domain-level invalidation** — `CompositionInstrument.updateMapping` now clears `verifiedBy`/`verifiedAt` as part of the write. The hook lives in ONE authoritative seam, so *every* writer (US-7.1 panel, issue #241 voice editor, any future AI ingestion) goes through it automatically; there is no per-call-site wiring to forget, and no cache to flush.
- [x] **Aggregate status follows the invariant** — `Composition.markDraft()` demotes a stale `READY` back to `DRAFT`; `CompositionCommandService.invalidateStaleReadyState(...)` calls it after `addPart` *and* `updatePart`, but ONLY when the composition is READY and at least one part no longer has a verification pair. DRAFT/ARCHIVED states are untouched, so `archive()`/`restore()` semantics are preserved; if by chance every part is still approved, the piece stays READY. The rule is total: **READY ⇒ every voice has a frozen human audit.**
- [x] **Re-verify closes the loop** — a fresh US-3.03 pass (`verifyCompositionParts`) re-freezes the audit pair and re-promotes to READY; US-6.5 added no second verification regime, it just makes the existing gate honest again.

**Tests (TDD RED → GREEN, real Postgres via Testcontainers):**
- `CompositionCommandServiceTest` — 4 new story tests: edit of a verified part clears the frozen pair AND demotes READY→DRAFT; edit of an already-unverified part changes nothing unexpected; adding a voice to a READY piece drops it back (the "whole map approved" claim breaks); re-verify after edit restores READY with a FRESH audit pair (`b@x.com`, not the pre-edit `a@x.com`).
- `EventCompositionPartsQueryServiceTest` — 1 new **end-to-end** test through Epic 6's own live read model: verified → US-6.2 reports `verified=true`; composer edits the range → the *very next* `forEvent` reports `verified=false` with no cache in between; re-verify → `true` again.

**Dependencies:** US-3.03 (ready-gate — the state being protected), US-6.2/US-6.3 (the readers invalidated on behalf of). No migration: both `verified_by`/`verified_at` are already nullable (`V38`; `DEFAULT NULL`). Story Points: 5.

---

### **US-6.6: "Historia rozdań" — audit trail per delivery run** ✅ (branch `feat/us-6.6-delivery-audit-trail`)
> **As a** band manager
> **I want an immutable, per-run history of EVERY delivery decision for an event — who received which part in which run, and for everyone who did NOT (no consent / no e-mail / no covering score file / send failed)**
> **So that** when someone asks "did X really get their March part?" I can answer with the exact attempt, the exact reason, and who triggered it — without digging through e-mail logs

**Why it was missing:** the US-6.3/6.4 command side computed all five policy buckets (DELIVERED / SKIPPED_NO_CONSENT / SKIPPED_NO_EMAIL / REFUSED_NO_SCORE_FILE / SEND_FAILED) but returned them as a throwaway in-memory `PartDeliveryResult` — after `deliverParts` exits, the only trace is the e-mail inbox. Re-sending silently replaces the story: an honest "refused because no score file" becomes indistinguishable from "never happened".

**Real implementation (this change):**
- [x] **Migration V46** — new append-only table `event_part_deliveries`: scalar snapshot per decision row (`member_name`, `recipient_email`, `piece_title`, `part_role`, `page_from/to`, `channel`, `outcome`, `reason`, `actor`, `sent_at`) with a single `event_id` FK `ON DELETE CASCADE`. **Denormalized deliberately**: history survives member/part deletion and rename (an audit row must document what was true AT THAT RUN), and zero lazy associations means `open-in-view: false` can never bite the read path.
- [x] **Append-only entity** — `EventPartDelivery` with **no mutators at all** (no `updateX`, no `modifiedAt`); the repository contract exposes only `save` + `findAllByEventIdOrderBySentAtDesc`. The five outcome constants live here (`OUTCOME_DELIVERED`, …, `OUTCOME_REFUSED_NO_SCORE_FILE`) — single source of truth shared by command + query sides.
- [x] **Audit hooks inside the delivery loop** — `EventPartDeliveryCommandService` writes a decision row in EVERY bucket: after each successful send (`DELIVERED`), inside the send `catch` before rethrow (`SEND_FAILED`, so the run still fails the API call), and at each skip branch (`SKIPPED_NO_CONSENT`, `SKIPPED_NO_EMAIL`, `REFUSED_NO_SCORE_FILE`). All rows of one run share one `runAt = clock.instant()` captured ONCE at loop start — that is what makes "which attempt?" groupable without a second column.
- [x] **Best-effort audit, never breaks delivery** — the persist helper (`recordAudit`) catches any persistence failure, logs it, and lets the run continue: an audit outage must not prevent musicians from getting their parts (the result object and the banner still tell the truth about the SEND).
- [x] **Read model with band isolation** — `EventPartDeliveryQueryService.historyForEvent(eventId, bandId)`: unknown event → 404 (`EventNotFoundException`), foreign band → 409 (`IllegalStateException`), rows grouped into `DeliveryBlock`s newest‑first by the run sentinel, tallies summed across runs (a re-send ADDS one DELIVERED tally per run — cumulative count of decisions, which is what an audit trail means).
- [x] **REST** — `GET /api/events/{id}/part-deliveries` in `EventController` (thin adapter — no try/catch; `GlobalExceptionHandler` maps the query service's exceptions, same surface as US-6.3/6.4's `sendAllParts`; same active-team resolution from session/principal).
- [x] **UI panel "📜 Historia rozdań"** in `events/detail.html` — always rendered (never a silent absence): honest empty state "Brak historii"; per-run cards (attempt #, ISO stamp, row count, actor) + a full decision table (date, musician, e-mail, piece, part, pages, per-outcome chip, reason). Wires the scalar DTO straight into record accessors — no lazy entities on the view path.

**Tests (TDD: RED→GREEN, real DB H2/Postgres-mode, `open-in-view: false`):**
- `EventPartDeliveryCommandServiceTest` +3: every policy decision writes exactly its own row (per-bucket `outcome`/`recipientEmail`/`reason` assertions); SEND_FAILED row is persisted BEFORE the rethrow; audit persistence failure never breaks the delivery itself.
- `EventPartDeliveryHistoryRestControllerTest` (new, 5 scenarios): 200 success with runs newest‑first + tally math; 404 unknown event; 409 foreign band; 400 missing team; empty-history envelope. (**Lesson pinned into the windband skill:** `@JsonFormat(pattern="y…")` on an `Instant` record field fails Jackson serialization — YearOfEra unsupported; ISO instant is the wire format.)
- `EventPartDeliveryIntegrationTest` +3 (real context, real DB): two runs ACCUMULATE (append-only verified row-by-row, history grouped into 2 blocks newest‑first, DELIVERED tally counts both); one run lands both the deliver AND every skip decision in the table; cross-band audit read fails closed with 409.
- `EventDeliveryHistoryRenderTest` (new — the UI seam Selenium can't reach on CI): after a real delivery the detail page renders the panel with tallies, the ✅ chip, the actor, and Jan's row (name/part/pages); a fresh event renders the honest empty state. (This test caught two template defects the REST lane could never see: a `</strong>` closing a `<span>` that silently dropped 4 run-header elements, and `#temporals.toString(Instant)` which does not exist.)

**Dependencies:** US-6.3 (the loop instrumented), US-6.4 (result vocabulary), US-7.10/US-7.11 (gates + token links feeding the audited decisions). Migration: V46, new table only — nothing altered, safe to replay on every environment. **Story Points: 8.**

---

## 🔶 Epic 7: UI & UX — 🔶 PARTIAL (navigation, parts panel, upload/preview, role-map admin, voice-file binding, replace-in-place and the verification gate all done; remaining: US-7.4–7.8 polish)

### **US-7.0: "Utwory" entry point in top-nav + hamburger menu** ✅ (commit `020ce81`)
> **As a** user
> **I want to find the compositions library from the main navigation, exactly like events & members
> **So that** I don't have to know its URL

Implemented: the shared layout was extended (via the team's normal `fragments/layout.html` touch-point, which is a *global* file per §6 of the project's workflow skill and required an integration owner) with a "Utwory" link resolving to `/bands/{bandId}/compositions`; visible in both the desktop top-nav and the mobile hamburger. Covered by the same `PageHeaderConsistencyUiTest` suite that guards every other nav entry, plus the navigation-specific assertions inside `CompositionPageUiTest` (every page it drives is reached through the normal user path).

**Story Points:** 1.
**Dependencies:** US-3.x pages must exist and be login-gated.

---

### **US-7.1: "Oznacz głosy na stronach nut" panel** ✅ (commit `8aeb95e`; SpotBugs fix-ups `1aa6216`, Checkstyle fix `34b8c83`)
> **As a** band manager or librarian
> **I want to map an instrument role → page range on this score, from the detail page, for any band member in my team
> **So that** the ready-gate (US-3.03) + distribution (Epic 6) have real data to work with — without waiting for the AI pipeline

**Real implementation:**
- [x] `CompositionPageController#detail` (US-7.1 seam) injects `parts` (via `ScoreFileListQueryService.partsFor(id, bandId)`) and `bandInstruments` (the full roster via `InstrumentQueryService.findAll(bandId)` — no alias-filtering at the panel level; US-5.x read path isn't required yet for manual mapping)
- [x] `CompositionCommandService.addPart(compositionId, instrumentId, role, pageFrom, pageTo, confidence, bandId)` — enforces same-band membership on the instrument (`InstrumentRepository.findByIdAndBandId`), calls `CompositionInstrument.forComposition(...)` with `PartSource.MANUAL`, defaults a null `confidence` to 1.0 (a human-stamped row is high-confidence by construction), and fails closed cross-band
- [x] Panel in `compositions/detail.html`: one row per existing part (instrument + role + page range + verified marker if US-3.03 has run over it), plus an inline form to add a new mapping for any band member's instrument; HTMX-aware so the panel refits inside `#compositions-content`
- [x] UI test: `CompositionPartPanelUiTest` (Selenium end-to-end over Testcontainers PG — pins the happy path + at least one cross-band reject + one blank-role reject, and asserts on the rendered table rows after each add)

**Story Points:** 5.
**Dependencies:** US-1.3, US-1.2 (instrument list), US-3.03 (verification semantics must stay intact — this panel *feeds* that gate rather than replacing it).

---

### **US-7.3 (was Epic 1.4 open item): Admin UI for InstrumentRoleMap** ✅ (PR #250, 2026-09-27 — in review)
> **As a** band manager
> **I want to add / remove rows in `instrument_role_map` from the app, not from SQL
> **So that** a new member instrument tag ("Klarinet B"→"Saksofon 1") can be wired in seconds

Implemented on `feat/us-7.3-role-map-admin-ui` (PR #250). Delivered exactly the seam this story asked for:
- `InstrumentRoleMapPageController` at `/bands/{bandId}/instrument-roles` — GET list+form, POST add, POST `/{id}/delete`, with a `belongsToTeam` guard so a foreign band's rows can never be listed/mutated.
- `InstrumentRoleMapCommandService` (`addMapping` / `deleteMapping`) layered over the existing US-1.4 port; duplicate → app-side reject, blank input → HTTP 400, cross-team/unknown-band delete → HTTP 409 with no row touched.
- `InstrumentRoleMapQueryService` projecting a band's rows to a DTO so the template never touches the lazy `Band` association.
- `instrument-roles/list.html` + one menu link in the hamburger **Administracja** section (ADMIN/SYSTEM_ADMIN).
- Tests: controller unit tests, command-service integration test (add / duplicate / cross-band delete isolation), and a Selenium UI test through the real admin login against band 1 (add → renders → remove → gone; duplicate rejected with the Polish message; blank-field server validation).

`./mvnw clean verify` is green (Checkstyle + SpotBugs passing). Status flips to fully ✅ on merge to `main`.

---

### **US-7.9: PDF score file upload button + header preview panel** ✅ present on `main` (added 2026-09-19 per team request; verified against HEAD `5c337f0`)

> **As a band manager or librarian**
> **I want to upload a PDF score file from the composition detail page and see it rendered as an inline header preview (page thumbnails / first lines of each page)**
> **So that I can visually confirm which instrument parts start on which pages — without opening the full PDF in a separate tab**

**Storage:** The REST endpoint (`POST /bands/{bandId}/compositions/{compositionId}/files`, US-2.1 / `ScoreFileUploadRestController`) and persistence layer are already in place. **This story is purely UI + optional preview render path** — no new domain entity or migration required for MVP.

**Status (re-checked 2026-09-27 on `origin/main`):** the story is implemented and merged — `ScoreFileThumbRestController` + `ScoreFileThumbQueryService` (server-side PDFBox page thumbnails, LRU-cached) + a controller test are all on `main`, and the upload section + inline header/preview panel render in `compositions/detail.html`. The acceptance criteria below are therefore satisfied; kept verbatim as the spec this was built to.

**Acceptance criteria (target state):**

- [ ] **Upload section** on `compositions/detail.html` — visible above the parts panel when the composition exists. Contains:
  - a file input (`<input type="file" accept="application/pdf">`) + "Wgraj nuty (PDF)" button
  - a drop zone (optional UX nicety, not MVP-blocking)
  - client-side file-type / size validation mirroring `UploadValidator` limits (PDF only, up to `windband.scores.max-file-size-bytes`) before the POST fires; friendly error toast on 413 / 415 / 422 responses
  - after upload: a visible confirmation with file name + page count (from `ScoreFileDto.pageCount`, already computed by `PdfPageCounter` in US-2.2), and the file appears in the header preview list below
- [ ] **Header preview panel** — same detail page, below the upload section. The goal is a lightweight per-page "header" view, NOT a full PDF embed:
  - One option (MVP — **recommended first pass**): an inline `<img>` grid of page thumbnails for pages **1–3 only** (title page + first two measure pages typically show enough context). Thumbnails rendered client-side via `pdf.js` (or rendered server-side by `PdfBoxPageThumbnailGenerator` → served as JPEG at a fixed viewport width via the existing US-2.4 download endpoint with `?page=N` parameter — see open design question below). This is the smart minimum: the user doesn't need all 20 pages to know which pages hold "Flet 1" — only the first few where parts start.
  - Fallback when `pageThumbnailUrl` isn't available (no thumbnails generated, large PDF, or the rendering dependency fails): a text-only header list showing **page number + first line of extractable text per page** for pages 1–3, using Apache PDFBox `PDFTextStripper` with `setStartPage(1); setEndPage(3)` and `setTextSortByPosition(false)` (line-by-line output). This requires no new render pipeline — only a controller method that returns `List<String>` of header lines.
  - "Pokaż cały PDF" link → opens the US-2.4 download endpoint in a new tab (`target="_blank"`, browser's native PDF viewer) as the escape hatch for pages 4+.
- [ ] **Mobile usability:** upload + preview sections must be usable at viewport width ≥ 360 px (Safari/Chrome on iPhone SE). Form fields stack vertically, no horizontal scroll required. Test on `CompositionDetailPageUiTest` with a mobile viewport assertion.
- [ ] **Band isolation:** unchanged — the upload endpoint's existing band guard (`ScoreFileCommandService.requireOwned`) is reused; the preview / header fetch must carry the same band-scoped ownership check (reuse `requireOwned` from `CompositionQueryService`).
- [ ] **No new Flyway migration** for MVP. If thumbnail generation ends up persisting a rendered image per page in a new column / table, that's a follow-up story (V44+).

**Open design decision (pick before implementing):**

| Option | Pro | Con |
|--------|-----|-----|
| **A: pdf.js client-side render** — server returns the PDF URL, browser renders page 1 thumbnails in JS | No new server code, no new storage | Requires bundling `pdf.js` into the project's front-end (new dep); may not work well with large files on slow mobile connections; CSP must allow blob URLs |
| **B: Server-side PDFBox thumbnail → JPEG endpoint** (recommended) — add `GET .../files/{id}/thumb?page=N` that renders page N to a JPEG at ~400 px wide, streamed like US-2.4 | No new front-end dep; works in any browser; mobile-safe; consistent with existing stream-through pattern | One new controller method + one new PDFBox render call; must cache / rate-limit so the endpoint isn't abused by looping requests |

**Story Points:** 7 (UI-heavy, moderate back-end surface)
**Dependencies:** US-2.1 (upload endpoint), US-2.2 (`pageCount`), US-2.4 (download endpoint for the full-PDF link), US-3.x (detail page exists). **No Epic 4 / AI dependency.**

**Suggested task split:**

| Step | Deliverable |
|------|-------------|
| 1 | UI: upload section in `detail.html` + JS fetch to existing endpoint + success toast + file row add. No preview yet. |
| 2 | Server: `GET .../files/{id}/thumb?page=N&width=400` → JPEG (PDFBox `PDFRenderer.createImageAtIndex`). Cache with Spring `@Cacheable` or in-memory LRU keyed on `(fileId, page)`. Stream as `image/jpeg`, correct MIME, ETag = sha256 prefix (already in DB). |
| 3 | UI: header preview panel — grid of `img` tags for pages 1–3 fetched from step 2's endpoint; graceful "Brak podglądu" fallback when all three requests fail or the file has no text + render fails. |
| 4 | Mobile pass: verify at 360 px; `CompositionDetailPageUiTest` mobile-viewport assertion for upload section + preview grid. |
| 5 | Integration test: `ScoreFileThumbRestControllerIT` — 200 + JPEG byte header for a generated multi-page PDF; 422 for ZIP parent; 409 cross-band; 404 for unknown page number. |

### **US-7.10: Shareable voice link (per part row)** ✅ shipped 2026-09-23, superseded by US-7.11 for distribution
> **As a** band librarian
> **I want** a link per defined voice ("Flet 1, strony 23–24") that I can copy or e-mail to a musician

**Real implementation** (`PartLinkRestController` + `PartLinkQueryService` + `PartShareByEmailCommandService`):
- `GET /bands/{bandId}/compositions/{compositionId}/parts/{partId}` — streams the bound score file with `Content-Disposition` filename `<utwor>-<rola>_ss-<from>-<to>.pdf`.
- `POST .../parts/{partId}/share` `{"recipientsCsv": "..."}` → e-mails the link via the shared `EmailSender`.
- Two-layer band isolation (unknown band → 400; cross-band probe → 409); "largest covering file" selection when a part's range spans one of several uploaded PDFs.

**Gaps found in the 2026-09-23 audit (all fixed by → US-7.11):**
1. ⚠️ **No physical page split** — the endpoint streams the WHOLE score PDF; `pageFrom`/`pageTo` only decorate the filename. A musician downloading "strony 23–24" receives the entire book.
2. ⚠️ **Enumeration leak** — the URL embeds sequential numeric `bandId`/`compositionId`/`partId`; anyone with one link can iterate neighbours (`/bands/2/compositions/5/parts/1`…).
3. ⚠️ **Link is not actually public** — the route falls under `anyRequest().authenticated()`, so an external musician clicking the e-mailed link is bounced to Keycloak login; sharing only "works" for logged-in team members.

---

### **US-7.11: Public tokenized voice link + real PDF page split** ✅ shipped 2026-09-23 (branch `fix/composition-parts-table-truncated-render`)
> **As a** band librarian
> **I want** the shared voice link to be a public URL containing an opaque random token (UUIDv4) — and to serve ONLY the pages defined as that voice
> **So that** musicians get exactly their 2 pages without an account, and a leaked link cannot be used to enumerate other bands' scores

**Acceptance criteria:**

- [x] **AC1 — Physical PDF split.** (`PdfPageExtractor` — PDFBox 3.0.7 `PageExtractor`, NOTE: its API is 1-based INCLUSIVE, verified by probe; defensive null like PdfPageCounter). 7 unit tests incl. real round-trip page counts. New `PdfPageExtractor.extractPages(byte[] source, int from, int to)` in the application layer (PDFBox 3.x — already a dependency via US-2.2; mirrors `PdfPageCounter`'s defensive style: never throws on corrupt input, returns `null`). The share endpoint returns a NEW PDF containing exactly `pageFrom…pageTo` pages. Unit test: generate a 5-page PDF via `TestPdfBuilder`, extract 2–3, assert `pageCount == 2` and page order/content.
- [x] **AC2 — Token mapping.** Migration **V45__create_part_share_tokens.sql** (V44 was already taken by `add_composition_instrument_score_file`): `part_share_tokens(token UUID PRIMARY KEY, part_id BIGINT NOT NULL UNIQUE REFERENCES composition_instruments(id) ON DELETE CASCADE, created_at, created_by)`. Token generated app-side (`UUID.randomUUID()`, UUIDv4 — 122 bits of entropy, unguessable, sequential-ID-free). V44 also backfills one token per existing part row (`INSERT ... SELECT gen_random_uuid(), id FROM composition_instruments`). The token is STABLE per part — editing the page range does NOT rotate the link; the next GET simply serves the new range (range is resolved live through the part FK).
- [x] **AC3 — Public API.** `GET /public/parts/{token}` → 200 + split PDF (`inline` for PDF), 404 for unknown token, 410 when the part/composition was deleted (FK cascade removes the token → indistinguishable from 404, which is fine — fail closed, no existence oracle). Add the route to `SecurityConfig`'s `permitAll` list (the `/public/**` prefix is already public — putting the endpoint under it costs zero config churn). No authentication, no session cookie set on this path.
- [x] **AC4 — Deprecate the enumerable URL.** The share modal + e-mail body switch to `{app.base-url}/public/parts/{token}`; the UI never shows a `/bands/…/parts/…` URL again. DELETE the old authenticated streaming GET from `PartLinkRestController` (verified: no other consumer). Implemented as a 410-Gone tombstone on the old path so pre-migration e-mailed links get a clear message instead of a framework error. Keep the `POST .../share` e-mail endpoint authenticated as today.
- [x] **AC5 — Revocation (cheap, do it).** Modal "Zresetuj link" button → rotates the token (new UUID row, old one 404s from then on). Covers "I pasted the link in a wrong group chat". Column-wise this is just an UPDATE of `token` for the part.
- [x] **AC6 — Scope guard.** ZIP-backed parts (no single covering PDF, `NoCoveringFileException` path) keep returning 409 with the same message; token links inherit it.

**Design notes:**
- Token in path, not query string → doesn't leak into referrer headers or access logs as "just a parameter"; treat the full URL as a bearer credential and say so in the modal hint ("Każdy z tym linkiem widzi głos — nie udostępniaj dalej").
- Rate-limit / abuse: MVP — none beyond 404-on-guess (122-bit space makes online guessing impractical); revisit if access logs show probing.
- Filename stays `slug(title)-slug(role)_ss-from-to.pdf` (already RFC-6266-safe helpers in `PartLinkRestController`); reuse, don't rewrite.

**Tests (24 new, all green):** `PdfPageExtractorTest` (7 unit — real page-count round-trips through PDFBox); `PartShareTokenIT` (5 — lazy idempotent mint, PUBLIC READ SERVES A 2-PAGE SLICE OF A 5-PAGE SCORE asserted on the bytes, rotate revokes old, cascade on delete, 409 covering-file gate); `PublicPartLinkRestControllerTest` (4 web-slice — 200 inline + no-store + sliced filename, uniform 404 incl. non-UUID garbage segment, 409); `PartLinkRestControllerTest` (8 — token mint/rotate/share endpoints, band-isolation order, 410 tombstone, filename determinism); `CompositionDetailPartsUiTest` updated: modal link must match `/public/parts/<uuid>` regex and leak NO `/bands/`/`/compositions/` segment.

**Story points:** 5. **Dependencies:** US-2.2 (PDFBox), US-7.1 (part rows), US-7.10 (link semantics). **Blocks:** honest US-6.3 e-mail distribution (same token URL becomes the e-mail payload).

---

### US-7.4 / 7.5 / 7.6 / 7.7 / 7.8 (remainder) ⬜ Not started

Not planned in the original US list; listed here only to keep the "Epic 7" section honest about what has and hasn't landed:

- **US-7.4** — Bulk actions on the composition list (archive many at once, filter by status). No code; would extend `CompositionPageController#list` with a multi-select + batch POST through the existing `CompositionCommandService`.
- **US-7.5** — "Recent changes" feed scoped to a band. No code; reads `compositions.updated_at` + a future `score_files` / `composition_instruments` change-table to build one combined timeline.
- **US-7.6** — Keyboard-first editing for the parts panel (Tab to next instrument, arrow keys to page range). No code; pure front-end enhancement on top of US-7.1's `compositions/detail.html`.
- **US-7.7** — Print view for a composition + its setlist (A4-oriented, no nav or badges). No code; a new route `/bands/{bandId}/compositions/{id}/print` that renders with the existing status-badges included but without the action buttons.
- **US-7.8** — Search across titles + composers + arrangers with diacritic-insensitive matching. No code; `CompositionRepository.search(bandId, term)` (US-1.1) already exists in the repository and takes a raw `term` string — the open piece is normalisation in the service layer (`String.normalize(NFD)` + strip combining marks) plus an index on `lower(title)`/`lower(composer)`/`lower(arranger)`. The migration would be V44+ and the service method would be added to `CompositionQueryService.search(...)` (which currently delegates straight to the repo, so this is genuinely new work, already in place but unpolished).

---

### Epic 7 status (post-audit)

| Story | Status | Notes |
|-------|--------|-------|
| US-7.0 nav entry | ✅ done | "Utwory" in top-nav + hamburger, layout-level change, integration-owned |
| US-7.1 parts panel | ✅ done | Manual page→instrument mapping from the detail page; `addPart` command service method; Selenium test pins happy + 2 failure paths |
| US-7.3 role-map admin UI | ✅ merged (PR #250, 2026-09-28) | Persistence ready (US-1.4); page + thin command/query services + menu link implemented; tests green |
| US-7.9 upload + header preview | ✅ on `main` (verified @ `5c337f0`) | Upload UI for the existing US-2.1 endpoint + server-side PDFBox thumbnail endpoint (`ScoreFileThumbRestController`); inline header preview in `compositions/detail.html` |
| US-7.10 shareable voice link | ✅ shipped 2026-09-23 | Streams the FULL pdf under an enumerable `/bands/…/parts/N` URL that still requires login — see gaps + replacement below |
| US-7.11 public tokenized link + real page split | ✅ shipped 2026-09-23 | UUIDv4 link at /public/parts/{token}, unauthenticated, PDFBox PageExtractor slice; old URL = 410 tombstone; rotate button in modal |
| US-7.14 map voices onto an explicit score file | ✅ shipped via PR #211 (commit `13dc2b6c`, 2026-09-21) | `composition_instruments.score_file_id` (V44) + `PartSource`/`confidence_score`; picker in the add-part modal; parts panel shows the bound file; `PartLinkRestController` resolves links through the bound file when present |
| US-7.15 replace score file in place | ✅ merged (PR #259, commit `f5e892f7`, 2026-10-04) | New file is uploaded, rows are re-pointed, old file retired atomically — voice mappings and share tokens survive the swap; replaces the old delete-and-re-upload workaround |
| US-7.16 verification gate in the UI (DRAFT → READY) | ✅ merged (PR #261, commit `c43485af`, 2026-10-04) | `POST /bands/{b}/compositions/{id}/verify` stamps the US-3.03 audit pair with the logged-in identity and promotes to READY; "Zweryfikuj głosy" button in the parts panel behind the shared confirm-dialog; zero-parts guard fails closed in the controller (the service promotes vacuously by design) — closes the last gap that made Epic 6 unreachable from the app |
| US-7.4 – 7.8 | ⬜ not started | No code; see per-story notes above |

---

## 📌 Open work, in priority order (team decision needed)

0. ~~US-7.11~~ ✅ shipped 2026-09-23 (V45 tokens, `PdfPageExtractor`, public endpoint, modal + e-mail switched). Note for the next Epic 6 work: `PartShareByEmailCommandService` now embeds the token URL — US-6.3 bulk distribution should reuse `PartShareTokenCommandService.tokenFor` rather than minting its own scheme.
1. ~~US-7.9 — PDF upload button + header preview on detail page~~ ✅ present on `main` (verified against HEAD `5c337f0`, 2026-09-27): the US-2.1 upload endpoint was already there; `ScoreFileThumbRestController` + `ScoreFileThumbQueryService` (PDFBox page thumbnails, LRU-cached) and the upload + inline header/preview panels in `compositions/detail.html` are merged. MVP complete — an optional mobile-only visual polish pass may still be done ad-hoc, but it is no longer tracked here.
2. ~~US-7.3 — InstrumentRoleMap admin UI~~ ✅ merged (PR #250, 2026-09-28). Band-scoped page at `/bands/{bandId}/instrument-roles` with thin command/query services over the US-1.4 port; full test coverage (unit + IT + Selenium) and green `clean verify`. This unblocked the hand-written role-map fixtures used across Epic 6/7 tests.
3. ~~US-5.1 — alias resolution read path~~ ✅ in PR #251 (2026-09-28). US-1.2's `aliasOf` hierarchy is now walked by a read model: `InstrumentRoleResolutionQueryService` resolves a member tag to its alias-family role set ("Kornet" ⇒ {Trąbka 1, Trąbka 2, Kornet 1}), returning provenance-tagged + name-list projections for consumers. No cache yet (US-5.2) and no UI consumption (US-5.3).
4. ~~US-6.2 — generate the concrete per-member part list~~ ✅ merged (PR #252, 2026-09-28): `EventCompositionPartsQueryService` + `EventPartDistributionDto` + "Rozdanie głosów" panel in `events/detail.html`; consumes US-5.1's `resolveRoles`. Epic 6 then closed in order — **US-6.3 delivery (PR #254), US-6.4 batch ordering (PR #255), US-6.5 stale-voice demotion (PR #256), US-6.6 delivery audit trail (PR #257), all merged by 2026-10-04** — and **US-7.16 (verification gate UI, PR #261)** was pulled forward ahead of the remaining Epic-7 polish because without it *no real composition could ever reach READY*, making the whole freshly-built delivery loop unreachable from the app.
5. **US-4.4 + US-4.5 — AI preview render + accept.** The runner seam (US-4.1) and the detail-page entry point (US-4.3) are done; what's missing is turning `arrangement_json_path` into an editable table and then writing the accepted rows into `composition_instruments` with their `PartSource.AI`/`.HYBRID` stamp. This story unblocks US-3.03 for teams who *do* want to use AI (US-7.1 + US-7.16 are the no-AI path and now work end-to-end). **Gating reality:** the current runner is a stub returning 501 — build this when `windband-ai` actually produces `arrangement_json`, otherwise the preview table is only ever tested against a mock.
6. **US-7.2b — remove-from-setlist button (small).** The endpoint `DELETE /events/{id}/compositions/{cid}` is correct and now regression-tested (binding 500 fixed in PR #260, `EventCompositionUiTest` re-enabled and driving it via same-origin fetch), **but there is no UI affordance** — an operator cannot drop a composition from an event's setlist from the page today. Small story: per-row "🗑 Usuń z programu" in `events/detail.html` behind the shared confirm-dialog, calling the existing endpoint (JS fetch + panel reload — HTML forms cannot issue DELETE).
7. **US-5.2 — cache / invalidation seam.** Now that US-5.1's resolution service exists, layer a per-band in-memory cache keyed by `(bandId, sourceTag)` with invalidation hooks on alias/tag writes (see the US-5.2 "Design call"). Only worth doing once US-6.2 lands and distribution clicks make the un-cached read hot.
8. **US-4.6 / US-4.7 (re-run / cancel, ZIP-parent analysis).** Lower priority — only matters once an external-runner failure rate makes retries common, or once a band uploads multi-PDF ZIPs and expects them to be analysable per-entry.

---

## 🧾 Audit trail (for future readers)

- **Epic 1** closed 2026-09-15/16 (PR #195–#200).
- **Epic 2** closed 2026-09-16/17 (PR #200 US-2.1/US-2.2; PR #201 US-2.3; PR #202 US-2.4/US-2.5).
- **Epic 3** closed 2026-09-17/18 (PR #206 US-3.4; PR #207 US-3.4 review fixes + US-3.5; US-3.1/US-3.2 shipped in the same controller/template surface and are considered part of the Epic 3 closure).
- **Epic 4** in progress: US-4.1 (`c4088d0`), US-4.3 (`5b10a7f`) landed 2026-09-18 on `main`; US-4.2–4.7 open.
- **Epic 5** partial: **US-5.1 resolution algorithm (tag → role via alias family) in PR #251 (commit `987f3a9`, branch `feat/us-5.1-role-resolution`, 2026-09-28)** — `InstrumentRoleResolutionQueryService` + `ResolvedInstrumentRole` DTO, 10 unit + 9 integration tests, `clean verify` green (639 tests). US-5.2 (cache/invalidation) and US-5.3 (distribution consumption) open.
- **Epic 6** ✅ CLOSED: event-setlist link merged 2026-09-18 (`9c7bbbd`); US-6.1/6.2 part-list generation (PR #252, `0e9bfb4`); **US-6.3 delivery PR #254, US-6.4 batch ordering PR #255, US-6.5 stale-voice demotion PR #256, US-6.6 delivery audit trail PR #257 — all merged, green `clean verify` each.** The "Skład repertuaru" binding 500 reported from production on 2026-10-04 (`POST /events/{id}/compositions` → missing URI variable) was fixed the same day (PR #260): `assignComposition`/`unassignComposition` declared `@PathVariable Long eventId` against `{id}` templates — `MissingPathVariableException` before any service ran; the previously `@Disabled` `EventCompositionUiTest` was re-enabled as the regression net (dialog happy path, remove route, cross-band 409).
- **Epic 7** partial: US-7.0 nav (`020ce81`) + US-7.1 parts panel (`8aeb95e`, fixes `1aa6216` + `34b8c83`) landed 2026-09-18; US-7.9 upload + header preview verified on `main` @ `5c337f0`; US-7.10/7.11 public token links shipped 2026-09-23; US-7.3 role-map admin UI merged (PR #250); **US-7.14 voice→file binding shipped via PR #211 (`13dc2b6c`, 2026-09-21) — had never been recorded here**; **US-7.15 replace-score-file-in-place merged (PR #259, `f5e892f7`, 2026-10-04)**; **US-7.16 verification gate UI merged (PR #261, `c43485af`, 2026-10-04)** — DRAFT → READY finally clickable, zero-parts guard fails closed. US-7.4–7.8 + setlist-remove button (US-7.2b) remain open.

*Last audited against HEAD on this branch: commit `1dc9159` (2026-09-18), PR numbers verified through #207.*
*Re-checked against `origin/main` HEAD `5c337f0` on 2026-09-27, PR numbers verified through #250: US-7.3 now in review (PR #250); US-7.9 confirmed present on main.*
*Re-checked against `origin/main` HEAD `fb22c38` on 2026-09-29: US-7.3 merged (PR #250), US-5.1 merged (PR #251), **US-6.2 merged (PR #252)**; PR #253 (voice editing, issue #241) also merged. US-6.3 (delivery) is the next story.*
*Re-checked against `origin/main` HEAD `6ac1c3ac` on 2026-10-04: Epic 6 fully closed (PRs #254–#257); US-7.14 (via PR #211, never recorded here before), US-7.15 (PR #259) and US-7.16 verification gate (PR #261) on `main`; production setlist-binding 500 hotfixed (PR #260) and `EventCompositionUiTest` re-enabled as its regression net. Next in line: US-7.2b (remove-from-setlist button) → US-5.3 → US-4.4/4.5 gated on a real `windband-ai` runner.*