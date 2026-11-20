# Tasks — 2026-11-verify-ai-cardflow

Kolejność = kolejność realizacji. Każdy task: mały, samodzielny, weryfikowalny.
Nie zaznaczaj [x] bez uruchomionej weryfikacji danego taska.

- [x] T1. Backend: guard overlapu `ensureNoPageOverlap` w `AcceptAiProposalsCommandService` (vs partie utworu + taż partia); podpiąć do `acceptCorrected` i `accept`; komunikaty PL z nazwą roli/zakresem konfliktu
- [x] T2. Backend: komenda `assignGapPages(compositionId, bandId, analysisId, pageFrom, pageTo, instrumentId, verifier)` + endpoint `POST /bands/{b}/compositions/{c}/analysis/{a}/gaps/assign` (flag 404, guardy: zakres≤pageCount, instrument w zespole, overlap, skip duplikatu) → partia rolą = nazwa instrumentu, HYBRID, confidence 1.0, verify()
- [x] T3. Backend: `AiVerifyReportDto.existingPartsRanges` (list of from/to) — partie utworu powiązane z plikiem analizy (fallback wszystkie); build w `AiVerifyReportQueryService`
- [x] T4. Testy jednostkowe: serwis komend (overlap reject/taj partii/dup skip/gap happy + 3 nieudane), REST contract (404 flag off, 201/200 gap assign, 400 overlap), query service (existingPartsRanges)
- [x] T5. UI: jedna karta aktywna + pasek ◀ n/N ▶ + auto-next; przycisk masowy „Przyjmij pozostałe zgodnie z propozycją AI" + confirm(); live-preview miniatur po zmianie od/do
- [x] T6. UI: karty luk (recompute pokrycia po każdej akceptacji; intervaly; wybór instrumentu; podgląd stron; akceptacja → POST gaps/assign)
- [x] T7. UI: mobile — ukryć `.dashboard-header` tylko na tym widoku (CSS scope'owany)
- [x] T8. Testy UI (Selenium, *UiTest): nawigacja, jedyna widoczna karta, live preview (src/alt), luka po skróceniu zakresu (tworzenie + zanik), przycisk masowy (nowa nazwa, confirm, odhaczanie), dup skip, hidden header mobile
- [x] T9. Bramki: agent-quick.sh w pętli → agent-verify.sh przed commitem; mutation-check 'pl.michalbzowski.windband.application.*'
- [x] T10. Commit + pre-push-check + push main + ci-status do zieleni + Railway SUCCESS (prod)
- [x] T11. Archiwizacja openspec (npx @fission-ai/openspec archive 2026-11-verify-ai-cardflow)
