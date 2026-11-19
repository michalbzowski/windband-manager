# Zmiana: Raport spójności propozycji AI + akceptacja z korektą zakresów

Identyfikator: `2026-11-ai-verify-report` · Branch: `feat/ai-verify-report-m1` · Data: 2026-11-19

## 1. Problem i cel
Po zakończonej analizie AI użytkownik widzi tablicę głosów (głos / strony / pewność / instrument)
i może ją zaakceptować **wyłącznie dosłownie**: akceptacja wiersza z innym zakresem niż zaproponował
AI kończy się 400 (`AcceptAiProposalsCommandService` wymusza `pageFrom/pageTo` identyczne z artefaktem).
Nie ma też żadnego widoku globalnej spójności podziału (nakładania, luk, głosów bez odpowiednika w
zespole) — problemy wychodzą dopiero po akceptacji. Wymaganie: ekran raportu spójności (mobile-first),
który pokazuje głosy + wykryte nieścisłości i pozwala przed akceptacją poprawić **zakres** (od–do)
oraz **tag/instrument**; wszystko za prosta flagą funkcji, bez ingerencji w dotychczasowy flow.

## 2. Kryteria akceptacji (obowiązkowe, mierzalne)
- [ ] Given analiza SUCCEEDED z 5 głosami (jeden nakłada się z innym, jeden poza pageCount, jeden bez instrumentu zespołu), When użytkownik otwiera raport spójności, Then widzi per-wierszowe problemy (nakładanie + partner, przekroczenie stron, brak mapowania) oraz podsumowanie liczb; flaga wyłącza widok (link nie istnieje, endpoint 404).
- [ ] Given wiersz z problemem zakresu (AI: 10–17), When użytkownik ustawi 10–16 i zaakceptuje, Then powstaje `CompositionInstrument` 10–16 ze `PartSource.HYBRID` i `verifiedBy` ustawionym; wiersz zaakceptowany dosłownie z instrumentem zgodnym z rezolwerem dostaje `PartSource.AI`.
- [ ] Given wiersz zaakceptowany z zakresem wykraczającym poza `ScoreFile.pageCount`, When submission, Then 400 (IAE) i żaden wiersz się nie tworzy.
- [ ] Given głos którego zakres się nakłada z innym zaakceptowanym w tym samym submitcie, When submission, Then drugi wiersz (kolejnościowo później) jest SKIPPED, a nie nadpisuje pierwszego (reguła 1:1 rola→partia zachowana).
- [ ] Given dotychczasowy endpoint `POST .../analysis/{id}/accept` z zakresem różnym od propozycji AI, When submission (po zmianie), Then nadal 400 — stare flow zachowuje swój kontrakt (brak regresji dla istniejącego UI).
- [ ] Given raport spójności przy analizie z 0 głosami (stub runner), When otwarcie widoku, Then pusty stan "brak głosów" bez błędu i z komunikatem symulacji.

## 3. Grilling — rozstrzygnięte pytania
| Pytanie | Odpowiedź | Kto rozstrzygnął |
|---|---|---|
| Które role zespołu widzą tę funkcję? | Uwierzytelnieni użytkownicy z dostępem do bandu (ten sam guard `requireBandAccess` co istniejący flow); brak nowych ról | użytkownik + kod bazowy |
| Co z danymi historycznymi / migracją Flyway? | Brak zmian schematu; brak migracji | rozmowa 2026-11-19 |
| Jaki jest zachowanie przy braku uprawnień? | 403/redirect login zgodnie z `anyRequest().authenticated()` + guard bandu (jak sąsiednie ekrany) | kod bazowy |
| Czy to zmienia kontrakt API używany przez windband-inventory? | Nie — nowe endpointy w WM-UI; `/proposals` i `/accept` niezamienione (stary kontrakt w testach) | rozmowa 2026-11-19 |
| Co NIE jest w zakresie (v1)? | Dowody tekstowe/OCR, flaga "zweryfikowane" na partii, snapshot/rollback, nieciągłe zakresy (ontologia `CompositionInstrument`), regeneracja z feedbackiem — M2 po pomiarach | rozmowa 2026-11-19 (analiza krytyczna) |
| Jak wygląda feature switch? | `app.ai-verify-report.enabled` (default `false`; @Value jak `inventory-write-freeze`; link w detalu warunkowy przez advice model-atributów) | użytkownik ("prosty switch") |

