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

| Epic | Stories | Focus |
|------|---------|-------|
| **Epic 1: Domain Model & Persistence** | 1.1 – 1.6 | Core entities, repositories, migrations |
| **Epic 2: File Upload & Storage** | 2.1 – 2.5 | Secure upload, storage strategy, validation |
| **Epic 3: Composition CRUD (Manual)** | 3.1 – 3.5 | Create/read/update/delete without AI |
| **Epic 4: AI-Assisted Score Analysis** | 4.1 – 4.7 | PDF/ZIP analysis, preview, verification |
| **Epic 5: Instrument Alias Mapping** | 5.1 – 5.3 | Tag-to-role resolution for distribution |
| **Epic 6: Event Integration & Distribution** | 6.1 – 6.6 | Assign to event, generate parts, send |
| **Epic 7: UI & UX** | 7.1 – 7.8 | Thymeleaf templates, HTMX interactions |

---

## 🟢 Epic 1: Domain Model & Persistence (Foundation)

### **US-1.1: Composition Entity & Repository**
> **As a** system  
> **I want** a `Composition` aggregate root with band-scoped persistence  
> **So that** compositions are isolated per team and support full CRUD

**Acceptance Criteria:**
- [ ] `Composition` entity in `domain/composition/` with fields: `id`, `title`, `description`, `composer`, `arranger`, `band` (ManyToOne, not null), `status` (DRAFT/READY/ARCHIVED), `createdAt`, `updatedAt`
- [ ] `CompositionRepository` interface in `domain/composition/` with methods: `save`, `findById`, `findAllByBandId`, `findByTitleContainingIgnoreCaseAndBandId`, `delete`
- [ ] Spring Data adapter in `adapter/out/persistence/composition/`
- [ ] Flyway migration `V18__create_composition_table.sql`
- [ ] Unit tests for repository adapter

**Story Points:** 3  
**Dependencies:** None

---

### **US-1.2: Instrument Entity Enhancement**
> **As a** developer  
> **I want** the existing `Instrument` entity extended with `aliasOf` self-reference  
> **So that** instrument hierarchies (e.g., Kornet → Trąbka) support alias resolution

**Acceptance Criteria:**
- [ ] Add nullable `aliasOf` (ManyToOne self-ref) to `Instrument`
- [ ] Add `band_id` NOT NULL enforcement via migration (per multi-tenant isolation pattern)
- [ ] Update `InstrumentRepository` with `findByAliasOf` / `findRootInstrumentsByBandId`
- [ ] Migration `V19__add_alias_of_to_instrument.sql` with backfill strategy
- [ ] Update existing UI/tests for new field

**Story Points:** 2  
**Dependencies:** US-1.1

---

### **US-1.3: CompositionInstrument Link Entity**
> **As a** system  
> **I want** a `CompositionInstrument` entity linking compositions to instruments with page/file mapping  
> **So that** each part knows its source pages (PDF) or files (ZIP)

**Acceptance Criteria:**
- [ ] Entity in `domain/composition/` with: `id`, `composition` (ManyToOne), `instrument` (ManyToOne), `instrumentRole` (e.g., "Flet 1"), `pageFrom`, `pageTo` (for PDF), `fileRef` (for ZIP), `source` (AI/MANUAL/HYBRID), `confidenceScore` (0.0–1.0), `verifiedBy`, `verifiedAt`, `band_id` (denormalized)
- [ ] Unique constraint: `(composition_id, instrumentRole)` — one mapping per role per composition
- [ ] `CompositionInstrumentRepository` with band-scoped queries
- [ ] Migration `V20__create_composition_instrument_table.sql`
- [ ] Cascade delete when composition removed

**Story Points:** 3  
**Dependencies:** US-1.1, US-1.2

---

### **US-1.4: InstrumentRoleMap (Tag-to-Role Mapping)**
> **As a** band manager  
> **I want** configurable mapping from member instrument tags to composition roles  
> **So that** "Trąbka" tag automatically matches "Trąbka 1", "Trąbka 2", "Kornet 1"

**Acceptance Criteria:**
- [ ] Entity `InstrumentRoleMap` in `domain/composition/` with: `id`, `band_id`, `sourceTag` (member tag), `targetRolePattern` (composition instrumentRole), `description`
- [ ] Repository with `findByBandIdAndSourceTag`, `findAllByBandId`
- [ ] Migration `V21__create_instrument_role_map_table.sql`
- [ ] Seed default mappings for common instruments (Trąbka, Flet, Waltornia, etc.)
- [ ] Admin UI for managing mappings (later story)

**Story Points:** 2  
**Dependencies:** US-1.3

