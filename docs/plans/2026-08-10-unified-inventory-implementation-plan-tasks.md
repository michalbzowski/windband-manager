# Plan Implementacji: Moduł Zarządzania Ekwipunkiem Orkiestry Dętej

## Przegląd

Ten dokument opisuje plan implementacji nowego, ujednoliconego modułu zarządzania ekwipunkiem w aplikacji Windband Manager.

---

## Faza 1: Fundamenty - Model Danych i Repozytoria (23 zadania, ~2 tygodnie)

### 1.1-1.5: Model Encji Głównej
- [ ] **1.1** Utworzyć enum `ItemType` z wszystkimi typami rzeczy
- [ ] **1.2** Stworzyć bazową encję `InventoryItem` (MappedSuperclass lub SINGLE_TABLE)
- [ ] **1.3** Przenieść wspólne pola z `UniformItem`/`InstrumentItem`/`AwardItem` do `InventoryItem`
- [ ] **1.4** Dodać pola systemowe: `systemId`, `externalInventoryNumber`, `externalOwnerType`, `externalOwnerName`, `serialNumber`, `manufacturer`, `model`, `purchaseDate`, `purchaseCost`, `condition`, `notes`, `unit`
- [ ] **1.5** Dodać relację do `Warehouse` (location) w `InventoryItem`

### 1.6-1.7: Magazyny
- [ ] **1.6** Stworst-case-scenario
I'll now create the markdown plan file and proceed with the implementation.
<tool_call>
<function=name, description, type (MAIN/SERVICE/ARCHIVE/EXTERNAL), band, address, contactPerson, active
- [ ] **1.7** Stworzyć `WarehouseRepository` z podstawowymi metodami

### 1.8-1.9: Atrybuty Ujednolicone
- [ ] **1.8** Stworzyć `ItemAttributeDef` (zastępuje `Uniform/Instrument/Order/AwardAttributeDef`)
- [ ] **1.9** Stworzyć `ItemAttributeValue` (zastępuje osobne tabele)

### 1.10-1.13: Nowe Encje
- [ ] **1.10** Stworzyć encję `WarehouseTransfer` (historia przeniesień między magazynami)
- [ ] **1.11** Stworzyć encję `PrivatePossessionDeclaration` (deklaracje posiadania prywatnego/zewnętrznego)
- [ ] **1.12** Stworzyć encję `InstrumentServiceRecord` (historia serwisowa instrumentów)
- [ ] **1.13** Stworzyć encję `InventoryNeed` (zgłoszenie potrzeby, zastępuje/rozszerza `InventoryOrder`)

### 1.14-1.17: Enumy i Rozszerzenia
- [ ] **1.14** Stworzyć enum `NeedStatus` (NOWE, PRZEKAZANE_DO_AKCEPTACJI, ZAAKCEPTOWANE, ODRZUCONE, ZAMÓWIONE, W_REALIZACJI, DOSTARCZONE, ZAKOŃCZONE, ANULOWANE)
- [ ] **1.15** Stworzyć enum `ExternalOwnerType` (PRIVATE, OTHER_BAND, OTHER_INSTITUTION, UNKNOWN)
- [ ] **1.16** Rozszerzyć `ItemLifecycleStatus`: AVAILABLE, ASSIGNED, IN_SERVICE, RETIRED_FROM_STOCK, DISPOSED, LOST
- [ ] **1.17** Rozszerzyć `OwnershipStatus`: OWNED, BORROWED, EXTERNAL, PRIVATE

### 1.18-1.19: Repozytoria
- [ ] **1.18** Rozszerzyć `InventoryRepository` o metody: `findAllByBandIdAndType`, `findByLocation`, `findByExternalOwnerType`, `findBySystemId`
- [ ] **1.19** Stworzyć repozytoria dla nowych encji: `WarehouseRepository`, `ItemAttributeDefRepository`, `ItemAttributeValueRepository`, `WarehouseTransferRepository`, `PrivatePossessionDeclarationRepository`, `InstrumentServiceRecordRepository`, `InventoryNeedRepository`

### 1.20-1.23: Migracje i Refaktoryzacja
- [ ] **1.20** Napisać migrację Flyway V22 (strukturę nowych tabel)
- [ ] **1.21** Napisać migrację Flyway V23 (migrację danych z `uniform_items`/`instrument_items`/`award_items` do nowej struktury)
- [ ] **1.22** Zaktualizować `UniformItem`, `InstrumentItem`, `AwardItem` do dziedziczenia po `InventoryItem`
- [ ] **1.23** Przenieść logikę atrybutów do ujednolnionego `ItemAttributeCommandService`

---

