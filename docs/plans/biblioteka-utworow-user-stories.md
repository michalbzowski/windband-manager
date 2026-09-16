# 📚 Biblioteka Utworów (Compositions Library) — User Stories

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
| **Epic 2: File Upload & Storage** | 2.1 – 2.5 | Secure upload, storage strategy, validation | ⬜ Not started |
| **Epic 3: Composition CRUD (Manual)** | 3.1 – 3.5 | Create/read/update/delete without AI | ⬜ Not started |
| **Epic 4: AI-Assisted Score Analysis** | 4.1 – 4.7 | PDF/ZIP analysis, preview, verification | ⬜ Not started |
| **Epic 5: Instrument Alias Mapping** | 5.1 – 5.3 | Tag-to-role resolution for distribution | ⬜ Not started |
| **Epic 6: Event Integration & Distribution** | 6.1 – 6.6 | Assign to event, generate parts, send | ⬜ Not started |
| **Epic 7: UI & UX** | 7.1 – 7.8 | Thymeleaf templates, HTMX interactions | ⬜ Not started |

> **Status legend:** ✅ done · 🔶 partial / open items listed below · ⬜ not started · ❌ deliberately deferred

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

**Open items:** none — story is complete.

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
- [x] Migrations: **`V36__create_composition_instrument.sql`** (disabled no-op, comment-only placeholder) and **`V38__create_composition_instruments.sql`** (the corrected, idempotent DDL — see pitfall note below). Table in DB: `composition_instruments`.
- [x] Cascade delete when composition removed — via JPA (`CascadeType.ALL` + `orphanRemoval`) on `Composition.parts` **and** DB-level `ON DELETE CASCADE` from `composition_instruments.composition_id → compositions.id`. Both paths apply.

**Open items:** none — story is complete.

> **Pitfall for future readers:** the first attempt to ship this entity as `V36__create_composition_instrument.sql` used a PostgreSQL-invalid `CHECK (col1 >= 1, col2 >= 1)` syntax (comma-separated predicates inside one CHECK). Flyway stamped v36=FAILED with a stale checksum, so the corrected DDL had to be re-homed in **V38**, and V36 was preserved as a comment-only file. Do not edit V36's body or rename V38 — both are load-bearing for shared (Railway/DB) Flyway history.

**Story Points:** 3
**Dependencies:** US-1.1, US-1.2

---

### **US-1.4: InstrumentRoleMap (Tag-to-Role Mapping)** ✅ (persistence) / ⬜ (admin UI → Epic 7)
> **As a** band manager
> **I want** configurable mapping from member instrument tags to composition roles
> **So that** "Trąbka" tag automatically matches "Trąbka 1", "Trąbka 2", "Kornet 1"

