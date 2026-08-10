# Windband Manager — Zarządzanie Orkiestrą Detą

> **Aplikacja webowa do kompleksowego zarządzania orkiestrą detą.** Od członkowników i prób, przez wydarzenia i inwentarz, po analitykę i raporty — wszystko w jednym miejscu, dostępnym z każdego urządzenia.

---

## 🎯 Dla kogo?

| Rola | Jak Windband Manager pomaga |
|------|----------------------------|
| **Dyrygent / Przewodnik zespołu** | Pełny obraz składu orkiestry, szybkie planowanie prób i koncertów, odznaczanie obecności w sekundach |
| **Zarząd / Sekretarz zespołu** | Rejestracja członków, zarządzanie danymi osobowymi (RODO), zaproszenia na wydarzenia bez logowania, historia płatności |
| **Kierownik inwentarza** | Ewidencja stroji, instrumentów i odznaczeń — kto ma co, co jest dostępne, historia przydziałów |
| **Członek orkiestry** | Otrzymuje zaproszenia e-mail, potwierdza udział jednym kliknięciem, widzi swoje przydziały |
| **Administrator IT** | Nowoczesny stack (Java 21, Spring Boot 3, Keycloak, Superset), wdrożenie na Railway/Docker, testy automatyczne |

---

## 🚀 Co potrafi?

### 👥 Zarządzanie członkami i strukturą zespołu
- **Profil członka**: dane osobowe, instrumenty, historia członkostwa, zgody RODO
- **Grupy statyczne i dynamiczne**: np. "Aktywni gracze" auto-tworzone na podstawie atrybutu `Grający członek = true`
- **Atrybuty niestandardowe**: dodaj dowolne pola (BOOLEAN, TEXT, SELECT, DATE, MULTI_SELECT) — np. "Prawo jazdy", "Dostępność w weekendy"
- **Import/eksport**, wyszukiwanie, filtrowanie, sortowanie

### 🎵 Próby i spotkania
- **Harmonogram**: nadchodzące i przeszłe próby w jednym widoku (podział na "nadchodzące" / "przeszłe")
- **Szybka obecność (⚡)**: modalny tryb "jeden członek na raz" — 4 przyciski (Obecny / Usprawiedliwiony / Nieobecny / Brak odp.), automatyczne przejście do następnego, cofanie, zapis co krok
- **Zapis zbiorczy**: zaznacz statusy w tabeli, jeden klik "Zapisz obecność"
- **Zaproszenia e-mail**: automatyczne powiadomienia o nowej próbie (tylko dla członków z zgodą)
- **Historia obecności** na poziomie członka i prób

### 🎪 Wydarzenia i koncerty
- **Typy**: Koncert, Ceremonia, Parad, Konkurs, Festiwal, Inne
- **Modele płatności**: Bezpłatne / Podział między grających / Na kasę zespołu
- **Zaproszenia publiczne (magic link)**: członek klika w e-mailu → strona z przyciskami "Będę / Nie będę / Dam znać później" — **bez logowania**
- **Wybór instrumentów**: każdy potwierdzony uczestnik wybiera swój instrument
- **Statystyki płatności**: kto zapłacił, kto nie, wypłaty na osobę (dla modelu podziału)

### 📦 Inwentarz (stroje, instrumenty, odznaczenia, zamówienia)
- **Cykle życia**: Dostępny → Przydzielony → Zwrócony → Wycofany ze stanu / Zlikwidowany
- **Historia przydziałów**: kto, co, kiedy otrzymał, w jakim stanie, kiedy zwrócił
- **Atrybuty niestandardowe** per typ inwentarza: rozmiar stroju, marka instrumentu, numer seryjny, data przyznania odznaki
- **Zamówienia**: proces SUBMITTED → PENDING_APPROVAL → IN_PRODUCTION → SHIPPED → DELIVERED / CANCELLED
- **Widoki tabowe**: Zamówienia / Ekwipunek / Instrumenty / Odznaczenia — przełączane bez przeładowania (HTMX)

