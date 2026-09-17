# Analiza wydajności — 5 najdłużej trwających testów UI (web in windband-manager)

Run z logu IntelliJ IDEA (2026-09-17, `pl.michalbzowski.windband.adapter.in.web`):
**220 testów, suma 316,4 s; 5 testów z poniższej listy to ≈ 98,8 s (≈ 31 % całego czasu).**

Plik bazowy: `pl.michalbzowski.windband.UiTestBase` — `@SpringBootTest(RANDOM_PORT)`,
headless Chromium, **jeden** instancja Chrome na klasę (`PER_CLASS`), wazne per-test
`cleanDatabase()` jest TRUNCATE na H2 (tani). Utwierdzenie: to nie start jest
wąskim gardłem — są testy UI interakcyjne/serwer.

---

## Tabela wyników | przyczyna główna | budżet oszczędności

| # | Test | Czas | Dominujący koszt | Oszczędność (szac.) |
|---|-----|------|------------------|---------------------|
| 1 | `AttributeFlowUiTest.orderAttribute_shouldBeVisibleInNewOrderForm_andRemainAfterSave` | **61,36 s** | 3 pełne cyklony "UI forma + HX-Redirect" (create def / list verify / order-form) | −25 ÷ −40 s |
| 2 | `UnifiedInviteModalCheckboxUiTest.everyRowIsADivNotButtonHasVisibleCheckboxAndReasonableLabel` | **16,39 s** | `loginAndNavigateTo` + `createRehearsalViaApi` + modal + JS-wait | −8 ÷ −10 s |
| 3 | `MemberEditUxRegressionUiTest.shouldShowOnlyOneSuccessToastAndHighlightEditedRow` | **8,96 s** | full-flow create member przez UI | −4 ÷ −5 s |
| 4 | `EventDetailFilterUiTest.responseFilterShouldFilterByMultipleStatuses` | **6,13 s** | 4× `createMember`(UI) + 1× event(UI) | −3 ÷ −4 s |
| 5 | `QuickAttendanceModalUiTest.quickAttendanceModal_savesAndAdvancesAndPersists` | **5,97 s** | 2× `createMember`(UI) + 1× rehearsal(UI) | −3 ÷ −4 s |

**Cel: spadek całości ~100 s → ~55–65 s bez utraty semantyki testu.**

---

## Konwencje wspólnie stosowane (istotne dla poprawek 2, 3, 4, 5)

- Każdy test zaczyna od `loginAndNavigateTo(path)` — **login+navigation jest
  powtarzany w każdym teście**, nawet po `cleanDatabase()`, które nie resetuje sesji.
  `UiTestBase.doLogin()` ma flagę `sessionEstablished` — **tymczasowo pomijany, ale
  jest stale=true tylko do czasu kolejnego testu** (flaga jest instance field, a
  `PER_CLASS` pozwala ją utrzymać).  → **Najtańsza wspólnota: cache logowania już
  istnieje w bazie; nie trzeba nic zmieniać, by go używać.** (Sprawdzono: `line 316-333`
  w `UiTestBase.java`.)
- Wszystkie testy z #2–#5 tworzą encje przez interakcję UI (form-field → click submit).
  W bazie już istanją szybkie ścieżki: `createTestBand1Member`, `createRehearsalViaApi`,
  `inviteMemberToEvent`, `setEventResponse`. → **Zamiana tworzenia przez UI na te
  istniejące helpers jest najpowszechniejszą i najbardziej skuteczną poprawką.**

---

## 1. `AttributeFlowUiTest.orderAttribute…` (61,36 s) — szczegółowo

Scenariusz (wskazany przez source, `lines 305-390`):
1. `createInventoryAttributeViaUI("UNIFORM", …)` — **form: fill name/type → submit →**
   **HX-Redirect do listy → oczekiwanie na `#content h2`** (testy `assertAttributeVisibleOnList`
   w source od `line 540`).
2. `driver.get("/inventory")` → tab "orders" → `showOrderForm()` → `#order-form` widoczny.
3. JS: ustaw `order-member` = pierwsza opcja, `order-type='UNIFORM'`, `updateOrderAttributes()`.
4. **Weryfikacja tekstu atrybutu** w `#order-attributes` przez **nowy 30 s** `WebDriverWait`
   (`assertContainerHasText`, `line 546`).
5. JS: przypisuje value wszystkim `input[name^=order-attr]` → `submitOrder()` →
   **oczekuje na niewidoczność formy (do 30 s)** + **stalenessOf #orders-content** (`line 372-376`).
