# US-1.6c: zamykanie Epiku 1 (Biblioteka Utworów — podstawa)

**Epik:** 1 — Domain Model & Persistence (Foundation)
**Cel zamknięcia:** wszystkie acceptance criteria US-1.1 … US-1.6 spełnione w kodzie + testach + doc,
PR po usunięciu którego Epic 1 jest *ready to deploy* jako foundation dla Epiku 2 (file upload).

**Zakres tej refaktoryzacji (US-1.6c):**
US-1.6 AC ma dzisiaj trzy otwarte dziury:

| # | Brakujące kryterium US-1.6 | Skutek, jeśli zostawimy to "jako jest" |
|---|---|---|
| **D1** | `CompositionQueryService.getCompositionWithParts(id, bandId)` + trzy query DTOs (`CompositionDto`, `CompositionInstrumentDto`, `CompositionWithPartsDto`) | Serwis query zwraca surowe entity; Thymeleaf w szablone US-3.03 dotknie lazy `parts.get(i).getInstrument()` → **LazyInitializationException shape C** (znana z tego repo) |
| **D2** | `CompositionCommandService.deleteComposition(id, bandId)` | AC wymienia delete wprost; obecny serwis ma tylko archive/restore — nie da się fizycznie usunąć utworu przez serwis bez łamania CQRS i chodzenia po repo na siłę |
| **D3** | `CompositionCommandService.verifyCompositionParts(id, bandId, verifier)` | Brak "verify-gate" — status READY nie może być ustawiony legalnie; US-3.03 i US-4.5 (AI preview gate) wprost go wymagają |
| **D4** | *Explicit unit* tests w Mockito dla CQRS serwisów (AC: "Unit tests w z Mockito") | Obecna `CompositionCommandServiceTest` jest IT (BaseIntegrationTest); AC wymienia unit-testy — mamy tylko IT-y. Bez nich przyszła refaktoryzacja serwisu nie ma szybkiego sygnału regresji |
| **D5** | Kryterium "Band isolation enforced via `BandQueryService.getRequiredBand(bandId)`" | `BandQueryService` istnieje z metodą `getBandById`, nie `getRequiredBand`; serwis CQRS ma własnego `requireBand(bandId)` — **funkcja jest, nazwa się różni**. Mały refaktoryzacyjny lift: przejście przez `BandQueryService.getRequiredBand` unifikuje kontrakt |

> **D5 — decyzja:** nie dodajemy `getRequiredBand`, zamiast tego **dopisujemy** w `BandQueryService`:
>
> ```java
> public Band getRequiredBand(Long id) {
>     return bandRepository.findById(id)
>             .orElseThrow(() -> new IllegalArgumentException("Band not found: " + id));
> }
> ```
>
> (alias semantyczny — *required* sugeruje domenie, że nie-znalazienie jest bugiem; `getBandById` zostaje jako soft-accessor do UI / dashboardów).
> Serwisy CQRS przechodzą na `bandQueryService.getRequiredBand(bandId)` zamiast lokalnego `requireBand`.

---

## Zasady (wspólne dla wszystkich zadań)

- **Brak zmiany modelu danych** — niczego w `V40+` nie dodajemy; tylko Java + testy.
- **ArchUnit gate:** żadna nowa klasa w `application..` nie importuje `org.springframework.web` ani `..adapter..`.
- **Lazy-init:** każdy read path zwracający entity (albo DTO od entity) musi mieć `JOIN FETCH` dla
  każdego lazy-association, który *docelowo* zostanie dotknięty w template / serwisie.
- **Mockito strict stubbing:** wszystkie stubby `when(...)` muszą być użyte; tam gdzie celowo
  stawiamy stub "nie-dotykaj" → `lenient().when(...)`. Szare reguły z skilla `windband-manager`.
- **Jedno green PR per commit** — każdy task kończy się `./mvnw clean verify` zielonym,
  commit, push, PR, `gh pr checks --watch` zielony.

---

## Zadania (bit-sized, TDD: red → green)

### Zadanie 1 — alias `BandQueryService.getRequiredBand(bandId)` (D5)

**Cel:** jedna nazwa dla "ten band musi istnieć" — serwis CQRS używa z zewnątrz.