---

### **US-1.5: CompositionFile Entity (Stored Files Metadata)**
> **As a** system  
> **I want** track uploaded score files with storage metadata  
> **So that** files are retrievable, secure, and auditable

**Acceptance Criteria:**
- [ ] Entity `CompositionFile` in `domain/composition/` with: `id`, `composition` (ManyToOne), `originalFilename`, `storedPath`, `mimeType`, `sizeBytes`, `fileType` (PDF/ZIP), `pageCount` (for PDF), `uploadedBy`, `uploadedAt`
- [ ] Storage path convention: `/mnt/sda1/media/windband-scores/{bandId}/compositions/{compositionId}/{uuid}_{originalName}.ext`
- [ ] Repository with band-scoped queries
- [ ] Migration `V22__create_composition_file_table.sql`

**Story Points:** 2  
**Dependencies:** US-1.1

---

### **US-1.6: Composition Command & Query Services**
> **As a** developer  
> **I want** `CompositionCommandService` and `CompositionQueryService` in `application/command/composition/` and `application/query/composition/`  
> **So that** use cases are encapsulated following CQRS pattern

**Acceptance Criteria:**
- [ ] `CompositionCommandService`: `createComposition(cmd, bandId)`, `updateComposition(id, cmd, bandId)`, `deleteComposition(id, bandId)`, `verifyCompositionParts(id, bandId, currentUser)`
- [ ] `CompositionQueryService`: `getComposition(id, bandId)`, `getAllCompositions(bandId, pageable)`, `searchCompositions(bandId, query, pageable)`, `getCompositionWithParts(id, bandId)`
- [ ] Command DTOs: `CreateCompositionCommand`, `UpdateCompositionCommand`
- [ ] Query DTOs: `CompositionDto`, `CompositionWithPartsDto`, `CompositionInstrumentDto`
- [ ] Band isolation enforced via `BandQueryService.getRequiredBand(bandId)`
- [ ] Unit tests with Mockito

**Story Points:** 5  
**Dependencies:** US-1.1, US-1.3, US-1.5

---

## 🟡 Epic 2: File Upload & Storage (Infrastructure)

### **US-2.1: Secure File Upload Endpoint**
> **As a** band member  
> **I want** upload a score file (PDF or ZIP) for a composition  
> **So that** it can be analyzed and used for part distribution

**Acceptance Criteria:**
- [ ] REST endpoint `POST /api/compositions/{id}/files` (multipart)
- [ ] Validation: MIME types `application/pdf`, `application/zip`, `image/jpeg`, `image/png`
- [ ] Size limits: 50 MB per file, 200 MB per ZIP
- [ ] ZIP slip protection (validate paths, no `../`)
- [ ] Virus scan placeholder (ClamAV integration point)
- [ ] Temporary storage during analysis, permanent on verification
- [ ] Returns `CompositionFileDto` with `fileId` for next steps
- [ ] Integration test with test PDF/ZIP

**Story Points:** 5  
**Dependencies:** US-1.5, US-1.6

---

### **US-2.2: PDF Page Count Extraction**
> **As a** system  
> **I want** automatically extract page count from uploaded PDF  
> **So that** UI can show page range inputs for manual mapping

**Acceptance Criteria:**
- [ ] Service `PdfPageCountService` using `pdfplumber`/`PyMuPDF` via subprocess or Java library (Apache PDFBox)
- [ ] Called during upload for `fileType=PDF`
- [ ] Updates `CompositionFile.pageCount`
- [ ] Handles password-protected PDFs gracefully (error message)
- [ ] Unit test with sample PDFs

**Story Points:** 2  
**Dependencies:** US-2.1

---

### **US-2.3: ZIP Content Enumeration**
> **As a** system  
> **I want** list files inside uploaded ZIP archive  
> **So that** UI can show thumbnails/filenames for manual mapping

**Acceptance Criteria:**
- [ ] Service `ZipContentService` enumerates entries
- [ ] Filters to image files (JPG, PNG, TIFF) + PDF
- [ ] Returns list of `{filename, size, isImage}`
- [ ] ZIP slip protection during extraction
- [ ] Unit test with test ZIP

**Story Points:** 2  
**Dependencies:** US-2.1

---

### **US-2.4: File Download & Access Control**
> **As a** band member  
> **I want** download score files I have access to  
> **So that** I can view full scores or parts

**Acceptance Criteria:**
- [ ] Endpoint `GET /api/compositions/{id}/files/{fileId}`
- [ ] Authorization: only members of the composition's band
- [ ] Streaming response (not loading full file into memory)
- [ ] Content-Disposition: `inline` for PDF, `attachment` for ZIP
- [ ] Audit log entry on download

