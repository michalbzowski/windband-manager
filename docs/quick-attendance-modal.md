# Szybka obecność (Quick Attendance Modal)

Funkcjonalność szybkiego wprowadzania obecności na próbach, dostępna ze szczegółów
spotkania (`/rehearsals/{id}`). Pozwala zaznaczyć obecność wszystkich członków zespołu
przy **minimalnej liczbie kliknięć** — po jednym na członka.

---

## 1. Cel i założenia

- Dyrygent/administrator otwiera próbę i chce szybko odhaczyć kto przyszedł, bez
  przewijania długiej tabeli i bez formularza "wybierz z listy + zatwierdź".
- Każdy członek jest pokazywany **osobno**, z czterema dużymi przyciskami statusu.
- Kliknięcie przycisku **natychmiast zapisuje** obecność i przechodzi do następnej osoby.
- W razie pomyłki można cofnąć się do poprzedniego członka.
- Zapis dzieje się **przed** przejściem dalej (save-then-advance), więc awaria sieci
  pod koniec listy nie powoduje utraty danych wcześniejszych osób.

---

## 2. Jak z tego korzystać (UX)

1. Wejdź w **szczegóły próby** (przycisk "Szczegóły" na liście prób).
2. Kliknij **"⚡ Szybka obecność"** (nowy przycisk obok "Zapisz obecność").
3. Otwiera się modal pokazujący **pierwszego członka** i postęp `1 / N`:
   - Imię i nazwisko członka
   - 4 przyciski: `✓ Obecny` · `🕓 Usprawiedliwiony` · `✕ Nieobecny` · `? Brak odp.`
   - Przycisk `← Wstecz` (nieaktywny dla pierwszej osoby) i `Anuluj`
4. Kliknij odpowiedź → następuje zapis i modal pokazuje **kolejną osobę** (`2 / N`).
5. Powtarzaj aż do ostatniej osoby. Po zapisie ostatniej:
   - pojawia się toast `✓ Zapisano obecność`,
   - modal się zamyka,
   - widok szczegółów próby odświeża się, a tabela obecności odzwierciedla wprowadzone
     wartości.
6. W dowolnym momencie `← Wstecz` cofa do poprzedniej osoby (można poprawić wybór).
7. `Anuluj` lub kliknięcie poza modal zamyka go bez zapisywania **pozostałych** osób
   (te już zapisane pozostają zapisane — zapis jest przyrostowy).

---

## 3. Przepływ danych

```
[Szczegóły próby]
   │
   ▼  kliknięcie "⚡ Szybka obecność"
openQuickAttendance()
   │  czyta #rehearsals-content tbody tr → lista {id, name, status}
   ▼
<dialog id="quick-attendance-modal">  (openAppModal)
   │  pokazuje members[index]
   ▼  kliknięcie przycisku statusu
quickAttendanceSelect(status)
   │  POST /api/rehearsals/{id}/attendance  {rehearsalId, memberId, status}
   │  (fetchWithToast, showSuccessToast:false)
   ▼
[odpowiedź OK]  →  sync <select> w tabeli, index++, render następnej osoby
[odpowiedź ERROR] → Toast.error, BRAK advance (zostań na osobie)
   │
   ▼  gdy index == ostatni
Toast.success + closeAppModal + htmx.ajax(GET /rehearsals/{id})  → odświeżenie tabeli
```

Kolejność członków w modalu = kolejność wierszy w tabeli na stronie szczegółów
(`#rehearsals-content tbody tr`). Pokazywani są **wszyscy aktywni członkowie zespołu**,
włącznie z tymi mającymi obecnie status `NO_RESPONSE`, żeby nic nie umknęło.

---

## 4. Kontrakt z backendem

Funkcjonalność nie wymagała nowych endpointów — wykorzystuje istniejący:

```
POST /api/rehearsals/{id}/attendance
Content-Type: application/json

{
  "rehearsalId": <long>,
  "memberId":    <long>,
  "status":      "PRESENT" | "EXCUSED" | "UNEXCUSED" | "NO_RESPONSE"
}
```

- Mapuje na `RecordAttendanceCommand` i `RehearsalController.recordAttendance()`.
- Zachowanie: **upsert** — jeśli rekord obecności dla pary (próba, członek) już istnieje,
  zostaje zaktualizowany (`Rehearsal.updateAttendance()`); w przeciwnym razie tworzony
  (`Rehearsal.recordAttendance()`). Dzięki temu wielokrotne kliknięcie dla tego samego
  członka jest idempotentne.
