# US-4.1 — Start AI analysis pipeline (windband-manager)

**Epic:** 4 · AI-Assisted Score Analysis · **Status:** ⬜ Plan
**Story Points:** 5
**Dependency chain:** US-1.3/US-2.x (ScoreFile + storage) ✅ → US-3.03 (verify gate) ✅ → **this** → US-4.5 (preview accept) → US-6.x (distribution)

## Scope (what this story IS / IS NOT)

| Yes (shipped in US-4.1) | No (out of scope — separate stories) |
|---|---|
| New `ScoreAnalysis` domain entity + `score_analysis` Flyway migration V42 | Accepting the AI's output into a composition (that is US-4.5) |
| `AiAnalysisRunner` interface + `ProcessBuilder` impl (spawns windband-ai CLI) | UI polish / HTMX micro-interactions (UI → Epic 7) |
| `ScoreAnalysisCommandService.start()` — upload → spawn → poll → persist | Instrument alias mapping / tag→role resolution (Epic 5) |
| REST POST `/bands/{bandId}/compositions/{id}/score-files/{fileId}/analyze` | Distribution / send-to-event (Epic 6) |
| Async polling endpoint GET `/bands/{bandId}/compositions/{id}/analysis?active=1` that returns the running job + artefact paths when done | AI retry strategy beyond windband-ai's own retry loop |
| Band isolation on all read AND write paths (fail-closed 409) | |

### What "AI analysis" means here
A `ScoreFile` (PDF or ZIP of PDFs) is fed to the external **windband-ai** pipeline. US-4.1's contract is: **start** that pipeline bound to one composition+file, and **expose** its artefacts (arrangement.json / .musicxml / .mid / validation.txt) when it completes.

> The windband-ai repo `/home/mbzowski/windband-ai` is the LLM orchestration layer — already done and tested. US-4.1 does not re-implement that; it **invokes** it from Java via a thin `AiAnalysisRunner` SPI (so tests can swap in a fake) and persists the artefacts.

## Acceptance Criteria

### [AC-1] Domain entity
- New JPA entity `ScoreAnalysis` in package `pl.michalbzowski.windband.domain.composition`:
  - `id` (BIGSERIAL PK), `composition_id` (FK → compositions, non-null)
  - `score_file_id` (BIGINT, the file that triggered this analysis)
  - `status` (STRING default `"PENDING"` — PENDING | RUNNING | SUCCEEDED | FAILED)
  - `runner_ref` (VARCHAR(255), e.g. PID or job id from the CLI process; null until started)
  - `error_message` (TEXT nullable — populated on FAILED only, user-visible, in Polish)
  - `arrangement_json_path`, `arrangement_musicxml_path`, `arrangement_mid_path`, `validation_txt_path` (VARCHAR(1024), NULL until SUCCEEDED)
  - `started_at` (TIMESTAMPTZ nullable), `finished_at` (TIMESTAMPTZ nullable)
  - `created_at` TIMESTAMPTZ NOT NULL (default `now()` at @PrePersist)
- Flyway migration **V41** — new table + index `(composition_id, created_at DESC)` + index on `(status)`.
- Factory methods: `ScoreAnalysis.pending(Long scoreFileId, Composition comp)`; `ScoreAnalysis.transitionTo(RUNNING, runnerRef)` / `.transitionTo(SUCCEEDED, artefacts)` / `.transitionTo(FAILED, errorMessage)` — all return a new instance (immutable state machine), illegal transitions throw `IllegalStateException`.

### [AC-2] AI runner SPI
```java
public interface AiAnalysisRunner {
    /** Launches the pipeline for a PDF file on disk. Must be non-blocking (returns after process.start()). */
    AnalysisProcess start(AnalysisRequest req);
    /** Polls the launched process; returns its status + extracted artefact paths when available. */
    AnalysisStatus inspect(Long runnerRef);

    record AnalysisRequest(String inputPath, String outputDir, long compositionId, long scoreFileId) {}
    record AnalysisStatus(Phase phase, java.nio.file.Path artefactRootIfAny, String errorMessage) {
        enum Phase { PENDING, RUNNING, SUCCEEDED, FAILED }
    }
}
```
- Production impl: `ProcessBuilderAiAnalysisRunner` (spawns `python3 /home/mbzowski/windband-ai/src/main.py -i {inputPath} -o {outputDir} --chunk-size 4` in a tracked background thread; writes stderr to `{outputDir}/stderr.log`; exit code maps to Phase).
- Test impl: `StubAiAnalysisRunner` (records calls, returns pre-seeded phases so ITs do not need Ollama).
- The runner SPI lives in application layer (`pl.michalbzowski.windband.application.command.scoreanalysis`), not adapter — ArchitectureTest is satisfied.

### [AC-3] Command service
```java
@Service @RequiredArgsConstructor
public class ScoreAnalysisCommandService {
    /** id of the composition's existing score file (PDF) that should be analysed. */
    public Long start(Long scoreFileId, Long bandId);   // returns new ScoreAnalysis.id
}
```
- Band isolation: `compositionRepository.findByIdAndBandId(compositionOf(file).getId(), bandId)` or throw `IllegalStateException` → 409 (fail-closed on cross-band or missing file).
- Reuses existing storage (`ScoreFile.storagePath`) as the input path.
- `outputDir = <scores.root>/analysis/{scoreAnalysis.id}/` — created at start, cleaned up by a follow-on cleanup job (out of scope for this story; tracked under Epic 7 ops).

