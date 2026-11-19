# Tasks — 2026-11-ai-verify-report

Kolejność = kolejność realizacji. Każdy task: mały, samodzielny, weryfikowalny.
Nie zaznaczaj [x] bez uruchomionej weryfikacji danego taska.

- [x] T1. `ProposalConsistency` (czyste funkcje statyczne) + `ProposalConsistencyTest` — nakładania z partnerem, luka między zakresami, przekroczenie pageCount, czysty zestaw, pusty zestaw; weryfikacja: `TEST=ProposalConsistencyTest scripts/agent-quick.sh` (12/12 zielone 2026-11-19)
- [x] T2. `AiVerifyReportQueryService` (preview + pageCount/fileId + spójność) + test z artefaktem tymczasowym (wzorzec istniejącego testu query service); weryfikacja: `TEST=AiVerifyReportQueryServiceTest scripts/agent-quick.sh` (8/8 zielone 2026-11-19)
- [x] T3. `acceptCorrected(...)` w `AcceptAiProposalsCommandService` (rekord `AcceptedRowCorrected`, walidacje, HYBRID przy korekcie) + testy (HYBRID vs AI, 400 pageCount, skip istniejącej partii, stary `accept` bez zmian); weryfikacja: `TEST=AcceptAiProposalsCommandServiceTest scripts/agent-quick.sh` (19/19 zielone 2026-11-19)
- [x] T4. `FeatureFlagsModelAdvice` (`app.ai-verify-report.enabled`, default false) + linia właściwości w `application.yml`; kontroler REST `POST .../proposals/accept-corrected` (flag off → 404) + MockMvc testy; weryfikacja: `TEST=ScoreAnalysisAiRestControllerTest scripts/agent-quick.sh` (6/6 zielone 2026-11-19)
- [x] T5. `AiVerifyReportPageController` GET `/bands/{b}/compositions/{c}/verify-ai` (last-SUCCEEDED analiza, guard bandu; flag off → 404) + szablon `compositions/verify-ai.html` (mobile-first Pico, korekta od–do + wybór instrumentu per głos, badge' błędów/ostrzeżeń, zero emoji) + link warunkowy w `detail.html`; weryfikacja: `TEST=AiVerifyReportPageControllerTest scripts/agent-quick.sh` (4/4 zielone 2026-11-19)
- [x] T6. UI test (webui) — flag on: widok raportu renderuje głosy + błąd/ostrzeżenie przy właściwych rolach, akcept korektowanego zakresu tworzy partię HYBRID, masa z głosem poza pageCount → rollback, głosy poprawne → przyjęte; bez analizy: brak linku + stan pusty; weryfikacja: `TEST=AiVerifyReportUiTest scripts/agent-quick.sh` (2/2 zielone 2026-11-19)
- [ ] T7. Bramki: `scripts/agent-verify.sh` zielony; `scripts/mutation-check.sh 'pl.michalbzowski.windband.application.query.scoreanalysis.*'` i `...command.scoreanalysis.*` (przeżyły mutant = napraw test, nie próg)
- [ ] T8. Commit (PL, `feat(score)...`), rebase na origin/main, `scripts/pre-push-check.sh`, push, `scripts/ci-status.sh` do zieleni
- [ ] T9. Archiwizacja `openspec/changes/2026-11-ai-verify-report` po wdrożeniu