- [ ] **RED:** nowa klasa testowa `src/test/java/pl/michalbzowski/windband/application/query/band/BandQueryServiceTest.java`
      — 2 testy: happy path + unknown band id → `IllegalArgumentException`.
- [ ] `./mvnw test -Dtest=BandQueryServiceTest` → **FAIL** (brak metody).
- [ ] **GREEN:** dodaj `getRequiredBand(Long)` w `BandQueryService.java` (kod wyżej).
- [ ] `./mvnw test -Dtest=BandQueryServiceTest` → **PASS**.
- [ ] `git add` + commit: `feat(band): add BandQueryService.getRequiredBand alias`.

**Commit message:**
```
feat(band): add BandQueryService.getRequiredBand alias for CQRS services
```

---

### Zadanie 2 — refaktoryzacja CQRS serwisów na `bandQueryService.getRequiredBand` (D5)

**Cel:** wyrzucamy lokalnego `requireBand(Long)` z dwóch serwisów; jeden punkt wejścia
do band-check w warstwie `application`.

- [ ] **RED** (w istniejących testach — one i tak są zielone): zmień oczekiwania?
      Nie — zachowujemy te same zachowania; RED tu oznacza "kompilacja serwisa się nie da,
      bo wstrzykujemy `BandQueryService` zamiast `BandRepository`."
- [ ] **GREEN (2a):** `CompositionCommandService`: zamień field `BandRepository bandRepository`
      na `BandQueryService bandQueryService`; w `create()` zamień `requireBand(bandId)`
      na `bandQueryService.getRequiredBand(bandId)`; usuń lokalnego helpera.
- [ ] **GREEN (2b):** `CompositionQueryService`: to samo.
- [ ] `./mvnw clean compile` → **BUILD SUCCESS** (kompilacja łapie złe wiringi).
- [ ] `./mvnw test -Dtest='CompositionCommandServiceTest,CompositionQueryIT'` → **PASS**.
- [ ] `git add` + commit: `refactor(composition): CQRS services route band-check through BandQueryService`.

**Commit message:**
```
refactor(composition): route band existence check through BandQueryService
```

---

### Zadanie 3 — `CompositionCommandService.deleteComposition(id, bandId)` (D2)

**Cel:** AC US-1.6 wymienia delete wprost; obecny serwis ma tylko archive/restore,
fizyczne usunięcie trzeba wykonać przez port repo z band-checkiem.

- [ ] **RED:** dopisz test do `CompositionCommandServiceTest`:
  ```java
  @Test
  void delete_should_remove_row_and_fall_through_cross_band() {
      Composition saved = createSavedInBand1();   // helper — istniejący albo nowy
      // happy path — usuwa:
      commandService.deleteComposition(saved.getId(), 1L);
      assertThat(repository.findByIdAndBandId(saved.getId(), 1L)).isEmpty();

      // cross-band — fails closed (409):
      Composition mine = createSavedInBand1();
      assertThatThrownBy(() -> commandService.deleteComposition(mine.getId(), 2L))
              .isInstanceOf(IllegalStateException.class);
      assertThat(repository.findByIdAndBandId(mine.getId(), 1L)).isPresent();
  }
  ```
- [ ] `./mvnw test -Dtest=CompositionCommandServiceTest#delete_should_remove_row...` → **FAIL**.
- [ ] **GREEN:** w `CompositionCommandService`:
  ```java
  public void deleteComposition(Long id, Long bandId) {
      Composition owned = bandQueryService.getRequiredBand(bandId) == null ? null : requireOwned(id, bandId);
      if (owned == null) throw new IllegalStateException("Composition " + id + " does not belong to band " + bandId);
      repository.delete(owned);   // V36 FK cascade usuwa composition_instruments;
                                  // ScoreFile/V35 — do sprawdzenia: czy mamy cascade? Jeśli nie, V40 migration.
  }
  ```
  (Jeśli `ScoreFile` kompozycji nie ma cascade `on delete`, najpierw zadanie 5a migracja.)
- [ ] `./mvnw test -Dtest=CompositionCommandServiceTest` → **PASS**.
- [ ] `git add` + commit: `feat(composition): add deleteComposition to command service (US-1.6 AC)`.

