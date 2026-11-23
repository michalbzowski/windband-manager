# Zmiana: Oznaczanie stron „nie wykorzystam" w raporcie spójności AI

Identyfikator: `2026-11-unused-pages-marking` · Branch: `feat/unused-pages-marking` · Data: 2026-11-23

## 1. Problem i cel
Utwór (np. „Amerland") po analizie AI ma wiele głosów dodatkowych, z których użytkownik
wiem, że nie będzie korzystał. Dziś każda strona pliku nut niepokryta przyjętą partią
powraca jako „luka" w raporcie spójności i musi zostać obsadzona instrumentem — a tag
wymusza wybory, które dla stron celowo nieużywanych są sztuczne. Po zmianie użytkownik
może oznaczyć dowolny zakres wolnych stron jako **„nie wykorzystam"**: znika z kolejki
luk, nie jest dłużej męczony „Następnym", a oznaczenie pozostaje w pełni edytowalne —
da się je cofnąć („Przywróć") i w dowolnym momencie przypisać tag/instrument.

## 2. Kryteria akceptacji (obowiązkowe, mierzalne)
- [ ] Given strona X utworu nie należy do żadnej partii When użytkownik na karcie luki
      klika „Nie wykorzystam" Then zakres jest trwale zapisany (kompozycja + plik nut +
      zakres + autor + czas), karta zmienia stan na „oznaczono: nie wykorzystam", a po
      odświeżeniu strony zakres NIE pojawia się jako luka.
- [ ] Given zakres 1–20 oznaczony jako niewykorzystywany When użytkownik „Przywraca"
      zakres 5–6 Then pozostałe wiersze oznaczenia dzielą się (stają się 1–4 i 7–20), a
      strony 5–6 wracają do kolejki jako luka.
- [ ] Given strony już pokryte przyjętą partią When próba oznaczenia ich „nie wykorzystam"
      Then 400 z czytelnym komunikatem (strony nie mogą być jednocześnie w głosie i
      nieużywane).
- [ ] Given strona oznaczona „nie wykorzystam" When użytkownik przypisuje jej instrument
      (karta luki po „Przywróć") akceptując partię pokrywającą ten zakres Then przysłone
      wiersze oznaczenia są usuwane/dzielone automatycznie i wynik akceptacji melduje,
      jakie zakresy anulowano.
- [ ] Given oznaczone zakresy When widok raportu się odświeża Then sekcja „Nie
      wykorzystam" wylistowuje je z autorem i datą oraz przyciskiem „Przywróć".
- [ ] Nawigacja „Następny" pomija kolumny oznaczone (nie są lukami) — użytkownik nie
      wraca do nich w kolejce.
- [ ] Zmiana działa przy włączonej i wyłączonej flamie `app.ai-verify-report.enabled`:
      przy OFF endpoints odpowiadają 404 tak jak reszta kontraktu widoku.

## 3. Grilling — rozstrzygnięte pytania (brak = zatrzymaj implementację)
| Pytanie | Odpowiedź | Kto rozstrzygnął |
|---|---|---|
| Gdzie akcja „nie wykorzystam"? | Wyłącznie na kartach luk (przycisk obok „Oznacz"); niezaakceptowane głosy AI i tak stają się stronami luk — jedno miejsce, jeden model danych. Użytkownik milczał w oknie decyzyjnym → agent przyjął rekomendację (rewersybilne). | agent (rekomendacja) |
| Tag na stronę już oznaczoną? | Auto czyści nachodzący znacznik i przypisuje tag — jeden krok, nowsza decyzja wygrywa, wynik melduje anulowane zakresy. | agent (rekomendacja) |
| Akceptacja partii nachodzącej na oznaczenie? | Auto czyszcze nachodzące znaczniki + komunikat w podsumowaniu akceptacji (akceptacja jest zdecydowanie silniejszą intencją). | agent (rekomendacja) |
| Gdzie widać oznaczone zakresy? | Nowa sekcja „Nie wykorzystam" w tym samym widoku raportu: zakres, autor, data + „Przywróć". | agent (rekomendacja) |
| Zakres wpływu poza raportem AI? | Wyłącznie raport spójności; partie głosów pozostają jedynym źródłem prawdy dla reszty aplikacji (dystrybucja, karty utworu). Kolejne miejsca dopnieszemy osobno przy potrzebie. | agent (rekomendacja) |
| Co z danymi / migracją Flyway? | Nowa tabela `score_page_exclusions` (V52), FK na compositions + score_files, unikalny indeks (composition, file, od, do), CHECK page_from<=page_to. Twarde usuwanie wiersza przy „Przywróć" — oznacza, zakres nie jest daną historyczną (audit = ostatni autor/czas). | agent |
| Uprawnienia? | Te same co akceptacja głosów (dostęp do zespołu przez kontroler; tożsamość `verifierOf` w audycie). | agent |
| Kontrakt API windband-inventory? | Nie dotknięty — nowy podrdział widoku AI w tym samym kontrolerze, bez zmian istniejących endpointów (rozszerzenie addytywne wyników: nowe pole `exclusionsCleared`). | agent |
| Co NIE jest w zakresie? | Wycofanie przyjętej partii, globalne „wyciszenie" luk w innych widokach, eksport/raporty nad oznaczeniami, migracja danych historycznych (tabela pusta po V52). | agent |

## 4. Rozwiązanie (skrót techniczny)
- **Domain**: encja `ScorePageExclusion` (composition FK CASCADE, scoreFile required,
  pageFrom/pageTo, markedBy/markedAt, created/updated) + repozytorium Spring Data;
  inwariante w domenie (zakres legalny, te same kompozycje/plik).
- **Application**: `ExcludePagesCommandService` — `markUnused` (guardy: zakres w pliku,
  brak pokrycia przez części), `restore`/`removeOverlap` (algebra zbiorów pod rozpad:
  usuń objęte, rozdziel częściowe nachodzenie, scal sąsiednie przy dodawaniu),
  używane też z `AcceptAiProposalsCommandService` (assignGapPages / acceptCorrected /
  accept czyszczą nachodzące znaczniki — wynik poszerzony o `exclusionsCleared`).
- **Query**: `AiVerifyReportDto` + `excludedRanges` (zakres, autor, czas) +
  `excludedRangesJson()`; `AiVerifyReportQueryService` ładuje po pliku nut analizy.
- **Adapter REST** (`ScoreAnalysisAiRestController`, ten sam szablon co `/gaps/assign`):
  `POST .../pages/exclude` i `POST .../pages/restore` — flaga OFF → 404; IAE→400,
  ISE→409 przez GlobalExceptionHandler. Ciała: `{pageFrom, pageTo}`.
- **Thymeleaf** (`verify-ai.html`): atrybut boot `data-excluded-ranges`; JS: luki =
  pageCount − pokryte − oznaczone; kolumna lukanie ma drugi przycisk „Nie wykorzystam";
  sekcja listy znaczników z „Przywróć"; toast dla `exclusionsCleared` z akceptacji.
- **Testy**: unit `ExcludePagesCommandServiceTest` (guardy + algebra znaczników),
  rozszerzenia testów akceptacji (czyszczenie znaczników przy assign/accept),
  UI `UnusedPagesUiTest` (oznacz → znikną lukie; przywróć częściowo → rozdzielone;
  tag na oznaczone → anulowane w wyniku).
- **Ryzyko**: dodanie pola do istniejących rekordów wyników (addytywne JSON — starczy
  dla istniejących klientów JS); dzielenie znaczników przy częściowym przywróceniu
  (pokryte testem jednostkowym PIT-em na pakiecie).

## 5. Weryfikacja
- [ ] `scripts/agent-quick.sh` (po każdej warstwie)
- [ ] `scripts/agent-verify.sh` (bramka przed commitem)
- [ ] `scripts/mutation-check.sh 'pl\.michalbzowski\.windband\.(domain|application)\.[a-z.]*scoreanalysis.*|pl\.michalbzowski\.windband\.domain\.composition.*'`
      (przeżywa mutant → popraw asercje, nie próg)
- [ ] Testy nowe: `ExcludePagesCommandServiceTest`, rozszerzenia
      `AcceptAiProposalsCommandServiceTest`, `UnusedPagesUiTest` — asercje na treści
      (komunikaty, zakresy po rozpadzie, JSON wyników), nie na „nie null"
- [ ] `scripts/ci-status.sh` zielony po pushu

## 6. Decyzje/odrzucone alternatywy
- Tabela stron vs kolumna na `composition_instruments`: odrzucone — strony bez głosu
  nie mają naturze części; „nie wykorzystam" to druga rzecz, nie wariant tagu (partia z
  fikcyjnym instrumentem zanieczyściłaby dystrybucję i listę głosów).
- Usunięcie głosów AI jako sposób oznaczenia: odrzucone — usuń propozycji z artefaktu
  jest operacją nad plikiem, nie daną kompozycji; oznaczenie stron jest jawnie edytowalne.
- Per-strona bitmapa: odrzucone — zakresy są naturalną jednostką raportu (od/do), mapa
  stron była by ciężka w audycie i przy rozpadzie znaczników.
