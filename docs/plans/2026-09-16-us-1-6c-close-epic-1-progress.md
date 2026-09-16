# Progress — US-1.6c (close Epic 1) + US-1.5 (ScoreFile / storedPath)

**Source plan:** `docs/plans/2026-09-15-us-1-6c-close-epic-1-plan.md`
**Session:** 2026-09-16, Hermes Agent. Single repo @ `/home/mbzowski/windband-manager`.

---

## Status Legend
- `[ ]` not started
- `[-]` in progress (see details below the task)
- `[x]` DONE — green CI + merge (or PR created, awaiting)
- `[!]` BLOCKER — see details

---

## Task Checkboxes

### US-1.6c tasks (D1–D5)
- [ ] Zadanie 1 — `BandQueryService.getRequiredBand(bandId)` (D5)
- [ ] Zadanie 2 — CQRS services route band-check via BandQueryService (D5)
- [ ] Zadanie 3 — `CompositionCommandService.deleteComposition(id, bandId)` (D2)
- [ ] Zadanie 4 — `getCompositionWithParts` + 3 DTOs (D1)
- [ ] Zadanie 5 — `CompositionCommandService.verifyCompositionParts` gate (D3)
- [ ] Zadanie 6 — Mockito *unit* tests for CQRS services (D4)

### US-1.5 — ScoreFile / storedPath completion
- [ ] Inspect `ScoreFile` entity + current state (V35 was seeded by earlier PR; verify what's missing)
- [ ] Confirm what US-1.5 AC requires (read user story in `docs/us/biblioteka-utworow-user-stories.md`)
- [ ] Gap analysis: missing fields / migration / service methods / query DTOs
- [ ] TDD RED → GREEN each sub-gap, separate commit per task
- [ ] PR + CI

---

## Per-task progress (append-only)

### 2026-09-16 — Session start
- Initial git state observed:
  - Head branch at session start: `feat/instrument-role-map-us14`
  - Untracked files in tree (likely from earlier unfinished session):
    - `docs/plans/2026-09-15-us-1-6c-close-epic-1-plan.md` (the plan — commit if not yet)
    - `src/main/java/pl/michalbzowski/windband/application/command/composition/ScoreFileUploadPort.java`
    - `.worktrees/` (kanban lanes; MUST be left alone, NEVER `git add .` from root when it exists)
- Skills loaded: windband-coding-standards, windband-manager-development,
  windband-manager-frontend, windband-manager-workflow, github-code-review,
  requesting-code-review.
- Key workflow commitments (re-check before every push):
  1. `git add <exact-files>` (NO `git add .` when `.worktrees/` exists)
  2. `./mvnw clean verify` → BUILD SUCCESS
  3. `git commit -m "..."` (message per plan)
  4. `git fetch --prune` + `rebase origin/main` (NO branch switching mid-PR flow)
  5. re-verify after rebase → push
  6. `gh pr create --title ... --body "..."` then `gh run watch --exit-status`
  7. Do NOT use `git push --no-verify`. NO pre-push hook required (memory says it was
     removed) — the second local `./mvnw clean verify` is the real gate.
  8. Selenium UI tests are mandatory per windband-coding-standards — for pure
     service-layer methods this is satisfied by MockMvc + ITs (no page changes).
  9. For US-1.5: may touch `V40+` migration if needed, but only after user-story reading.

### Next action
Branch decision → clean tree check → START Zadanie 1.
