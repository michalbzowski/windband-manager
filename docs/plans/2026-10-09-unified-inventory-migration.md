# Migracja do ujednoliconego modułu inwentaryzacji orkiestry dętej

Data: 2026-10-09 · Status: **DO WYKONANIA** (decyzje D1–D4 zamknięte z użytkownikiem)
Zastępuje i unifikuje plan z 2026-08-10 (`2026-08-10-unified-inventory-implementation-plan-tasks.md`), w którym Fazy 3–5 nie istniały w kodzie (audyt 16.10.2026).

---

## Decyzje zamknięte (nie otwierać bez usera)

| # | Decyzja |
|---|---|
| D1 | **Jeden konkretny `InventoryItem`** + silnik atrybutów (`item_attribute_defs`). Brak podklas Java per typ. Typ różnicuje wyłącznie przez `ItemType` (discriminator) i aktywną defnicję atrybutów danego `band`/typu. |
| D2 | **Zakres spisu (full picture):** (a) stan magazynowy, (b) kto co trzyma (przydziały), (c) deklaracje prywatnego posiadania z innego źródła. Wymagany widok: oczekiwany zestaw vs faktyczny per osota. |
| D3 | **Zamknięcie legacy natychmiast**: `UniformItem/InstrumentItem/AwardItem/InventoryOrder` — usuwamy encje, kontrolery i UI legacy po migracji danych; dane w tabelich `uniform_items/instrument_items/award_items/inventory_orders/…attribute*` **mogą zostać usunięte** (za zgoda usera). |
| D4 | **Serwis instrumentów w planie** (`InstrumentServiceRecord` + workflow IN_SERVICE + `nextServiceDate`). Raporty Jaspers w **zakresie odłożonym**. |

---

## Model docelowy (docelowo 1 źródło prawdy)

```
inventory_items (V29_5, concrete)
 ├── inventory_needs (F1: +purpose, +source_item_id)
 ├── item_attribute_defs / item_attribute_values (V30/V31)
 ├── warehouses / warehouse_transfers (F1: brak migracji → dodać Flyway)
 ├── instrument_service_records (F4)
 ├── private_possession_declarations (F3)
 ├── asset_assignment_history (F3)
 └── stocktakes / stocktake_lines (F5 — nowy)
```

`InventoryItem` w code: `abstract` → `concrete`, `@Inheritance SINGLE_TABLE, discriminator item_type`.
Typy rozszerzające „typu orkiestry dętej": UNIFORM · INSTRUMENT · AWARD · SHEET_MUSIC · EQUIPMENT · CONSUMABLE · FURNITURE · DOCUMENT · OTHER (8 w enumie, wystarczająco).

---

## Fazy PR-owe (kolejność = zależności; każdy PR jest zielony = `./mvnw verify`)

### **F1 — Fundament: tabele pod kontrolą + konkretny model** (~5–7 dni)

| # | Co |
|---|---|
| 1.1 | Migracja **V48** (następczo po V47): `warehouses`, `warehouse_transfers`, `instrument_service_records`, `private_possession_declarations`, `asset_assignment_history` — wzorzec V29_5 (`CREATE TABLE IF NOT EXISTS`, indexy, FK). W tej chwili utworzone tylko z `ddl-auto: update` → w prod (Railway) **mogą nie istnieć**. |
| 1.2 | `InventoryItem`: usunąć `abstract`, upewnić `name+description+type` jako identyfikację; dodać convenience `getAttributes()` map dla renderu. |
| 1.3 | `Member.instruments` pozostaje jako źródło „co powinno być" — nie ruszamy. |
| **Akceptacja** | `./mvnw verify`; lokalny DB od zera (czysty `~/.local/share/...`) startuje bez błędów; kolumny istnieją w PG (wywołanie `flyway migrate` na dev DB); `InventoryItem` kompiluje + test jednostkowy `getAttributes()` map. |

### **F2 — Model + API + UI mobile-first** (~7–10 dni)