**Story Points:** 2  
**Dependencies:** US-2.1

---

### **US-2.5: File Deletion & Cleanup**
> **As a** band manager  
> **I want** delete score files when no longer needed  
> **So that** storage doesn't accumulate orphaned files

**Acceptance Criteria:**
- [ ] Endpoint `DELETE /api/compositions/{id}/files/{fileId}`
- [ ] Deletes physical file from storage
- [ ] Removes `CompositionFile` record
- [ ] If composition has no files and status=DRAFT, allow composition deletion
- [ ] Scheduled job to clean temp files > 24h old (optional)

**Story Points:** 1  
**Dependencies:** US-2.1

---

## 🔵 Epic 3: Composition CRUD — Manual Entry (Core Feature)

### **US-3.1: Create Composition (Manual)**
> **As a** band librarian  
> **I want** create a composition record with title, composer, arranger, description  
> **So that** I can catalog pieces before uploading scores

**Acceptance Criteria:**
- [ ] Page: `/compositions/new` (Thymeleaf fragment)
- [ ] Form with fields: title*, description, composer, arranger
- [ ] Save → redirects to composition detail with status=DRAFT
- [ ] Validation: title required, max 255 chars
- [ ] Band-scoped: auto-assigns current user's active band
- [ ] Selenium test: create → verify in list

**Story Points:** 3  
**Dependencies:** US-1.6

---

### **US-3.2: List Compositions**
> **As a** band member  
> **I want** see a paginated, searchable list of my band's compositions  
> **So that** I can find and manage pieces

**Acceptance Criteria:**
- [ ] Page: `/compositions` with table: Title, Composer, Arranger, Status, Parts Count, Actions
- [ ] Search by title/composer/arranger (debounced)
- [ ] Filter by status (DRAFT/READY/ARCHIVED)
- [ ] Sort by title, composer, updatedAt
- [ ] Pagination (20 per page)
- [ ] "New Composition" button
- [ ] Selenium test: search, filter, paginate

**Story Points:** 3  
**Dependencies:** US-1.6

---

### **US-3.3: Composition Detail View**
> **As a** band member  
> **I want** view full composition details including mapped parts  
> **So that** I can verify the score mapping before distribution

**Acceptance Criteria:**
- [ ] Page: `/compositions/{id}` with tabs: Details, Parts, Files, History
- [ ] Details tab: all metadata + status badge
- [ ] Parts tab: table of `CompositionInstrument` rows — Role, Instrument, Pages/File, Source, Confidence, Verified
- [ ] Files tab: list of uploaded files with download links
- [ ] Actions: Edit, Upload File, Analyze (if file exists), Verify, Archive
- [ ] Selenium test: navigate, verify tabs

**Story Points:** 3  
**Dependencies:** US-1.6, US-2.4

---

### **US-3.4: Edit Composition Metadata**
> **As a** band librarian  
> **I want** edit composition title, description, composer, arranger  
> **So that** I can correct or update catalog information

**Acceptance Criteria:**
- [ ] Edit form at `/compositions/{id}/edit` (fragment)
- [ ] Pre-populated with current values
- [ ] Save → returns to detail with updated values
- [ ] Only allowed when status=DRAFT (READY/ARCHIVED locked)
- [ ] Audit trail: `updatedAt`, `updatedBy`

**Story Points:** 2  
**Dependencies:** US-3.3

---

### **US-3.5: Manual Parts Mapping (No AI)**
> **As a** band librarian  
> **I want** manually define instrument-to-page/file mappings  
> **So that** I can catalog pieces without relying on AI