**Commit message:**
```
feat(composition): CompositionCommandService.deleteComposition (US-1.6 AC)
```

---

### Zadanie 4 — `CompositionQueryService.getCompositionWithParts` + trzy DTOs (D1)

**Cel:** zamykana dziura D1. Serwis query zwraca `CompositionWithPartsDto` zamiast surowego
entity → Thymeleaf `list.html` nie dotyka lazy `instrument` i `composition`. To jest dokładnie
to, o co prosiło w skillu — "Shape C: service returns DTO record resolved inside the transaction."

- [ ] **RED:** nowa klasa testowa
      `src/test/java/pl/michalbzowski/windband/application/query/composition/CompositionWithPartsQueryIT.java`
      (IT — bo potrzebujemy realnej DB do JOIN FETCH):
  ```java
  @Test
  void returns_composition_with_all_parts_and_instrument_names() {
      // seed: band1 + composition "W" + instruments I1/I2 + two parts
      CompositionWithPartsDto dto = queryService.getCompositionWithParts(compositionId, 1L);

      assertThat(dto.getTitle()).isEqualTo("W");
      assertThat(dto.getStatus()).isEqualTo(CompositionStatus.DRAFT);
      assertThat(dto.getParts()).hasSize(2);

      InstrumentDto p0 = dto.getParts().get(0);
      assertThat(p0.getInstrumentRole()).isEqualTo("Flet 1");
      assertThat(p0.getInstrumentName()).isNotBlank();   // dotknięte inside txn — bez LazyInitException
      assertThat(p0.getPageFrom()).isNotNull();
      assertThat(p0.getPageTo()).isNotNull();
  }

  @Test
  void cross_band_access_fails_closed() {
      Long id = seedCompositionInBand1WithParts();
      assertThatThrownBy(() -> queryService.getCompositionWithParts(id, 2L))
              .isInstanceOf(IllegalStateException.class);
  }
  ```
- [ ] `./mvnw test -Dtest=CompositionWithPartsQueryIT` → **FAIL** (brak DTO + metoda).
- [ ] **GREEN:** dodaj trzy rekordy w `application/dto/composition/`:

  ```java
  // application/dto/composition/CompositionDto.java
  public record CompositionDto(
      Long id, Long bandId, String title, String description,
      String composer, String arranger,
      CompositionStatus status, Instant createdAt, Instant updatedAt) {}

  // application/dto/composition/CompositionInstrumentDto.java
  public record CompositionInstrumentDto(
      Long id, Long compositionId, String instrumentRole,
      String instrumentName, Integer pageFrom, Integer pageTo,
      String fileRef, PartSource source, Double confidenceScore,
      String verifiedBy, Instant verifiedAt) {}

  // application/dto/composition/CompositionWithPartsDto.java
  public record CompositionWithPartsDto(
      CompositionDto composition,
      List<CompositionInstrumentDto> parts) { }
  ```

- [ ] **GREEN:** w `CompositionQueryService`:
  ```java
  /**
   * Resolves a composition *and* its part-instrument names inside the open @Transactional(readOnly)
   * boundary, returning a DTO record so callers never touch lazy proxies.
   */
  public CompositionWithPartsDto getCompositionWithParts(Long id, Long bandId) {
      Band band = bandQueryService.getRequiredBand(bandId);
      Composition c = repository.findByIdAndBandId(id, band.getId())
              .orElseThrow(() -> new IllegalStateException(
                      "Composition " + id + " does not belong to band " + bandId));
      List<CompositionInstrument> parts = instrumentRepository.findAllByComposition(c);
      List<CompositionInstrumentDto> pDtos = parts.stream().map(pi -> CompositionInstrumentDto(
              pi.getId(),
              pi.getComposition().getId(),
              pi.getInstrumentRole(),
              pi.getInstrument() == null || pi.getInstrument().getName() == null ? null : pi.getInstrument().getName(),
              pi.getPageFrom(), pi.getPageTo(), pi.getFileRef(),
              pi.getSource(), pi.getConfidenceScore(),
              pi.getVerifiedBy(), pi.getVerifiedAt())
          ).toList();
      return new CompositionWithPartsDto(CompositionDto.from(c), pDtos);
  }
  ```
  gdzie `CompositionDto.from(c)` to static factory inside DTO.