| # | Co |
|---|-----|
| 2.1 | `UniformItem/InstrumentItem/AwardItem/InventoryOrder` + ich `*_attribute_defs/values`: **usuwanie** encji, repozytoriów, DTO-ków, query/command metod, fragmentów UI. `InventoryPageController` + `InventoryController` → nowy wspólny `InventoryServiceController`(REST: `/api/inventory/items`, `/items/{id}`, atrybuty, przypisanie/zwrot). |
| 2.2 | Legacy tabele: **nie usuwamy** (dane mogą być potrzebne odwracalnie w debug), ale encje znikają. |
| 2.3 | **UX mobile-first form** (to jest odpowiedź na Twoje „chcę gotowy na wszystko" + D1): <br>• 2-etap: **wybór typu → render def-ów** `displayOrder`;<br>• pole warunkowe: parent value zmienił się → fetch `attributesFragment.html?parentDefId=X&value=Y` (HTMX);<br>• typ input wg `AttributeDataType`: NUMBER→number pad, SELECT/MULTI→chips/segmented control, BOOLEAN→switch, DATE→date picker, TEXT→1-lin; <br>• `required=true` → `Dalej` disabled aż do wypełnienia; `validationRegex+message` przy polu;<br>• cards nie tables na liście; pinned bottom action bar; big tap targets (44px). |
| 2.4 | Seed atrybutów **per band** w `item_attribute_defs` (band_id = band, global = NULL): Czapka(rozm.Natural) / Mundur(barki.cm, pas.cm, wzrost.cm) / Sznur(—) / Trąbka(BB/A, serial) — jako **dane testów**, nie hardcode. |
| 2.5 | Selenium UI: create item (typ → attrs → warunki), update assign/return z condition, list filter po atrybutach. |
| **Akceptacja** | Wszystkie old endpoints `410 Gone` (albo redirect), nowe endpointy testowane; UI na 360px bez scroll horizontal; testy Selenium pass; **0 legacy entity w src/**. |

### **F3 — Kto co ma + Full picture per osoba** (~5–7 dni)

| # | Co |
|---|-----|
| 3.1 | Przydział i zwrot: `InventoryItem.assignTo/unassign` tworzy wpis w `asset_assignment_history` (assignedAt/returnedAt/condition/note). History endpoint per member + per item. |
| 3.2 | **Declaracja prywatnego posiadania**: `PrivatePossessionDeclaration` entity → CRUD; UI mobile: jedna karta na osobę „Co ma z zewnątrz" + toggle „Tak/Nie mam mundurku" itp. |
| 3.3 | **„Full picture" per member** (query service, nie nowa tabela): <br>• **Oczekiwane:** `member.instruments` (instrument/part) ± strój → wymagany zestaw;<br>• **Faktyczne:** `inventory_items where assignedMember=me AND status in ...` ∪ `private_declarations where member_id=me`; <br>• **Gap:** oczekiwane − faktyczne → sugestia `InventoryNeed`;<br>• UI: 3 sekcje z licznikami (Masz ✓ / Brak ✗ / Z zewnątrz ⇄), każdy gap = przycisk „Zgłoś wymianę" → F4. |
| 3.4 | Testy: member z pełnym/partial/brakiem; gap detection na 6 testach. |
| **Akceptacja** | API `/api/inventory/members/{id}/picture` zwraca JSON z trzema listami gap-ów; Selenium UI full picture na telefonie; testy pass. |

### **F4 — Serwis instrumentów + Potrzeba z purpose** (~7 dni)

| # | Co |
|---|-----|
| 4.1 | `InventoryNeed`: dodać **`purpose PURCHASE/EXCHANGE/REPAIR`**, **`sourceItemId`** (dla REPAIR: który konkretny egzemplarz), workflow status (9 już istniejące). Command service `registerRepair(needId)` → po delivery tworzy `InstrumentServiceRecord`. |
| 4.2 | **Workflow serwisu**: `sendToService(instrumentId, provider, estimatedCost)` → `IN_SERVICE`; `completeService(...)` → record + `AVAILABLE`; **`nextServiceDate`** auto-sugerowany (np. +12 mjes.) ale edytowalny; UI mobile karta „Instrument: serwis do …" z 2 przyciskami (Wysłano / Odbiór) + formularz koszt/provider/warranty. |
| 4.3 | **List „do serwisu"**: query `nextServiceDate < today` OR `condition ∈ {POOR, FAIR}` OR flaga `needsRepair` na itemie → priorytetowa karta na dashboardie bandu. |
| 4.4 | Testy: pełny cykl serwis (send→complete) z `nextServiceDate`; gap: brak purpose=REPAIR nie działa. |
| **Akceptacja** | `InventoryNeed` JSON zawiera purpose+itemId; `InstrumentServiceRecord` utworzony automatycznie po delivery; lista „do serwisu" widoczna na mobile; testy pass. |

### **F5 — MODUŁ INWENTARYZACJI (stocktake)** (~7–10 dni)  ← rdzeń projektu

| # | Co |
|---|-----|
| 5.1 | Encje: **`Stocktake`** (id, band, startedBy, startedAt, finishedAt, status DRAFT/IN_PROGRESS/CLOSED, notes) + **`StocktakeLine`** (stocktakeId, itemId, expectedQty, expectedQty=1 [per-unit model], expectedStatus AVAILABLE/ASSIGNED, foundQty, foundState OK/BROKEN/MISSING/EXTRA, note, countedBy, countedAt). FK do `inventory_items(band)`. |
| 5.2 | **Zasada spisu**: 1 row per physical unit (D1: nie ma „rodzaju" osobnym bytem; atrybuty definiują wariant). Stocktake = snapshot aktywnych itemów bandu + przydziały. |
| 5.3 | **UX mobile** — to jest UX, które miało „na telefonie prosto działać": <br>• start: filtry (typ, status, magazynek) → lista; each card = name+serial+member(if assigned); <br>• tap na kartę → 3 duże przyciski: **OK ✓ / BRAK ✗ / USZKODZONE ⚠** (+ „inny stan"); OK = auto-next po 500ms, BRAK/USZK → prompt note (opcjonalny) + auto-next; <br>• bottom bar z progress bar (n/N) i **liczby rozbieżności live**; <br>• pause/resume; offline queue (local storage → flush na reconnection z `stocktakeId`); <br>• podgląd spisu: 4 sekcje (OK / Brakuje / Uszkodzone / Nadwyżka). |
| 5.4 | **Zamknięcie spisu**: diff raport na ekran + sugestia akcji (BRAK → zgłoś potrzebę; USZK → otwórz serwis; EXCESS → przypisz do bandu); zamknięcie blokuje edycję line'ów (`CLOSED` state). |
| 5.5 | **API**: `POST /api/inventory/stocktakes` (start+filter), `PUT /stocktakeLines/{id}` (count update), `GET /stocktake/{id}/summary`, `PATCH /stocktake/{id}/close`. Authz: tylko ACTIVE band userze ze role bandu. |
| 5.6 | Testy: pełny stocktake flow (start → count 5 itemów z rozbieżnościami → close → diff report), double-close reject, permission test (band user without band can not). Selenium UI na phone viewport. |
| **Akceptacja** | Stocktake per band działa w realu: start → mobile count → close → diff + akcje (F4 link). Testy pass; performance < 500ms na line update. |

### **F6 — Sprzątanie + Docs** (~3 dni)
| # | Co |
|---|-----|
| 6.1 | Usuwać legacy tabele z prod (po sprawdzeniu backup) — dopiero po F2+F5 stabilnym w prod. |
| 6.2 | README/inventory: krótki PL opisy dla użytkownika (3 ekrany mobile). |
| 6.3 | ArchUnit test: `domain.inventory.*` ma 1 encję główną + child'ów; brak legacy imports. |

---

## Krytyczne zależności i ryzyka

- **Railway prod** (bez migraacji F1): `warehouses` etc. nie istnieją w PG → F2 ui może crashować na `resolveActiveBand` jeśli `ddl-auto:update` z różnych powodów ich nie utworzył. **Musi być Flyway V48 przed deploy.**
- **H2 vs PG**: `value` reserved w H2 → już załatwione w V31 (`value_text`). Nowe migracje trzymają ten konwenans.
- **N+1 atrybuty** w `InventoryPageController`: F2 refaktoryzuje do 1 query per item map (`@Query join`) — performance mobile.
- **Dane w prod legacy**: user potwierdzis „oryć" → backup przed F1 (Railway snapshot, lokalny `pg_dump`).

---

## Szacunki łącznie

| Fa | PR-y | Dni robocze |
|----|------|-------------|
| F1 | 1–2 | 5–7 |
| F2 | 2–3 | 7–10 |
| F3 | 2 | 5–7 |
| F4 | 2 | 7 |
| F5 | 3 | 7–10 |
| F6 | 1 | 3 |
| **Razem** | **~11 PR** | **~35–45 dni roboczych** ≈ **7–9 tyg end-to-end** |

---

## Odrzucone / odłożone (decyzje usera)

- ❌ JasperRaporty + Superset dashboardy (z poprzedniego planu) — raporty po integracji z systemem, kiedy indziej.
- ❌ Qr-code / `systemId` scan na telefonie — może na F5 jako „nice to have" ale nie blocked.
- ❌ Warunkowe atrybuty v2 (reguły logiczne IF/AND/OR) — aktualna `depends_on+conditional` wystarczy na 90% przypadków orkiestry; reszta per-def.