### 📊 Analityka i raporty (Superset)
- **Embedded dashboards**: Superset w iframe z Row-Level Security (`band_id = TwójZespół`)
- **Guest tokeny**: generowane na żądanie, ważne chwilę, bezpieczne
- **Raporty PDF (JasperReports)**: "Sprawozdanie Zespołu" — gotowy do druku/eksportu miesięczny raport

### 🔐 Bezpieczeństwo i wielodostępność
- **Keycloak (OIDC)**: logowanie SSO, rejestracja, reset hasła — poza aplikacją
- **Wielozespolowość (multi-tenant)**: jeden użytkownik → wiele zespołów, przełącznik w nagłówku
- **Role**: SYSTEM_ADMIN (globalny), ADMIN (zespołu), MEMBER
- **CSRF wyłączone dla API**, sesje HTTP-only, JWT w ciasteczku dla HTMX

---

## 🛠 Technologia

| Warstwa | Stack |
|---------|-------|
| **Backend** | Java 21, Spring Boot 3.3.5, Spring Security, JPA/Hibernate, Flyway |
| **Baza** | PostgreSQL (prod), H2 (testy), Testcontainers (integracyjne) |
| **Frontend** | Thymeleaf + HTMX (server-rendered SPA-like), PicoCSS, Vanilla JS |
| **Auth** | Keycloak 25 (OIDC Authorization Code Flow) |
| **BI** | Apache Superset (embedded SDK, guest tokens, RLS) |
| **Raporty** | JasperReports (lokalna kompilacja `.jrxml` → PDF) |
| **Testy** | JUnit 5, ArchUnit, Selenium (headless Chrome), MockMvc |
| **CI/CD** | Maven, Docker, Railway, GitHub Actions |
| **Jakość** | Checkstyle, SpotBugs, ArchUnit (egzekwuje Ports & Adapters) |

---

## 🏗 Architektura — Ports & Adapters (Hexagonal) + DDD + CQRS-light

```
domain/                    ← Czysta domena (żadnych zależności Spring)
  band/                    ← Band, MemberAttributeDef/Value
  member/                  ← Member, Instrument, Group, Attendance
  rehearsal/               ← Rehearsal, Attendance
  event/                   ← BandEvent, EventParticipation, EventInvitation
  inventory/               ← Uniform/Instrument/AwardItem, Order, AttributeDefs
  user/                    ← AppUser, UserTeamRole
  dashboard/               ← SupersetDashboard, DashboardBandAssignment

application/               ← Przypadki użycia (brak frameworków)
  command/                 ← Zapis (Command Services)
  query/                   ← Odczyt (Query Services, DTO)

adapter/
  in/web/                  ← Kontrolery REST + Thymeleaf, Security
  out/persistence/         ← Adaptery repozytoriów (implementują interfejsy z domain/)
```

**Kluczowa zasada**: Interfejsy repozytoriów w `domain/` definicują **wszystkie** metody. Spring Data w `adapter/out/` rozszerza tylko `JpaRepository`. Adaptery implementują interfejsy domenowe.

**ArchUnit egzekwuje**:
- `application.*` nie zależy od `org.springframework.web.*` ani `adapter.*`
- Warstwy nie łączą się "na skróty"

---

## 📦 Szybki start (lokalnie)

```bash
# Wymagania: Java 21, Docker, Maven (lub ./mvnw)
git clone https://github.com/michalbzowski/windband-manager.git
cd windband-manager

# Podnieś infrastrukturę (Keycloak, PostgreSQL, Mailpit)
docker compose up -d

# Zbuduj i uruchom
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Aplikacja: http://localhost:8080
# Keycloak Admin: http://localhost:8180 (admin/admin z .env)
# Mailpit (podgląd e-maili): http://localhost:8025
```

> **Uwaga**: Profil `local` używa Keycloak na `localhost:8180` i bazy na `localhost:5432`. Zmienne w `.env.local` / `application-local.yml`.

---

## 🧪 Testy

```bash
# Wszystkie testy (jednostkowe + integracyjne + UI Selenium + ArchUnit)
./mvnw test

# Tylko testy jednostkowe / query / command
./mvnw test -Dtest="*Query*Test,*Command*Test"

# Tylko testy architektury
./mvnw test -Dtest=ArchitectureTest

# Pełna weryfikacja (compile + checkstyle + spotbugs + testy)
./mvnw clean verify
```

