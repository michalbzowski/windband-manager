# Analiza biznesowa: Profil Muzyka + cross-musician Gig Solicitation

**Wersja:** 1.1 — po decyzjach Q1–Q8
**Data:** 2026-10-06
**Autor:** analityk (agent Hermes)
**Status:** DECYZJE PRZYJĘTE — gotowe do planowania PR-ów

---

## 1. Cel i zakres

Budujemy **trzy spójne moduły**, z których każde jest samodzielnie wartościowe:

| # | Moduł | Dla kogo | Wartość |
|---|-------|----------|---------|
| M1 | **Profil Muzyka** — imię/nazwisko, opis, dom (adres), kwota, skłonność do dojazdu, lista instrumentów | Każdy logowany użytkownik | Ustrukturyzowane CV; warunek wstępny dla M2 |
| M2 | **Globalny katalog instrumentów** — spis, którego mogą używać wszyscy muzycy | Muzycy + liderzy | Stabilne słownictwo do matchmakingu (nie tagi per zespół) |
| M3 | **Zapytanie do muzyka (cross-musician solicitation)** → wysyłka do spełniających kryteria | Lider zespołu szukającego zastępstwa | Szybkie znalezienie gracza na określone wydarzenie bez telefonowania i bez wycieku danych osobowych kandydatów |

M3 jest docelowym celem biznesowym, M1/M2 to fundamenty. Bez M1+M2 nie ma czego dopasowywać; bez M3 profil jest martwym CV.

### Problem, który rozwiązujemy
Lider zespołu ma zaplanowaną próbę/koncert/wedding. Na D-7 jeden z muzyków odpada (choroba, kolizja) lub instrument, na którym gra, nie istnieje w zespole (np. potrzeba trąbki, a zespół gra saksofonem). Dzisiaj: lider dzwoni po kolei do kolegów z innych zespołów, sprawdza kogo pamięta, jak daleko mieszkają, ile biorą. **Nie ma w aplikacji żadnego ścieżki „znajdź kogoś obcego, kto spełni te 4 kryteria"**.

M3 dostarcza tą ścieżkę w 3–5 kliknięć, z zachowaniem prywatności i bez ujawniania adresów dojeżdżających.

### Co NIE wchodzi w MVP
- **Przetwarzanie płatności** (aplikacja nie pobiera haraczów/przelewów) — to jest ogłoszenie, nie marketplace finansowy; brak KYC/PSD2.
- **Rozliczanie kosztów dojazdu** — kwota to oczekiwana stawka gracza, nie oferta logistyczna lidera; lider sam wycenia na miejscu.
- **Geolokalizacja „na żywo"** — adres jest statyczny (miejsce zamieszkania), nie GPS.
- **Wielojęzyczność** — app już jest po polsku (Thymeleaf + Pico.css).
- **Autentyczna geokodowana odległość** dla dowolnego adresu; w MVP pracujemy na **spisie mock adresów** (3 miasta) i Haversine.
- **Rozliczenia „reputacji" / ocen za zrealizowane eventy** — kandydat jest propozycją, nie umową.

### Kto są użytkownicy (persony)
1. **Lider zespołu szukającego zastępstwa** (główny buyer M3).
2. **Muzyk-zawodowiec / muzyk-amator** z profilem — docelowy odbiorca M3, beneficjent M1/M2.
3. **Admin systemu** — obsługuje globalny katalog instrumentów (M2).
4. *Czarnek*: muzyk, który nie chce się zgodzić na porównywalność (GDPR/RODO) — musi mieć twardszą ścieżkę wyjścia.

---

## 2. Stan obecnego systemu (co ma) i luki (czego brak)

### Co już istnieje w kodzie
Kluczowe fakty z `src/main/java` (stan `main @ 2e052fc3`):

