# Tasks — 2026-11-unused-pages-marking

Kolejność = kolejność realizacji. Każdy task: mały, samodzielny, weryfikowalny.
Nie zaznaczaj [x] bez uruchomionej weryfikacji danego taska.

- [x] T1. Migracja Flyway `V52__create_score_page_exclusions.sql` — tabela + indeksy
      (unikat composition/file/od/do, CHECK od<=do, FK compositions CASCADE,
      score_files NO ACTION)
- [x] T2. Encja `ScorePageExclusion` + repozytorium `ScorePageExclusionRepository`
      - [x] agent-quick.sh po warstwie domain zielone
- [x] T3. `ExcludePagesCommandService`: markUnused (guardy) + algebra znaczników
      (restore/removeOverlap z dzieleniem i scalaniem, czyste metody statyczne testowalne)
      - [x] agent-quick.sh zielone
- [x] T4. `AiVerifyReportDto` + `excludedRanges`/`excludedRangesJson`;
      `AiVerifyReportQueryService` ładuje po pliku analizy
      - [x] agent-quick.sh zielone
- [x] T5. `AcceptAiProposalsCommandService`: akceptacja/assign czyszcze nachodzące
      znaczniki; wynik + `exclusionsCleared`; aktualizacja istniejących konstrukcji
      rekordów
      - [x] agent-quick.sh zielone (w tym testy akceptacji)
- [x] T6. REST: `POST /pages/exclude` + `/pages/restore` w `ScoreAnalysisAiRestController`
      (flaga OFF → 404, ciało `{pageFrom, pageTo}`, audyt verifierOf)
      - [x] agent-quick.sh zielone
- [x] T7. Thymeleaf `verify-ai.html`: data-excluded-ranges; luki bez oznaczonych stron;
      przycisk „Nie wykorzystam" na kolumnach; sekcja znaczników + „Przywróć"; toast
      exclusionsCleared
- [x] T8. Testy: `ExcludePagesCommandServiceTest` (guardy, rozdzial, pokrycie, bandy),
      rozszerzenia testów akceptacji (czyszczenie przy assign/accept/zmianie zakresu),
      `UnusedPagesUiTest` E2E (oznacz → luka znika; przywróć środkowy kawał → dwa wiersze;
      tag na oznaczone → wynik melduje unieważnienie)
      - [x] agent-quick.sh zielone z nowymi testami
- [x] T9. Bramki: `scripts/agent-verify.sh` (cały verify + stamp),
      `scripts/mutation-check.sh` na ruszonych pakietach (przeżyj mutant → napraw asercje)
- [x] T10. Git (per domena): commit PL, rebase, `pre-push-check.sh`, push HEAD→main,
      `ci-status.sh` do zieleni
- [ ] T11. Archiwizacja `openspec/changes/2026-11-unused-pages-marking` → specs (po green CI)
