# Zmiana: Widok AI — jeden głos na raz, karty luk i twarda walidacja zakresów

Identyfikator: `2026-11-verify-ai-cardflow` · Branch: bezpośredni flow na `main` (ustalony wzór tej funkcji w tej sesji) · Data: 2026-11-20

## 1. Problem i cel
Bieżący widok „Raport spójności AI" (makieta C) pokazuje wszystkie karty głosów naraz — na mobile i na szerokości — co rozprasza wzrok i utrudnia decyzję głos-po-głosie. Zmiana zakresu stron nie aktualizuje podglądów, przycisk masowy kłamie („Przyjmij pozostałe bez zmian" ignoruje wpisaną korektę), a po skróceniu zakresu (np. Amerland: partytura 1–14 → 1–13) str. 14 po prostu znika z mapowania bez śladu. Dodatkowo serwer akceptuje zakłady stron, które nachodzą na istniejące partie utworu — raport flaguje to tylko jako błąd wizualny, ale nic nie blokuje zapisu. Po zmianie: jeden głos na raz z nawigacją ◀/▶ u dołu; mobilny widok bez nagłówka zasłaniającego ekran; podglądy stron śledzą wpisywane numery; przycisk masowy mówi prawdę; luki stają się kartami do oznaczenia instrumentem; nachodzące zakresy są niemożliwe do zapisu.