| Znak | Co to jest | Wpływ na plan |
|------|-----------|---------------|
| ✅ | `AppUser` (`app_users`) — OIDC + team role, ma już `firstName`, `lastName`, `email`, `username`, `systemAdmin`. **Nie ma** opisu, adresu, kwoty. | Punkt kotwiczny M1 — nowy agregat „Profil" musi wiazać z `AppUser` (bo to jedyny podmiot cross-zespołowy). |
| ✅ | `Member` (`members`) — relacja do `Band`; `add/remove/changeInstrument(Instrument, primary)`. | Nie dotykamy. Member zostaje jak jest. |
| ✅ | `Instrument` (`instruments`) — **zakres teamowy**: `unique(band_id, name)`; samoreferencjalny `aliasOf`; już ma `InstrumentCommandService` per band. | **To są obecne „tagi" użytkownika.** Mianowicie: w `TagController /api/tags?teamId=…`. |
| ✅ | `EventInvitation` (`event_invitations`) — zaproszenie do członka **tego samego zespołu** + `ConsentType.MANAGER_MESSAGES` / `EVENTS` brama. | M3 **nie może** reużyć tego (target to inny zespół, cel: oferta dla zewnętrznych muzyków). Trzeba nowego agregatu. |
| ✅ | `BandEvent.location: String` — wolny tekst, **bez** geo; `paymentAmount` już istnieje jako `BigDecimal`. | Lokalizacja w M3 musi być adres z listy (mock), nie wolny tekst. |
| ❌ | **Nie ma** niczego o adresie muzyka, stawce, skłonności do dojazdu, odległości. | Nowe encje + nowy katalog + dopasowanie. |
| ❌ | **Nie ma** profilu w sensie „mój CV" (brak `User*Profile`, brak `/profile` szablonu). | M1 = nowa strona + encja + service. |
| ❌ | **Nie ma** globalnego spisu instrumentów (wszystko per `band_id`). | M2 = nowa encja o bezosobowym charakterze. |

### Decyzje architektoniczne, które wymusza stan istniejący
1. **`Member` ≠ podmiot publiczny.** W zapytanie do muzyka wchodzi muzyk jako *człowiek*, nie „członek zespołu X". `AppUser` to jedyny cross-team podmiot → profil będzie powiązany 1:1 z `AppUser`.
2. **Nowy katalog instrumentów, nie nowy `Instrument`.** Dziś `instruments(band_id, name)` — rozszerzenie tego o `band_id NULL = global` łamie unikalność i semantykę. Czystsze: nowa tabela `global_instruments(name, code?, description?)`, a muzycy w profilu mają `user_global_instruments(user_id, instrument_id, primary)`.
3. **M3 to nie rozszerzenie `EventInvitation`.** `event_invitations.member_id → members` jest jawnie jednozespółowe (unikum na parze event+member tego zespołu). M3 to nowa encja `gig_solicitations(...)` + `solicitation_candidates(...)` + status per kandydat.
4. **Matchmaking jako separate service.** Nie mieszamy z `MemberCommandService` (to jest bandowy) ani z `InstrumentCommandService` (bandowy). Nowy agregat `match/`.

---

## 3. Model koncepcyjny danych (bez DDL — decyzja architekturalna)

```
app_user
  └── user_profile (1:1)                     [M1]
        location_id (z mock listy miejscowości, D5)| lat, lng
        expected_rate_pln     // oczekiwana stawka muzyka — filtr, nie cena (D1)
        max_km                // jak daleko muzyk jest skłonny dojechać
        self_description (text)
        open_for_match (bool)    // RODO: opt-in do porównywalności, default false (D6)
  └── user_global_instruments (N:N)         [M1+M2]
        instrument_id -> global_instruments.id
        primary (bool)

global_instruments                          [M2 — katalog admina]
        id | name | code? | description?
        // nie ma band_id; unikalne po nazwie w całej bazie

gig_solicitation                            [M3]
        id | event_id(BandEvent) | requester_app_user_id
        required_instrument_id | date_needed
        proposed_rate_pln                    // kwota proponowana przez lidera (TWARDE: muzyk rate ≤ tej kwoty, D1/D4)
        max_km                               // twardy limit dojazdu (D4)
        selected_candidate_id (nullable)     // wybrany muzyk (D3)
        status (DRAFT | SENT | IN_PROGRESS | CLOSED)
  └── solicitation_candidate (N per 1)       [M3]
        candidate_id(app_user) | distance_km (snapshot, RB-7)
        status (NOTIFIED | ACCEPTED | DECLINED | SELECTED | DISMISSED)   // D3: po wyborze
```