- CSRF: wyłączone w produkcji (zgodnie z `SecurityConfig`); w testach również.
- `fetchWithToast` dodaje automatycznie toast błędu przy non-2xx; sukcesy są
  wyciszone (`showSuccessToast: false`), bo zbiorczy toast pokazujemy po zamknięciu.

---

## 5. Zachowanie w scenariuszach brzegowych

| Sytuacja | Zachowanie |
|---|---|
| Brak członków w zespole | `Toast.error('Brak członków do odznaczenia')`, modal się nie otwiera. |
| Błąd zapisu (sieć / 5xx) | `Toast.error('Błąd zapisu: ...')`, **modal zostaje na bieżącej osobie** — można spróbować ponownie. Brak utraty wcześniejszych zapisów. |
| Kliknięcie "Wstecz" na 1. osobie | Przycisk nieaktywny (`disabled`). |
| Anulowanie w połowie | Modal się zamyka; osoby już zapisane pozostają zapisane (zapis przyrostowy). |
| Zmiana zdania po zapisie | Można otworzyć modal ponownie i kliknąć inny status — upsert nadpisze wartość. |
| Członek z już ustawionym statusem | Modal podświetla jego obecny status (przycisk z klasą `selected`); ponowne kliknięcie go potwierdza/aktualizuje. |

---

## 6. Struktura kodu

### Szablon — `src/main/resources/templates/rehearsals/detail.html`

- Przycisk otwierający:
  ```html
  <button type="button" class="primary" id="quick-attendance-btn"
          onclick="window.openQuickAttendance()">⚡ Szybka obecność</button>
  ```
- Modal (`<dialog class="app-modal">` zgodny z wzorcem z `fragments/layout.html`):
  - `#qa-progress` — tekst `i / N`
  - `#qa-member-name` — imię bieżącego członka
  - `.qa-status[data-status="..."]` — 4 przyciski statusu
  - `#qa-back` — przycisk Wstecz
- Skrypty (globalne, `window.*`):
  - `openQuickAttendance()` — zbiera członków z tabeli, resetuje stan, otwiera modal
  - `quickAttendanceRender()` — rysuje bieżącą osobę + postęp + podświetlenie
  - `quickAttendanceSelect(status)` — zapis + advance / zamknięcie
  - `quickAttendanceBack()` — cofnięcie

Stan modału trzymany jest w `window._qa = { members: [...], index, rehearsalId }`.

### Zależności od istniejących helperów (layout / windband-utils.js)
- `openAppModal(id)` / `closeAppModal(dlg)` — otwieranie/zamykanie native `<dialog>`.
- `fetchWithToast(url, opts)` — zapisy z automatycznym tostem błędu.
- `Toast.success/error` — powiadomienia.
- `#toast-container` — wymagany do wyświetlania toastów (wstawiony przez
  `th:insert="~{fragments/layout :: toast-container}"`).

---

## 7. Testy

- **`QuickAttendanceModalUiTest`** (Selenium, `UiTestBase`) — pełny flow przez
  przeglądarkę:
  1. Tworzy członka + próbę.
  2. Otwiera szczegóły (pełny page-load, by skrypt i `openAppModal` były dostępne).
  3. Klika "⚡ Szybka obecność" → modal otwarty, postęp `1 / N`.
  4. Klika `Obecny` → postęp `2 / N` (save-then-advance).
  5. Klika `Wstecz` → postęp `1 / N`, przycisk nieaktywny.
  6. Klika `Obecny` → `2 / N`, znowu `Obecny` → `3 / N` (ostatni).
  7. Klika `Obecny` (ostatni) → modal zamknięty + toast `Zapisano obecność`.
  8. Przeładowuje stronę → wszystkie `<select>` mają wartość `PRESENT` (persist).
- Pokrycie jednostkowe/integracyjne backendu: istniejące testy `RehearsalController`
  (upsert attendance) — modal ich nie zmienia.

Uruchomienie:
```bash
mvn test -Dtest=QuickAttendanceModalUiTest
```

---

## 8. Powiązane dokumenty

- `PROJECT_DOCS.md` → sekcja *Rehearsal Detail View* (opis widoku szczegółów + tego modału).
- `PROJECT_DOCS.md` → *Required page fragments* (dlaczego `#toast-container` jest
  niezbędny do działania toastów).
- Endpointy REST: `POST /api/rehearsals/{id}/attendance` w tabeli *API Endpoints*.