## Faza 2: Logika Biznesowa - Command/Query Services (26 zadań, ~2 tygodnie)

### 2.1-2.13: InventoryCommandService
- [ ] **2.1** `createItem(ItemType type, CreateItemCommand cmd)` - tworzy dowolny typ rzeczy
- [ ] **2.2** `updateItem(Long id, UpdateItemCommand cmd)` - aktualizacja pól, atrybutów, lokalizacji, właściciela
- [ ] **2.3** `assignItem(Long itemId, Long memberId, AssignItemCommand cmd)` - przydział z historią
- [ ] **2.4** `returnItem(Long itemId, ReturnItemCommand cmd)` - zwrot do magazynu / innego członka
- [ ] **2.5** `transferItem(Long itemId, Long fromWarehouseId, Long toWarehouseId, TransferCommand cmd)` - przeniesienie między magazynami
- [ ] **2.6** `sendToService(Long instrumentId, ServiceCommand cmd)` - wysłanie instrumentu do serwisu (status IN_SERVICE)
- [ ] **2.7** `completeService(Long instrumentId, CompleteServiceCommand cmd)` - odbiór z serwisu, zapis `InstrumentServiceRecord`
- [ ] **2.8** `retireItem(Long itemId)` - likwidacja ewidencyjna (status RETIRED_FROM_STOCK)
- [ ] **2.9** `disposeItem(Long itemId)` - likwidacja fizyczna (status DISPOSED) - tylko jeśli RETIRED i nieprzydzielony
- [ ] **2.10** `declarePrivatePossession(PrivatePossessionCommand cmd)` - deklaracja posiadania prywatnego/zewnętrznego
- [ ] **2.11** `createNeed(CreateNeedCommand cmd)` - zgłoszenie potrzeby
- [ ] **2.12** `updateNeedStatus(Long needId, NeedStatus status)` - zmiana statusu potrzeby
- [ ] **2.13** `receiveNeed(Long needId, ReceiveNeedCommand cmd)` - odbiór dostawy, tworzenie egzemplarzy w magazynie

### 2.14-2.17: Nowe Serwisy
- [ ] **2.14** `WarehouseCommandService` - CRUD magazynów
- [ ] **2.15** `WarehouseQueryService` - query magazynów
- [ ] **2.16** `InstrumentServiceCommandService` - logika serwisowa
- [ ] **2.17** `PrivatePossessionCommandService` - zarządzanie deklaracjami prywatnymi

### 2.18-2.26: InventoryQueryService
- [ ] **2.18** `getAllItems(Long bandId, ItemType type)` - filtrowanie po typie
- [ ] **2.19** `getItemsByLocation(Long warehouseId)` - ekwipunek magazynu
- [ ] **2.20** `getItemsByMember(Long memberId)` - wszystko przydzielone członkowi
- [ ] **2.21** `getAvailableItems(Long bandId, ItemType type, Map filters)` - wolne rzeczy + filtrowanie po atrybutach
- [ ] **2.22** `getItemDetails(Long itemId)` - pełne szczegóły: atrybuty, historia, serwisy, przeniesienia
- [ ] **2.23** `getMemberEquipmentReport(Long memberId)` - raport odpowiedzialności członka
- [ ] **2.24** `getWarehouseStockReport(Long warehouseId)` - stan magazynu
- [ ] **2.25** `getNeeds(Long bandId, NeedStatus status)` - lista potrzeb z filtrem
- [ ] **2.26** `getServiceHistory(Long instrumentId)` - historia serwisowa

---

## Faza 3: UI - Kontrolery i Szablony (22 zadania, ~2 tygodnie)

### 3.1-3.11: Kontrolery Stron
- [ ] **3.1** `InventoryPageController` `/inventory` - główny widok z zakładkami
- [ ] **3.2** `/inventory/items` - lista wszystkich rzeczy (tabela z filtrami)
- [ ] **3.3** `/inventory/items/new` - formularz dodawania dowolnej rzeczy (wybór typu → dynamiczne pola)
- [ ] **3.4** `/inventory/items/{id}` - szczegóły rzeczy (historia, atrybuty, serwisy, przeniesienia)
- [ ] **3.5** `/inventory/items/{id}/edit` - edycja rzeczy
- [ ] **3.6** `/inventory/warehouses` - lista magazynów
- [ ] **3.7** `/inventory/warehouses/new` - dodawanie magazynu
- [ ] **3.8** `/inventory/needs` - lista potrzeb (zamówień) z filtrami statusów
- [ ] **3.9** `/inventory/needs/new` - zgłaszanie potrzeby
- [ ] **3.10** `/inventory/private-possessions` - deklaracje posiadania prywatnego/zewnętrznego
- [ ] **3.11** `/inventory/service` - historia serwisowa instrumentów