## 4. Rozwiązanie (skrót techniczny)
- **Czysty moduł spójności** (`application/query/scoreanalysis/ProposalConsistency`): funkcje statyczne — detekcja nakładania się zakresów (per para, z partnerem i stronami), luk między kolejnymi zakresami, przekroczenia `pageCount`; wynik = rekord per wiersz + statystyki. Zero zależności od Spring-a → easy-to-test + PIT.
- **Query service** `AiVerifyReportQueryService`: na bazie istniejącego `ScoreAnalysisProposalsQueryService.preview()` (bez dotykania starej klasy) dodaje `pageCount` z `ScoreFile`, `fileId`, id analizy i wyniki spójności; select instrumentów z DTO podglądu.
- **Akceptacja z korektą**: nowa metoda `acceptCorrected(...)` w `AcceptAiProposalsCommandService` + rekord `AcceptedRowCorrected(role, pageFrom, pageTo, instrumentId)`. Walidacje: znana rola w artefakcie (proweniencja), zakres w `[1..pageCount]`, instrument z bandu, skok na istniejącą partię. `PartSource.HYBRID` gdy zakres różny od propozycji AI; reguła AI/HYBRID przez rezolwer w pozostałych przypadkach (identyczna logika jak dziś dla dosłownych wierszy). Metoda `accept(...)` NIE ulega zmianie (regresyjna 400-powyższą kontrakt).
- **Endpointy**: `GET /bands/{b}/compositions/{c}/verify-ai` (wydajanie widoku; analizę = ostatnia SUCCEEDED z bandu — wzorzec `latestFor`) i `POST /bands/{b}/compositions/{c}/analysis/{a}/proposals/accept-corrected`. Oboje za flagą (`FlagOff → 404` w kontrolerze REST, brak linku w UI).
- **Widok** `templates/compositions/aiVerifyReport.html`: Pico + tokeny istniejące, mobile-first, polski, zero emoji; wiersz = nazwa głosu AI, select instrumentu (domyślnie rozstrzelony), od/do jako inputs number ze stepperami +/- (vanilla JS ≤ 40 linii), badge nieścisłości po polsku, miniatury str. pierwszej i Ostatniej zakresu przez istniejący endpoint thumb (`/bands/{b}/compositions/{c}/files/{f}/thumb?page=N`); stopka: "Akceptuj zaznaczone (z korektami)".
- **Flaga**: `app.ai-verify-report.enabled:false` w `application.yml` + `FeatureFlagsModelAdvice` (ModelAttribute `aiVerifyReportEnabled`) — wzorzec `ExternalLinksModelAdvice`; warunkowy link pod tablicą AI w `detail.html` (jeden fragment).
- Ryzyka: PIT na pakiety application (czyste funkcje = łatwe zabicie mutantów); Selenium UI w v1 ograniczone do renderu + jednego cyklu akceptacji; flak timingowe wg skilla GA.

## 5. Weryfikacja
- [ ] `scripts/agent-quick.sh` (loop)
- [ ] `scripts/agent-verify.sh` (bramka przed commitem)
- [ ] `scripts/mutation-check.sh 'pl.michalbzowski.windband.application.query.scoreanalysis.*'` (bramka fake-coverage; dodatkowo pakiet command.scoreanalysis)
- [ ] Nowe testy: `ProposalConsistencyTest` (nakładania/luka/przekroczenie/czysty), rozszerzone testy komendy acceptCorrected (HYBRID vs AI, 400 pageCount, skip istniejącego), MockMvc dla GET flaga on/off + POST korekta
- [ ] `scripts/ci-status.sh` zielony po pushu

## 6. Decyzje/odrzucone alternatywy
- Flaga przez @Value (jak `inventory-write-freeze`), a nie nowa infrastruktura feature-flag — "prosty switch" wg wymagań.
- Nowa metoda akceptacji zamiast rozszerzania `AcceptedRow` — stary kontrakt + jego testy pozostają zamknięte; PIT widzi dwie ścieżki osobno.
- Reużenie `preview()` w nowym query service, nie refaktoring starej klasy — mniejsze drzewo zmian, zero ryzyka dla istniejącego flow.
- Bez zmiany ontologii zakresów (1 głos = 1 przedział) — rozszerzenie modelu domenowego to decyzja samostana z grillingiem; v1 pracuje w ramach istniejącego `CompositionInstrument`.
- Bez OCR/dowodów tekstowych w v1 — najpierw pomiar dystrybucji błędów (analiza 2026-11-19), potem M2.