**Acceptance Criteria:**
- [x] Entity `InstrumentRoleMap` in `domain/composition/` with: `id`, `band` (ManyToOne → `Band` entity, not a raw `band_id` column), `sourceTag`, `targetRolePattern`, `description`, `createdAt`, `updatedAt`. Factory `InstrumentRoleMap.forBand(...)`.
- [x] Repository with `findByBandIdAndSourceTag(Long, String)` and `findByBandIdAndSourceTagAndTargetRolePattern(...)` in the domain port `InstrumentRoleMapRepository` (adapter: `SpringDataInstrumentRoleMapRepository`).
- [x] Migration: actual file is **`V39__create_instrument_role_map.sql`** (doc's suggested `V21` already taken). Idempotent DDL with unique index `(band_id, lower(source_tag), target_role_pattern)`, covering index on `(band_id, lower(source_tag))`.
- [x] **Seed mappings for the "default" band (id=1)** in the migration: Trąbka→Trąbka 1 & 2, Flet→Flet 1, Waltornia→Waltornia 1, Puzon→Puzon 1, Saksofon→Saksofon 1. Idempotent under repeated replay via `INSERT ... WHERE NOT EXISTS`.
- [ ] **Admin UI for managing mappings** — ⬜ NOT YET. Belongs deliberately in **Epic 7 (UI)** per original spec "later story". The persistence layer is ready for it.

**Open items:**
- ⬜ Admin UI for managing `InstrumentRoleMap` — tracked as part of **Epic 7**, not Epic 1.

**Story Points:** 2
**Dependencies:** US-1.3

---

### **US-1.5: ScoreFile Entity (Stored Files Metadata)** ✅ (persistence) / ⬜ (upload path → Epic 2)
> **As a** system
> **I want** track uploaded score files with storage metadata
> **So that** files are retrievable, secure, and auditable

> Naming note: the entity is named **`ScoreFile`** (not `CompositionFile` as originally sketched). Table in DB: **`score_files`**. Deliberate rename — "score file" is clearer than "composition file" (a composition can hold several score files over time).

**Acceptance Criteria:**
- [x] Entity `ScoreFile` in `domain/composition/` with: `id`, `composition` (ManyToOne, not null), `mimeType`, `sizeBytes`, **`sha256`** (integrity + dedup hook — stronger than the original spec's plain metadata), `storagePath` (absolute path, nullable until Epic 2 populates it), `originalName` (display-only, nullable), `createdAt`.
  - ⚠️ Field deltas vs. original spec: adds `sha256`; drops `fileType` enum (MIME type is the discriminator); drops `pageCount` (owned by Epic 2's `PdfPageCountService`, not the persistence row); drops `uploadedBy`/`uploadedAt` in favour of a single immutable `createdAt` (authed-uploader audit columns can be added in Epic 2 when needed).
- [x] Repository with band-scoped queries: `ScoreFileRepository` port + Spring Data adapter.
- [x] Migration: actual file is **`V35__create_scorefile.sql`** (doc's suggested `V22` already taken).
- [ ] **Storage path convention `/mnt/sda1/media/windband-scores/{bandId}/compositions/{compositionId}/{uuid}_{originalName}.ext`** — ⬜ not implemented yet. Belongs to **Epic 2 (US-2.1 upload adapter)** as the write-path concern. The `storagePath` column exists and is ready to receive it; the path-computation + byte-write logic lives in Epic 2.
- [x] Test: `ScoreFileIT`

**Open items:**
- ⬜ Upload / storage-path materialisation, file-size validation, SHA-256 computation — belongs to **Epic 2**, not Epic 1. Persistence contract is complete.

**Story Points:** 2
**Dependencies:** US-1.1

---

### **US-1.6: Composition Command & Query Services** ✅ (core CQRS) / ⬜ (READY-gate orchestrator → Epic 3)
> **As a** developer
> **I want** `CompositionCommandService` and `CompositionQueryService` in `application/command/composition/` and `application/query/composition/`
> **So that** use cases are encapsulated following CQRS pattern

**Acceptance Criteria:**
- [x] `CompositionCommandService` (in `pl.michalbzowski.windband.application.command.composition`) with: `create(cmd, bandId)`, `update(id, cmd, bandId)`, `archive(id, bandId)`, `restore(id, bandId)`, `deleteComposition(id, bandId)`.
  - ⚠️ **`verifyCompositionParts(id, bandId, currentUser)` is NOT in the command service.** The per-part audit is already implemented at the entity level (`CompositionInstrument.verify(String userIdentifier, Instant at)` — idempotent; freezes on first call). The service-level orchestrator that flips a composition to READY once all parts are verified is an **Epic 3 concern (US-3.03 gate)** — per the in-code comment "`Only legal once the part map is verified (US-3.03 gate lives in the command service)`" it lands when Epic 3 wires the "mark ready" action.
- [x] `CompositionQueryService` (in `pl.michalbzowski.windband.application.query.composition`) with: `get(id, bandId)`, `listByBand(bandId, statusFilter)` + paginated overload, `search(bandId, term)`, `getCompositionWithParts(id, bandId)`. ⚠️ naming: shorter verb-first form (not `getAllCompositions` / `searchCompositions`).
- [x] Command DTOs: `CreateCompositionCommand`, `UpdateCompositionCommand` in `application/command/composition/`.
- [x] Query DTOs: `CompositionDto`, `CompositionWithPartsDto`, `CompositionInstrumentDto` in `application/dto/composition/`.
- [x] Band isolation enforced via `BandQueryService.getRequiredBand(bandId)` on both sides.
- [x] Unit tests with Mockito: `CompositionCommandServiceTest`; integration-style: `CompositionQueryServiceIT`; page-level UI hook: `CompositionPageUiTest`.

**Open items:**
- ⬜ `verifyCompositionParts(id, bandId, currentUser)` service method (READY-gate orchestrator) — **deferred to Epic 3 by design**; per-part `verify(...)` already in the entity, so Epic 3 only needs the thin service glue.

**Story Points:** 5
**Dependencies:** US-1.1, US-1.3, US-1.5

---

### Epic 1 status summary (post-audit)

| Story | Status | Notes |
|-------|--------|-------|
| US-1.1 Composition + repository | ✅ done | Migration V32; all tests green |
| US-1.2 Instrument `aliasOf` | ✅ done | Migration V37; alias validation tests; write path in command service |
| US-1.3 CompositionInstrument | ✅ done | Migrations V36 (disabled) + V38 (corrected); cascade via JPA and DB FK |
| US-1.4 InstrumentRoleMap | 🔶 persistence done, UI open | Migration V39 with seed maps; admin UI → Epic 7 |
| US-1.5 ScoreFile | 🔶 persistence done, upload path open | Entity `ScoreFile`, migration V35; upload / path materialisation → Epic 2 |
| US-1.6 Command + Query services | 🔶 core CQRS done, READY-gate orchestrator open | `verifyCompositionParts` glue → Epic 3 (per-part entity `verify()` already in place) |

**Migration numbering reality (vs. the original plan):** the doc suggested V18/V19/V20/V21/V22 — all were already occupied by unrelated features (system_admin, member groups, event invitations, event participation-instrument). The score-library migrations actually landed as **V32** (compositions), **V35** (score_files), **V36** (composition_instruments placeholder), **V37** (alias_of), **V38** (composition_instruments fixed DDL), **V39** (instrument_role_map). Future migrations for Epic 2+ should use **V40 and up**.

---