### 3.12-3.22: Szablony i Fragmenty
- [ ] **3.12** `inventory/list.html` - tabela z filtrami po typie, kolumny dynamiczne wg `displayInList`
- [ ] **3.13** `inventory/item-form.html` - formularz dynamiczny: wybór `ItemType` → ładuje odpowiednie pola + atrybuty
- [ ] **3.14** `inventory/item-detail.html` - zakładki: Podstawowe \| Atrybuty \| Historia przydziałów \| Serwisy \| Przeniesienia magazynowe
- [ ] **3.15** `inventory/warehouse-list.html`, `inventory/warehouse-form.html`
- [ ] **3.16** `inventory/need-list.html`, `inventory/need-form.html` (zastępuje/rozszerza zamówienia)
- [ ] **3.17** `inventory/private-possession-list.html`, `inventory/private-possession-form.html`
- [ ] **3.18** `inventory/service-history.html` (dla instrumentów)
- [ ] **3.19** Fragment `fragments/inventory-filters.html` - filtry: typ, status, magazyn, właściciel zewnętrzny, atrybuty (HTMX)
- [ ] **3.20** Fragment `fragments/item-attribute-fields.html` - renderowanie pól atrybutów (zależne, warunkowe)
- [ ] **3.21** Fragment `fragments/member-equipment-card.html` - karta wyposażenia członka
- [ ] **3.22** Fragment `fragments/warehouse-selector.html` - wybór magazynu/lokalizacji

---

## Faza 4: Raportowanie i Analityka (7 zadań, ~1 tydzień)

### 4.1-4.6: JasperReports
- [ ] **4.1** `member-equipment.jrxml` - pełny ekwipunek członka (wszystkie typy)
- [ ] **4.2** `warehouse-stock.jrxml` - stan magazynu (grupowanie: typ → stan)
- [ ] **4.3** `instrument-service-report.jrxml` - historia serwisowa / plan serwisów
- [ ] **4.4** `needs-tracking.jrxml` - śledzenie potrzeb (od zgłoszenia do realizacji)
- [ ] **4.5** `private-possessions.jrxml` - ewidencja posiadania prywatnego/zewnętrznego
- [ ] **4.6** `utilization-report.jrxml` - wykorzystanie zasobów (% przydzielonych)

### 4.7: Superset
- [ ] **4.7** Superset dashboardy: Ekwipunek zespołu, Potrzeby i zamówienia

---

## Faza 5: Testy i Jakość (6 zadań, ~1 tydzień)

- [ ] **5.1** Testy jednostkowe Command services: przydziały, zwroty, przeniesienia, serwisy, likwidacje, deklaracje prywatne, zgłaszanie potrzeb
- [ ] **5.2** Testy jednostkowe Query services: filtrowanie, raporty, dostępne rzeczy
- [ ] **5.3** ArchUnit: nowe encje w `domain/`, serwisy w `application/`, brak zależności `application → adapter`
- [ ] **5.4** UI Selenium: dodawanie każdego typu rzeczy, przydział/zwrot, serwis, likwidacja, deklaracja prywatna, zgłoszenie potrzeby, filtrowanie tabeli
- [ ] **5.5** Testy integracyjne (Testcontainers): pełne przepływy zgłoszenie→akceptacja→zamówienie→dostawa→magazyn→przydział→zwrot→serwis→likwidacja
- [ ] **5.6** Pełna weryfikacja: `./mvnw clean verify` (checkstyle, spotbugs, archunit, testy jednostkowe, UI, integracyjne)

---

## Zależności Sekwencyjne (Critical Path)

```
1.1 → 1.2 → 1.3 → 1.4 → 1.5 → 1.22 (model encji)
1.6 → 1.7 (magazyny)
1.8 → 1.9 (atrybuty)
1.10, 1.11, 1.12, 1.13 (nowe encje)
1.20 → 1.21 (migracje Flyway)
→ FAZA 2 (wymaga gotowego modelu i repozytoriów)
→ FAZA 3 (wymaga gotowych serwisów)
→ FAZA 4, 5
```

---

## Metryki

| Faza | Zadania | Szacunkowy czas |
|------|---------|-----------------|
| 1. Fundamenty | 23 | 2 tygodnie |
| 2. Logika Biznesowa | 26 | 2 tygodnie |
| 3. UI | 22 | 2 tygodnie |
| 4. Raportowanie | 7 | 1 tydzień |
| 5. Testy | 6 | 1 tydzień |
| **RAZEM** | **89** | **~8 tygodni** |