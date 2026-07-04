# windband-manager — Project Documentation

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Tech Stack](#tech-stack)
4. [Project Structure](#project-structure)
5. [Domain Model](#domain-model)
6. [API Endpoints](#api-endpoints)
7. [Page Controllers & Templates](#page-controllers--templates)
8. [Security](#security)
9. [Superset Integration](#superset-integration)
10. [Testing](#testing)
11. [Key Patterns & Conventions](#key-patterns--conventions)
12. [Common Pitfalls](#common-pitfalls)
13. [Database Migrations](#database-migrations)
14. [Build & Deploy](#build--deploy)

---

## Overview

Wind band management application ("Zarzadzanie orkiestra deta"). DDD/CQRS-light with Ports & Adapters architecture. Manages members, instruments, groups, rehearsals, events, inventory (uniforms/instruments/awards/orders), dashboards (Superset), and multi-tenant team access.

## Architecture

**Ports & Adapters (Hexagonal) + DDD + CQRS-light**

```
pl.michalbzowski.windband/
  domain/                    <-- Pure domain (no Spring deps except JPA annotations)
    band/                    <-- Band, MemberAttributeDef, MemberAttributeValue
    member/                  <-- Member, Instrument, Group, MemberInstrument
    rehearsal/               <-- Rehearsal, Attendance
    event/                   <-- BandEvent, EventParticipation
    inventory/               <-- UniformItem, InstrumentItem, AwardItem, InventoryOrder,
                                 AssetAssignmentHistory, attribute defs, OrderStatus, etc.
    user/                    <-- AppUser, UserTeamRole, TeamRole
    dashboard/               <-- SupersetDashboard, DashboardBandAssignment
  application/               <-- Use cases (no framework deps)
    command/                 <-- Command services (write operations)
      member/                <-- MemberCommandService, InstrumentCommandService, GroupCommandService
      rehearsal/             <-- RehearsalCommandService
      event/                 <-- EventCommandService
      inventory/             <-- InventoryCommandService, *AttributeCommandService
      band/                  <-- MemberAttributeCommandService
      dashboard/             <-- DashboardAssignmentCommandService
    query/                   <-- Query services (read operations)
      member/                <-- MemberQueryService, GroupQueryService, InstrumentQueryService
      rehearsal/             <-- RehearsalQueryService
      event/                 <-- EventQueryService
      inventory/             <-- InventoryQueryService, InventoryAttributeQueryService, AwardQueryService
      band/                  <-- BandQueryService, MemberAttributeQueryService
      dashboard/             <-- DashboardQueryService
      report/                <-- ReportQueryService
      team/                  <-- TeamQueryService
  adapter/
    in/
      web/                   <-- Controllers (REST + Thymeleaf pages)
      security/              <-- WindbandOidcUser, KeycloakOAuth2UserService
    out/
      persistence/           <-- Repository adapters (implement domain repo interfaces)
        user/                <-- SpringDataAppUserRepository, SpringDataUserTeamRoleRepository
  config/                    <-- SecurityConfig, TestSecurityConfig, RestTemplateConfig, etc.
  infrastructure/
    superset/                <-- SupersetClient, SupersetApiDtos
```

**Key rule:** Domain repositories are interfaces in `domain/` with ALL methods defined. Spring Data interfaces in `adapter/out/persistence/` extend ONLY `JpaRepository` (no custom methods). Adapter classes implement domain repository interfaces and delegate to Spring Data.

## Tech Stack

- **Java 21**, **Spring Boot 3.3.5**
- **PostgreSQL** (prod), **H2** (test), **Flyway** (migrations)
- **Spring Security** + **Keycloak** (OIDC Authorization Code Flow)
- **Thymeleaf** + **HTMX** (server-rendered SPA-like UI)
- **JPA/Hibernate**, **Lombok**
- **Apache HttpClient 5** (Superset API calls with Host header manipulation)
- **Superset** (embedded dashboards via guest tokens + RLS)
- **Testcontainers** (PostgreSQL for integration tests)
- **Selenium** + **WebDriverManager** (UI tests)
- **ArchUnit** (architecture rule enforcement)
- **SpringDoc OpenAPI** (Swagger at /swagger-ui.html)
- **PicoCSS** (CSS framework)

## Project Structure

```
windband-manager/
  src/
    main/
      java/pl/michalbzowski/windband/
        WindBandApplication.java
        domain/              <-- Domain entities, enums, repository interfaces
        application/         <-- Command & query services, DTOs, commands
        adapter/
          in/
            web/             <-- Controllers, GlobalExceptionHandler, TeamModelAdvice
            security/        <-- WindbandOidcUser, KeycloakOAuth2UserService
          out/
            persistence/     <-- Repository adapters + Spring Data interfaces
        config/              <-- SecurityConfig, TestSecurityConfig, RestTemplateConfig, etc.
        infrastructure/
          superset/          <-- SupersetClient, SupersetApiDtos
      resources/
        templates/           <-- Thymeleaf templates
          fragments/layout.html
          members/, instruments/, tags/, groups/, rehearsals/, events/,
          inventory/, orders/, reports/, dashboards/, band/
        static/
          js/                <-- htmx.min.js, windband-utils.js, superset-embedded-sdk.js
        db/migration/        <-- Flyway migrations V1-V17
        application.yml
        application-local.yml
    test/
      java/pl/michalbzowski/windband/
        BaseIntegrationTest.java   <-- Testcontainers PostgreSQL base
        UiTestBase.java            <-- Selenium base (headless Chrome)
        IntegrationTest.java
        architecture/ArchitectureTest.java
        config/TestSecurityConfig.java
        adapter/in/web/            <-- UI tests (Selenium)
        application/command/       <-- Command service unit tests
        application/query/         <-- Query service tests
        domain/                    <-- Domain entity tests
      resources/
        application-test.yml       <-- H2 in-memory, Flyway disabled
        data.sql                   <-- Test seed: band id=1, user id=1, admin/admin
  docker-compose.yml
  Dockerfile
  pom.xml
```

## Domain Model

### Bounded Contexts

#### Band Context
- **Band** (bands): id, name (unique), slug (unique), description, createdAt
- **MemberAttributeDef** (member_attribute_defs): band-specific custom fields for members (name, type="BOOLEAN"/"TEXT", required, displayOrder, displayInList, options as JSON)
- **MemberAttributeValue** (member_attribute_values): values of custom attributes per member

#### Member Context
- **Member** (members): firstName, lastName, dateOfBirth, email, phone, active, joinedDate, resignedDate, band (ManyToOne). Has `instruments` (OneToMany MemberInstrument, orphanRemoval). Methods: `addInstrument()`, `removeInstrument()`, `changeInstrument()` (clears all, adds new), `deactivate()`, `isMinor()`, `isSenior()`, `getAge()`.
- **Instrument** (instruments): name (unique), description, sortPriority. Global catalog.
- **MemberInstrument** (member_instruments): member + instrument + isPrimary. Equals/hashCode on member+instrument.
- **Group** (member_groups): name (unique), description. Has `members` (OneToMany GroupMember, orphanRemoval).
- **GroupMember** (group_members): group + member
- **Group ↔ MemberAttributeDef (dynamic)**: A `Group` whose `dynamicSource` field is non-null is a "dynamic" group: it is auto-created for every `MemberAttributeDef` of type `BOOLEAN`, members are auto-added when their attribute value is `"true"`, and the group follows the attribute's name/type through rename/delete/type-change. Manual add/remove via the UI or API is rejected with 409 Conflict.

#### Rehearsal Context
- **Rehearsal** (rehearsals): date, startTime, endTime, location, notes, band (ManyToOne EAGER). Has `attendances` (OneToMany Attendance, orphanRemoval, EAGER).
- **Attendance** (attendances): rehearsal (EAGER) + member (EAGER) + status. Unique constraint: rehearsal+member.
- **AttendanceStatus**: PRESENT, EXCUSED, UNEXCUSED, NO_RESPONSE

#### Event Context
- **BandEvent** (band_events): name, date, startTime, location, eventType, paymentType, paymentAmount, notes, band (ManyToOne). Has `participations` (OneToMany EventParticipation, orphanRemoval).
- **EventParticipation** (event_participations): bandEvent + member + response + paymentAmount + paymentStatus. Unique constraint: event+member.
- **EventType**: CONCERT, CEREMONY, PARADE, COMPETITION, FESTIVAL, OTHER
- **ParticipationResponse**: CONFIRMED, DECLINED, NO_RESPONSE, LATER
- **PaymentType**: FREE, PAID_SPLIT, PAID_TO_TEAM
- **PaymentStatus**: NOT_APPLICABLE, PENDING, PAID

#### Inventory Context
- **UniformItem** (uniform_items): name, description, assignedMember (ManyToOne LAZY), ownershipStatus, lifecycleStatus, band (ManyToOne LAZY, not null), orderNumber. Methods: `assignTo()`, `unassign()`, `dispose()` (throws if assigned), `retireFromStock()`.
- **InstrumentItem** (instrument_items): name, brand, serialNumber, description, assignedMember, ownershipStatus, lifecycleStatus, band (not null), orderNumber. Same lifecycle methods as UniformItem.
- **AwardItem** (award_items): name, description, assignedMember, band (not null), dateAwarded, orderNumber.
- **InventoryOrder** (inventory_orders): requester (Member, EAGER), orderNumber, orderType, status, createdAt, updatedAt, notes, attributesJson. Lifecycle: SUBMITTED -> PENDING_APPROVAL -> IN_PRODUCTION -> SHIPPED -> DELIVERED, or CANCELLED.
- **AssetAssignmentHistory** (asset_assignment_history): uniformItem OR instrumentItem (one is null), member (EAGER), assignedBy (AppUser), assignedAt, returnedAt, active, conditionAtAssign, conditionAtReturn, notes. Active assignment has returnedAt=null.
- **OwnershipStatus**: OWNED, BORROWED, MISSING
- **ItemLifecycleStatus**: AVAILABLE, RETIRED_FROM_STOCK, DISPOSED
- **OrderStatus**: SUBMITTED, PENDING_APPROVAL, IN_PRODUCTION, SHIPPED, DELIVERED, CANCELLED
- **InventoryOrderType**: UNIFORM, INSTRUMENT
- **Attribute definitions**: UniformAttributeDef, InstrumentAttributeDef, OrderAttributeDef, AwardAttributeDef — band-specific custom attributes for inventory items. Each has corresponding AttributeValue entity.

#### User Context
- **AppUser** (app_users): username (unique), email (unique), passwordHash, active, emailVerified, externalId (Keycloak subject), firstName, lastName, createdAt, lastLoginAt. Has `teamRoles` (OneToMany UserTeamRole, orphanRemoval).
- **UserTeamRole** (user_team_roles): user + team (Band) + role + invitationToken + invitationAccepted. Unique constraint: user+team.
- **TeamRole**: ADMIN, MEMBER

#### Dashboard Context
- **SupersetDashboard** (superset_dashboards): supersetId (Integer, unique), supersetUuid (String, unique), embeddedUuid, title, slug, description, icon, position, active, embedded, firstSyncedAt, lastSyncedAt.
- **DashboardBandAssignment** (dashboard_band_assignments): dashboard + band + autoAssignNew + assignedAt + assignedBy. Unique constraint: dashboard+band.

### Repository Interfaces (Domain)

All repository interfaces are in `domain/` packages. They define ALL methods — Spring Data interfaces in `adapter/out/persistence/` extend only `JpaRepository` with query method signatures.

| Repository | Key Methods |
|---|---|
| BandRepository | save, saveAndFlush, findById, findByName, findBySlug, existsBySlug, findAll, delete, count |
| MemberRepository | save, saveAndFlush, findById, findAllActive, findAllActiveByBandId, findByInstrument, existsById, delete |
| InstrumentRepository | save, findById, findAll, findAllOrderBySortPriority, findByName |
| GroupRepository | save, findById, findAllOrderByName, findAllWithMembers, delete |
| RehearsalRepository | save, findById, findByDateBetween, findByDateBetweenAndBandId, findAllOrderByDateDesc, findAllOrderByDateDescByBandId, delete |
| EventRepository | save, findById, findByDateBetween, findByDateBetweenAndBandId, findAllOrderByDateDesc, findAllOrderByDateDescByBandId, delete |
| InventoryRepository | saveUniformItem, findAllUniformItems, findAllUniformItemsByBandId, findUniformItemsByMember, findUniformItemById, deleteUniformItem, saveInstrumentItem, findAllInstrumentItems, findAllInstrumentItemsByBandId, findInstrumentItemsByMember, findInstrumentItemById, deleteInstrumentItem, saveOrder, findAllOrders, findAllOrdersByBandId, findOrdersByMember, findOrdersByStatus, findOrderById, saveAssignment, deleteAssignment, findHistoryByUniformItem, findHistoryByInstrumentItem, findHistoryByMember, findActiveAssignments |
| AppUserRepository | save, findById, findByUsername, findByEmail, existsByUsername, existsByEmail, findByExternalId |
| UserTeamRoleRepository | (Spring Data) findByUserId, etc. |
| SupersetDashboardRepository | (Spring Data) findBySupersetId, findByActiveTrueOrderByPositionAsc, existsBySupersetId, findActiveByBandId |
| DashboardBandAssignmentRepository | (Spring Data) findByDashboardIdAndBandId, findByBandId, findByDashboardId, existsByDashboardIdAndBandId, findAssignmentsForDashboard, deleteByDashboardIdAndBandId, existsBySupersetIdAndBandId |

## API Endpoints

### REST Controllers

#### /api/members — MemberController
| Method | Path | Description |
|---|---|---|
| GET | /api/members | List all members |
| GET | /api/members/{id} | Get member by ID |
| POST | /api/members | Create member |
| PUT | /api/members/{id} | Update member |
| POST | /api/members/{id}/instruments | Assign instrument |
| PUT | /api/members/{id}/tag | Tag member |
| DELETE | /api/members/{id} | Delete member |

#### /api/instruments — InstrumentController
| Method | Path | Description |
|---|---|---|
| GET | /api/instruments | List instruments |
| POST | /api/instruments | Create instrument |
| PUT | /api/instruments/{id} | Update instrument |
| PUT | /api/instruments/{id}/priority | Update sort priority |
| DELETE | /api/instruments/{id} | Delete instrument |

#### /api/tags — TagController
| Method | Path | Description |
|---|---|---|
| GET | /api/tags | List tags |
| POST | /api/tags | Create tag |
| PUT | /api/tags/{id} | Update tag |
| PUT | /api/tags/{id}/priority | Update priority |
| DELETE | /api/tags/{id} | Delete tag |

#### /api/groups — GroupController
| Method | Path | Description |
|---|---|---|
| POST | /api/groups | Create group |
| POST | /api/groups/{groupId}/members/{memberId} | Add member to group |
| DELETE | /api/groups/{groupId}/members/{memberId} | Remove member from group |
| DELETE | /api/groups/{id} | Delete group |

**Dynamic groups**: `POST /api/groups/{id}/members/{memberId}` and `DELETE /api/groups/{id}/members/{memberId}` return **409 Conflict** when the group is dynamic (auto-managed). The UI hides these buttons for dynamic groups, but the API is the authoritative gate. To add/remove a member from a dynamic group, change the member's attribute value via `POST /api/bands/{bandId}/attribute-defs/{attrId}/members/{memberId}`.

#### /api/rehearsals — RehearsalController
| Method | Path | Description |
|---|---|---|
| GET | /api/rehearsals | List rehearsals |
| GET | /api/rehearsals/{id} | Get rehearsal |
| POST | /api/rehearsals | Create rehearsal |
| PUT | /api/rehearsals/{id} | Update rehearsal |
| POST | /api/rehearsals/{id}/attendance | Record attendance |
| DELETE | /api/rehearsals/{id} | Delete rehearsal |

#### /api/events — EventController
| Method | Path | Description |
|---|---|---|
| GET | /api/events | List events |
| GET | /api/events/{id} | Get event |
| POST | /api/events | Create event |
| PUT | /api/events/{id} | Update event |
| POST | /api/events/{id}/invite | Invite member |
| POST | /api/events/{id}/invite-group | Invite group |
| POST | /api/events/{id}/response | Record response |
| POST | /api/events/{id}/payment | Record payment |
| POST | /api/events/{eventId}/payment/{memberId}/paid | Mark payment paid |
| POST | /api/events/{id}/payment-status | Update payment status |
| DELETE | /api/events/{id} | Delete event |

#### /api/inventory — InventoryController
| Method | Path | Description |
|---|---|---|
| GET | /api/inventory/orders | List orders |
| GET | /api/inventory/uniforms | List uniform items |
| GET | /api/inventory/instruments | List instrument items |
| GET | /api/inventory/awards | List award items |
| GET | /api/inventory/uniforms/{id}/history | Uniform assignment history |
| GET | /api/inventory/instruments/{id}/history | Instrument assignment history |
| GET | /api/inventory/members/{memberId}/assignments | Member assignments |
| GET | /api/inventory/assignments/active | Active assignments |
| POST | /api/inventory/orders/uniform | Create uniform order |
| POST | /api/inventory/orders/instrument | Create instrument order |
| POST | /api/inventory/orders/{id}/approve | Approve order |
| POST | /api/inventory/orders/{id}/produce | Move to production |
| POST | /api/inventory/orders/{id}/ship | Mark shipped |
| POST | /api/inventory/orders/{id}/deliver | Mark delivered |
| POST | /api/inventory/orders/{id}/cancel | Cancel order |
| POST | /api/inventory/uniforms | Create uniform item |
| POST | /api/inventory/instruments | Create instrument item |
| POST | /api/inventory/awards | Create award item |
| POST | /api/inventory/uniforms/{id}/assign | Assign uniform to member |
| POST | /api/inventory/instruments/{id}/assign | Assign instrument to member |
| POST | /api/inventory/uniforms/{id}/return | Return uniform |
| POST | /api/inventory/instruments/{id}/return | Return instrument |
| POST | /api/inventory/uniforms/{id}/retire | Retire uniform |
| POST | /api/inventory/instruments/{id}/retire | Retire instrument |
| POST | /api/inventory/uniforms/{id}/dispose | Dispose uniform |
| POST | /api/inventory/instruments/{id}/dispose | Dispose instrument |
| POST | /api/inventory/awards/{id}/assign | Assign award |
| POST | /api/inventory/awards/{id}/return | Return award |
| POST | /api/inventory/awards/{id}/dispose | Dispose award |
| PUT | /api/inventory/awards/{id}/attributes | Update award attributes |
| DELETE | /api/inventory/uniforms/{id} | Delete uniform |
| DELETE | /api/inventory/instruments/{id} | Delete instrument |

#### /api/dashboards — SupersetGuestTokenController
| Method | Path | Description |
|---|---|---|
| GET | /api/dashboards/{id}/guest-token | Generate guest token for embedded dashboard |

#### /api/reports — ReportController
| Method | Path | Description |
|---|---|---|
| GET | /api/reports/monthly | Monthly report |

#### /api/auth — AuthController
| Method | Path | Description |
|---|---|---|
| POST | /api/auth/register-team | Register new team |
| POST | /api/auth/teams | Create team |
| GET | /api/auth/check-username | Check username availability |
| GET | /api/auth/check-email | Check email availability |
| GET | /api/auth/check-slug | Check slug availability |
| GET | /api/auth/me | Get current user info |
| POST | /api/auth/switch-team/{teamId} | Switch active team |
| GET | /api/auth/debug-dns | Debug DNS resolution |
| GET | /api/auth/build-info | Build info |

#### /api/teams/{teamId} — TeamInviteController
| Method | Path | Description |
|---|---|---|
| POST | /api/teams/{teamId}/admin/invite | Invite member (ADMIN only) |
| POST | /api/teams/{teamId}/accept-invitation/{token} | Accept invitation |
| GET | /api/teams/{teamId}/members | List team members |

#### /api/bands/{bandId}/attribute-defs — MemberAttributeApiController
| Method | Path | Description |
|---|---|---|
| GET | /api/bands/{bandId}/attribute-defs | List attribute definitions |
| POST | /api/bands/{bandId}/attribute-defs | Create attribute definition |
| PUT | /api/bands/{bandId}/attribute-defs/{id} | Update attribute definition |
| DELETE | /api/bands/{bandId}/attribute-defs/{id} | Delete attribute definition |
| POST | /api/bands/{bandId}/attribute-defs/{attrId}/members/{memberId} | Set attribute value for member |

### Page Controllers (Thymeleaf)

| Controller | Base Path | Key Endpoints |
|---|---|---|
| PageController | / | GET / (dashboard home), GET /register |
| LoginController | /login | GET/POST /login |
| DashboardController | /dashboards | GET /, GET /{id} |
| DashboardAdminController | /admin/dashboards | GET /, POST /sync, POST /save-assignments |
| MemberPageController | /members | GET /, GET /list, GET /new, GET /{id}/edit, GET /{id}/detail |
| GroupPageController | /groups | GET /, GET /list, GET /new, GET /{id}, POST /, POST /{groupId}/members/{memberId}, POST /{groupId}/members/{memberId}/remove, POST /{id}/delete |
| TagPageController | /tags | GET /, GET /list, GET /new, GET /{id}/edit |
| InstrumentPageController | /instruments | GET /, GET /list, GET /new, GET /{id}/edit |
| EventPageController | /events | GET /, GET /list, GET /new, GET /{id}, GET /{id}/edit |
| RehearsalPageController | /rehearsals | GET /, GET /list, GET /new, GET /{id}, GET /{id}/edit |
| ReportPageController | /reports | GET /, GET /list, GET /generate |
| InventoryPageController | /inventory | GET /, GET /orders, GET /uniforms/fragment, GET /instruments/fragment, GET /awards/fragment, GET /orders/{id}/history |
| OrderPageController | /orders | GET / |
| MemberAttributeController | /band/attributes | GET /list, GET /new, GET /{id}/edit, POST /, PUT /{id}, DELETE /{id} |
| InventoryAttributePageController | /band/inventory-attributes | GET /, GET /new, GET /{id}/edit, POST /, PUT /{id}, DELETE /{id}, POST /{defId}/uniforms/{itemId}, POST /{defId}/instruments/{itemId}, POST /{defId}/orders/{orderId} |

## Page Controllers & Templates

### Layout
- `fragments/layout.html` defines: `head(title)`, `nav`, `toast-container`, `footer`, `footer-scripts`
- Nav uses `hx-target="#content" hx-swap="innerHTML transition:true"` for SPA-like navigation
- JWT token read from cookie `JWT=`, attached to all HTMX requests via `htmx:configRequest` event
- Logout form: POST `/api/auth/logout`

### HTMX Navigation Pattern
- All page-level templates use `hx-target="#content" hx-swap="innerHTML transition:true"`
- Sub-content areas use their own targets (e.g., `#members-content`, `#rehearsals-content`)
- Forms mostly use JavaScript `fetchWithToast()` with JSON body, NOT native HTML form submission
- Exception: `groups/form.html` uses native `hx-post`

### Template Structure
- **Full pages** (extend layout): list pages, detail pages, form pages for create/edit
- **Fragments** (standalone, no layout): form.html files for create/edit (loaded into content areas)
- **HTMX fragment returns**: Controllers return `"template :: fragment"` (e.g., `"members/detail :: member-detail-content"`) for HTMX requests, NOT full pages

### Key HTMX Targets
| Area | Target ID | Used By |
|---|---|---|
| Main content | #content | Page-level navigation |
| Members | #members-content | Member list, form, detail |
| Instruments | #instruments-content | Instrument list, form |
| Tags | #tags-content | Tag list, form |
| Groups | #groups-content | Group list, form, detail |
| Rehearsals | #rehearsals-content | Rehearsal list, form, detail, edit |
| Events | #events-content | Event list, form, detail, edit |
| Reports | #reports-content | Report list, generated report |
| Inventory tabs | (JS-driven) | Orders, uniforms, instruments, awards |

### Two Attribute Systems (COEXIST)
1. **Legacy** (`/band/attributes`): `attribute-new.html`, `attribute-edit.html`, `attribute-defs.html`, `attribute-form.html`
2. **Newer** (`/band/inventory-attributes`): `inventory-attributes.html`, `inventory-attribute-form.html` — supports 5 types: UNIFORM, INSTRUMENT, ORDER, AWARD, MEMBER

## Security

### Production (SecurityConfig, profile != test)
- OIDC Authorization Code Flow with Keycloak
- CSRF disabled
- Session-based auth (IF_REQUIRED)
- Custom `OAuth2AuthorizationRequestResolver` overrides redirect_uri with public BASE_URL
- Public endpoints: `/api/auth/register-team`, `/api/auth/check-*`, `/login`, `/register`, static resources, Swagger, `/api/auth/build-info`, `/api/auth/debug-dns`
- ADMIN role required for `/api/teams/*/admin/**` and `/admin/**`
- Success handler: if user has no active team, redirect to `/register`; otherwise `/`
- Logout: POST `/api/auth/logout` -> Keycloak end_session_endpoint

### Test (TestSecurityConfig, profile = test)
- Form-based login (bypasses Keycloak)
- In-memory user: `admin`/`admin` with ROLE_ADMIN
- Custom success handler creates mock `WindbandOidcUser` with userId=1, activeTeamId=1, slug="test-band", role=ADMIN
- Stored in HTTP session for Selenium persistence

### WindbandOidcUser
Wraps standard `OidcUser` + adds: `getUserId()`, `getWbUsername()`, `getWbEmail()`, `isWbActive()`, `getActiveTeamId()`, `getActiveTeamSlug()`, `getActiveTeamRole()`, `getTeamIds()`, `isAdmin()`, `belongsToTeam()`.

### KeycloakOAuth2UserService
On login: (1) find by externalId, (2) find by email, (3) find by username, (4) auto-provision. Loads team roles from DB. Builds `WindbandOidcUser` with active team info.

### TeamModelAdvice (@ControllerAdvice)
Adds global model attributes: `userTeams`, `activeTeamId`, `activeTeamSlug`, `activeTeamRole`. Respects session override for team switching (session attribute `activeTeamId`).

## Superset Integration

### Flow
1. Admin opens `/admin/dashboards` -> triggers `POST /admin/dashboards/sync`
2. `DashboardSyncService.syncFromSuperset()` fetches all dashboards from Superset API
3. For each: insert if new, update if changed, deactivate if unpublished
4. Registers embedded dashboard in Superset (gets `embeddedUuid`)
5. User opens `/dashboards/{id}` -> frontend calls `GET /api/dashboards/{id}/guest-token`
6. `SupersetGuestTokenController` generates guest token with RLS clause `band_id = {bandId}`
7. Frontend embeds dashboard using Superset Embedded SDK with the guest token

### SupersetClient
- Login: POST `/api/v1/security/login` -> access token
- List dashboards: GET `/api/v1/dashboard/`
- Guest token: POST `/api/v1/security/guest_token/` with RLS `band_id = {bandId}`
- Register embedded: POST `/api/v1/dashboard/{id}/embedded` with allowed_domains
- CSRF token: GET `/api/v1/security/csrf_token/` (available but not used in current flow)

### Key IDs
- `supersetId` (Integer) — used for guest token API calls
- `supersetUuid` (String) — Superset's internal UUID
- `embeddedUuid` (String) — registered via embedded dashboard API, used by frontend SDK

## Testing

### Test Profiles
- **test**: H2 in-memory, Flyway disabled, `ddl-auto: create-drop`, `defer-datasource-initialization: true`
- Seed data in `data.sql`: band id=1 "Test Band", user id=1 "admin", ADMIN role

### Base Classes
- **BaseIntegrationTest**: `@SpringBootTest`, `@Testcontainers`, `@ActiveProfiles("test")`, `@ContextConfiguration(initializers = TestcontainersInitializer.class)`. Shared PostgreSQL container.
- **UiTestBase**: `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ActiveProfiles("test")`. Headless Chrome via Selenium. `loginAndNavigateTo(path)` helper: logs in as admin/admin, navigates to path, waits for `#content`.

### Test Types
1. **Architecture tests** (`ArchitectureTest.java`): ArchUnit rules enforcing layer dependencies
2. **Command service tests**: Unit tests for command services (mock repos)
3. **Query service tests**: Unit tests for query services
4. **UI tests** (Selenium): Extend `UiTestBase`, test full user flows
5. **Integration tests**: Extend `BaseIntegrationTest`, test with real PostgreSQL

### Running Tests
```bash
mvn test                    # All tests
mvn test -pl . -Dtest=...   # Specific test
```

## Key Patterns & Conventions

### Repository Pattern
- Domain repository interface: ALL methods defined in `domain/` package
- Spring Data interface: extends ONLY `JpaRepository`, no custom methods
- Adapter: `@Component` implementing domain repo, delegates to Spring Data

### Band Context Flow
1. Controller gets `WindbandOidcUser` from `@AuthenticationPrincipal OidcUser`
2. Calls `BandQueryService` to resolve Band from `activeTeamId`
3. Passes Band to command service as parameter
4. Command service does NOT read Band directly (enforced by ArchUnit)

### HTMX Fragment Returns
- For HTMX requests, return `"template :: fragment"` (e.g., `"members/detail :: member-detail-content"`)
- For full page loads, return `"template"` (full page with layout)
- NEVER return full page for HTMX requests (causes duplicate head/nav/footer)

### Form Submission
- Most forms use JavaScript `fetchWithToast(url, {method, body: JSON.stringify(data)})`
- `fetchWithToast` shows success/error toasts automatically
- Groups form uses native `hx-post` (exception)

### Duplicate Event Listener Guards
All inline `<script>` blocks use `window._*FormHandlerAttached` boolean flags to prevent double-binding when forms are reloaded via HTMX.

### OrphanRemoval Pattern
When updating collections with `orphanRemoval: true`:
- `Member.changeInstrument()`: clears all, adds new (guards with identity check to avoid duplicate key)
- `Member.addInstrument()`: checks identity (`==`) before adding
- Always guard with identity check before clear+add

### Attribute System
Custom attributes use EAV pattern:
- `*AttributeDef`: defines the attribute (name, type, required, displayOrder, options)
- `*AttributeValue`: stores the value (attributeDef + item + value)
- Types: "BOOLEAN", "TEXT" (legacy); newer system supports SELECT/MULTI_SELECT via JSON options

## Common Pitfalls

### Selenium + HTMX
1. **sendKeys mangles dates**: Use JavaScript for date input read+write
2. **htmx+JS submit conflict**: Don't mix htmx form submit with JS submit
3. **HX-Redirect + hx-swap="none"**: Doesn't redirect in Selenium — use `driver.get()` after action
4. **orphanRemoval + clear() + add()**: Causes duplicate key — guard with identity check
5. **Assert ALL fields after edit**: Partial assertions miss regressions
6. **Cleanup order**: Delete AssetAssignmentHistory before inventory items
7. **Debug**: Use `System.out.println` + hidden div for element state inspection

### patch tool + read_file
NEVER use `read_file`'s content directly as `old_string` in `patch()`. The display format prepends `N|` to each line. Extract from actual file via grep/terminal.

### General
- Always run tests before commit/push
- Changes must be visible in UI immediately (user tests in incognito)
- `Member.changeInstrument()`: identity check prevents duplicate key on same instrument
- `InventoryItem.dispose()`: throws if assigned to member — unassign first
- `BandEvent.inviteMember()`: throws if already invited
- `Rehearsal.recordAttendance()`: throws if already recorded; use `updateAttendance()` for changes

## Database Migrations

21 Flyway migrations (V1-V17, V21):
- V1: Initial schema
- V2: Add band
- V3: Add member attributes
- V4: Update members (remove role OSP, add resigned_date)
- V5: Extend attribute types
- V6: Inventory attributes
- V7: Add order attributes
- V8: Add display_in_list
- V9: Remove order name/desc not null
- V10: Add conditional attribute columns
- V11: Multi-tenant users
- V12: Add payment_type to events
- V13: Add instrument sort_priority
- V14: Enhance asset_assignment_history
- V15: Superset dashboard management
- V16: Add superset UUID
- V17: Add embedded UUID
- V21: Add `dynamic_source_id` to `member_groups` (FK → `member_attribute_defs`, ON DELETE CASCADE, UNIQUE 1:1). Backfill handled by `DynamicGroupBackfillRunner` on app startup (skipped in `test` profile).
  - **Name-collision handling** (2026-07-04 incident): when a BOOLEAN attribute's name collides with an existing manual group in the same band, the dynamic group is created with a numeric suffix (`"Gość"` → `"Gość (2)"` → `"Gość (3)"`). This keeps the manual group untouched while the dynamic group still gets created. See `GroupCommandService.resolveNameCollision` and `AI_HARNESS.md` § 1.7.
  - **Backfill runner** has NO outer `@Transactional` — per-attribute isolation comes from `MemberAttributeCommandService.ensureDynamicGroupExists`'s class-level transaction, contained by the runner's per-iteration `try/catch`. Wrapping the loop in one transaction (the original 2026-07-04 bug) cascades one attribute's failure into a startup crash.

## Build & Deploy

### Build
```bash
mvn clean package
```

### Docker
```bash
docker build -t windband-manager-app .
docker compose up
```

### Docker Compose Services
- **keycloak-db**: postgres:16-alpine (port 5433)
- **keycloak**: quay.io/keycloak/keycloak:25.0 (port 8180)
- **db**: postgres:16-alpine (port 5432)
- **app**: windband-manager-app (port 8080)

### Environment Variables
See `.env.example`. Key vars: DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASS, KEYCLOAK_CLIENT_ID, KEYCLOAK_CLIENT_SECRET, KEYCLOAK_AUTH_URL, KEYCLOAK_INTERNAL_URL, KEYCLOAK_PUBLIC_URL, KEYCLOAK_REALM, BASE_URL, SERVER_PORT, SUPERSET_BASE_URL, SUPERSET_PUBLIC_URL, SUPERSET_USERNAME, SUPERSET_PASSWORD, MAIL_HOST, MAIL_PORT, MAIL_USER, MAIL_PASS.

### Local Development
Use `application-local.yml` profile: localhost Keycloak (8180), localhost PostgreSQL (5432), Mailpit (1025), show-sql enabled.