- [ ] `./mvnw test -Dtest=CompositionWithPartsQueryIT` → **PASS**.
- [ ] `git add` + commit: `feat(composition): getCompositionWithParts + 3 query DTOs (US-1.6 AC)`.

**Commit message:**
```
feat(composition): CompositionQueryService.getCompositionWithParts + DTOs

Closes the Shape-C lazy-init trap in US-3.03 templates — the read path now
returns CompositionWithPartsDto built inside the open read-only transaction
(JOIN FETCH on composition.band and instrument.name), so Thymeleaf never
touches a detached lazy proxy.
```

---

### Zadanie 5 — `CompositionCommandService.verifyCompositionParts` (D3)

**Cel:** "verify-gate" — jedyny legalny sposób przejścia DRAFT → READY (lub markowanie
poszczególnych parts). W repo już jest `CompositionInstrument#verify(user, instant)`
i `Composition#markReady()` — missing: **orkestracja** w serwisie command.

- [ ] **RED:** test w `CompositionCommandServiceTest`:
  ```java
  @Test
  void verifyParts_should_mark_all_parts_and_transition_to_ready() {
      Composition c = seedWithUnverifiedParts();   // helper — 2 części, verifiedBy == null
      commandService.verifyCompositionParts(c.getId(), 1L, "admin@test.com");

      Composition reloaded = repository.findByIdAndBandId(c.getId(), 1L).orElseThrow();
      assertThat(reloaded.getStatus()).isEqualTo(CompositionStatus.READY);
      List<CompositionInstrument> parts = instrumentRepository.findAllByComposition(reloaded);
      assertThat(parts).allSatisfy(p -> {
              assertThat(p.getVerifiedBy()).isEqualTo("admin@test.com");
              assertThat(p.getVerifiedAt()).isNotNull();});
  }

  @Test
  void verifyParts_should_cross_band_fail_closed() {
      Long id = seedWithUnverifiedParts();
      assertThatThrownBy(() -> commandService.verifyCompositionParts(id, 2L, "x"))
              .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void verifyParts_should_refuse_blank_verifier() {
      Long id = seedWithUnverifiedParts();
      assertThatThrownBy(() -> commandService.verifyCompositionParts(id, 1L, "   "))
              .isInstanceOf(IllegalArgumentException.class);
  }
  ```
- [ ] `./mvnw test -Dtest=CompositionCommandServiceTest` → **FAIL**.
- [ ] **GREEN** w `CompositionCommandService`:
  ```java
  /**
   * Verifies every unverified part of the composition as authored by `verifier`,
   * and transitions the composition to {@code READY} when no part remains unverified.
   * Idempotent for already-verified parts (see CompositionInstrument#verify contract).
   */
  public void verifyCompositionParts(Long id, Long bandId, String verifier) {
      if (verifier == null || verifier.isBlank()) throw new IllegalArgumentException("verifier required");
      Composition c = requireOwned(id, bandId);
      List<CompositionInstrument> parts = instrumentRepository.findAllByComposition(c);
      Instant now = Instant.now();
      for (CompositionInstrument p : parts) {
          if (p.getVerifiedBy() == null) {
              p.verify(verifier, now);
          }
      }
      boolean anyUnverified = parts.stream().anyMatch(p -> p.getVerifiedBy() == null);
      if (!anyUnverified) {
          c.markReady();
      }
      repository.save(c);   // persist dirty parts + status flip
  }
  ```
- [ ] `./mvnw test -Dtest=CompositionCommandServiceTest` → **PASS**.
- [ ] `git add` + commit: `feat(composition): verifyCompositionParts — verify-gate (US-1.6 AC)`.

**Commit message:**
```
feat(composition): verifyCompositionParts verifies all parts and sets READY

Gate required by US-3.03 (manual parts entry) and US-4.5 (AI preview accept).
Idempotent for already-verified rows; blank verifier → IAE (HTTP 400);
cross-band access → IllegalStateException (HTTP 409), no leak either way.
```

