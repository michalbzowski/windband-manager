# Sortowanie i wyróżnianie list (Spotkania / Wydarzenia)

Listy prób (spotkań) i wydarzeń są podzielone na dwie sekcje według daty
względem dzisiejszego dnia (`LocalDate.now()`):

- **Nadchodzące** — data ≥ dzisiaj. Sortowane **rosnąco** (najbliższa data na górze).
- **Przeszłe** — data < dzisiaj. Renderowane **na końcu** listy, sortowane
  **malejąco** (najmłodsze/nowsze przeszłe najwyżej w sekcji przeszłych).
  Wyróżnione graficznie: wiersz ma klasę `.past-item` (obniżona przezroczystość)
  oraz odznakę **"Odbyło się"** (`.past-badge`).

---

## 1. Przepływ danych

```
Controller.listPage / listFragment
   │  model: upcomingRehearsals, pastRehearsals  (lub *Events)
   ▼
QueryService.getUpcomingRehearsals(teamId) / getPastRehearsals(teamId)
   │  loadSortedAsc(teamId)  (repo.findAllOrderByDateDesc* + stream sort ASC)
   │  filter:  upcoming -> !date.isBefore(today)        (today + future)
   │           past     ->  date.isBefore(today)         (strictly before)
   │  past sort: Comparator.comparing(date).reversed()   (newest first)
   ▼
Template (rehearsals/list.html, events/list.html)
   │  dwie sekcje <table> w <div th:if="not #lists.isEmpty(...)">
   │  sekcja przeszła: <tr class="past-item"> + <span class="past-badge">Odbyło się</span>
```

Granica "dzisiaj": wydarzenie z datą **dzisiejszą** traktowane jest jako
nadchodzące (aktywne). Tylko daty ściśle wcześniejsze niż dzień dzisiejszy
wpadają do sekcji przeszłej.

---

## 2. Warstwa backend

### `RehearsalQueryService`
- `getUpcomingRehearsals(Long teamId)` — przyszłe, ASC.
- `getPastRehearsals(Long teamId)` — przeszłe, DESC.
- prywatne `loadSortedAsc(teamId)` ładuje `findAllOrderByDateDesc*` i sortuje
  rosnąco przez `Comparator.comparing(Rehearsal::getDate)` (brak zmian w repo).

### `EventQueryService`
- `getUpcomingEvents(Long teamId)` / `getPastEvents(Long teamId)` — analogicznie
  dla `BandEvent`.

### Kontrolery
- `RehearsalPageController.listPage` / `listFragment` →
  `model.addAttribute("upcomingRehearsals", ...)` + `"pastRehearsals", ...`
- `EventPageController.listPage` / `listFragment` →
  `model.addAttribute("upcomingEvents", ...)` + `"pastEvents", ...`

> Stare atrybuty modelu (`rehearsals`, `events`) zostały zastąpione parą
> `upcoming*` / `past*`. Szablony list muszą używać nowych nazw.

---

## 3. Warstwa frontend

### Szablony `rehearsals/list.html` / `events/list.html`
- Dwie niezależne sekcje `<div th:if="not #lists.isEmpty(upcomingX)">` i
  `<div th:if="not #lists.isEmpty(pastX)">`.
- Nagłówek sekcji nadchodzącej (`Nadchodzące`) pokazuje się tylko gdy istnieją
  też przeszłe (`th:unless="${#lists.isEmpty(pastX)}"`), by nie dublować
  nagłówka gdy lista jest w całości nadchodząca.
- Sekcja przeszła ma zawsze nagłówek `📅 Przeszłe spotkania` / `📅 Przeszłe wydarzenia`.
- Wiersze przeszłe: `<tr class="past-item">` + w komórce daty
  `<span class="past-badge">Odbyło się</span>`.
- Pusty stan: gdy obie listy puste (`#lists.isEmpty(upcomingX) and #lists.isEmpty(pastX)`).

### CSS `static/css/app.css`
```css
tr.past-item, li.past-item { opacity: 0.62; }
.past-badge { /* pill, secondary color */ }
.section-heading { /* uppercase muted label */ }
```

---

## 4. Zachowanie brzegowe

| Sytuacja | Zachowanie |
|---|---|
| Wszystkie w przyszłości | Tylko sekcja Nadchodzące (nagłówek ukryty). |
| Wszystkie w przeszłości | Tylko sekcja Przeszłe (z odznakami). |
| Wydarzenie dzisiaj | Traktowane jako nadchodzące (górna sekcja). |
| Brak danych | Komunikat "Brak zaplanowanych prób." / "Brak wydarzeń w systemie." |
| Zmiana zespołu (aktywny band) | `teamId` filtruje obie listy przez repo. |

---

## 5. Testy

- `RehearsalListSortingUiTest` (Selenium) — tworzy próbę wczorajszą + dwie
  przyszłe, sprawdza: istnienie `tr.past-item` + `.past-badge`, że upcoming
  rosnąco (`[tomorrow, +30]`), past malejąco (`[yesterday]`).
- `EventListSortingUiTest` (Selenium) — na seedzie z `data.sql` (Koncert +30 dni,
  Parada -10 dni) sprawdza podział sekcji + odznakę "Odbyło się" na przeszłym.

Uruchomienie:
```bash
mvn test -Dtest=RehearsalListSortingUiTest,EventListSortingUiTest
```

---

## 6. Powiązane

- `PROJECT_DOCS.md` → sekcje *Rehearsal Detail View* / *Event Detail View*.
- Endpointy REST w `PROJECT_DOCS.md` (listy to GET `/rehearsals`, `/events`).