### [AC-4] REST adapter (adapter-in-web)
- `POST /bands/{bandId}/compositions/{id}/score-files/{fileId}/analyze` — 202 Accepted + body `{"analysisId": <Long>}`.
- `GET  /bands/{bandId}/compositions/{id}/analysis/latest` — 200 with the latest `ScoreAnalysis` (whatever phase), or 404 if none has been started for this composition in that band.
- Errors: cross-band → 409, missing file → 404, malformed → 400 (all via `GlobalExceptionHandler`).

### [AC-5] Tests
- Unit: `ScoreAnalysis` state-machine test (every legal + illegal transition).
- Integration (against Testcontainers Postgres): `ScoreAnalysisCommandServiceIT`
  - `start_should_create_PENDING_row_and_link_score_file`
  - `start_should_fail_closed_on_cross_band_composition`
  - `start_should_reject_if_file_missing_for_band`
  - `transitionRunning_thenSucceeded_populates_artefact_paths` (with Stub runner)
  - `transitionFailed_records_error_message_in_Polish`
- REST adapter test (`MockMvc`): `StartScoreAnalysisRestControllerTest` — 202 happy path, 409 cross-band, 404 missing file, GET latest returns last row.
- **No** windband-ai calls in any of the above (Stub runner).

## Out of scope / parked for follow-on stories
- UI button + HTMX polling (US-4.1 is backend-only; the UI lives in US-4.2/US-4.5 + Epic 7)
- AI-preview review UI (US-4.5) — that's the *next* story and consumes this analysis's artefacts
- Clean up stale `outputDir` folders (ops concern, Epic 7)
- ZIP-with-multiple-PDFs analysis (one file per analysis is the MVP; multi-PDF batch is US-4.6 candidate)
- Async WebSocket / SSE updates — polling via HTTP GET is sufficient for v1

## Files to create
```
src/main/java/pl/michalbzowski/windband/domain/composition/ScoreAnalysis.java   (new, ~150 LOC)
src/main/java/pl/michalbzowski/windband/domain/composition/ScoreAnalysisRepository.java
src/main/java/pl/michalbzowski/windband/application/command/scoreanalysis/AiAnalysisRunner.java     (SPI)
.../application/command/scoreanalysis/ProcessBuilderAiAnalysisRunner.java             (~120 LOC)
.../application/command/scoreanalysis/StubAiAnalysisRunner.java                        (~40 LOC, test)
.../application/command/scoreanalysis/ScoreAnalysisCommandService.java                 (~90 LOC)
src/main/java/pl/michalbzowski/windband/application/query/scoreanalysis/ScoreAnalysisQueryService.java
.../application/query/scoreanalysis/LatestScoreAnalysisDto.java
src/main/java/pl/michalbzowski/windband/adapter/in/web/StartScoreAnalysisRestController.java         (~70 LOC)
.../adapter/in/web/GetLatestScoreAnalysisRestController.java                                        (~55 LOC)
src/main/resources/db/migration/V42__score_analysis_table.sql                                       (~25 LOC)

src/test/java/pl/michalbzowski/windband/domain/composition/ScoreAnalysisTest.java                    (state machine, ~130 LOC)
.../application/command/scoreanalysis/ScoreAnalysisCommandServiceIT.java                             (~250 LOC, 5 ITs)
.../adapter/in/web/StartScoreAnalysisRestControllerTest.java                                         (~180 LOC)
.../adapter/in/web/GetLatestScoreAnalysisRestControllerTest.java                                     (~140 LOC)
```

## Rollout order (TDD-style per windband-manager-workflow skill)
1. V41 migration + `ScoreAnalysis` + repository (green: `ScoreAnalysisTest`)
2. SPI + Stub + `ScoreAnalysisCommandService.start()` (green: `ScoreAnalysisCommandServiceIT`)
3. REST POST/GET + GlobalExceptionHandler wiring (green: adapter tests)
4. REAL impl `ProcessBuilderAiAnalysisRunner` — manual smoke on a real PDF in the sandbox, not covered by ITs
5. `./mvnw clean verify` (all 521+ tests green) → commit → push → PR

## Risks & mitigations
- **windband-ai binary path hard-coded to `/home/mbzowski/windband-ai`** — must not leak into production. Mitigation: read from env `WINDBAND_AI_BIN` (default `/usr/local/bin/windband-ai`); if null → `AiAnalysisRunner` throws 501 with a clear Polish message "Pipeline AI nie jest skonfigurowany".
- **Long-running process blocks the servlet thread** — never. `Process.start()` is non-blocking; we return immediately to the caller and the process runs in the JVM (tracked by `runner_ref`). Status is polled via GET.
- **Process leaks across JVM restarts** — mitigated by `ScoreAnalysis.status` + a startup hook that marks any RUNNING row older than 1 minute as FAILED("JVM restarted mid-analysis"). This hook lands as a follow-on if/when it is actually required; for US-4.1 the scope is "start+persist".
- **Band isolation bypass** — every read AND write path goes through `requireOwned` (the exact pattern from CompositionCommandService). Cross-band IT explicitly covers this.

## Definition of Done
- [ ] All files above exist with correct package + ArchUnit rule satisfied
- [ ] V41 migration applied cleanly on fresh DB
- [ ] `ScoreAnalysisTest` — every transition covered (legal + illegal)
- [ ] `ScoreAnalysisCommandServiceIT` — 5 green ITs (Stub runner, no Ollama)
- [ ] Adapter REST tests — 202/409/404 paths green
- [ ] `./mvnw clean verify` green (521+ tests), Checkstyle 0 violations, SpotBugs clean
- [ ] Real smoke test: run the pipeline on a sample MusicXML with the Stub runner swapped out for the ProcessBuilder impl — artefacts land in `output/analysis/{id}/`. (Manual step, not in CI, because it needs Ollama.)
