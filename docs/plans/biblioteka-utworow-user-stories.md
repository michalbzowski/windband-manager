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
| **Epic 5: Instrument Alias Mapping** | 5.1 – 5.3 | Tag-to-role resolution for distribution | ⬜ Not started (domain layer US-1.2 ready) |
| **Epic 6: Event Integration & Distribution** | 6.1 – 6.6 | Assign to event, generate parts, send | 🔶 US-7.2/6.1 event-setlist link done · generation + sending not started |
| **Epic 7: UI & UX** | 7.0 – 7.9 | Thymeleaf templates, HTMX interactions | 🔶 nav (7.0) + parts panel (7.1) done · US-7.9 upload/preview next · role-map admin UI (7.3) and the rest open |

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
- ⬜ **Epic 5 (US-5.1–5.3)**: consuming the `aliasOf` hierarchy to resolve member instrument tags → composition roles ("Kornet" tag matches "Kornet 1", which is an alias of "Trąbka"). Persistence and domain mutator are ready; no resolution service or UI exists yet.

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

## ⬜ Epic 5: Instrument Alias Mapping — Not started

> **Status note:** the *persistence* layer (US-1.2 — `aliasOf` self-reference on `Instrument`, guards against self/cross-band aliasing, root-target only) and the *domain unit test* are in place. What's missing is the **resolution service** that walks the alias chain to answer "which composition role does this member's `Kornet` tag map to", and its UI.

### **US-5.1: Resolution algorithm (tag → role via alias chain)** ⬜
> **As a** band manager or librarian
> **I want a function that, given a member's instrument tag and my team's `InstrumentRoleMap` rows, returns the matching composition role(s)
> **So that** "Kornet" (alias of "Trąbka") can legitimately match "Trąbka 1", "Trąbka 2", **and** "Kornet 1"

Proposed design (not yet implemented): read via `InstrumentRoleMapRepository.findByBandIdAndSourceTag(bandId, sourceTag)` (US-1.4), then resolve each `targetRolePattern` against the instrument's `aliasOf` chain from US-1.2 (max depth guarded to prevent cycles — already enforced by the write path, but the read must still terminate). The result feeds US-5.3's distribution list.

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

## 🔶 Epic 6: Event Integration & Distribution — 🔶 PARTIAL (setlist link done; generation + sending not started)

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

### **US-6.2: Generate the concrete part list for an event × member** ⬜ Not started
> **As a** band manager
> **I want, for a given READY composition and a chosen member, the exact page range (or extracted ZIP entry) they should play
> **So that** I can print / share it to them individually

Not yet implemented. Inputs are all present: US-7.2's ordered setlist rows, US-7.1 / US-4.5 part rows (with `pageFrom`/`pageTo`, `fileRef`, `PartSource`), US-2.3's per-entry `score_files` rows for ZIP uploads, and US-5.1's alias resolution (member tag → role). Output is a new read model (not yet designed) — probably a `List<EventPartDto>` assembled by a `application/query/event/EventCompositionPartsQueryService` that does not yet exist.

---

### **US-6.3: Deliver parts to musicians (e-mail or app)** ⬜ Not started
> **As a** band manager
> **I want one click to "send this event's parts" and every member gets their page range / file via the channel they prefer
> **So that** I don't hand-copy each PDF