Uwagi:
- `open_for_match = false` → profile nie jest zapytywalne (RODO: pełna kontrola).
- `required_instrument_id` musi być z `global_instruments`, a nie bandowy.
- `distance_km` wyliczane **przy wysyłce** i zapamiętane — bo adres kandydata może się zmienić później; to „historia dopasowania".

---

## 4. User stories (MVP)

### Lider zespołu (buyer M3)
- **US-L1** Jako lider chcę, żeby przy utworzonym wydarzeniu jednym przyciskiem wysłać powiadomienie do muzyków spełniających: instrument + `open_for_match` + kwota ≥ stawka muzyka + odległość ≤ max_km (wszystkie twardo, D4).
- **US-L2** Chcę widzieć listę kandydatów **bez ich telefonu/adresu/emaila** (tylko imię + odległość) — ochrona danych.
- **US-L3** Chcę filtrować po instrumentach z **globalnego katalogu**, nie po „tagach zespołu" (bo to dwie różne listy, celowo).
- **US-L4** Po wysłaniu chcę widzieć status każdego kandydata (ZAPRASZANY / PRZYJĄŁ / ODMÓWIŁ) z datą.
- **US-L5** Kiedy ≥1 muzyk przyjmie, chcę **wybrać dokładnie jednego** — wybrany jest informowany, pozostali akceptujący automatycznie odrzuceni i informowani (D3).

### Muzyczka/kandydat (odbiorca M3)
- **US-P1** Jako muzyk chcę uzupełnić profil: imię, nazwisko, opis, miasto (z listy), kwota oczekiwana, max km, instrumenty.
- **US-P2** Chcę wybrać, czy jestem widoczna w dopasowaniach (`open_for_match`) i odwołać zgodę kiedykolwiek.
- **US-P3** Chcę dostawać powiadomienia **o wydarzeniach, których profil spełnia** (instrument + stawka + odległość) — bez spamu (D4: twardy filtr).
- **US-P4** Chcę jedno kliknięcie: przyjmam / odmawiam; po decyzji lidera dostaję powiadomienie „wybrany/a" lub „nie wybrany/a" (D3).

### Admin
- **US-ADMIN1** Mogę dodawać/usuwać/podglądać globalne instrumenty.
- **US-ADMIN2** Widzę, ilu muzyków ma dany instrument (statystyki katalogu).

### GDPR / RODO
- **US-GDPR1** Muzyc może eksportować swój profil (JSON) i usunąć wszystkie dane.
- **US-GDPR2** Muszę mieć wyraźne opt-in do porównywalności (`open_for_match`), a nie domyślne ON.

---

## 5. Scenariusze (happy path + edge)

**S1 — Happy**: Lider tworzy event „Impreza firmowa" 2026-11-20, lokalizacja „Kraków, ul. Długa 24” (precyzyjny adres). Zdarzenie ma punkt geo Krakowa. Otwiera formę zapytania: Trąbka (globalny instrument), kwota 350 zł, max dojazd 100 km. System wyszukuje muzyków z `global_instruments=trąbka AND rate ≤ 350 AND open_for_match=true`, liczy Haversine punkt→miejsce zamieszkania; tylko ci **w granicach max_km (≤, twardo — D4)** dostają powiadomienie o wydarzeniu. Trzech akceptuje, dwóch odrzuca. Na panelu zapytania lider widzi: dotarło do 5, przyjęli 3, odrzucili 2. Wybiera jednego — wybrana osoba dostaje powiadomienie „zostałeś/a wybrany/a”, pozostali akceptujący automatycznie DISMISSED + powiadomienie. Zapytanie → CLOSED.

**S2 — Edge**: W zapytaniu jest instrument, którego nikt nie ma w globalnym katalogu. Behawior: formularz blokuje dodanie do zapytania; lista proponuje najbliższe nazwy (fuzzy) lub prosi o kontakt z adminem. NIE tworzymy „instrumentu ad-hoc" w zapytaniu (bo to nie jest bandowy alias).

