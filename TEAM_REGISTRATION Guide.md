# Instrukcja obsługi: Rejestracja zespołów i zarządzanie użytkownikami

## Spis treści

1. [Rejestracja nowego zespołu](#1-rejestracja-nowego-zespołu)
2. [Logowanie](#2-logowanie)
3. [Zarządzanie użytkownikami zespołu](#3-zarządzanie-użytkownikami-zespołu)
4. [Akceptacja zaproszenia](#4-akceptacja-zaproszenia)
5. [Wielokrotne zespoły](#5-wielokrotne-zespoły)
6. [Bezpieczeństwo](#6-bezpieczeństwo)
7. [API Reference](#7-api-reference)

---

## 1. Rejestracja nowego zespołu

Rejestracja zespołu jest **publiczna i dostępna bez logowania**. Każdy może utworzyć zespół orkiestry dętej.

### Przepływ rejestracji

```
Użytkownik → Formularz rejestracyjny → POST /api/auth/register-team
                                    ↓
                              Utworzony Zespół (Band)
                              Utworzony Admin (AppUser)
                              Przypisanie roli ADMIN (UserTeamRole)
                                    ↓
                              Odpowiedź JSON z danymi zespołu
```

### Wymagane pola

| Pole | Typ | Walidacja | Opis |
|------|-----|-----------|------|
| `teamName` | string | 2-128 znaków | Nazwa zespołu (np. "Orkiestra Dęta Wojkowice Kościelne") |
| `teamSlug` | string | 3-64 znaki, lowercase alphanumeric z myślnikami | URL-friendly identyfikator (np. "wolkow-koscielne") |
| `adminUsername` | string | 3-64 znaki, alphanumeric + `_.-` | Nazwa użytkownika administratora |
| `adminEmail` | string | Format email | Email administratora |
| `adminPassword` | string | 8-128 znaków | Hasło administratora |

### Ograniczenia

- `teamSlug` musi być unikalny w całym systemie (case-insensitive)
- `adminUsername` musi być unikalny w całym systemie
- `adminEmail` musi być unikalny w całym systemie
- Jedna osoba może zarejestrować wiele zespołów (każdy wymaga osobnego konta admina)

### Przykład request (curl)

```bash
curl -X POST http://localhost:8080/api/auth/register-team \
  -H "Content-Type: application/json" \
  -d '{
    "teamName": "Orkiestra Dęta Wojkowice Kościelne",
    "teamSlug": "wojkowice-koscielne",
    "adminUsername": "dyrygent",
    "adminEmail": "jan@wojkowice.pl",
    "adminPassword": "tajneHaslo123"
  }'
```

### Przykład odpowiedzi (201 Created)

```json
{
  "teamId": 2,
  "teamSlug": "wojkowice-koscielne",
  "adminUserId": 2,
  "adminUsername": "dyrygent"
}
```

### Przykład błędu (400 Bad Request)

```json
{
  "error": "Team slug already taken: wojkowice-koscielne"
}
```

### Sprawdzanie dostępności (podczas wypełniania formularza)

```bash
# Czy username jest dostępny?
curl http://localhost:8080/api/auth/check-username?username=dyrygent
# {"available": true}

# Czy email jest dostępny?
curl http://localhost:8080/api/auth/check-email?email=jan@wojkowice.pl
# {"available": true}
```

---

## 2. Logowanie

Logowanie wymaga konta utworzonego podczas rejestracji zespołu lub zaakceptowanego zaproszenia.

### Przepływ logowania

```
Użytkownik → POST /api/auth/login (username + password)
                        ↓
                  Spring Security Authentication
                  BCrypt password verify
                        ↓
                  JWT Token generowane z claims:
                  { sub, userId, email, activeTeamId, activeTeamSlug,
                    activeTeamRole, teamIds }
                        ↓
                  Cookie "JWT" (HttpOnly, SameSite=Lax, 24h)
                        ↓
                  Przekierowanie na stronę główną
```

### JWT Token claims

| Claim | Typ | Opis |
|-------|-----|------|
| `sub` | string | Username |
| `userId` | long | ID użytkownika w bazie |
| `email` | string | Email użytkownika |
| `activeTeamId` | long | ID aktualnie wybranego zespołu |
| `activeTeamSlug` | string | Slug aktualnie wybranego zespołu |
| `activeTeamRole` | string | Rola w aktywnym zespole (ADMIN/MEMBER) |
| `teamIds` | array[long] | Lista ID wszystkich zespołów użytkownika |

### Przykład logowania (curl)

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "dyrygent", "password": "tajneHaslo123"}' \
  -c cookies.txt

# Odpowiedź: 200 OK + cookie JWT
```

### Po logowaniu

- Token JWT jest przechowywany w ciasteczku `JWT` (HttpOnly)
- Wszystkie kolejne requesty (fetch, HTMX) automatycznie dołączają token
- Spring Security ładuje `TeamAwareUserDetails` z bazy danych
- Dane wyświetlane są zgodnie z `activeTeamId` z tokenu

### Wylogowanie

```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -b cookies.txt

# Cookie JWT zostaje wyczyszczone (maxAge=0)
# Przekierowanie na /login
```

---

## 3. Zarządzanie użytkownikami zespołu

Tylko użytkownik z rolą **ADMIN** może zapraszać nowych członków.

### Zaproszenie użytkownika

```
Admin → POST /api/teams/{teamId}/admin/invite (email + role)
                    ↓
              Sprawdzenie czy admin.isAdminOf(teamId)
                    ↓
              Czy user z tym emailiem istnieje?
              ├── TAK → Utworzenie UserTeamRole(user, team, role, token)
              └── NIE → Utworzenie nieaktywnego AppUser
                        + UserTeamRole(invitationToken)
                    ↓
              Zwrócenie invitationToken (w produkcji: wysyłka email)
```

### Przykład zaproszenia (curl)

```bash
curl -X POST http://localhost:8080/api/teams/2/admin/invite \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{
    "email": "muzyk@wojkowice.pl",
    "role": "MEMBER"
  }'
```

### Odpowiedź (200 OK)

```json
{
  "message": "Invitation sent",
  "invitationToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

> **Uwaga:** W tej wersji token jest zwracany w odpowiedzi API.
> W produkcji token powinien być wysyłany jako link w emailu.

### Role użytkowników

| Rola | Uprawnienia |
|------|-------------|
| `ADMIN` | Pełny dostęp: zarządzanie zespołem, zapraszanie użytkowników, wszystkie operacje CRUD |
| `MEMBER` | Dostęp do danych zespołu, brak zarządzania użytkownikami |

Ograniczenia:
- Jeden zespół musi mieć co najmniej jednego ADMINA
- ADMIN może delegować rolę ADMIN innemu userowi (przez zaproszenie z role=ADMIN)
- User może należeć do wielu zespołów z różnymi rolami

---

## 4. Akceptacja zaproszenia

### Przepływ akceptacji

```
Nowy user → otrzymuje invitationToken (przez email w produkcji)
            → POST /api/teams/accept-invitation/{token}
              (username + password)
                              ↓
                    Weryfikacja tokena (UserTeamRole.findByInvitationToken)
                              ↓
                    AppUser.acceptInvitation(newPassword, newUsername)
                    UserTeamRole.acceptInvitation()
                              ↓
                    User może się zalogować
```

### Przykład akceptacji (curl)

```bash
curl -X POST http://localhost:8080/api/teams/accept-invitation/a1b2c3d4-e5f6-7890-abcd-ef1234567890 \
  -H "Content-Type: application/json" \
  -d '{
    "username": "muzyk1",
    "password": "mojeHaslo456"
  }'
```

### Odpowiedź (200 OK)

```json
{
  "message": "Invitation accepted. You can now log in."
}
```

### Błędy

| Scenariusz | Status | Komunikat |
|------------|--------|-----------|
| Nieprawidłowy token | 400 | "Invalid or expired invitation" |
| Token już zużyty | 400 | "Invitation already accepted" |

---

## 5. Wielokrotne zespoły

### Model danych

```
AppUser (1) ──── (N) UserTeamRole (N) ──── (1) Band

Przykład:
  user "jan" → UserTeamRole(team=wojkowice, role=ADMIN)
             → UserTeamRole(team=psary, role=MEMBER)
             → UserTeamRole(team=czeladz, role=ADMIN)
```

### Przełączanie zespołu

Po logowanieniu użytkownik widzi dane zespołu z `activeTeamId` (domyślnie pierwszy zespół z rolą ADMIN, a potem pierwszy dostępny).

> **Do zaimplementować:** UI wyboru zespołu gdy user ma >1 zespół.

### Sprawdzenie aktualnego użytkownika

```bash
curl http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

Odpowiedź:

```json
{
  "userId": 2,
  "username": "dyrygent",
  "email": "jan@wojkowice.pl",
  "activeTeamId": 2,
  "activeTeamSlug": "wojkowice-koscielne",
  "activeTeamRole": "ADMIN",
  "teamIds": [2, 5, 8]
}
```

---

## 6. Bezpieczeństwo

### Izolacja danych między zespołami

- Każdy request API weryfikuje JWT token
- Token zawiera `activeTeamId` - serwisy filtrowane po team
- Operacje CRUD sprawdzają `user.belongsToTeam(teamId)`
- Dane zespołu A są niewidoczne dla zespołu B

### Endpointy publiczne (bez autoryzacji)

| Endpoint | Metoda | Opis |
|----------|--------|------|
| `/api/auth/login` | POST | Logowanie |
| `/api/auth/register-team` | POST | Rejestracja zespołu |
| `/api/auth/accept-invitation/{token}` | POST | Akceptacja zaproszenia |
| `/api/auth/check-username` | GET | Sprawdzenie username |
| `/api/auth/check-email` | GET | Sprawdzenie email |
| `/login`, `/register` | GET | Strony HTML |
| `/css/**`, `/js/**`, `/images/**` | GET | Assety statyczne |

### Endpointy administracyjne (wymagają roli ADMIN)

| Endpoint | Metoda | Opis |
|----------|--------|------|
| `/api/teams/{teamId}/admin/invite` | POST | Zaproszenie użytkownika |

### Wszystkie pozostałe endpointy

Wymagają autoryzacji (poprawny JWT token).

---

## 7. API Reference

### Rejestracja zespołu

```
POST /api/auth/register-team
Content-Type: application/json

Request:
{
  "teamName": "string (2-128 chars, required)",
  "teamSlug": "string (3-64 chars, [a-z0-9-], required)",
  "adminUsername": "string (3-64 chars, required)",
  "adminEmail": "string (valid email, required)",
  "adminPassword": "string (8-128 chars, required)"
}

Response 201:
{ "teamId": Long, "teamSlug": String, "adminUserId": Long, "adminUsername": String }

Response 400:
{ "error": "string" }
```

### Logowanie

```
POST /api/auth/login
Content-Type: application/json

Request:
{ "username": "string", "password": "string" }

Response 200: (cookie JWT ustawiona)
Response 401: Unauthorized
```

### Wylogowanie

```
POST /api/auth/logout

Response 200: (cookie JWT wyczyszczone)
```

### Info o userze

```
GET /api/auth/me
Authorization: Bearer <JWT>

Response 200:
{
  "userId": Long, "username": String, "email": String,
  "activeTeamId": Long, "activeTeamSlug": String,
  "activeTeamRole": String, "teamIds": [Long]
}
```

### Zaproszenie użytkownika

```
POST /api/teams/{teamId}/admin/invite
Authorization: Bearer <JWT> (admin teamu)
Content-Type: application/json

Request:
{ "email": "string", "role": "ADMIN|MEMBER" }  // domyślnie MEMBER

Response 200:
{ "message": "Invitation sent", "invitationToken": "string" }

Response 400: błędy walidacji
Response 401: brak autoryzacji
Response 403: user nie jest adminem tego teamu
```

### Akceptacja zaproszenia

```
POST /api/teams/accept-invitation/{token}
Content-Type: application/json

Request:
{ "password": "string (8+ chars)", "username": "string" }

Response 200:
{ "message": "Invitation accepted. You can now log in." }

Response 400: nieprawidłowy token / już akceptowane
```

### Listowanie członków zespołu

```
GET /api/teams/{teamId}/members
Authorization: Bearer <JWT> (członek teamu)

Response 200:
{ "teamId": Long, "members": [...] }
```

---

## Schemat bazy danych

```
app_users
├── id (PK)
├── username (UNIQUE)
├── email (UNIQUE)
├── password_hash
├── active
├── email_verified
├── created_at
└── last_login_at

user_team_roles
├── id (PK)
├── user_id (FK → app_users)
├── team_id (FK → bands)
├── role (ADMIN | MEMBER)
├── assigned_at
├── invitation_token
├── invitation_accepted
├── invitation_accepted_at
└── UNIQUE(user_id, team_id)

bands
├── id (PK)
├── name (UNIQUE)
├── slug (UNIQUE, NOT NULL)  ← NOWE
├── description
└── created_at
```

---

## Migracja istniejących danych

Przy pierwszym uruchomieniu z nową wersją:

1. Flyway V11 tworzy tabele `app_users` i `user_team_roles`
2. Flyway V11 dodaje kolumnę `slug` do `bands` (NOT NULL)
3. Flyway V11 seeduje domyczny user `admin` / hasło: `admin` (BCrypt)
4. Flyway V11 łączy admin z istniejącym zespołem id=1
5. Stary hardcoded user `/InMemoryUserDetailsManager` zostaje usunięty
6. Nowy system w pełni oparty na bazie danych

---

## Historia zmian

| Komentarz | Opis |
|-----------|------|
| `V11__multi_tenant_users.sql` | Nowe tabele + seed danych |
| `V8__add_display_in_list.sql` | Idempotentne `ADD COLUMN IF NOT EXISTS` |
| `V7__add_order_attributes.sql` | Idempotentne `ADD COLUMN IF NOT EXISTS` |
