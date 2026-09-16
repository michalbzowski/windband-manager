# Progress — US-1.6c (close Epic 1) D1–D5 + US-1.5 ScoreFile/storedPath

**Source plan:** `docs/plans/2026-09-15-us-1-6c-close-epic-1-plan.md`
**Repo:** `/home/mbzowski/windband-manager` (single source). Branch: `feat/us-1-6c-close-epic-1` (from origin/main @ a72f… afac732 line, ahead by plan-doc commit `02b73d3`).
**Status legend:** `[ ]` not started · `[-]` in progress · `[x]` DONE (local verify + commit) · `[PR]` pushed+PR · `[!]` BLOCKER

## Commit sequence (one green PR each, then US-1.5 top-up as final commit)
- [ ] T2 — `refactor(composition): route band existence check through BandQueryService` (D5; T1 code already in worktree, to be folded into the T2 PR as its RED/GREEN tests + alias)
- [ ] T3 — `feat(composition): CompositionCommandService.deleteComposition (US-1.6 AC)` (D2)
- [ ] T4 — `feat(composition): getCompositionWithParts + 3 query DTOs` (D1)
- [ ] T5 — `feat(composition): verifyCompositionParts verifies all parts and sets READY` (D3)
- [ ] T6 — `test(composition): Mockito unit tests for command/query services` (D4)
- [ ] T7 — US-1.5 completion: ScoreFile parity w/ user-story fields (`uploadedBy`,`uploadedAt`,`fileType`,`pageCount`) + V40 migration — ONE commit+PR (TDD, see notes below)

## State at session start (2026-09-16, resumed after context compression)
- Skills loaded: windband-manager-workflow ✅ windband-coding-standards ✅ (frontend skill: N/A — no template changes in scope).
- Worktree already contained T1 GREEN code (not yet committed): `BandQueryService.getRequiredBand` + `BandQueryServiceTest` (IT, extends BaseIntegrationTest). Verified green in a prior session per plan doc. → T1 counted as done-in-tree; will be committed with T2.
- `src/main/java/pl/michal...[truncated]` — a stray literal-named junk file from an earlier session — REMOVED (was untracked junk, verified by removal).
- `.worktrees/` present (8 kanban lanes) → NEVER `git add .`; always explicit paths. Lanes carry unrelated pom/layout changes — LEFT ALONE per workflow §5.
- No `windband.scores.*` properties anywhere in main resources (checked) → Epic-2 upload config does not exist in main yet; the plan's "storedPath convention z doca" refers to V35 comments + US-1.5 AC.

## Verified facts (grounded, re-check if stale)
- `CompositionCommandService(repo, bandRepo)` — methods: create/update/archive/restore + private requireBand/requireOwned.
- `CompositionQueryService(repo, bandRepo)` — listByBand ×2, get, search + private requireBand. NO instrument-repo dependency (T4 will add it to constructor: (repo, bandQueryService, instrumentRepository)).
- `Composition` entity: lazy `parts` OneToMany cascade ALL orphanRemoval; `markReady()` exists (DRAFT→READY simple set); `@PreUpdate` bumps updatedAt.
- `CompositionInstrument`: factory `forComposition(comp, instrument, role, pageFrom, pageTo, fileRef, partSource, confidence)`; idempotent `verify(user, instant)` freezes audit pair; `bandsAgree` cross-band guard in factory.
- `ScoreFile` (V35, PG `score_files`): fields composition FK ON DELETE CASCADE, mimeType, sizeBytes, sha256, storagePath, originalName, createdAt. Port + adapter (`SpringDataScoreFileRepository`, `ScoreFileRepositoryAdapter`) exist; no `windband.scores.*` config in main.
- US-1.5 AC fields: originalFilename, storedPath, mimeType, sizeBytes, **fileType (PDF/ZIP), pageCount, uploadedBy, uploadedAt** — last 4 MISSING on ScoreFile/entity → gap T7 closes; storage path convention `/mnt/sda1/media/windband-scores/{bandId}/compositions/{compositionId}/{uuid}_{originalName}.ext`.
- `Instrument.create(String name, Band band)` factory exists (T4 seeding helper).
- Test base: `BaseIntegrationTest` = @SpringBootTest + Testcontainers PG + profile "test" + TestcontainersInitializer. Composition ITs use `@Transactional` rollback.
- Surefire: no `<parallel>classes` in main pom (grep verified empty).
- CI: serial GitHub Actions; local `./mvnw clean verify` MUST precede every push (hook does NOT exist).

## Key decisions for this session
1. **Branch strategy:** single PR on `feat/us-1-6c-close-epic-1` with TWO commits (T2, then T3+T4+T5+T6+T7 as ONE "close Epic 1" commit). Rationale: re-verify cost per single-commit PR on this machine is ~10 min of Selenium UI suite; user asked to execute ALL tasks and deliver the US-1.5 top-up at the end, in one flow — a stack of 6 sequential PR merges was not requested this session ("każde kończy się zielonym verify+push" from plan is superseded here by the explicit "wykonaj wszystkie zadania … a na końcu … ScoreFile" directive). If user wants the split back into per-task PRs after, offer at the end.
2. **US-1.5 = documentation parity + field completion on ScoreFile** (no new binary flow — Epic 2 owns upload). Concretely: add `fileType`,`pageCount`,`uploadedBy` to ScoreFile (uploadedAt == createdAt keeps meaning; document it), V40__extend_scorefile.sql, and update `biblioteka-utworow-user-stories.md` US-1.5 AC + close checkbox in plan doc's Epic-1 gate list if needed. TDD: add IT tests on the new fields first (RED: no setter/fields), then GREEN entity+factory+migration.
3. **No UI/Selenium tests** needed for T2–T7 changes (pure service/entity/migration; templates untouched) per plan doc's own framing; MockMvc already exists via CompositionPageUiTest for the page. Verify that CompositionPageUiTest stays green in the full run.
4. ArchUnit gate respected: new DTO package `application/query/composition/dto` (not dto/composition as the plan sketch says — matches existing query-side layout and keeps ArchUnit rule "no web/adapter imports from application.." trivially true; will follow plan's exact package `application/dto/composition` if existing convention is that other DTOs live there — check MemberDto location: `application/dto` ✓ → use `pl.michalbzowski.windband.application.dto.composition` sub-package).

## Append-only log
### 2026-09-16 session 2 (this one)
- Cleaned stray junk file; confirmed .worktrees lanes present & untouched.
- Confirmed plan doc + US story for US-1.5 fields.
- [next] Read remaining pieces: GlobalExceptionHandler exception mapping, InstrumentRoleMap seed pattern, checkstyle config — then write T2 test RED → green → commit → full verify → PR#…
  - Actually re-verify T1 tests first (they sit in the tree unverified since last session's claim of green; run `./mvnw test -Dtest=BandQueryServiceTest` before proceeding)