**S3 — Edge**: Lista kandydatów pusta. UI pokazuje: „Nikt nie spełnia kryteriów" + podpowiedź rozszerzenia (większa kwota, dłuższy dojazd, inna nazwa instrumentu). **Bez cichego wysłania do całego zespołu** — to byłoby złamaniem RODO.

**S4 — Edge**: Kandydat jest `open_for_match=false`. Nie pojawia się w wynikach. Nigdy nie ma dostępu do jego danych w kontekście tego zapytania.

**S5 — Edge**: Wysłanie duplikatu zapytania do tego samego muzyka. Deduplikacja przez `unique(gig_solicitation_id, candidate_id)` + `status`; system proponuje „wyślij ponownie" tylko po CLOSED/REJECT.

**S6 — Edge (wybór, D3)**: Dwoje/ więcej muzyków ACCEPT. Lider wybiera jednego → statusy: wybrany `SELECTED`, pozostali akceptujący `DISMISSED` (auto), powiadomienia o obu zdarzeniach wysyłane w tej samej transakcji. Zapytanie zamykane (`CLOSED`) automatycznie przy wyborze; zamknięcie „na pusto" (bez ACCEPT) możliwe ręcznie.

---

## 6. Reguły biznesowe (RB)

| RB | Reguła | Uzasadnienie |
|----|--------|--------------|
| RB-1 | Match wymaga WSZYSTKICH kryteriów: instrument + `open_for_match` + kwota + odległość. Nie jest „best-effort". | Chroni od wysyłki do niechcianych osób (RODO). |
| RB-2 | Odległość = Haversine (w linii prostej), nie droga. **Hard limit** (D4): muzyk poza limitem `max_km` zapytania NIE dostaje powiadomienia; warunek `odległość ≤ max_km`, nie „jak najbliżsi". | MVP — bez routingu, bez Google API; twardy filtr chroni przed spamem. |
| RB-3 | Kwota w profilu to *oczekiwana stawka* muzyka (D1: kwota wyłącznie informacyjna — filtr propozycji, brak płatności w systemie); w zapytaniu lider podaje kwotę, którą proponuje. Twardy limit: muzyk dostaje zapytanie tylko gdy `profile.rate ≤ proposed_rate`. | Jednoznacznie: `profile.rate ≤ solicitation.rate`. |
| RB-4 | Zawytkanie (solicitation) dotyczy **jednego instrumentu**. Multi-instrumenty = wiele zapytań. | Upychenie „Trąbka+Flet" w jednym zapytaniu komplikuje status per kandydat. |
| RB-5 | Statusy zapytania: DRAFT → SENT (wysłano powiadomienia) → IN_PROGRESS (≥1 ACCEPT) → CLOSED (zamyka się automatycznie przy wyborze jednego muzyka lub ręcznie „na pusto"). Statusy kandydata: NOTIFIED → ACCEPTED / DECLINED; po wyborze: wybrany `SELECTED`, pozostali akceptujący `DISMISSED` (auto, D3). | Jasne, jednoznaczne stany każdej strony; łatwe do raportowania i powiadomień. |
| RB-6 | Kandydat po ACCEPT nie jest usuwany; lider może zamknąć zapytanie bez „odwołania" przyjęcia. | Ochrona przed niezgodnym z realiami odwołaniem umowy. |
| RB-7 | `distance_km` zapisywane w `solicitation_candidate` przy wysyłce, nie liczone w locie (w UI to snapshot). | Stabilna audytu; profil się może zmienić później. |
| RB-8 | Globalne instrumenty: nazwa unikalna; admin może edytować opis, nie nazwy (nazwa = klucz biznesowy). | Mniejsze ryzyko rozjechania profili. |
| RB-9 | `open_for_match` = zgoda muzyka na porównywalność (czyli: jego profil wchodzi do puli, o którą liderzy wysyłają powiadomienia). Ustawiana przy logowaniu (`AppUser.create`) na **false** (D7/D6: domyślnie wyłączona — muzyk sam ją włącza w profilu). | RODO: opt-in, nie opt-out; bez zgody profil nigdy nie trafia do wyników wyszukiwania. |
| RB-10 | Lider nie widzi kontaktów kandydatów do czasu decyzji muzyka (email/telefon odkrywany dopiero po ACCEPT). | Ochrona PI; mniej spamu. |
| RB-11 | Granule adresowe (D5): profil muzyka = **miejscowość** (z mock listy, poziom miasta/miejscowości); wydarzenie = **precyzyjny adres** (ulica+numer) z własnym punktem geo. Odległość liczona jako punkt eventu ↔ punkt miejscowości zamieszkania — przez granularność „miejsce" zasięg jest lekko szerszy niż liczenie od dokładnej ulicy. | Zasięg + RODO: profil nie musi trzymać dokładnego adresu domu. |

---

## 7. Model geolokalizacji — mock

MVP: 3 prawdziwe adresy z listy:
```json
[
  { "id": "waw",     "label": "Warszawa, ul. Marszałkowska",      "lat": 52.23080,  "lng": 21.00678 },
  { "id": "krakow",  "label": "Kraków, ul. Długa",                "lat": 50.05792,  "lng": 19.93866 },
  { "id": "gdynia",  "label": "Gdynia, ul. Kazimierza Górskiego", "lat": 54.51889,  "lng": 18.53072 }
]
```

**Dlaczego tak (a nie wolny tekst + geocoder)?**
- Wolne pole `address: String` to błąd: dopasowanie po tekście jest niemożliwe; Haversine wymaga `lat,lng`.
- Mock = pełna kontrola nad UX testowym; brak zależności od API keys i rate limits.
- Interfejs programistyczny (`LocationCatalog.list()`) zaprojektowany tak, żeby w v2 podmiana na realny geocoder (Nominatim / Google) to podmiana adaptera, nie refactoryzacja domen.

**Granice mock:**
- Brak możliwości wybrania adresu spoza 3 miast — UI blokuje.
- Odległości Haversine między tymi 3 punktami: WAW⇄KRK ~300 km; WAW⇄GDY ~420 km; KRK⇄GDY ~620 km → „dojeżdżam <200 km" wykluczałoby drugi zespół. **Dobrze** — pokazuje, że filtr działa merytorycznie, nie na wyrost.

**Plan migracji do realnego geocodera (v2):**
1. `AddressProvider` = interface (`lookup(addressLabel) → GeoPoint`).
2. v1 impl: lista z bazy/dicta. v2: Nominatim ( darmowy, keyless, rate limit 1 req/s per IP).
3. Cache po adresie (etykiecie) w Redis/H2; TTL 7 dni.

---

## 8. RODO / zgodność

| RYZYKO | MITIGACJA |
|--------|-----------|
| Brak wyraźnej zgody na porównywalność | RB-9: domyślne OFF. |
| Wyciek telephonu/adresu kandydata | RB-10: ujawniany dopiero po ACCEPT. |
| „Profil" = dane osobowe (art. 4 RODO) | `user_profile` ma `personal_data_flag=true`; „usunięcie" profilu = anonimizacja wszystkich pól osobowych + `open_for_match=false` (D7); historia zapytań anoniczna zostaje. |
| Historia zapytań zawiera `distance_km` — to pośrednio ujawnia lokalizację | RB-7: snapshot; przy usunięciu konta usuwany też zapytania (lub anonimizacja). |
| Prawo do sprostowania | Aktualizacja profilu automatycznie „odwija" statusy ACCEPT (kandydat traci prawo do wcześniejszej decyzji) — decyzja do potwierdzenia. |

**Decyzje do omówienia z prawnikiem (poza MO scope tego dokumentu):**
- Czy adres w `user_profile` ma być „miejsce zamieszkania" (RODO: szczególnie chroniona kategoria), czy wystarczy „miasto+ulica"? **Rozstrzygnięte (D5, 2026-10-06): poziom miejscowości** (mock 3 miasta); precyzyjny adres dotyczy TYLKO wydarzenia.

---

## 9. Ryzyka i odpowiedzi na nie

| RYZYKO | Prawdop. | Wpływ | Mitygacja |
|--------|----------|-------|-----------|
| **Pustka katalogu globalnego instrumentów (nikt nic nie uzupełni)** | Średnia | Wysoki | Admin pre-populuje 30–50 najpopularniejszych (Trąbka, Puzon, Saksofon, Flet, Perkusja…). UI wymaga wyboru w profilu. |
| **Nikt nie włącza `open_for_match`** (default OFF — D6) | Średnia | Wysoki | Przy pierwszym pełnym profilu UI prosi o zgodę z wyjaśnieniem korzyści („dostaniesz zapytania dopasowane do Twojej stawki i odległości"); metryka §12 to policzy. |
| Pusta pula kandydatów po filtrach (RB-3) | Wyższa | Średni | UI daje podpowiedź poszerzenia (S3). |
| Zawytkanie wysłane „na sucho", nikt nie odpowiada | Wysoka | Niski | To nie jest błąd systemu; to sygnał produktowy. Statystyki: % zapytań z ≥1 ACCEPT. |
| Muzycy nie chcą się rejestrować (bo muszą uzupełnić profil) | Średnia | Średni | Progresywna ujawnianie: najpierw podstawowe imię/nazwisko; opisy/lokalizacja opcjonalne, ale warunkują widoczność w matchu. |
| Liderzy zaczynają spamować zapytaniami do tych samych muzyków | Niska | Średni | Per kandydata: min 1 tydzień między zapytaniami od tego samego lidera (rate limit). |
| `Instrument` (bandowy) i `GlobalInstrument` żyją obok — ktoś się pomyli w UI | Średnia | Średni | W kodzie: osobne klasy, osobne endpointy, osobne repozytoria. W UI: w „tagach zespołu" nie ma przycisku „dodaj do profilu"; w profilu nie ma opcji edycji bandowych. |
| Mock 3 miasta ogranicza testy E2E | Niska | Niski | Dostateczne; lista jest adapterem, podmiana jest tania. |

---

## 10. Zależności (co musi istnieć zanim zaczniemy kodować M3)

- ✅ `AppUser` — gotowe.
- ⏳ **M1 (profil)**: `user_profile` encja + serwis CRUD + strona HTML (Thymeleaf/Pico.css).
- ⏳ **M2 (katalog globalny)**: `global_instruments` encja + `GlobalInstrumentService` + admin page.
- ⏳ **Matchmaking service**: `MatchQueryService.findCandidates(requiredInstrumentId, maxRate, maxKm) → List<CandidateView>`.
- ⏳ **Powiadomienia**: Email (istnieje — `EmailSender`, `ChannelResolver EMAIL`) + opcjonalnie TG/WhatsApp v2.
- ⏳ **Lokalizacja eventów**: nowy pól `event_location_id` na `band_events` (mock id) + `GeoPoint` na `band_events`.
- ⏳ **Flyway migracje**: `V###__user_profile.sql`, `V###__global_instruments.sql`, `V###__gig_solicitations.sql`.

---

## 11. Kryteria akceptacji MVP

1. Muzyczka/kandydat może w <3 min uzupełnić profil i zobaczyć go po odświeżeniu.
2. Lider może wysłać zapytanie do muzyków spełniających kryteria; lista kandydatów nie zawiera telefonów/adresów/emaili (tylko imię + miasto + odległość).
3. Kandydat może przyjąć/odmówić jednym kliknięciem z powiadomienia email/TG.
4. Statystyki po tygodniu: **≥5 zapytań wysłanych, ≥1 wybrany muzyk (SELECTED)**, 0 zgłoszeń RODO o wyciek danych. (Korekta D3 — „ACCEPT" jest stanem pośrednim; celem jest SELECTED.)
5. Zero wycieków `AppUser`/`Member` do listy kandydatów (QA test: próba odczytu `/api/solicitations/{id}/candidates` z innego zespołu → 403).

---

## 12. Metryki sukcesu (6 tyg. od startu)

- % muzyków z uzupełnionym profilem (`open_for_match=True`) — cel ≥ 50% aktywnych.
- Średni czas od wysłania powiadomień do pierwszej decyzji ACCEPT muzyka — cel < 48 h.
- **% zapytań z wyborem (≥1 SELECTED) — cel ≥ 30%** (nowy kluczowy cel po D3).
- % zapytań z ≥1 ACCEPT — obserwacja; bez twardego celu (stan pośredni do SELECTED).

---

## 13. Kolejność pracy (wewnątrz MVP)

| Krok | Co | Szacunek |
|------|----|----------|
| P0 | `global_instruments` + admin CRUD + seed danych | D2 |
| P1 | `user_profile` endpoint + UI formularza | D3 |
| P2 | `GlobalInstrument` ↔ `UserGlobalInstrument` (N:N) | D2 |
| P3 | Mock `AddressCatalog` (3 miasta) + Haversine helper | D1 |
| P4 | `MatchQueryService.findCandidates(...)` + testy unit | D3 |
| P5 | `gig_solicitation` + `solicitation_candidate` + serwis wysyłki + **logika wyboru jednego (SELECTED/DISMISSED)** (D3) | D4 |
| P6 | UI: „Zapytaj muzyków" (formularz zapytania) + panel statystyk (dotarło/ACCEPT/odmówiło) + przycisk wyboru muzyka | D3 |
| P7 | Powiadomienia email: „zaproszenie" / „wybrany" / „nie wybrany" + link ACCEPT/REJECT (D3) | D2 |
| P8 | Testy end-to-end + QA z RODO checklistą | D2 |

**Suma: ~ 21 dni roboczych po 1 developerze.** Przy 2 deweloperało (parallel: UI + backend): ~ 10–12 dni.

---

## 14. Pytania otwarte do decyzji właściciela produktu

> **Status: WSZYSTKIE ROZSTRZYGNIĘTE (2026-10-06, Michał).** Decyzje oznaczono D-n; one są wiążące dla PR-ów.

| # | Pytanie | Odpowiedź właściciela produktu / decyzja |
|---|---------|------------------------------------------|
| Q1 | Czy zapytanie może dotyczyć więcej niż jednego instrumentu? | **Jedno zapytanie = jeden instrument** (potwierdzone). Ale ten sam muzyk może otrzymywac wiele zapytań o ten sam instrument; lider może wysłac wiele zapytań o kolejne instrumenty na tym samym evencie (np. Trąbka, potem Puzon — dwa oddzielne zapytania powiązane z tym samym eventem). RB-4 pozostaie w mocy. |
| Q2 | Jak działa flow po dotarciu zapytania do muzyk? | **Inaczej niż w szkicu — decyzja D3:** Muzyk, którego profil spełnia kryteria, dostaje **powiadomienie o wydarzeniu** i może **aplikować (ACCEPT) lub odrzucic**. Na widoku wydarzenia/zapytania lider widzi: do ilu osób dotarło, ile odrzucilo, czy ktoś zaakceptował. Spośród zaakceptowanych **lider wybiera dokładnie jednego** → wybrana osoba dostaje powiadomienie „zostałeś/a wybrany/a”, pozostali akceptujący są automatycznie odrzuceni (DISMISSED) i informowani. Zapytanie zamykane przy wyborze. — *Korekta do scenariusza S1; nowy scenariusz S6.* |
| Q3 | Czy `max_km` ma byc twarde, a nie miękko? | **TWARDE (decyzja D4):** muzyk mieszkający w odległości większej niż limit max_km zapytania NIE dostaje powiadomienia. Filtr twardy: `odległość ≤ max_km`. Podpowiedź rozszerzenia (S3) zostaje — ale wysyłka tylko do spełniających, nigdy „najbliżsi”. |
| Q4 | Czy event musi istnieć, żeby wysłać zapytanie? | **(Nierosstrzygnięte jawnie; wniosek z Q2):** wydarzenie musi wczesniej istnieć i mieć precyzyjny adres — bo to ono generuje powiadomienie do muzyk. Lider: najpierw event (z adresem), potem zapytanie. Bezpieczny wniosek, potwierdzić przy PR. |
| Q5 | Czy lider może zaplacic z aplikacji? | **NIE — potwierdzone wprost:** nie chce płatności; kwota w profilu ma być tylko informacja, żeby muzyk dostawał propozycje tylko za kwotę, która go satysfakcjonuje. Brak rozliczeń na poziomie systemu (RODO/KYC bezpieczne), rozliczenie poza systemem. |
| Q6 | Czy „adres" w profilu ma być szczegółowy (ulica/numer) czy sam miasto? | **Profil = miasto/miejsce z listy (D5)** — bez ulicy. Wydarzenie = precyzyjny adres (ulica+numer), żeby wiadomo bylo gdzie jechać. Odległość liczona na poziomie miejscowości, nie dokładnego punktu domu (RODO safe + zasięg lekko szerszy). |
| Q7 | Czy `open_for_match` ma byc globalny czy per instrument? | **Nie rozumiem pytania** — wyjaśnienie: „porównywalność" = zgoda na to, że Twój profil wchodzi do wspólnej puli, którą lider może przeszukiwać przy tworzeniu zapytania (przycisk/włącznik „pozwól dołączać mnie do wyszukiwań"). Rekomendacja bez zmian (D6): **jeden globalny switch w profilu**, nie per instrument — M2 nie ma takiej granularności. Jeśli chcesz per-instrumentowy switch, dopiszemy RB-12 po decyzji. |
| Q8 | Czy profil jest tylko edytowalny (bez usuwania)? | **Rekomendacja akceptowana:** „Usun" = anonimizacja pól osobowych (imię/adres/kwota puste, `open_for_match=false`), ale historia zapytań pozostaje zachowana. To standard RODO: edycja pełna, „usuwanie" technicznie = anonimizacja danych wrażliwych. |

> **D1** — Kwota wyłącznie informacyjna (Q5).
> **D3** — Wybór jednego musicza po kolekcji ACCEPT + powiadomienia o obu stronach (Q2).
> **D4** — Twardy filtr `max_km` (Q3).
> **D5** — Granule adresowe: profil=miejsce, event=adres (Q6).
> **D6** — Switch `open_for_match` globalny; default=false; RODO opt-in (Q7).
> **D7** — Edytowalno/usuwanie profilu = anonimizacja PI (Q8).

### Co się zmienia w architekturze przez te decyzje

| Obszar | Co się zmienia | Gdzie w dokumencie |
|--------|---------------|---------------------|
| Lifecycle zapytania | Nowy stan `SELECTED` po stronie lidera + auto-`DISMISSED` na reszcie ACCEPT-ów; CLOSED = wybieranie/nic (D3) | RB-5, S6 |
| Powiadomienia | Trzy typy: (1) „masz zaproszenie" [match], (2) „byłeś/aś wybrany/a", (3) „nie byłeś/aś wybrany/a" (auto) | P7, RB-5 |
| Model danych | `solicitation_candidate.status`: NOTIFIED / ACCEPTED / DECLINED / SELECTED / DISMISSED; `gig_solicitation.selected_candidate_id` (nullable) | §3, D |
| Kryteria akceptacji MVP | „≥10 solikacji z ≥2 ACCEPT" → **zmienione**: „≥5 zapytań wysłanych, ≥1 wybrany muzyk (SELECTED)" — bo to jest cel; ACCEPT jest pośredni. | §11 |
| Granule geo | `user_profile.location` = miejscowość (mock 3 miasta); `band_events.geo_point` + `event_precision_address` (ulica+numer) | RB-2, RB-11 |

---

## 15. Co dalej (po zatwierdzeniu)

> Decyzje Q1–Q8 zatwierdzone 2026-10-06 — przejdź wprost do PR-ów. Jedyny otwarty wniosek: **Q4** (event musi istnieć przed zapytaniem) — potwierdzić przy planowaniu P5.

1. PR: `V###__user_profile_and_global_instruments.sql` + encje M1+M2 (+ seed 3 lokalizacji mock).
2. PR: UI profilu + CRUD globalnych instrumentów + switch `open_for_match` (D6).
3. PR: match service (twarde filtry RB-1/RB-4, Haversine) + testy unit.
4. PR: solicitation lifecycle + wybór jednego (SELECTED/DISMISSED, D3) + powiadomienia email (3 typy).
5. Beta na 1 zespole, docelowo: wszystkie.
6. v2: realny geocoder (Nominatim jako adapter, RB-7), per-instrumentowy switch zgodności (D7) jeśli poprosi.