**Acceptance Criteria:**
- [ ] In Parts tab: "Add Part" button → modal with fields:
  - Instrument (select from band's instruments)
  - Role name (e.g., "Flet 1", "Trąbka Bb")
  - Page From / Page To (for PDF) OR File Ref (for ZIP)
  - Source = MANUAL
- [ ] Validation: no overlapping page ranges for same composition
- [ ] Save → adds row to Parts table
- [ ] Inline edit/delete for existing MANUAL parts
- [ ] Selenium test: add/edit/delete part

**Story Points:** 5  
**Dependencies:** US-1.3, US-3.3

---

## 🟠 Epic 4: AI-Assisted Score Analysis (Advanced)

### **US-4.1: AI Analysis Trigger Endpoint**
> **As a** band librarian  
> **I want** trigger AI analysis of an uploaded score file  
> **So that** I get a proposed parts mapping to review

**Acceptance Criteria:**
- [ ] Endpoint `POST /api/compositions/{id}/files/{fileId}/analyze`
- [ ] Accepts `fileType` (PDF/ZIP) to select strategy
- [ ] Delegates to `CompositionAnalysisService` (async, `@Async`)
- [ ] Returns `analysisId` for polling status
- [ ] Analysis runs in background; UI shows spinner/polling

**Story Points:** 3  
**Dependencies:** US-2.1, US-1.6

---

### **US-4.2: PDF Analysis Strategy (Text-Based PDF)**
> **As a** system  
> **I want** extract text from text-layer PDF and identify part boundaries  
> **So that** AI can propose page ranges for each instrument

**Acceptance Criteria:**
- [ ] `PdfTextAnalysisStrategy` using PDFBox/pdfplumber
- [ ] Extracts text per page, looks for instrument names in headers/footers
- [ ] Prompt to local LLM (Ollama) with structured JSON output schema
- [ ] Output: `{ parts: [{ name, pages: [from,to], confidence }] }`
- [ ] Confidence < 0.7 → `needsHumanReview: true`
- [ ] Unit test with sample concert PDF

**Story Points:** 8  
**Dependencies:** US-4.1

---

### **US-4.3: PDF Analysis Strategy (Scanned PDF / OCR)**
> **As a** system  
> **I want** OCR scanned PDF pages to identify instrument parts  
> **So that** handwritten/scanned scores can be analyzed

**Acceptance Criteria:**
- [ ] `PdfOcrAnalysisStrategy` using Tesseract or vision model
- [ ] Processes each page as image → OCR → text extraction
- [ ] Same JSON output schema as text strategy
- [ ] Significantly lower confidence scores expected
- [ ] Always sets `needsHumanReview: true`
- [ ] Configurable: enable/disable per band (resource intensive)

**Story Points:** 13  
**Dependencies:** US-4.1

---

### **US-4.4: ZIP Analysis Strategy (Image Files)**
> **As a** system  
> **I want** analyze ZIP contents (filenames + optional OCR) to map parts  
> **So that** pre-split score scans can be mapped

**Acceptance Criteria:**
- [ ] `ZipImageAnalysisStrategy`
- [ ] Primary: parse filenames for instrument clues (e.g., "01_Flet_1.jpg")
- [ ] Secondary: OCR each image for confirmation
- [ ] Output: `{ parts: [{ name, fileRef, confidence }] }`
- [ ] Groups consecutive files for same instrument (e.g., Flet 1 = files 6-7)
- [ ] Unit test with sample ZIP

**Story Points:** 5  
**Dependencies:** US-4.1, US-2.3

---

### **US-4.5: Analysis Result Preview & Editing**
> **As a** band librarian  
> **I want** review and edit AI-proposed mapping before accepting  
> **So that** errors are caught before parts are distributed

**Acceptance Criteria:**
- [ ] Preview page/modal after analysis completes
- [ ] For PDF: visual page range selector (slider or input) with page thumbnails
- [ ] For ZIP: file list with checkboxes per part
- [ ] Editable fields: role name, page range/file ref, instrument (select)
- [ ] "Accept" → saves as `CompositionInstrument` rows with `source=AI`, `verifiedBy=currentUser`, `verifiedAt=now`
- [ ] "Reject" → discards analysis, allows re-run or manual entry
- [ ] Selenium test: accept/edit/reject flow

**Story Points:** 8  
**Dependencies:** US-4.2, US-4.3, US-4.4, US-3.3

---

### **US-4.6: Analysis Status Polling & Notifications**
> **As a** band librarian  
> **I want** see analysis progress and get notified when done  
> **So that** I don't have to wait on the page

**Acceptance Criteria:**
- [ ] Polling endpoint `GET /api/compositions/analysis/{analysisId}/status`
- [ ] States: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`
- [ ] On `COMPLETED`: show "Review Results" button
- [ ] On `FAILED`: show error, allow retry
- [ ] Toast notification when analysis completes (if user navigated away)
- [ ] WebSocket/SSE optional enhancement

**Story Points:** 3  
**Dependencies:** US-4.1

---

### **US-4.7: Analysis History & Re-analysis**
> **As a** band librarian  
> **I want** see past analysis attempts and re-run if needed  
> **So that** I can compare results or retry with better file

**Acceptance Criteria:**
- [ ] History tab in composition detail shows analysis attempts
- [ ] Each entry: timestamp, file, strategy, status, confidence, verifiedBy
- [ ] "Re-analyze" button for any file
- [ ] Previous verified mappings preserved unless explicitly replaced
- [ ] Audit trail for compliance

**Story Points:** 2  
**Dependencies:** US-4.5

---

## 🟣 Epic 5: Instrument Alias Mapping (Distribution Logic)

### **US-5.1: Manage Instrument Role Mappings (Admin UI)**
> **As a** band admin  
> **I want** configure which member tags map to which composition roles  
> **So that** part distribution works for my band's naming conventions

**Acceptance Criteria:**
- [ ] Page: `/band/attributes/instrument-mappings` (or similar)
- [ ] Table: Source Tag → Target Roles (multi-select), Description
- [ ] Add/Edit/Delete mappings
- [ ] Default mappings seeded per US-1.4
- [ ] Band-scoped: each band manages own mappings
- [ ] Selenium test: CRUD mappings

**Story Points:** 3  
**Dependencies:** US-1.4

---

### **US-5.2: Tag-to-Role Resolution Service**
> **As a** system  
> **I want** a service that resolves member tags to composition instrument roles  
> **So that** distribution logic knows which parts to send to which member

**Acceptance Criteria:**
- [ ] `InstrumentRoleResolver` in `domain/composition/`
- [ ] Method: `resolveRolesForMember(member, composition, bandId) -> Set<String> targetRoles`
- [ ] Logic: for each member tag (primary/secondary instrument), find `InstrumentRoleMap` entries, collect `targetRolePattern`
- [ ] Supports exact match and prefix match (e.g., "Trąbka" → "Trąbka 1", "Trąbka 2")
- [ ] Fallback: if no mapping, suggest all roles containing tag as substring
- [ ] Unit tests covering: exact, prefix, multi-tag, no-mapping, Waltornia Es/F

**Story Points:** 5  
**Dependencies:** US-1.4, US-5.1

---

### **US-5.3: Ambiguous Mapping Resolution UI**
> **As a** band librarian  
> **I want** resolve ambiguous tag-to-role mappings before distribution  
> **So that** each musician gets exactly the right parts

**Acceptance Criteria:**
- [ ] During "Send Parts" flow (US-6.3), show mapping review step
- [ ] For each recipient: show detected tag → proposed roles with checkboxes
- [ ] Pre-check based on `InstrumentRoleResolver` suggestions
- [ ] Allow override: add/remove roles per recipient
- [ ] "Remember this mapping" → creates/updates `InstrumentRoleMap`
- [ ] Skip step if all mappings are unambiguous (high confidence)

**Story Points:** 5  
**Dependencies:** US-5.2, US-6.3

---

## 🔴 Epic 6: Event Integration & Part Distribution (End Goal)

### **US-6.1: Add Composition to Event Program**
> **As a** band manager  
> **I want** add a composition from the library to an event's program  
> **So that** I can plan the concert repertoire

**Acceptance Criteria:**
- [ ] In Event detail (`/events/{id}`): "Add Composition" button
- [ ] Modal: search/select from band's compositions (status=READY only)
- [ ] Adds `EventComposition` link entity: `eventId`, `compositionId`, `programOrder`, `notes`
- [ ] Display in program list with drag-drop reorder
- [ ] Migration `V23__create_event_composition_table.sql`
- [ ] Selenium test: add, reorder, remove

**Story Points:** 5  
**Dependencies:** US-1.1, existing Event domain

---

### **US-6.2: Generate Parts Package for Recipient**
> **As a** system  
> **I want** generate a PDF package containing only the pages/files for a specific member's parts  
> **So that** each musician receives exactly their music

**Acceptance Criteria:**
- [ ] `PartPackageGeneratorService` in `application/command/composition/`
- [ ] Input: `compositionId`, `targetRoles` (Set<String>), `bandId`
- [ ] For PDF source: extract page ranges → merge into single PDF (PDFBox)
- [ ] For ZIP source: select files → optionally merge images into PDF
- [ ] Output: `byte[]` PDF or `byte[]` ZIP
- [ ] Filename: `{compositionTitle}_{role}.pdf` or `{compositionTitle}_parts.zip`
- [ ] Unit test with sample PDF/ZIP

**Story Points:** 8  
**Dependencies:** US-1.3, US-1.5, US-5.2

---

### **US-6.3: Send Parts to Event Participants**
> **As a** band manager  
> **I want** one-click send practice parts to all invited event participants  
> **So that** musicians get their music automatically

**Acceptance Criteria:**
- [ ] In Event detail: "Wyślij głosy do ćwiczenia" button
- [ ] Step 1: Select compositions from event program (multi-select)
- [ ] Step 2: Select recipients (from invited members, pre-checked)
- [ ] Step 3: Mapping review (per US-5.3) — show tag → roles per recipient
- [ ] Step 4: Confirm & Send
- [ ] Background job: for each recipient, generate package (US-6.2) → email with attachment/link
- [ ] Email template: personalized, lists included parts, download link (expires in 7 days)
- [ ] Audit log: `PartDistributionLog` with eventId, compositionId, recipientId, sentAt, status
- [ ] Selenium test: full flow

**Story Points:** 13  
**Dependencies:** US-6.1, US-6.2, US-5.3, existing email infrastructure

---

### **US-6.4: Conductor Receives Full Score**
> **As a** conductor  
> **I want** receive the full score (all pages) when parts are distributed  
> **So that** I have the complete picture for rehearsal

**Acceptance Criteria:**
- [ ] Members with tag "Dyrygent" or role=CONDUCTOR get full PDF
- [ ] For PDF source: pages 1 to N (entire document)
- [ ] For ZIP source: all files merged into single PDF
- [ ] Separate email or same email with distinct attachment
- [ ] Configurable per band (some bands may not want this)

**Story Points:** 3  
**Dependencies:** US-6.3

---

### **US-6.5: Part Download Portal (Alternative to Email)**
> **As a** band member  
> **I want** access my parts via a secure download link in the app  
> **So that** I don't rely on email delivery

**Acceptance Criteria:**
- [ ] Page: `/my-parts` — lists all parts sent to me
- [ ] Each entry: composition title, event, role, sent date, download button
- [ ] Download generates fresh package (US-6.2) or serves cached
- [ ] Access control: only parts where my tags match
- [ ] Mobile-friendly

**Story Points:** 5  
**Dependencies:** US-6.2, existing member portal

---

### **US-6.6: Distribution History & Resend**
> **As a** band manager  
> **I want** see distribution history and resend failed/missing parts  
> **So that** I can ensure everyone has their music

**Acceptance Criteria:**
- [ ] In Event detail: "Historia wysyłki" tab
- [ ] Table: Date, Composition, Recipient, Role, Status (SENT/FAILED/OPENED), Actions
- [ ] "Resend" button per row or bulk
- [ ] "Resend to unopened" bulk action
- [ ] Failed sends show error, allow retry

**Story Points:** 3  
**Dependencies:** US-6.3

---

## 🟤 Epic 7: UI & UX Polish (Frontend)

### **US-7.1: Compositions Navigation & Menu Integration**
> **As a** band member  
> **I want** access Compositions Library from main navigation  
> **So that** it's discoverable and consistent with app UX

**Acceptance Criteria:**
- [ ] Add "Biblioteka utworów" to main nav (sidebar/header)
- [ ] Icon: music note or folder
- [ ] Active state highlighting
- [ ] Responsive: collapses on mobile
- [ ] Follows `unified-header-pattern` and `dashboard-header-migration`

**Story Points:** 2  
**Dependencies:** US-3.2

---

### **US-7.2: Composition List — Inline Actions**
> **As a** band librarian  
> **I want** quick actions (edit, upload, analyze, delete) directly in the list  
> **So that** I don't need to open detail for common tasks

**Acceptance Criteria:**
- [ ] Action menu per row (dropdown): Edit, Upload, Analyze, Verify, Archive, Delete
- [ ] "Upload" and "Analyze" only enabled when appropriate
- [ ] HTMX-driven: actions update row inline without full reload
- [ ] Confirmation modals for destructive actions
- [ ] Toast notifications on success/error

**Story Points:** 3  
**Dependencies:** US-3.2

---

### **US-7.3: PDF Page Thumbnail Preview**
> **As a** band librarian  
> **I want** see page thumbnails when mapping parts manually or reviewing AI  
> **So that** I can visually verify page assignments

**Acceptance Criteria:**
- [ ] Component: `pdf-thumbnail-strip` (JS + Thymeleaf fragment)
- [ ] Renders first page of each range as thumbnail
- [ ] Click thumbnail → opens full-page view in modal
- [ ] Lazy-load thumbnails (intersection observer)
- [ ] Works for both manual mapping and AI review

**Story Points:** 5  
**Dependencies:** US-2.2, US-3.5, US-4.5

---

### **US-7.4: ZIP File Thumbnail Gallery**
> **As a** band librarian  
> **I want** see image thumbnails for ZIP contents  
> **So that** I can identify parts visually

**Acceptance Criteria:**
- [ ] Grid of thumbnails for image files in ZIP
- [ ] Filename overlay on hover
- [ ] Click → full-size modal
- [ ] Checkbox for selection in AI review
- [ ] Lazy-load, responsive grid

**Story Points:** 3  
**Dependencies:** US-2.3, US-4.5

---

### **US-7.5: Sticky Parts Table Header (Collision Detection)**
> **As a** band librarian  
> **I want** the parts table header stay visible while scrolling  
> **So that** I can always see column names

**Acceptance Criteria:**
- [ ] Uses existing `adaptive-sticky-filters-with-collision-detection` pattern
- [ ] Header sticks below morphed dashboard header
- [ ] Auto-collides with header → header compacts
- [ ] CSS custom property `--sticky-parts-top`
- [ ] Follows `references/adaptive-sticky-filters-with-collision-detection.md`

**Story Points:** 2  
**Dependencies:** US-3.3

---

### **US-7.6: Composition Search with Highlights**
> **As a** band member  
> **I want** search compositions with highlighted matches  
> **So that** I quickly find pieces

**Acceptance Criteria:**
- [ ] Search input in list header (debounced 300ms)
- [ ] Highlights matching text in title/composer/arranger
- [ ] Searches across all band compositions
- [ ] Keyboard accessible (Esc to clear, Enter to focus first result)

**Story Points:** 2  
**Dependencies:** US-3.2

---

### **US-7.7: Empty States & Onboarding**
> **As a** new band librarian  
> **I want** helpful empty states with guidance  
> **So that** I know how to get started

**Acceptance Criteria:**
- [ ] Empty list: "No compositions yet. Click 'New Composition' to add your first piece."
- [ ] Empty parts: "No parts mapped. Upload a score and analyze, or add parts manually."
- [ ] Empty files: "No score file uploaded. Click 'Upload' to add a PDF or ZIP."
- [ ] Illustrations (SVG) matching app style

**Story Points:** 1  
**Dependencies:** US-3.2, US-3.3

---

### **US-7.8: Responsive Design & Mobile Support**
> **As a** band member on mobile  
> **I want** use Compositions Library on my phone  
> **So that** I can check parts on the go

**Acceptance Criteria:**
- [ ] List: card view on mobile (< 640px), table on desktop
- [ ] Detail: stacked tabs, scrollable parts table
- [ ] Modals: full-screen on mobile
- [ ] Touch-friendly targets (44px min)
- [ ] Tested on Chrome DevTools device toolbar
- [ ] Follows `dashboard-responsive-patterns.md` (3 breakpoints)

**Story Points:** 5  
**Dependencies:** US-3.2, US-3.3

---

## 📊 Implementation Order Summary (by dependency)

| Order | Story ID | Title | Points | Epic |
|-------|----------|-------|--------|------|
| 1 | US-1.1 | Composition Entity & Repository | 3 | 1 |
| 2 | US-1.2 | Instrument Entity Enhancement | 2 | 1 |
| 3 | US-1.3 | CompositionInstrument Link Entity | 3 | 1 |
| 4 | US-1.4 | InstrumentRoleMap Entity | 2 | 1 |
| 5 | US-1.5 | CompositionFile Entity | 2 | 1 |
| 6 | US-1.6 | Command & Query Services | 5 | 1 |
| 7 | US-2.1 | Secure File Upload Endpoint | 5 | 2 |
| 8 | US-2.2 | PDF Page Count Extraction | 2 | 2 |
| 9 | US-2.3 | ZIP Content Enumeration | 2 | 2 |
| 10 | US-2.4 | File Download & Access Control | 2 | 2 |
| 11 | US-2.5 | File Deletion & Cleanup | 1 | 2 |
| 12 | US-3.1 | Create Composition (Manual) | 3 | 3 |
| 13 | US-3.2 | List Compositions | 3 | 3 |
| 14 | US-3.3 | Composition Detail View | 3 | 3 |
| 15 | US-3.4 | Edit Composition Metadata | 2 | 3 |
| 16 | US-3.5 | Manual Parts Mapping | 5 | 3 |
| 17 | US-5.1 | Manage Instrument Role Mappings (Admin UI) | 3 | 5 |
| 18 | US-5.2 | Tag-to-Role Resolution Service | 5 | 5 |
| 19 | US-7.1 | Navigation & Menu Integration | 2 | 7 |
| 20 | US-7.2 | List Inline Actions | 3 | 7 |
| 21 | US-7.6 | Search with Highlights | 2 | 7 |
| 22 | US-7.7 | Empty States & Onboarding | 1 | 7 |
| 23 | US-7.8 | Responsive Design | 5 | 7 |
| 24 | US-4.1 | AI Analysis Trigger Endpoint | 3 | 4 |
| 25 | US-4.2 | PDF Text Analysis Strategy | 8 | 4 |
| 26 | US-4.3 | PDF OCR Analysis Strategy | 13 | 4 |
| 27 | US-4.4 | ZIP Image Analysis Strategy | 5 | 4 |
| 28 | US-4.5 | Analysis Preview & Editing | 8 | 4 |
| 29 | US-4.6 | Analysis Status Polling | 3 | 4 |
| 30 | US-4.7 | Analysis History | 2 | 4 |
| 31 | US-5.3 | Ambiguous Mapping Resolution UI | 5 | 5 |
| 32 | US-6.1 | Add Composition to Event Program | 5 | 6 |
| 33 | US-6.2 | Generate Parts Package | 8 | 6 |
| 34 | US-6.3 | Send Parts to Participants | 13 | 6 |
| 35 | US-6.4 | Conductor Full Score | 3 | 6 |
| 36 | US-6.5 | Part Download Portal | 5 | 6 |
| 37 | US-6.6 | Distribution History & Resend | 3 | 6 |
| 38 | US-7.3 | PDF Thumbnail Preview | 5 | 7 |
| 39 | US-7.4 | ZIP Thumbnail Gallery | 3 | 7 |
| 40 | US-7.5 | Sticky Parts Header | 2 | 7 |

**Total: ~131 Story Points**

---

## 🎯 Definition of Ready (per story)
- [ ] Acceptance criteria clear and testable
- [ ] Dependencies identified and ordered
- [ ] Technical approach sketched (service, repository, endpoint)
- [ ] UI mockup or fragment reference (for UI stories)
- [ ] Test strategy defined (unit + integration + Selenium where applicable)

## 🎯 Definition of Done (per story)
- [ ] Code implemented following `windband-coding-standards`
- [ ] Unit tests pass (`./mvnw test -Dtest=*StoryName*`)
- [ ] Integration tests pass (if applicable)
- [ ] Selenium UI test added/updated (for UI stories)
- [ ] `./mvnw clean verify` passes locally
- [ ] Code reviewed (self-review + `requesting-code-review` skill)
- [ ] Merged to main via PR with green CI

---

## ⚠️ Key Risks & Mitigations (from analysis)

| Risk | Mitigation in Stories |
|------|----------------------|
| AI hallucination on instrument names | US-4.5: mandatory human verification; US-4.2/4.3: confidence scoring |
| PDF type variability (text vs scan) | US-4.2 vs US-4.3: separate strategies with file-type detection |
| Handwritten/abbreviated names | US-4.3: OCR + `needsHumanReview=true`; US-4.5: visual preview |
| Tag-to-role ambiguity (Waltornia Es/F) | US-5.1/5.2/5.3: configurable mapping + review UI |
| Cross-band data leakage | All entities have `band_id`; all repositories filter by `band_id` (US-1.1, 1.3, 1.4, 1.5) |
| Large file handling | US-2.1: streaming, size limits, temp storage |

---

## 🚀 Suggested Sprint Plan

| Sprint | Stories | Goal |
|--------|---------|------|
| **Sprint 1** | US-1.1 – 1.6, US-2.1 – 2.5 | Foundation: model, storage, basic CRUD |
| **Sprint 2** | US-3.1 – 3.5, US-7.1, 7.2, 7.6, 7.7 | Manual composition management + list UI |
| **Sprint 3** | US-5.1, 5.2, US-7.3, 7.4, 7.5, 7.8 | Mapping config + detail UI polish |
| **Sprint 4** | US-4.1, 4.2, 4.4, 4.5, 4.6, 4.7 | AI analysis (text PDF + ZIP) + review UI |
| **Sprint 5** | US-4.3 | AI analysis (scanned PDF/OCR) — optional, can defer |
| **Sprint 6** | US-6.1 – 6.6, US-5.3 | Event integration + distribution (core value) |

---

## 📝 Notes for Developers

1. **Start with Epic 1 & 2** — they're pure backend and unblock everything else
2. **Defer OCR (US-4.3)** — highest effort, lowest immediate value; text PDF + ZIP cover 80% of cases
3. **Reuse existing patterns**:
   - `BandQueryService.getRequiredBand(bandId)` for isolation
   - `fetchWithToast` + HTMX for UI interactions
   - `AsyncConfig` for background analysis
   - `TeamModelAdvice` for global `activeTeamId`
4. **Follow `windband-manager-workflow`** for git/verify/PR discipline
5. **Test pyramid**: unit (services) > integration (repositories) > Selenium (UI flows)

---

*Document created: 2026-09-13*  
*Author: Hermes Agent (QA & Business Analyst role)*  
*Based on: Analysis document + windband-manager architecture skills*