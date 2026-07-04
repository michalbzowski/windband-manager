# AI_HARNESS.md — Engineering Harness for windband-manager

> Na podstawie koncepcji "Harness Engineering" Emily Bache (https://www.youtube.com/watch?v=JaiJ5wxdmCA).
> Harness = Guides (przewodniki) + Sensors (czujniki) — koło zamachowe poprawy jakości kodu z AI.

## Cel

Zapewnić, że AI-asystent (lub programista) generuje kod zgodny z architekturą, konwencjami i standardami jakości windband-manager. Każda ulepszenie kodu powinno prowadzić do ulepszenia harnessu, tworząc efekt koła zamachowego.

---

## 1. Guides (Przewodniki) — feedforward przed generowaniem kodu

### 1.1 Architektura (Ports & Adapters + DDD + CQRS-light)

- **Domain layer** — czysty kod bez zależności Springa (oprócz JPA annotations). Repozytoria interfejsów w `domain/`, wszystkie metody zdefiniowane tam.
- **Spring Data** — interfacy w `adapter/out/persistence/` rozszerzają TYLKO `JpaRepository`. Zero custom methods.
- **Adapter layer** — klasy `@Component` implementujące interfejsy domain, delegujące do Spring Data.
- **Application layer** — command/query services. Command services otrzymują Band jako parametr (NIE czytają samodzielnie — ArchUnit enforced).
- **ArchUnit** — testy architektury w `ArchitectureTest.java` wymuszają zależności między warstwami.
- **Dynamic groups are a domain invariant**: never manually insert/update `member_groups.dynamic_source_id` for an attribute-backed group. All changes flow through `MemberAttributeCommandService` (which calls `GroupCommandService.createDynamicGroupForAttribute` / `syncMemberInDynamicGroup` / `renameDynamicGroup` / `deleteDynamicGroup`). UI/API must reject manual edits.

### 1.2 HTMX + Thymeleaf

- HTMX requests: zwracaj `"template :: fragment"` (np. `"members/detail :: member-detail-content"`), NIGDY pełną stronę.
- Page-level navigation: użyj `hx-target="#content" hx-swap="innerHTML transition:true"`.
- Sub-content areas: własne targety (`#members-content`, `#rehearsals-content`, itd.).
- Formularze: JavaScript `fetchWithToast(url, {method, body: JSON.stringify(data)})` — NIE native HTML form submit.
- Grupy: wyjątek — native `hx-post`.
- Event listener guards: `window._*FormHandlerAttached` boolean flags w każdym inline `<script>`.

### 1.3 Testy

- **Unit tests**: mock repos, test command/query services.
- **UI tests (Selenium)**: extend `UiTestBase`, `loginAndNavigateTo(path)`.
- **Integration tests**: extend `BaseIntegrationTest` (Testcontainers PostgreSQL).
- **Architecture tests**: `ArchitectureTest.java` — ArchUnit rules.
- **Zawsze uruchom testy przed commit/push.**

### 1.4 Security

- OIDC Authorization Code Flow z Keycloak.
- `WindbandOidcUser` z `@AuthenticationPrincipal` — resolver Band przez `BandQueryService`.
- ADMIN-only: `/api/teams/*/admin/**`, `/admin/**`.
- Test profile: form-based login, `admin`/`admin`, mock `WindbandOidcUser`.

### 1.5 Inventory / Asset Lifecycle

- `UniformItem`, `InstrumentItem`: `assignTo()`, `unassign()`, `dispose()` (throws if assigned), `retireFromStock()`.
- `AwardItem`: `assignTo()`, `return()`, `dispose()`.
- `AssetAssignmentHistory`: active = `returnedAt == null`. Usuń history przed item.
- `InventoryOrder`: SUBMITTED -> PENDING_APPROVAL -> IN_PRODUCTION -> SHIPPED -> DELIVERED, lub CANCELLED.

### 1.6 Common Pitfalls

- `orphanRemoval + clear() + add()` = duplicate key → guard with identity check.
- `sendKeys` w Selenium psuje daty → użyj JS.
- `HX-Redirect + hx-swap="none"` nie działa w Selenium → `driver.get()`.
- `patch()` tool: NIGDY nie używaj `read_file` content jako `old_string` (prefix `N|`).
- Po patchowaniu `.py` w kontenerach → czyść `__pyc`.
- **Thymeleaf `th:attr` gotcha**: when multiple `th:attr` attributes appear on the same element, **only the LAST one is honored** — earlier ones are silently dropped. Always merge into a single comma-separated form: `th:attr="data-a=${val1},data-b=${val2}"`. Symptom: HTMX/SPA behavior breaks with no Thymeleaf error, only Selenium timeout on missing element.
- **Renaming a dynamic group's name via raw SQL or direct `Group.setName()` is a footgun**: the next `ensureDynamicGroupExists` won't re-sync the name from the source attribute. Always rename through `MemberAttributeCommandService.updateAttributeDef` (which calls `GroupCommandService.renameDynamicGroup`). ArchUnit should enforce this — see `ArchitectureTest.java`.

### 1.7 Transaction Propagation — The Backfill Footgun (2026-07-04 incident)

**The bug**: `DynamicGroupBackfillRunner.run()` was annotated `@Transactional` and wrapped the entire loop in a single transaction. When a BOOLEAN attribute with name "Gość" tried to create a dynamic group and a manual group with the same name already existed, the INSERT raised `DataIntegrityViolationException` on the UNIQUE constraint (`band_id`, `name`). In PostgreSQL this marks the whole transaction as "aborted" — every subsequent SQL statement (including the next attribute's INSERT) fails with "current transaction is aborted, commands ignored until end of transaction block", then Hibernate throws `AssertionFailure: null id in Group entry (don't flush the Session after an exception occurs)`, then Spring throws `UnexpectedRollbackException` on commit. The application context never starts.

**The fix has two parts**:

1. **No outer `@Transactional` on the runner loop.** Each `ensureDynamicGroupExists(def)` call runs in its own transaction (via the class-level `@Transactional` on `MemberAttributeCommandService`); the runner's `try/catch` contains any failure to that single attribute's transaction. The next attribute gets a brand-new transaction.

2. **Name-collision resolution in `createDynamicGroupForAttribute`.** Detect the collision BEFORE attempting the INSERT (via `existsByNameAndBandId`) and append a numeric suffix: "Gość" → "Gość (2)" → "Gość (3)". This keeps the manual group and the dynamic group both alive.

**Do NOT use `REQUIRES_NEW` for "per-iteration isolation"**. We tried this and it backfired in two ways:
- The new transaction's session cannot see entities that are flushed-but-not-committed in the caller's session. Inserting a child row with FK to a parent row that lives in the caller's session → foreign-key violation.
- Tests that wrap the call in `@Transactional` then deadlocked when Hibernate tried to attach a detached entity from the outer session to the inner session.

The correct semantic is **default `REQUIRED` propagation**: the dynamic-group insert joins the caller's transaction so the parent def and the child group are atomic (commit together or rollback together). For the backfill runner, "the caller's transaction" is the per-attribute transaction started by `MemberAttributeCommandService.ensureDynamicGroupExists`, which gives us per-attribute isolation without any explicit `REQUIRES_NEW`.

**Diagnostic checklist when the app fails to start with a `JpaSystemException` or `UnexpectedRollbackException` on the first DB write after startup**:
1. Read the FIRST exception in the chain — usually a `DataIntegrityViolationException` or a UNIQUE/FK violation.
2. Trace back to the `@Transactional` method that wrapped it.
3. Ask: "Is this method called in a loop? If so, the loop body and the loop's try/catch are likely both inside a single transaction. The fix is to remove the outer `@Transactional` and rely on per-call class-level `@Transactional` for isolation."

---

## 2. Sensors (Czujniki) — feedback po wygenerowaniu kodu

### 2.1 Deterministyczne reguły (uruchamiane automatycznie)

| Sensor | Opis | Priorytet |
|--------|------|-----------|
| `ArchitectureTest` | ArchUnit: layer dependencies, repo pattern | BLOKUJĄCY |
| `mvn test` | Wszystkie testy jednostkowe + integracyjne | BLOKUJĄCY |
| `mvn verify` | ArchUnit + testy + inne checks | BLOKUJĄCY |
| Max plik | Żaden plik > 600 linii (z wyjątkami: generated, migrations) | OSTRZEŻENIE |
| Max metoda | Żadna metoda > 30 linii | OSTRZEŻENIE |
| Spring Data interface | Tylko `JpaRepository`, zero custom methods | BLOKUJĄCY |
| Command service | Nie czyta Band bezpośrednio (przez BandQueryService) | BLOKUJĄCY |
| HTMX fragment | Zwraca `:: fragment` dla HX-Request | BLOKUJĄCY |
| Test profile | `@ActiveProfiles("test")` dla UI/integration tests | BLOKUJĄCY |

### 2.2 Spot checks (przy każdej zmianie)

- [ ] Czy nowy test przechodzi RED→GREEN?
- [ ] Czy `PROJECT_DOCS.md` wymaga aktualizacji?
- [ ] Czy `AGENTS.md` memory wymaga aktualizacji?
- [ ] Czy zmiana jest widoczna w UI (incognito, refresh)?
- [ ] Czy `data.sql` seed data aktualne?

### 3.3 Automatyzacja (do implementacji w CI)

```bash
#!/bin/bash
# sensors/validate-harness.sh
set -e

echo "=== ArchUnit ==="
mvn test -Dtest=ArchitectureTest -q

echo "=== Unit tests ==="
mvn test -q

echo "=== File length check ==="
find src/main/java -name "*.java" -exec sh -c 'lines=$(wc -l < "$1"); if [ "$lines" -gt 600 ]; then echo "WARN: $1 has $lines lines"; fi' _ {} \;

echo "=== Spring Data interface check ==="
grep -rn "extends JpaRepository" src/main/java/ | grep -v "JpaRepository<" && echo "FAIL: custom methods in Spring Data interface" && exit 1 || true

echo "=== All sensors passed ==="
```

---

## 3. Flywheel — ciągłe ulepszanie

### Kiedy aktualizować harness:

1. **Nowy pitfall odkryty** → dodaj do sekcji 1.6 (Common Pitfalls) + memory
2. **Nowy wzorzec architektoniczny** → dodaj do sekcji 1.1 lub 1.2
3. **Nowy sensor potrzebny** → dodaj do sekcji 2.1
4. **Reguła nie jest potrzebna** → USUŃ (długie guide'y kosztują tokeny)
5. **Zmiana w domain model** → zaktualizuj `PROJECT_DOCS.md` + `AGENTS.md` memory

### Przykład flywheel:

```
Problem: orphanRemoval + clear() + add() = duplicate key
  → Guide: dodaj identity check (sekcja 1.6)
  → Sensor: test w ArchitectureTest lub unit test
  → Code: Member.changeInstrument() z guard
  → Harness: ulepszony
  → Efekt: AI agent nie popełni tego błędu w przyszłości
```

---

## 4. Praca z AI-asystentem — workflow

### Każda sesja:

1. **Przed**: przeczytaj `PROJECT_DOCS.md` + `AGENTS.md` + ten plik
2. **W trakcie**: stosuj guides z sekcji 1
3. **Po każdej zmianie**: uruchom sensors (sekcja 2)
4. **Na końcu**: zaktualizuj harness jeśli odkryto nowy wzorzec/pitfall

### Komunikacja z AI:

- Podaj kontekst: "Jestem w module inventory, chcę dodać nowy endpoint"
- Oczekuj: AI sprawdza architekturę, pisze testy, implementuje, waliduje
- Weryfikuj: sprawdź czy AI nie łamie Ports & Adapters, nie dodaje custom methods do Spring Data
- Iteruj: jeśli AI coś robi źle → wyjaśnij, dodaj do harnessu, poproś ponownie

---

## 5. Metryki jakości

| Metryka | Cel | Obecna |
|---------|-----|--------|
| ArchUnit violations | 0 | ? |
| Test coverage (domain) | > 80% | ? |
| Test coverage (application) | > 60% | ? |
| Max file length | < 600 LOC | ? |
| Max method length | < 30 LOC | ? |
| AI-generated code acceptance rate | > 90% (z < 2 iteracjami) | ? |

---

*Ostatnia aktualizacja: 2026-06-24*
*Wersja harness: 1.0*