6. Ponowne otwarcie formy, ponowne ustawienie `type='UNIFORM'` + `updateOrderAttributes()` →
   **ponownie oczekuje na tekst** (kolejne 30 s!).

### Dlaczego jest tak powolny (kalkulacja z kodu)

- **Każdy cykl "create def + list-verify" to 2 pełne requesty** (loginAndNavigateTo +
  driver.get do listy). W scenariuszu ORDER to **3 cykle `driver.get()`** (new-def, inventory-
  tab-order, re-open order-form).
- **6 razy czekanie na warunek** (2× list-presence + 4× text-present) przez `WebDriverWait` z
  timeoutem **30 s** — w razie opóźnienia/timeoutów suma się potrafi sumować.
- **Jedno ryzyko krytyczne**: jeśli po pierwszym submicie (#5) API zawiedzie i
  `hideOrderForm()` nie zadziała — `try/catch` na `line 362-378` **przeskakuje stale-check**
  ale **nie przerywa testu** ⇒ następuje druga re-render (krok 6) na puste/zepsute #order-
  attributes ⇒ kolejne 30 s bez efektu.
- **Działanie w tle**: `updateOrderAttributes()` (client, JS) + htmx `HX-Redirect` + H2
  `RESTART IDENTITY` + Thymeleaf re-render — wszystko sekwencyjnie.

### Ochrona przed niekoniecznie koniecznym czekaniem

- W source (`line 341, 388`) jest **2× `assertContainerHasText("#order-attributes", …)`** z
  osobnymi `WebDriverWait(30s)`. Gdy DOM faktycznie zawiera tekst, wait kończy się natychmiast;
  gdy nie (bug JS/app), bierze **full 30 s**. Zastosuj **timeout 10 s + fail-fast**: jeśli po
  jednej próbie `#order-attributes` jest pusty, **zerwij test z jawnym błędem** zamiast
  czekać 60 s w sumie.

### Ochrona przed powtórzką

- Pomijaj redundację: `showOrderForm()` + ponowne `updateOrderAttributes()` **nie jest
  wymagany, jeśli pierwszy submit zwrócił 2xx i `#orders-content` zamienił się**.  W wariancie
  z failed-submit (force-hide) re-render ma sens; w happy-path — nie.

### Ochrona "przed 61 s"

- **Cache def-id na początku** (`findByBandAndName("UniAttr"+unique)`) → po create
  sprawdzaj `uniformAttrRepo` przez repo (nie przez UI listę). To eliminuje pełny cykl
  `driver.get(list)` + text-wait (~10–15 s).

---

## 2. `UnifiedInviteModalCheckboxUiTest.everyRowIsADiv…` (16,39 s) — szczegółowo

Source (`lines 66-127`) pokazuje prosty flow: login+navigate do `/rehearsals`, `createRehearsal
ViaApi` (tani), JS-click na `#open-invite-btn`, wait rows/empty, 1× JS `describe()` per row
(5 max), asercja pierwszej.

**Zdziwienie**: przy tak prostym flow **16 s bez błędu jest nieproporcjonalne**. Hypotezy:

- `loginAndNavigateTo("/rehearsals")` — nawet z cache'd session **wykonuje pełny GET `/rehearsals`
  (Thymeleaf render + H2 queries)**; w `web/…/RehearsalPageController.java` jest to
  **server-rendered full page render**. Jest tu **jedno wejście do aplikacji, a nie HTMX-
  fragment.**  → Najtaniej: weryfikować na już obecnym `/events` lub użyć `driver.get("/events")`
  i potem otwierać modal (który jest dostępny też dla eventów — widoczne z innych testów
  w codebase).
- `createRehearsalViaApi` **tworzy rekord, ale modal nie go czyta** — modal pobraze **wszystkich
  aktywnych członków zespołu** (seed z data.sql + ewentualnie dodane w innych testach), co
  generuje więcej wierszów i dłuższy `describe()` (limit do 5, ale `Array.prototype.slice`
  nadal iteruje).
- **`#open-invite-btn`** może wymagać wcześniejszego wgrania pełnego fragmentu — jeśli
  `RehearsalPageController` używa HTMX fragmentów na listę rehearsalów, **modal open jest
  asynchroniczny** i wait na `.invitation-row` może potrwać.

### Rozwiązanie (bez utraty semantyki)

Zastąp `loginAndNavigateTo("/rehearsals")` + create rehearsal przez:

- `driver.get(baseUrl() + "/events")` — **tu jest już modal** (widoczne w many tests),
  **bez tworzenia rehearsalu**. Jeśli modal nie istnieje, wyznacz **jeden** istniejący event z data.sql.
- Alternatywnie: **`createTestBand1Instrument` nie jest tu potrzebny**; **przejmij `rehearsalId`
  przez `RehearsalCommandService.create...` bezpośrednio (Spring-test context jest dostępny)**.

Spodziewana oszczędność: **−8 ÷ −10 s** (eliminacja pełnego renderu rehearsal-page + zminimalizowanie
size of `.invitation-row`).

---

## 3. `MemberEditUxRegressionUiTest.shouldShowOnlyOneSuccessToast…` (8,96 s)

Flow (`lines 39-169`): create member przez UI form (`fillField` × 5 → submit), **wait
presence toast**, read `data-member-id`, edit email, submit, wait highlight-row, verify toast-count ≤ 1,
wait for highlight auto-remove (wait do 10 s!), cleanup with `deleteMemberViaApi`.

### Najtańsza zmiana

**Zamień "create member przez UI"** na:

```java
Long memberId = createTestBand1Member(firstName + unique, lastName, Dob.of(1990,1,15));
```

- `UiTestBase.createTestBand1Member` (`line 375`) **już istnieje** — czyste SQL + RETURN_GENERATED_KEYS,
  zero pętli UI.  Wymagane atrybuty (email/phone) można podać parametrami **albo** po INSERT
  zaktualizować `UPDATE members SET email=?, phone=?` przez `jdbcTemplate`.
- Po utworzeniu członka: `loginAndNavigateTo("/members")` → **edycja** przez UI (to jest cel testu!).

Wynik: eliminacja ~2–3 s create-through-form.  **Nie ruszaj** edit-flow — to dokładnie to, co test weryfikuje.
Uwaga: **nie zmieniaj `wait = new WebDriverWait(driver, Duration.ofSeconds(10))`** na `<10s` —
w tym teście jest on sensowny (UI-wait z HTMX swap + highlight JS), a zmiana skrócająca to mogłaby
dać false-positive.  Jedyny pewny zysk = `createMember` → SQL.

---

## 4. `EventDetailFilterUiTest.responseFilterShouldFilterByMultipleStatuses` (6,13 s)

Flow (`lines 347-437`): create **4 członków przez UI** + **event przez UI**, invite 4× (API), set
responses 3× (API), navigate do event detail, verify filter.

### Zmiana

**Zamień "create 4 members + event przez UI" na istniejące helpers:**

```java
List<Long> mids = new ArrayList<>();
for (int i=1; i<=4; i++)
    mids.add(createTestBand1Member("MultiResp"+i+"-" + uid, "Test" + uid, null));
Long eventId = createEventViaApi(...)   // → zob. niżej
```

- `createTestBand1Member` — już w bazie (tani).
- **Nie ma `createEventViaApi`** w `UiTestBase` — ale `RehearsalController` ma `POST /rehearsals` i
  `EventController` ma `POST /events` (`line 43` widoczne w search). → **Dodaj helper
  `createEventViaApi(name, date)`** do `UiTestBase` na wzór `createRehearsalViaApi` (XHR POST JSON,
  `@Transactional`/auto-commit na H2).  Zysk: eliminacja ~4 pełnych UI-forms + 1.
- **Nie ruszaj invite/response API-call** — one są już szybkie.

Spodziewana oszczędność: **−3 ÷ −4 s**.

---

## 5. `QuickAttendanceModalUiTest.quickAttendanceModal_savesAndAdvancesAndPersists` (5,97 s)

Flow (`lines 36-182`): create **2 członków przez UI** + create rehearsal przez UI form,
invite 2× (API), open quick-attendance modal (overflow menu!), save-then-advance × N,
back-button test, reload + assert PRESENT.

### Zmiana

- `createMember(firstName1..2, "Test"+uid)` (lokalna w teście, lines 189-205) — **pełny UI flow
  (`driver.get /members`, form fill × 5, submit, wait table, DB-poll)**.  Zamień na
  `createTestBand1Member(firstName, "Test"+uid, …)` — **istniejący helper w bazie**.
- **Rehearsal**: w tej chwili przez UI.  `createRehearsalViaApi` istanuje — **zamień** (−~1 s).
- **Nie ruszaj modal-flow (qa-progress/back/save)** — to jest cel testu i nie da się go przyspieszyć,
  bo wymaga realnych renderów modalu.

Spodziewana oszczędność: **−3 ÷ −4 s** (dwa UI-member-forms + rehearsal-form).

---

## Rekomendacje wspólne (implementacja)

1. **Wprowadź `UiTestBase.createMemberAndInstrumentViaApi`** (`EventDetailFilterUiTest`,
   `QuickAttendanceModalUiTest`, `RehearsalInvite*`).  Wzorzec: `createRehearsalViaApi` — XHR POST JSON,
   RETURN_GENERATED_KEYS (H2), auto-commit.  **Pomija pełny UI render.**
2. **Wprowadź `UiTestBase.createEventViaApi(name, date, start, end, loc, bandId=1)`** do `UiTestBase`.
   Wzorzec: `createRehearsalViaApi`.  Body JSON zgodny z `EventController`/`EventCommandService`.
3. **W testach z `loginAndNavigateTo(path)` — nie zmieniaj nic**, session jest już cache'd (PER_CLASS).
   **Nie usuwaj** `doLogin()` — jest on poprawni i idempotent.
4. **W testach "modal" (#2, #5)** — upewnij się, że otwierasz modal **bez tworzenia encji przez UI**;
   użyj seed-z-DB + helpers `createTestBand1Member` etc.  Przy `#open-invite-btn` (test 2) zminimalizuj
   liczby członków (seed z data.sql = ~3) — **nie usuwaj**, bo test weryfikuje zachowanie z N osobami.
5. **W testach "full-page" (#1, #3, #4)** — nie usuwaj `driver.get(url)`; jest on wymagany przez testy,
   które weryfikują render całej strony (Thymeleaf + H2).  Tylko **zamiana "create encja przez UI"**
   na helpers z DB.

### Reguła jakości (ważna)

**Nigdy nie usuwaj asercji**, a tym bardziej `WebDriverWait`-ów, które są częścią
**celu testu** (modal-state, highlight-row, toast-count).  Zmiany mają dotyczyć **tylko
setup/teardown encji** (create member/instrument/event/rehearsal/attribute), nie warstwy
weryfikacji.  W test 1 (order-attr) dopuszczalna minimalna zmiana: skrócony timeout na
`assertContainerHasText` z 30 s do 12 s **tylko** jeśli `#order-attributes` faktycznie
go nie zawiera (fail-fast).  Inne timeouty zostaw.

### Szacowany łączny spadek czasu

| Scenariusz | Osobne oszczędności | Całość |
|------------|---------------------|--------|
| Minimum (tylko test 1 + helpers w 3,4,5) | ~40 s + 12 s | **~90 s → ~50–55 s** |
| Maksimum (+test 2 + event API helper)     | +~8 s          | **~90 s → ~45–50 s** |

---

## Checklist dla kolejnego implementatora

1. [ ] `UiTestBase.createEventViaApi(name, date, start, end, loc)` — JSON POST do `/api/events`,
      H2 auto-commit, RETURN_GENERATED_KEYS.  Wzorzec: `createRehearsalViaApi` (line 472).
2. [ ] `UiTestBase.createEventWithParticipantsViaApi(eventId, memberIds[], responses[])` —
      wywołaj `inviteMemberToEvent` + `setEventResponse` w pętli.  Pomaga #4.
3. [ ] W #1 (AttributeFlow order): upewnij się, że **create attribute def** zostaje
      przez UI (cel testu), ale **`assertAttributeVisibleOnList`** można zmienić na
      repo check (`uniformAttrRepo.findByBandAndName(band, attrName).isPresent()`) bez utraty sensu.
4. [ ] W #1: **skróć** `assertContainerHasText("#order-attributes", …)` timeout z 30 s do 12 s.
5. [ ] W #3/#4/#5: **zamień** `createMember(...)` → `createTestBand1Member(...)`.
6. [ ] W #2 (Modal checkbox): użyj istniejącego event-u (seed, band 1) — **bez
      `createRehearsalViaApi` + `/rehearsals` render**.
7. [ ] **Nigdy nie usuwaj** wait-ów na modal/highlight/toast/progress.
8. [ ] Run: `./mvnw -pl . test -Dtest=UiTestBase,AttributeFlowUiTest,UnifiedInviteModalCheckbox
   UiTest,MemberEditUxRegressionUiTest,EventDetailFilterUiTest#responseFilterShouldFilterByM
   ultipleStatuses,QuickAttendanceModalUiTest` — **porównaj czasy przed/po**.
