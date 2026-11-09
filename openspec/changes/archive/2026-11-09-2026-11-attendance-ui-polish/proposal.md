# Proposal — audyt UI: polska spójność i stany widoku obecności (`/rehearsals/{id}`)

## Właściwa propozycja (jedno zdanie)
Widok szczegółów próby mówi dwoma językami (selecty EN vs liczniki PL), nie odróżnia
statusów kolorem i używa emoji zamiast ikon — ujednolicamy warstwę prezentacyjną
bez zmian w logice domenowej ani API.

## Dlaczego to jest potrzebne
Przegląd UI (zrzuty 2026-11-08) wykazał niespójności psujące czytelność:
1. Opcje selecta obecności po angielsku ("No response/Present/Excused/Unexcused")
   przy polskich licznikach i modalu "Szybka obecność" na tej SAMEJ stronie.
2. "Present" i "Unexcused" wyglądają identycznie — kolor jest tylko w podsumowaniu.
3. Pasek "Status:" to emoji ✅🕓✕? bez etykiet i bez sygnalizacji, że to filtry
   (polityka ikon projektu: stroke SVG z `fragments/icons.html`, nie emoji).
4. Dwa formaty daty na jednej stronie ("niedziela, 8 listopada 2026" vs "2026-11-08").
5. Wiersz "Miejsce:" z pustą wartością wygląda jak błąd renderu.
6. Wisi " | " w stopce gdy build-version jest pusty.
7. Style inline w filtrach (łamanie konwencji frontendu), select na 100% szerokości
   <td> = ogromne wiersze; chipy nie do obsługi z klawiatury i bez aria-pressed.

## Co się zmienia
Wyłącznie warstwa prezentacyjna (Thymeleaf + CSS + atrybuty):
- `rehearsals/detail.html`: polskie etykiety opcji (wartości DB/`value` bez zmian),
  `data-current` (status zapisany) + `aria-label` na selectach, chipy statusów:
  ikony SVG + widoczne polskie etykiety + `role=button`/`tabindex`/`aria-pressed`
  z obsługą Enter/Space w dotychczasowym binderze; EXCUSED z klasy `declined`
  na `later` (bursztyn = semantyka "usprawiedliwiony", jak w `events`); data
  nagłówka przez `#temporals.format(..., 'pl')`; puste "Miejsce:" → podpowiedź;
  ➕ → `icons::plus`; style inline → klasy `.uber-filter-*`.
- `fragments/icons.html`: nowe fragmenty `clock`, `question` (polityka stroke).
- `fragments/layout.html`: separator " | " tylko gdy build-version aktywny.
- `events/detail.html`: wyłącznie ekstrakcja identycznych stylów inline do klas
  (geometria 1:1); chipy/events pozostają nietknięte — są obudowane testami.
- `static/css/app.css`: gęstość selecta (`max-width`, `margin-bottom:0` — bez
  ruszania wewnętrznych paddingów Pico), kolory po `[data-current]` (jasny+ciemny),
  klasy filtrów, rozmiar SVG w chipach 13px (bez zmiany wysokości linii — patrz
  nota ISSUE #119 o geometrii `#uber-filter-container`).

## Wpływ na kod
- Kontrakty zachowane: `value` opcji (NO_RESPONSE/PRESENT/EXCUSED/UNEXCUSED),
  `data-response-filter`, `.filter-count-*`, `#build-version`, `#open-invite-btn`
  (+ tekst "Zaproś"), selektory istniejących testów Selenium.
- Testy: nowy `RehearsalAttendancePolishUiTest` (4 scenariusze: PL etykiety+kolor
  po data-current, odporność auto-zapisu, chipy klawiatura/aria, data PL +
  puste Miejsce). Testy przejściowe (events chips, sticky #119) nie dotknięte.
- PIT: brak zmian w `domain/application/util` — próg mutacji nietknięty.
- Brak zmian API/DB/autoryzacji.