**Bramka jakości (Gate)**: `./mvnw clean verify` musi przejść **zanim** commit trafi do gita.  
Checkstyle = 0 naruszeń, SpotBugs = 0 ostrzeżeń, ArchUnit = 0 naruszeń, Testy = zielone.

---

## 🚢 Wdrożenie (Railway / Docker)

```bash
# Docker image
docker build -t windband-manager .
docker run -p 8080:8080 --env-file .env windband-manager

# Railway (zalecane dla produkcji)
railway deployment up --service windband-manager
```

**Kluczowe zmienne środowiskowe** (`.env.example`):
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS`
- `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET`, `KEYCLOAK_AUTH_URL`, `KEYCLOAK_REALM`
- `BASE_URL` (publiczny URL aplikacji, np. `https://app.bandmanager.pl`)
- `SUPERSET_BASE_URL`, `SUPERSET_PUBLIC_URL`, `SUPERSET_USERNAME`, `SUPERSET_PASSWORD`
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASS` (SendGrid API na porcie 443 — Railway blokuje SMTP)

---

## 📁 Kluczowe ścieżki w repo

```
src/main/java/pl/michalbzowski/windband/
├── domain/                    # Entities, enums, repository interfaces
├── application/
│   ├── command/               # Write use cases
│   └── query/                 # Read use cases, DTOs
├── adapter/in/web/            # Controllers (REST + Thymeleaf)
├── adapter/out/persistence/   # Spring Data adapters
├── config/                    # Security, Test, RestTemplate
└── infrastructure/superset/   # SupersetClient, DTOs

src/main/resources/
├── templates/                 # Thymeleaf (fragments/, members/, events/, ...)
├── static/js/                 # htmx.min.js, windband-utils.js, superset-embedded-sdk.js
├── reports/                   # .jrxml (JasperReports)
└── db/migration/              # Flyway V1..V21

src/test/
├── java/.../adapter/in/web/   # Selenium UI tests (UiTestBase)
├── java/.../application/      # Unit tests (command/query)
├── java/.../architecture/     # ArchUnit tests
└── resources/application-test.yml  # H2, test profile
```

---

## 💡 Dlaczego Windband Manager?

| Problem | Rozwiązanie w Windband Manager |
|---------|-------------------------------|
| "Kto ma który instrument?" | Historia przydziałów + stan techniczny + filtrowanie |
| "Kto przyjdzie na koncert?" | Magic link w e-mailu → 1 klik "Będę" bez logowania |
| "Ile zapłacił kto?" | Model płatności: podział / kasa zespołu, statusy PENDING/PAID |
| "Jak zrobić sprawozdanie?" | JasperReports w aplikacji → PDF w 1 kliknięciu |
| "Jak podzielić na sekcje?" | Grupy dynamiczne z atrybutu BOOLEAN (np. "Grający członek") |
| "Jak nie zgubić stroje?" | Cykl życia: dostępny → przydzielony → zwrócony → wycofany |
| "Jak dodać własne pole?" | Atrybuty niestandardowe (EAV) — bez migracji bazy |

---

## 🤝 Rozwój

1. **Fork** → **Branch** (`feat/nazwa` / `fix/nazwa`)
2. **Kod** + **testy** (jednostkowe / UI / ArchUnit)
3. **Lokalnie**: `./mvnw clean verify` — musi być zielone
4. **PR** → Code Review → Merge do `main`
5. **CI** (GitHub Actions) buduje, testuje, wdraża na Railway

> **Konwencja commitów**: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`

---

## 📄 Licencja

MIT License — wolne użytkowanie, modyfikacja, dystrybucja.

---

## 🔗 Linki

- **Produkcja**: https://app.bandmanager.pl
- **Keycloak Admin**: https://login.bandmanager.pl/admin/
- **Superset**: (wewnętrzny, dostępny przez aplikację)
- **Swagger UI**: `/swagger-ui.html` (lokalnie)
- **Build info**: `GET /api/auth/build-info`

---

*Zbudowane z ❤️ dla orkiestr detych. Jeśli Windband Manager ułatwia Ci pracę — daj ⭐ na GitHubie!*