# Event Notifications — Plan Implementacji

## Cel

Umożliwienie zarządcy zespołu wysyłki zaproszeń na wydarzenie do członków zespołu,
którzy mogą odpowiedzieć "Będę / Nie będę / Dam znać później" bez logowania się
do aplikacji (poprzez magic link w emailu).

## Architektura — Przegląd

```
┌──────────────────────────────────────────────────┐
│  Warstwa Domeny                                   │
│  ┌─────────────────────────────────────────────┐  │
│  │ EventInvitation (nowa encja)                 │  │
│  │ - eventId + memberId + token (UUID)          │  │
│  │ - notificationStatus (enum)                  │  │
│  │ - sentAt, respondedAt                        │  │
│  │ - preferredChannel (EMAIL, MESSENGER, ...)    │  │
│  └─────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────┐  │
│  │ Channel (interface)                          │  │
│  │ ┌─────────────────────────────────────────┐  │  │
│  │ │ EmailChannel                             │  │  │
│  │ │ MessengerChannel (future)               │  │  │
│  │ └─────────────────────────────────────────┘  │  │
│  └─────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────┐
│  Warstwa Aplikacji (Services)                     │
│  ┌─────────────────────────────────────────────┐  │
│  │ NotificationCommandService                   │  │
│  │ - sendInvitation(event, member)              │  │
│  │ - sendAllPending(event)                      │  │
│  │ - resendFailed(event, member)                │  │
│  │ - resolveChannel(member) -> Channel          │  │
│  └─────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────┐
│  Adapter (Web)                                    │
│  ┌─────────────────────────────────────────────┐  │
│  │ PublicResponseController (NEW)               │  │
│  │ GET /public/events/{token}                   │  │
│  │ POST /public/events/{token}/response         │  │
│  └─────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────┐  │
│  │ EventController (zmodyfikowany)              │  │
│  │ POST /api/events/{id}/send-all              │  │
│  │ POST /api/events/{id}/send/{memberId}       │  │
│  └─────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────┘
```

## Fazy Implementacji

### Phase 1: Core Data Model
- Encja `EventInvitation` w `domain/event/`
- Enum `NotificationStatus`: NOT_SENT, QUEUED, SENT, FAILED
- Repozytorium `EventInvitationRepository` (interfejs domenowy)
- Adapter JPA + Spring Data
- Flyway migration V22: `event_invitations` table
- **Testy**: jednostkowe encji, repozytorium

### Phase 2: Public Response API (magic link)
- `PublicResponseController` na `/public/events/{token}`
- Widok Thymeleaf z informacjami o wydarzeniu + przyciskami odpowiedzi
- Endpoint POST do udzielenia odpowiedzi przez magic link
- Po udzieleniu odpowiedzi: aktualizacja `EventParticipation` + `EventInvitation.respondedAt`
- Walidacja: token istnieje, nie wygasł (opcjonalnie)
- Bezpieczeństwo: endpoint publiczny (bez autoryzacji)
- **Testy**: jednostkowe + UI Selenium

### Phase 3: Channel Abstraction + Email Channel
- Interfejs `Channel` z metodą `send(EventInvitation, BandEvent, Member)`
- `ChannelResolver` — wybiera kanał na podstawie preferencji członka
- `EmailChannel` — implementacja wysyłki przez Spring Mail
- Szablon email (Thymeleaf): dane wydarzenia + przyciski odpowiedzi
- **Testy**: jednostkowe + integracyjne (GreenMail)

### Phase 4: UI Integration
- Przycisk "Wyślij zaproszenia do wszystkich" na szczegółach wydarzenia
- Przycisk "Wyślij zaproszenie" per uczestnik w tabeli
- Status wysyłki: NOT_SENT / QUEUED / SENT / FAILED
- Zabezpieczenie przed ponowną wysyłką (sprawdzanie statusu)
- **Testy**: UI Selenium dla nowych elementów

### Phase 5: NotificationCommandService
- `sendAllPending(event)` — wysyłka do wszystkich z NOT_SENT lub FAILED
- `sendSingle(event, member)` — wysyłka do pojedynczego członka
- Tworzenie `EventInvitation` przy pierwszej wysyłce
- Generowanie tokena (UUID)
- **Testy**: jednostkowe command service

## Szczegóły Implementacji

### EventInvitation Entity
```java
@Entity
@Table(name = "event_invitations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"event_id", "member_id"})
})
public class EventInvitation {
    Long id;
    BandEvent bandEvent;          // ManyToOne
    Member member;                // ManyToOne
    String token;                 // UUID, unique
    NotificationStatus status;    // NOT_SENT, QUEUED, SENT, FAILED
    LocalDateTime sentAt;
    LocalDateTime respondedAt;
    String preferredChannel;      // "EMAIL"
}
```

### NotificationStatus Enum
- `NOT_SENT` — nie rozpoczęto wysyłania
- `QUEUED` — w kolejce do wysłania
- `SENT` — wysłanie udane
- `FAILED` — wysłanie nieudane

### Email Template
- Nazwa wydarzenia, data, godzina, miejsce, typ płatności
- Instrument (tag) członka
- Imię/Nazwisko + email członka (weryfikacja)
- 3 przyciski: Będę / Nie będę / Dam znać później
- Link do strony publicznej (magic link)

### Public Page
- Pełne info o wydarzeniu
- Aktualna odpowiedź (jeśli udzielona)
- 3 przyciski odpowiedzi
- Po kliknięciu w email: przekierowanie na stronę + info o dodaniu odpowiedzi

### Zabezpieczenie Multi-Tenant
- `EventInvitation` kaskadowo przez `BandEvent.band` → `Band`
- Wszystkie query przez bandId
- Public response: token weryfikuje event → event.band → band_id w kontekście

### Pipeline Budowania
- Każda zmiana: `mvn test` przed commitem
- Nowe migracje Flyway: numer V22, V23, ...
- UI testy: nowa klasa lub rozszerzenie istniejącej