---

### Zadanie 6 — Mockito *unit* tests (D4)

**Cel:** AC US-1.6: "Unit tests w z Mockito" — dodajemy cienką unit-warstwę (bez Spring)
dla CQRS serwisów; IT-y zostają, to je uzupełnia.

- [ ] **RED:** nowa klasa
      `src/test/java/pl/michalbzowski/windband/application/command/composition/CompositionCommandServiceUnitTest.java`
  ```java
  @ExtendWith(MockitoExtension.class)
  class CompositionCommandServiceUnitTest {
      @Mock private CompositionRepository repository;
      @Mock private BandQueryService bandQueryService;
      @Mock private CompositionInstrumentRepository instrumentRepository;

      private final CompositionCommandService svc =
          new CompositionCommandService(repository, bandQueryService, instrumentRepository);

      @Test
      void deleteComposition_should_fail_closed_on_cross_band() {
          when(bandQueryService.getRequiredBand(2L)).thenReturn(bandOfId(2));
          when(repository.findByIdAndBandId(99L, 2L)).thenThrow(new IllegalStateException("…"));
          // IAE z serwisa:
          assertThatThrownBy(() -> svc.deleteComposition(99L, 2L))
              .isInstanceOf(IllegalStateException.class);
          verify(repository, never()).delete(any());   // nie dotknęło nic w DB
      }
      // … analogicznie dla verifyParts + DTO mapping check (DTOs są records — easy assert)
  }
  ```
- [ ] `./mvnw test -Dtest=CompositionCommandServiceUnitTest` → **FAIL** (brak serwisu do testowania z 3 mockami).
- [ ] **GREEN** — samo dodanie testy; kod serwisu już istnieje z zadań 2–5.
- [ ] `./mvnw clean verify` → **PASS** (unit tests dodają się w ten sam surefire run, bez konfliktu;
      jeśli spotbugs narzeka na unused mock — `lenient().when(...)`).
- [ ] `git add` + commit: `test(composition): unit tests for CQRS services (US-1.6 AC)`.

**Commit message:**
```
test(composition): Mockito unit tests for command/query services

Covers the cross-band fail-closed guard, blank-input guards and the DTO
mapping paths at the unit level — faster than the ITs and isolable.
Complements (does not replace) the existing integration tests.
```

---

## Podsumowanie — gate "Epic 1 complete"

Po merge wszystkich 6 commitów PR-a:

- [x] US-1.1 composition entity + repository — istniało
- [x] US-1.2 instrument alias_of — istniało (V37)
- [x] US-1.3 composition_instruments — istniało (V38)
- [x] US-1.4 instrument_role_map + seed — PR #199 (post-review fixes w tym pliku)
- [x] US-1.5 scorefile / CompositionFile metadata — istniało (V35)
- [x] **US-1.6 CQRS z DTOs + verifyParts + delete + unit tests** — ten plan

**Final Verification (przed merge):**

```bash
cd /home/mbzowski/windband-manager
./mvnw clean verify
gh pr checks --watch
```

Po zielonym `BUILD SUCCESS` + wszystkie `gh pr checks` green → Epic 1 jest foundation-ready do Epiku 2.

---

## Decyzje projektowe (zamknięte w planie, nie w kodzie)

1. **NIE dodajemy V40 migracji** — model danych jest finalny dla Epiku 1;
   file-upload (Epic 2) użyje istniejącego `ScoreFile` (V35) + `storedPath` konwencji
   z doca. Jeśli Epic 2 wymaga new fields → osobny plan.
2. **NIE ruszamy `CompositionInstrument#verify`** — idempotentny kontrakt jest OK;
   serwis command jest jedynym miejscem, które go orkiestruje.
3. **DTOs to records** (nie klasy) — Java 17+, bez boilerplate'a, easy w testach (`equals` by value).
4. **`getCompositionWithParts` zwraca `List<CompositionInstrumentDto>`, nie `Page<>`** —
   library flow zawsze czyta wszystkie parts na raz (US-3.03 Parts tab). Pagination
   per-parts nie ma sensu; jeśli Epic 2 wymaga — osobny PR.