Not yet implemented. The infrastructure to lean on already exists in the rest of this repo: `EmailChannel` / `SendGridApiChannel` + `SendGridEmailSender` (adapter-out), `NotificationSender` port, and `ConsentService` (the member's `email_consent` column from V25 is a hard gate — US-6.3 must refuse silently for non-consenting members rather than failing loudly mid-send). The distribution policy (who gets which part, in what order, with what subject line) belongs here and only here; the sender adapters are already generic and reused elsewhere (event invitations, welcome emails).

---

### **US-6.4 / US-6.5 / US-6.6** ⬜ (not started — not planned in detail by this audit)
- **US-6.4:** "Send *all* parts for the event, in concert order" (batch version of US-6.3 over the full setlist built in US-7.2's `orderInSet` walk).
- **US-6.5:** "Regenerate on fly when a composer edits part ranges" (invalidation hook on US-7.1 / US-4.5 writes — see the same invalidation seam as US-5.2 but at the *composition* level instead of the *instrument* level).
- **US-6.6:** "Audit trail per delivery" (who got which part, when, via which channel; joins into a new `event_part_delivery` table — future migration V44+).

---

## 🔶 Epic 7: UI & UX — 🔶 PARTIAL (navigation + parts panel done; US-7.9 upload/preview in progress; role-map admin UI and remaining stories open)

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

### **US-7.3 (was Epic 1.4 open item): Admin UI for InstrumentRoleMap** ⬜ Not started
> **As a** band manager
> **I want to add / remove rows in `instrument_role_map` from the app, not from SQL
> **So that** a new member instrument tag ("Klarinet B"→"Saksofon 1") can be wired in seconds

Not yet implemented. The persistence layer is ready (US-1.4: entity, repository, V39 migration with seed rows for band id=1). What's needed is a small admin page under `/bands/{bandId}/admin/instrument-roles` (or similar), a thin command service using the existing `InstrumentRoleMapRepository`, and a read path that renders the current list per band. No code in `application/command/composition/` or `application/query/composition/` exists yet for this surface.

---

### **US-7.9: PDF score file upload button + header preview panel** ⬜ Not started (added 2026-09-19 per team request)

> **As a band manager or librarian**
> **I want to upload a PDF score file from the composition detail page and see it rendered as an inline header preview (page thumbnails / first lines of each page)**
> **So that I can visually confirm which instrument parts start on which pages — without opening the full PDF in a separate tab**

**Storage:** The REST endpoint (`POST /bands/{bandId}/compositions/{compositionId}/files`, US-2.1 / `ScoreFileUploadRestController`) and persistence layer are already in place. **This story is purely UI + optional preview render path** — no new domain entity or migration required for MVP.

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

### **US-7.11: Public tokenized voice link + real PDF page split** ⬜ Not started (added 2026-09-23 per team request)
> **As a** band librarian
> **I want** the shared voice link to be a public URL containing an opaque random token (UUIDv4) — and to serve ONLY the pages defined as that voice
> **So that** musicians get exactly their 2 pages without an account, and a leaked link cannot be used to enumerate other bands' scores

**Acceptance criteria:**

- [ ] **AC1 — Physical PDF split.** New `PdfPageExtractor.extractPages(byte[] source, int from, int to)` in the application layer (PDFBox 3.x — already a dependency via US-2.2; mirrors `PdfPageCounter`'s defensive style: never throws on corrupt input, returns `null`). The share endpoint returns a NEW PDF containing exactly `pageFrom…pageTo` pages. Unit test: generate a 5-page PDF via `TestPdfBuilder`, extract 2–3, assert `pageCount == 2` and page order/content.
- [ ] **AC2 — Token mapping.** Migration **V44__create_part_share_tokens.sql**: `part_share_tokens(token UUID PRIMARY KEY, part_id BIGINT NOT NULL UNIQUE REFERENCES composition_instruments(id) ON DELETE CASCADE, created_at, created_by)`. Token generated app-side (`UUID.randomUUID()`, UUIDv4 — 122 bits of entropy, unguessable, sequential-ID-free). V44 also backfills one token per existing part row (`INSERT ... SELECT gen_random_uuid(), id FROM composition_instruments`). The token is STABLE per part — editing the page range does NOT rotate the link; the next GET simply serves the new range (range is resolved live through the part FK).
- [ ] **AC3 — Public API.** `GET /public/parts/{token}` → 200 + split PDF (`inline` for PDF), 404 for unknown token, 410 when the part/composition was deleted (FK cascade removes the token → indistinguishable from 404, which is fine — fail closed, no existence oracle). Add the route to `SecurityConfig`'s `permitAll` list (the `/public/**` prefix is already public — putting the endpoint under it costs zero config churn). No authentication, no session cookie set on this path.
- [ ] **AC4 — Deprecate the enumerable URL.** The share modal + e-mail body switch to `{app.base-url}/public/parts/{token}`; the UI never shows a `/bands/…/parts/…` URL again. Delete the old authenticated GET from `PartLinkRestController` (it has no other consumer — the "Otwórz" button points at the same link; verify with a repo-wide grep before removal). Keep the `POST .../share` e-mail endpoint authenticated as today.
- [ ] **AC5 — Revocation (cheap, do it).** Modal "Zresetuj link" button → rotates the token (new UUID row, old one 404s from then on). Covers "I pasted the link in a wrong group chat". Column-wise this is just an UPDATE of `token` for the part.
- [ ] **AC6 — Scope guard.** ZIP-backed parts (no single covering PDF, `NoCoveringFileException` path) keep returning 409 with the same message; token links inherit it.

**Design notes:**
- Token in path, not query string → doesn't leak into referrer headers or access logs as "just a parameter"; treat the full URL as a bearer credential and say so in the modal hint ("Każdy z tym linkiem widzi głos — nie udostępniaj dalej").
- Rate-limit / abuse: MVP — none beyond 404-on-guess (122-bit space makes online guessing impractical); revisit if access logs show probing.
- Filename stays `slug(title)-slug(role)_ss-from-to.pdf` (already RFC-6266-safe helpers in `PartLinkRestController`); reuse, don't rewrite.

**Tests:** `PdfPageExtractorTest` (unit, TestPdfBuilder); `PartShareTokenIT` (token issued on part create; backfilled for pre-existing; cascade on part delete → 404); `PublicPartLinkRestControllerTest` (web-slice: 200 + PDF magic bytes without auth; 404 unknown/garbage token; split contains exactly N pages); update `CompositionDetailPartsUiTest` to assert the copied link matches `/public/parts/<uuid>` and contains NO `/bands/` segment.

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
| US-7.3 role-map admin UI | ⬜ not started | Persistence ready (US-1.4); page + service not written |
| US-7.9 upload + header preview | ⬜ not started (added 2026-09-19) | Upload UI for the existing US-2.1 endpoint; server-side PDFBox thumbnail endpoint (pages 1–3); mobile pass |
| US-7.10 shareable voice link | ✅ shipped 2026-09-23 | Streams the FULL pdf under an enumerable `/bands/…/parts/N` URL that still requires login — see gaps + replacement below |
| US-7.11 public tokenized link + real page split | ⬜ not started (added 2026-09-23) | UUIDv4 link, public unauthenticated GET, PDFBox page extraction — supersedes US-7.10's URL scheme |
| US-7.4 – 7.8 | ⬜ not started | No code; see per-story notes above |

---

## 📌 Open work, in priority order (team decision needed)

0. **US-7.11 — Public tokenized voice link + real PDF page split.** Team decision 2026-09-23: the current share link leaks the whole book under an enumerable, login-required URL. Split the PDF to exactly the voice's page range and serve it at `/public/parts/{uuid}` (unauthenticated). Prerequisite for any real US-6.3 e-mail distribution.
1. **US-7.9 — PDF upload button + header preview on detail page.** The upload REST endpoint (US-2.1) and `pageCount` (US-2.2) both already exist; what's missing is the UI for uploading from the detail page + a lightweight per-page preview. Highest immediate user pain point per team request (2026-09-19). No new migration needed for MVP; one new server-side thumbnail endpoint (PDFBox `PDFRenderer.createImageAtIndex`) + two template sections + mobile pass. Story points: 7. **This is next in line.**
2. **US-7.3 — InstrumentRoleMap admin UI.** Persistence has been sitting idle since US-1.4 (PR #199 merged); every other story that consumes it is blocked on manual SQL or hand-written test fixtures. Highest ROI relative to effort, fully in the "UI + thin service" pattern already proven by US-3.x and US-7.1.
3. **US-5.1 — alias resolution read path.** US-1.2's `aliasOf` hierarchy is written but never walked by any read model yet. Once this lands, US-7.1's parts panel can auto-suggest roles from a member's tag (instead of requiring the manager to type "Trąbka 1"), and US-6.2's distribution list gets its primary input.
4. **US-4.4 + US-4.5 — AI preview render + accept.** The runner seam (US-4.1) and the detail-page entry point (US-4.3) are done; what's missing is turning `arrangement_json_path` into an editable table and then writing the accepted rows into `composition_instruments` with their `PartSource.AI`/`.HYBRID` stamp. This story unblocks US-3.03 for teams who *do* want to use AI (US-7.1 is the no-AI alternative and already works).
5. **US-6.2 / US-6.3 — distribution & delivery.** Epic 6's *core* value (getting pages to musicians) hasn't shipped yet; US-7.2 (setlist link) is the prerequisite and it's done, so 6.2 + 6.3 are the natural next pair.
6. **US-4.6 / US-4.7 (re-run / cancel, ZIP-parent analysis).** Lower priority — only matters once an external-runner failure rate makes retries common, or once a band uploads multi-PDF ZIPs and expects them to be analysable per-entry.

---

## 🧾 Audit trail (for future readers)

- **Epic 1** closed 2026-09-15/16 (PR #195–#200).
- **Epic 2** closed 2026-09-16/17 (PR #200 US-2.1/US-2.2; PR #201 US-2.3; PR #202 US-2.4/US-2.5).
- **Epic 3** closed 2026-09-17/18 (PR #206 US-3.4; PR #207 US-3.4 review fixes + US-3.5; US-3.1/US-3.2 shipped in the same controller/template surface and are considered part of the Epic 3 closure).
- **Epic 4** in progress: US-4.1 (`c4088d0`), US-4.3 (`5b10a7f`) landed 2026-09-18 on `main`; US-4.2–4.7 open.
- **Epic 6 (≈7.2)** event-setlist link merged 2026-09-18 (`9c7bbbd`); US-6.2/6.3/6.4/6.5/6.6 open.
- **Epic 7** partial: US-7.0 nav (`020ce81`) + US-7.1 parts panel (`8aeb95e`, fixes `1aa6216` + `34b8c83`) landed 2026-09-18; US-7.3–7.8 open.

*Last audited against HEAD on this branch: commit `1dc9159` (2026-09-18), PR numbers verified through #207.*