## 2. Kryteria akceptacji (obowiązkowe, mierzalne)
- [ ] GWT: Given raport z N głosami (N≥2), When otwieram widok — Then dokładnie jedna karta jest widoczna oraz pasek u dołu pokazuje „Głos 1 z N" z przyciskami Poprzedni/Następny (Następny działa, Poprzedni przy głosie 1 wyłączony).
- [ ] GWT: Given otwarta karta, When zmieniam „Strona do" na niższy numer — Then miniatura „ostatniej strony" zmienia src/alt na stronę o tym numerze bez przeładowania.
- [ ] GWT: Given przyjęte głosy nie pokrywają stron 1..pageCount, When kończę akceptacje — Then pojawi się karta luki z podglądem brakujących stron i wyborem instrumentu; jej akceptacja tworzy partię utworu dla tych stron (rola = nazwa wybranego instrumentu).
- [ ] GWT: Given istniejąca partia str. 1–13, When próbuję przyjąć głos o zakresie nachodzącym (np. 10–15) — Then serwer odrzuca (HTTP 400) z komunikatem wskazującym konflikt; nic nie zostaje zapisane.
- [ ] GWT: Given przycisk masowy, When klikam — Then dialog potwierdzenia informuje, że pozostali głosy wejdą z ORYGINALNYMI zakresami AI (korekty pominięte) i akcja odhacza wszystkie pozostałe karty.
- [ ] GWT: Given mobile viewport (<960px), When jestem na tym widoku — Then nagłówek (menu ☰, avatar, nazwa zespołu) jest ukryty, a dostępna pozostaje strzałka powrotna; po akceptacji karty przesuwa się automatycznie do następnej oczekującej.
- [ ] GWT: Given zaakceptowany uprzednio głos o dokładnym zakresie X, When przyjmuję go ponownie (masowo lub pojedynczo) — Then pozycja jest pominięta („partia już istnieje") bez błędu i bez duplikatu.

## 3. Grilling — rozstrzygnięte pytania (brak = zatrzymaj implementację)
| Pytanie | Odpowiedź | Kto rozstrzygnął |
|---|---|---|
| Jaki pasek nawigacji? | Prosty: ◀ Głos n/N ▶ u dołu widoku (mobile i wide identycznie) — formularz wyboru użytkownika wygasł; przyjęta rekomendacja „prosto" | Hermes (domyślna rekomendacja, zapisana w grillingu) |
| Co ukrywamy na mobile? | Cały `.dashboard-header` (menu ☰, avatar, nazwa zespołu) — tylko na tym widoku, przez CSS ograniczony do `.verify-ai-page`; strzałka powrotna = zjednoczona nagłówki strony | Hermes (domyślna rekomendacja) |
| Nowa nazwa przycisku masowego? | „Przyjmij pozostałe zgodnie z propozycją AI" + dialog potwierdzenia wyjaśniający pominięcie korekt | Hermes (domyślna rekomendacja) |
| Karty luk: mechanizm? | Karta luki wybiera instrument z zespołu; akceptacja tworzy partię (rola = nazwa instrumentu, PartSource=HYBRID, confidence 1.0, verified = użytkownik). Pokrycie liczone z: przyjętych głosów sesji + partii utworu powiązanych z plikiem nut tej analizy | Hermes (domyślna rekomendacja) |
| Walidacje twardo? | from≥1, to≤pageCount, from≤to (istnieją); NOWE: zakaz nachodzenia na jakąkolwiek partię utworu (również z wcześniejszych analiz) + zakaz nachodzenia w obrębie jednej partii; dokładne duplikaty = skip jak dziś | Użytkownik („nie dało się zapisać nachodzących zakresów") |
| Role/zespoły i uprawnienia? | Bez zmian: guard `belongsToTeam(bandId)` strony + `findByIdAndBandId` instrumentu; endpoint luki flagowany identycznie jak accept-corrected (404 gdy flaga OFF) | Istniejący mechanizm |
| Dane historyczne / migracja? | Brak migracji — nowe walidacje operują na istniejących wierszach `composition_instrument`; karty luk tworzą zwykłe partie | Hermes (brak danych do migrowania) |
| Kontrakt windband-inventory? | Dotknięty tylko nowy endpoint `POST .../analysis/{a}/gaps/assign` wewnątrz tej funkcji; inventory nie woła go | Hermes (weryfikacja: brak odwołań) |
| Co NIE jest w zakresie? | Edycja partii po zapisie (pozostaje „usuń i utwórz ponownie"), zmiany `PartSource`/audyt, raportowanie luk dla plików bez pageCount, inne widoki AI (legacy preview nie ruszamy), favicon 500 (osobny temat) | Użytkownik + Hermes |

## 4. Rozwiązanie (skrót techniczny)
- **UI (verify-ai.html)**: kolejka = jedna karta `.active` (display:none reszty); pasek nawigacji łączy licznik, ◀/▶, przycisk masowy (przeimowany) i licznik przyjęć; JS: `setActive(i)`, auto-advancem po akceptacji, debounce'owany live-preview swap miniatur (`/thumb?page=N`), po każdej akceptacji recompute „luk" z przyjętych zakresów + `existingPartsRanges` (nowe pole DTO) i renderingu kart luk (consecutive → intervaly); `confirm()` przed masowym.
- **Backend**: `AcceptAiProposalsCommandService` — wspólny guard overlapu (`ensureNoPageOverlap`) w `accept`/`acceptCorrected` vs wszystkie partie utworu + partie tej samej partii; NOWA komenda `assignGapPages` (valid range, pageCount, instrument band-a, overlap, dup skip) → `CompositionInstrument.forComposition` z rolą = nazwa instrumentu; `AiVerifyReportDto.existingPartsRanges` zapytanie (partie utworu powiązane z plikiem analizy, fallback: wszystkie partie gdy brak powiązań).
- **Ryzyka**: (a) istniejące testy mogą tworzyć nachodzące partie — będą je łapać nowe walidacje i trzeba je poprowadzić zgodnie z nową regułę; (b) `confirm()` w Selenium = accept alert; (c) ukryty nagłówek mobile = globalny fragment przez CSS scope'owany, więc wymaga testu iż tylko ten widok dotyczy.

## 5. Weryfikacja
- [ ] `scripts/agent-quick.sh` w pętli dev
- [ ] `scripts/agent-verify.sh` przed commitem (pełna clean verify)
- [ ] `scripts/mutation-check.sh 'pl.michalbzowski.windband.application.*'` — ruszane: command/query scoreanalysis
- [ ] Nowe testy: `AcceptAiProposalsCommandServiceTest` (+ overlap: odrzucenie, taż partia, dup), `...gaps.assign` (REST + serwis), `AiVerifyReportQueryServiceTest` (existingPartsRanges), `AiVerifyReportUiTest` (jedna karta, nawigacja, live preview, luka, przycisk masowy, hidden header mobile)
- [ ] `scripts/ci-status.sh` zielony po pushu + Railway deploy SUCCESS (prod = docelowy adres weryfikacji użytkownika)

## 6. Decyzje/odrzucone alternatywy
- Jedna karta na raz zamiast accordionu/wszystkich naraz — użytkownik wprost: „widoczny tylko jeden instrument na raz"; skróty-kafelek odrzucone (formularz) jako zbędne przy ◀/▶.
- Karty luk serwerowe (nowe DTO) a nie klienckie — odrzucone: pokrycie zmienia się dopiero po akceptacji (sesja), klient ma pełny stan; serwer dostaje tylko walidowaną komendę.
- Walidacja overlapu jako WARN zamiast ERROR — odrzucona: użytkownik „nie dało się zapisać"; spójne z domeną (OVERLAP=ERROR w ProposalConsistency).
- Rola luki = „(luka)" — odrzucone: `instrument_role` służy do mapowania członków; nazwa instrumentu ma sens downstream.
