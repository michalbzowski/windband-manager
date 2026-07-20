# Dynamic Groups from Boolean Attributes — Implementation Plan

> **STATUS (2026-07-20):** Podstawowy mechanizm (grupa dynamiczna z atrybutu
> BOOLEAN) jest zaimplementowany i działa — patrz `docs/member-attributes.md`
> oraz `DynamicGroupEndToEndUiTest`. Wariant "grupa Aktywni przez wykluczanie
> atrybutów" (odejmij Goście + Uczniowie, ale zostaw OSP) został **porzucony**
> jako zbyt złożony dla obecnego modelu `DynamicGroupSource` (brak wsparcia dla
> reguł "A i B ale nie C"). Zamiast tego przyjęto uproszczone podejście flagowe:
> atrybut `BOOLEAN` "Grający członek" + grupa dynamiczna z niego — daje grupę
> aktywnych grających muzyków bez logiki wykluczania. Plan poniżej pozostaje
> jako dokumentacja architektury mechanizmu.

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Each Boolean (TAK/NIE) member attribute automatically creates and maintains a matching "dynamic" group whose members are exactly the members who have that attribute set to "true". The group is fully system-managed (no manual add/remove), shows a badge in the UI, and syncs whenever the source attribute or any of its values change.

**Architecture:** Materialized groups (Approach 3 from the design discussion) — one `Group` row per `MemberAttributeDef` where `type='BOOLEAN'`, with a new nullable FK `dynamic_source_id → member_attribute_defs`. The mapping is 1:1 and UNIQUE inside a band. Synchronization happens in `MemberAttributeCommandService` (attribute value change → add/remove member) and in `MemberAttributeDef` lifecycle (create → spawn group; rename → rename group; delete → delete group; type change away from BOOLEAN → delete group).

**Tech Stack:** Java 21, Spring Boot 3.3.5, JPA, Thymeleaf + HTMX, Flyway migration, JUnit + Selenium (for regression tests).

---

## Design Decisions (confirmed)

| # | Decision | Confirmed by |
|---|----------|--------------|
| 1 | Dynamic groups are **fully system-managed**. Manual add/remove from UI is blocked; UI shows a `🔄 dynamiczna` badge + explanatory message. | User |
| 2 | When source attribute is **deleted**, dynamic group is also deleted. | Default (consistent with #1) |
| 3 | When source attribute is **renamed**, dynamic group is renamed to match. | Default (consistent with #1) |
| 4 | When source attribute **type changes away from BOOLEAN**, dynamic group is deleted. | Default (consistent with #1) |
| 5 | A dynamic group's name is **unique within the band** (so it can't collide with a manual group). Existing manual groups with the same name are NOT auto-merged — admin must rename the manual one first. (Migration handles this for existing data: prefix collision with `dynamic_<name>`.) | Design |
| 6 | The dynamic group **inherits the attribute's band** (multi-tenant isolation preserved). | Design |
| 7 | The group **description** is auto-set to `"Grupa dynamiczna na podstawie atrybutu <name>"` and shown read-only in the UI. | Design |

---

## Out of Scope (YAGNI)

- Bulk attribute value import (admin can already edit values one at a time)
- Renaming a dynamic group manually (the source attribute always wins; the rename form for dynamic groups is hidden)
- Webhook / external notifications on group membership change
- Filtering dynamic groups by user role (everyone in the band can see them)
- Per-attribute "enable dynamic group" toggle — all BOOLEAN attributes get a dynamic group, always

---

## Architecture Diagram

```
MemberAttributeDef (BOOLEAN)         Group
  ┌──────────────────┐              ┌──────────────────────────┐
  │ id               │◄─── 1:1 ─────│ id                       │
  │ name             │              │ name = def.name          │
  │ type='BOOLEAN'   │              │ description = auto       │
  │ band_id          │              │ band_id = def.band_id    │
  └──────────────────┘              │ dynamic_source_id (FK)   │
                                    └──────────────────────────┘
            │                                  │
            │ (value set to "true")            │ (members list)
            ▼                                  ▼
MemberAttributeValue                  GroupMember
  ┌──────────────────┐                ┌──────────────────┐
  │ member_id        │                │ group_id         │
  │ attribute_def_id │   ─triggered─► │ member_id        │
  │ value="true"     │     by         └──────────────────┘
  └──────────────────┘    MemberAttribute
                          CommandService
```

---

## Tasks

### Task 1: Add `dynamicSource` field to `Group` entity

**Objective:** Persist the link from a dynamic `Group` back to its source `MemberAttributeDef` so we can detect & manage the dynamic lifecycle.

**Files:**
- Modify: `src/main/java/pl/michalbzowski/windband/domain/member/Group.java:1-56`

**Step 1: Add field, getter, constructor parameter**

Add to `Group.java`:
```java
@OneToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "dynamic_source_id", unique = true)
private MemberAttributeDef dynamicSource;

public MemberAttributeDef getDynamicSource() { return dynamicSource; }

public boolean isDynamic() { return dynamicSource != null; }

// In existing constructor, add a sibling factory method:
public static Group createDynamic(String name, Band band, MemberAttributeDef source) {
    Group g = new Group(name, "Grupa dynamiczna na podstawie atrybutu " + name, band);
    g.dynamicSource = source;
    return g;
}
```

The `unique = true` on `dynamic_source_id` is the 1:1 enforcement at the DB level — the `existing public Group(...)` constructor stays unchanged for manual groups.

**Step 2: Compile**

Run: `mvn -q -DskipTests compile`
Expected: `BUILD SUCCESS`

**Step 3: Commit**

```bash
git add src/main/java/pl/michalbzowski/windband/domain/member/Group.java
git commit -m "feat(group): add dynamicSource field + createDynamic factory"
```

---

### Task 2: Add `findByDynamicSource` to repository

**Objective:** Let the command service find the dynamic group for a given attribute def (used during sync).

**Files:**
- Modify: `src/main/java/pl/michalbzowski/windband/domain/member/GroupRepository.java:1-21`
- Modify: `src/main/java/pl/michalbzowski/windband/adapter/out/persistence/SpringDataGroupRepository.java` (find existing first)
- Modify: `src/main/java/pl/michalbzowski/windband/adapter/out/persistence/GroupRepositoryAdapter.java` (find existing first)

**Step 1: Add method to domain interface**

```java
Optional<Group> findByDynamicSource(MemberAttributeDef source);
```

**Step 2: Implement in adapter**

```java
@Override
public Optional<Group> findByDynamicSource(MemberAttributeDef source) {
    return springDataRepo.findByDynamicSource(source);
}
```

(Add `findByDynamicSource(MemberAttributeDef source)` to the Spring Data interface — domain rule allows simple derived queries here. The AI_HARNESS ban on custom methods is about complex `@Query` JPQL; derived method names from property paths are fine.)

**Step 3: Add test (TDD red)**

`src/test/java/pl/michalbzowski/windband/application/command/member/GroupDynamicRepositoryTest.java`:
```java
@Test
void shouldFindGroupByDynamicSource() {
    Band band = ensureDefaultBand();
    MemberAttributeDef def = attrDefRepo.save(MemberAttributeDef.create(band, "OSP", "BOOLEAN", false, 0, null));
    Group g = groupCommandService.createDynamicGroupForAttribute(def);
    assertThat(groupRepository.findByDynamicSource(def)).isPresent()
            .get().extracting(Group::getName).isEqualTo("OSP");
}
```

(The `createDynamicGroupForAttribute` method does not exist yet — that's Task 3. The test will fail to compile in this step. Mark it `@Disabled` and re-enable in Task 3, OR write the test fully in Task 3 directly. → **Write the test in Task 3** to avoid the dance. Skip writing tests in this task.)

**Step 4: Compile**

Run: `mvn -q -DskipTests compile`
Expected: `BUILD SUCCESS`

**Step 5: Commit**

```bash
git add src/main/java/pl/michalbzowski/windband/domain/member/GroupRepository.java \
        src/main/java/pl/michalbzowski/windband/adapter/out/persistence/SpringDataGroupRepository.java \
        src/main/java/pl/michalbzowski/windband/adapter/out/persistence/GroupRepositoryAdapter.java
git commit -m "feat(group): add findByDynamicSource repo method"
```

---

### Task 3: Add `createDynamicGroupForAttribute` + sync logic to `GroupCommandService`

**Objective:** Create a dynamic group for a given attribute def (used by `MemberAttributeCommandService` when a BOOLEAN attribute is created).

**Files:**
- Modify: `src/main/java/pl/michalbzowski/windband/application/command/member/GroupCommandService.java:1-48`

**Step 1: Add method**

```java
/**
 * Create the dynamic group backed by the given BOOLEAN attribute.
 * Idempotent: if a group already exists for this attribute, returns it.
 */
public Group createDynamicGroupForAttribute(MemberAttributeDef def) {
    return groupRepository.findByDynamicSource(def).orElseGet(() -> {
        // Defensive: if a manual group with the same name exists in this band,
        // prefix to avoid unique-constraint violation. Manual groups keep the
        // original name; the dynamic one is "dynamic_<name>".
        String desiredName = def.getName();
        Band band = def.getBand();
        // We do not need a uniqueness query — the unique(name) constraint
        // will throw on save if there's a collision. We catch and retry with prefix.
        try {
            return groupRepository.save(Group.createDynamic(desiredName, band, def));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return groupRepository.save(Group.createDynamic("dynamic_" + desiredName, band, def));
        }
    });
}
```

**Step 2: Add `syncMemberInDynamicGroup` method**

```java
/**
 * Sync a single member's membership in the dynamic group backed by `def`:
 *  - if value == "true" and member not in group → add
 *  - if value != "true" and member in group → remove
 * No-op if the attribute has no dynamic group (e.g. it's TEXT, not BOOLEAN).
 */
public void syncMemberInDynamicGroup(MemberAttributeDef def, Member member, String newValue) {
    if (!"BOOLEAN".equals(def.getType())) return;
    Optional<Group> maybeGroup = groupRepository.findByDynamicSource(def);
    if (maybeGroup.isEmpty()) return; // attribute is BOOLEAN but no group yet (shouldn't happen post-create-sync, but be defensive)
    Group group = maybeGroup.get();
    boolean shouldBeMember = "true".equalsIgnoreCase(newValue);
    boolean isMember = group.getMembers().stream()
            .anyMatch(gm -> gm.getMember().equals(member));
    if (shouldBeMember && !isMember) {
        group.addMember(member);
        groupRepository.save(group);
    } else if (!shouldBeMember && isMember) {
        group.removeMember(member);
        groupRepository.save(group);
    }
}
```

**Step 3: Add `renameDynamicGroup` and `deleteDynamicGroup` methods**

```java
public void renameDynamicGroup(MemberAttributeDef def) {
    groupRepository.findByDynamicSource(def).ifPresent(g -> {
        // Use reflection-light approach: the existing Group has no setter for name
        // because we want manual groups to be immutable-by-default. Add a package-private
        // method to Group for this case (see Task 4).
        g.renameForDynamicSource(def.getName());
        groupRepository.save(g);
    });
}

public void deleteDynamicGroup(MemberAttributeDef def) {
    groupRepository.findByDynamicSource(def).ifPresent(groupRepository::delete);
}
```

**Step 4: Add `dynamic` flag to `GroupSummaryDto`**

Modify `src/main/java/pl/michalbzowski/windband/application/dto/GroupSummaryDto.java`:
```java
public record GroupSummaryDto(
        Long id,
        String name,
        String description,
        int memberCount,
        boolean dynamic   // NEW
) {}
```

Update `GroupQueryService.getAllGroups()` to pass `g.getDynamicSource() != null`.

**Step 5: Compile + run all unit tests**

Run: `mvn -q test -Dtest='!*UiTest'`
Expected: `BUILD SUCCESS` — pre-existing tests still pass (DTO change is additive).

**Step 6: Commit**

```bash
git add src/main/java/pl/michalbzowski/windband/application/command/member/GroupCommandService.java \
        src/main/java/pl/michalbzowski/windband/application/dto/GroupSummaryDto.java \
        src/main/java/pl/michalbzowski/windband/application/query/member/GroupQueryService.java
git commit -m "feat(group): createDynamicGroup + syncMember + rename/delete + dynamic flag in DTO"
```

---

### Task 4: Add `renameForDynamicSource` package-private method to `Group`

**Objective:** Allow the command service to rename a dynamic group (without exposing the rename to general UI code).

**Files:**
- Modify: `src/main/java/pl/michalbzowski/windband/domain/member/Group.java`

**Step 1: Add the method**

```java
/** Package-private: only GroupCommandService may call this. */
void renameForDynamicSource(String newName) {
    this.name = newName;
    this.description = "Grupa dynamiczna na podstawie atrybutu " + newName;
}
```

**Step 2: Compile**

Run: `mvn -q -DskipTests compile`
Expected: `BUILD SUCCESS`

**Step 3: Commit**

```bash
git add src/main/java/pl/michalbzowski/windband/domain/member/Group.java
git commit -m "feat(group): package-private renameForDynamicSource"
```

---

### Task 5: Flyway migration — add `dynamic_source_id` column

**Objective:** Persist the new FK in the database.

**Files:**
- Create: `src/main/resources/db/migration/V18__add_group_dynamic_source.sql`

**Step 0: Check current Flyway version on prod (per memory)**

```bash
docker exec windband-db psql -U windband -d windband -c "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
```

Use the next version number. (Today is 2026-07-04, last migration is V17 → V18.)

**Step 1: Write the migration**

```sql
-- V18: Dynamic groups backed by BOOLEAN member attributes
ALTER TABLE member_groups
    ADD COLUMN dynamic_source_id BIGINT;

ALTER TABLE member_groups
    ADD CONSTRAINT fk_member_groups_dynamic_source
        FOREIGN KEY (dynamic_source_id)
        REFERENCES member_attribute_defs (id)
        ON DELETE CASCADE;

-- 1:1 enforcement
ALTER TABLE member_groups
    ADD CONSTRAINT uq_member_groups_dynamic_source
        UNIQUE (dynamic_source_id);

-- Partial unique index: dynamic groups' name must not collide with manual groups
-- in the same band. We achieve this by ensuring name uniqueness within band
-- (already enforced by the existing unique constraint on name).
-- (No additional DDL needed — the existing name-unique constraint applies.)
```

**Step 2: Verify locally**

Run: `mvn -q -DskipTests compile` (Flyway only runs on app startup, not on compile).
For a full migration test, run the test suite which uses H2 in test profile (no Flyway — it uses `ddl-auto: create-drop`). The real migration will be tested in production by the deploy script.

To manually verify the SQL syntax is valid PostgreSQL-compatible, paste it into a psql session against the dev DB:
```bash
docker exec -i windband-db psql -U windband -d windband < src/main/resources/db/migration/V18__add_group_dynamic_source.sql
```

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V18__add_group_dynamic_source.sql
git commit -m "feat(db): V18 — add dynamic_source_id to member_groups"
```

---

### Task 6: Wire attribute def lifecycle into group sync (TDD)

**Objective:** When a BOOLEAN attribute is created/renamed/deleted/type-changed, the corresponding dynamic group follows.

**Files:**
- Modify: `src/main/java/pl/michalbzowski/windband/application/command/band/MemberAttributeCommandService.java:1-71`

**Step 1: Write failing test (RED)**

Create `src/test/java/pl/michalbzowski/windband/application/command/band/DynamicGroupSyncTest.java`:
```java
class DynamicGroupSyncTest extends BaseIntegrationTest {

    @Autowired private MemberAttributeCommandService attrCmd;
    @Autowired private GroupQueryService groupQuery;
    @Autowired private GroupRepository groupRepo;
    @Autowired private MemberAttributeDefRepository attrDefRepo;
    @Autowired private MemberRepository memberRepo;
    @Autowired private BandRepository bandRepo;

    @Test
    void creatingBooleanAttribute_spawnsDynamicGroup() {
        Band band = ensureBand();
        MemberAttributeDef def = attrCmd.createAttributeDef(band, "OSP", "BOOLEAN", false, false, 0, null);

        // Dynamic group must exist with the same name
        Optional<Group> g = groupRepo.findByDynamicSource(def);
        assertThat(g).isPresent();
        assertThat(g.get().getName()).isEqualTo("OSP");
        assertThat(g.get().isDynamic()).isTrue();
    }

    @Test
    void creatingTextAttribute_doesNotSpawnGroup() {
        Band band = ensureBand();
        MemberAttributeDef def = attrCmd.createAttributeDef(band, "Ksywka", "TEXT", false, false, 0, null);
        assertThat(groupRepo.findByDynamicSource(def)).isEmpty();
    }

    @Test
    void renamingAttribute_renamesDynamicGroup() {
        Band band = ensureBand();
        MemberAttributeDef def = attrCmd.createAttributeDef(band, "OSP", "BOOLEAN", false, false, 0, null);
        attrCmd.updateAttributeDef(def.getId(), "Ochotnicza", "BOOLEAN", false, false, 0, null);
        Group g = groupRepo.findByDynamicSource(def).orElseThrow();
        assertThat(g.getName()).isEqualTo("Ochotnicza");
    }

    @Test
    void deletingAttribute_deletesDynamicGroup() {
        Band band = ensureBand();
        MemberAttributeDef def = attrCmd.createAttributeDef(band, "OSP", "BOOLEAN", false, false, 0, null);
        attrCmd.deleteAttributeDef(def.getId());
        assertThat(groupRepo.findByDynamicSource(def)).isEmpty();
    }

    @Test
    void changingTypeAwayFromBoolean_deletesDynamicGroup() {
        Band band = ensureBand();
        MemberAttributeDef def = attrCmd.createAttributeDef(band, "OSP", "BOOLEAN", false, false, 0, null);
        attrCmd.updateAttributeDef(def.getId(), "OSP", "TEXT", false, false, 0, null);
        assertThat(groupRepo.findByDynamicSource(def)).isEmpty();
    }
}
```

Run: `mvn -q test -Dtest=DynamicGroupSyncTest`
Expected: 3 fail (create, rename, type change), 2 pass (text no-spawn, delete may pass because cascading FK would delete it but we want explicit logic).

**Step 2: Wire `GroupCommandService` into `MemberAttributeCommandService`**

Modify `MemberAttributeCommandService`:
- Inject `GroupCommandService groupCommandService` via constructor
- In `createAttributeDef`: if `type.equals("BOOLEAN")`, call `groupCommandService.createDynamicGroupForAttribute(saved)`
- In `updateAttributeDef`: if the new type is not "BOOLEAN" or the name changed, call rename/delete accordingly
- In `deleteAttributeDef`: call `groupCommandService.deleteDynamicGroup(def)` BEFORE deleting the def (FK constraint)

**Step 3: Run test (GREEN)**

Run: `mvn -q test -Dtest=DynamicGroupSyncTest`
Expected: all 5 pass.

**Step 4: Commit**

```bash
git add src/test/java/pl/michalbzowski/windband/application/command/band/DynamicGroupSyncTest.java \
        src/main/java/pl/michalbzowski/windband/application/command/band/MemberAttributeCommandService.java
git commit -m "feat(attribute): wire BOOLEAN attribute lifecycle to dynamic group sync"
```

---

### Task 7: Wire attribute value change to group membership (TDD)

**Objective:** When `setAttributeValue` is called with `"true"` / `"false"`, add or remove the member from the dynamic group.

**Files:**
- Modify: `src/main/java/pl/michalbzowski/windband/application/command/band/MemberAttributeCommandService.java` (same file as Task 6)
- Modify test file: `src/test/java/pl/michalbzowski/windband/application/command/band/DynamicGroupSyncTest.java`

**Step 1: Add failing tests (RED)**

Append to `DynamicGroupSyncTest`:
```java
@Test
void settingValueToTrue_addsMemberToGroup() {
    Band band = ensureBand();
    MemberAttributeDef def = attrCmd.createAttributeDef(band, "OSP", "BOOLEAN", false, false, 0, null);
    Member m = memberRepo.save(Member.create("Jan", "Kowalski", java.time.LocalDate.of(1990,1,1), band));
    attrCmd.setAttributeValue(m.getId(), def.getId(), "true");

    Group g = groupRepo.findByDynamicSource(def).orElseThrow();
    assertThat(g.getMemberCount()).isEqualTo(1);
    assertThat(g.getMembers().get(0).getMember().getId()).isEqualTo(m.getId());
}

@Test
void changingValueFromTrueToFalse_removesMemberFromGroup() {
    Band band = ensureBand();
    MemberAttributeDef def = attrCmd.createAttributeDef(band, "OSP", "BOOLEAN", false, false, 0, null);
    Member m = memberRepo.save(Member.create("Jan", "Kowalski", java.time.LocalDate.of(1990,1,1), band));
    attrCmd.setAttributeValue(m.getId(), def.getId(), "true");
    attrCmd.setAttributeValue(m.getId(), def.getId(), "false");

    Group g = groupRepo.findByDynamicSource(def).orElseThrow();
    assertThat(g.getMemberCount()).isZero();
}

@Test
void settingValueToFalse_directly_doesNotAddMember() {
    Band band = ensureBand();
    MemberAttributeDef def = attrCmd.createAttributeDef(band, "OSP", "BOOLEAN", false, false, 0, null);
    Member m = memberRepo.save(Member.create("Jan", "Kowalski", java.time.LocalDate.of(1990,1,1), band));
    attrCmd.setAttributeValue(m.getId(), def.getId(), "false");

    Group g = groupRepo.findByDynamicSource(def).orElseThrow();
    assertThat(g.getMemberCount()).isZero();
}
```

Run: `mvn -q test -Dtest=DynamicGroupSyncTest`
Expected: 3 new tests fail (add, remove, false-only).

**Step 2: Wire `setAttributeValue` to call sync**

In `MemberAttributeCommandService.setAttributeValue`:
```java
public void setAttributeValue(Long memberId, Long attributeDefId, String value) {
    Member member = memberRepository.findById(memberId).orElseThrow(...);
    MemberAttributeDef def = attributeDefRepository.findById(attributeDefId).orElseThrow(...);
    // ... existing upsert logic ...
    attributeValueRepository.save(attrValue);

    // Sync dynamic group membership
    groupCommandService.syncMemberInDynamicGroup(def, member, value);
}
```

**Step 3: Run test (GREEN)**

Run: `mvn -q test -Dtest=DynamicGroupSyncTest`
Expected: all 8 pass.

**Step 4: Commit**

```bash
git add src/test/java/pl/michalbzowski/windband/application/command/band/DynamicGroupSyncTest.java \
        src/main/java/pl/michalbzowski/windband/application/command/band/MemberAttributeCommandService.java
git commit -m "feat(attribute): sync dynamic group membership on value change"
```

---

### Task 8: UI — show `🔄 dynamiczna` badge on group list

**Objective:** Users see at a glance which groups are dynamic.

**Files:**
- Modify: `src/main/resources/templates/groups/list.html:31-44`

**Step 1: Add badge column + icon**

In the `<thead>`, add a "Typ" column. In the `<tbody>`, render the badge:
```html
<td>
    <strong th:text="${g.name}">Sekcja dęta</strong>
    <span th:if="${g.dynamic}" class="dynamic-badge" title="Grupa zarządzana automatycznie na podstawie atrybutu">🔄 dynamiczna</span>
</td>
```

**Step 2: Add CSS for badge**

In `src/main/resources/static/css/app.css`, add:
```css
.dynamic-badge {
    display: inline-block;
    margin-left: 0.5rem;
    padding: 0.1rem 0.4rem;
    font-size: 0.75rem;
    background: var(--pico-secondary-background);
    color: var(--pico-secondary-inverse);
    border-radius: 0.25rem;
}
```

**Step 3: Manual smoke test**

Run the app, navigate to `/groups`, verify:
- Manual group → no badge
- Dynamic group (after creating a BOOLEAN attribute) → has `🔄 dynamiczna` badge

**Step 4: Commit**

```bash
git add src/main/resources/templates/groups/list.html \
        src/main/resources/static/css/app.css
git commit -m "feat(groups-ui): show dynamic badge on group list"
```

---

### Task 9: UI — block manual add/remove on dynamic groups

**Objective:** Prevent users from manually editing members of a dynamic group (the form change submitted via the existing `/api/groups/{id}/members/{memberId}` POST/DELETE will also be blocked at the API layer — see Task 10).

**Files:**
- Modify: `src/main/resources/templates/groups/detail.html:13-67`

**Step 1: Conditional render**

Replace the "Zarządzaj członkami" section (lines 13-23) and the "Usuń" form per-row (lines 45-51) with conditional versions:
```html
<!-- Manual add UI: hidden for dynamic groups -->
<div th:unless="${group.dynamic}" style="display:flex; gap:0.5rem; margin-bottom:1rem;">
    <select id="add-member-select"> ... </select>
    <button class="outline" id="add-member-btn">Dodaj</button>
</div>
<div th:if="${group.dynamic}" class="dynamic-info-banner" style="margin-bottom:1rem; padding:0.75rem; background:var(--pico-card-background-color); border-radius:0.5rem;">
    <strong>🔄 Grupa dynamiczna</strong>
    <p style="margin:0.25rem 0 0;">Członkowie tej grupy są zarządzani automatycznie przez atrybut <code th:text="${group.name}">OSP</code>. Aby zmienić skład, edytuj wartość tego atrybutu u danego członka.</p>
</div>

<!-- Per-row "Usuń" form: hidden for dynamic groups -->
<form th:unless="${group.dynamic}" ...> ... </form>
```

**Step 2: Add `dynamic` flag to `GroupDetailDto`**

```java
public record GroupDetailDto(
        Long id,
        String name,
        String description,
        int memberCount,
        List<GroupMemberDto> members,
        boolean dynamic    // NEW
) { ... }
```

Update `GroupQueryService.getGroupDetailById` to set it.

**Step 3: Manual smoke test**

Open a dynamic group's detail page → no "Dodaj" button, no "Usuń" form, info banner visible. Open a manual group's detail page → both controls visible.

**Step 4: Commit**

```bash
git add src/main/resources/templates/groups/detail.html \
        src/main/java/pl/michalbzowski/windband/application/dto/GroupDetailDto.java \
        src/main/java/pl/michalbzowski/windband/application/query/member/GroupQueryService.java
git commit -m "feat(groups-ui): block manual add/remove on dynamic groups + info banner"
```

---

### Task 10: API — reject manual edits on dynamic groups

**Objective:** Even if a malicious user crafts a request directly to `/api/groups/{id}/members/{memberId}`, the server must reject it for dynamic groups.

**Files:**
- Modify: `src/main/java/pl/michalbzowski/windband/application/command/member/GroupCommandService.java:25-47`
- Modify: `src/main/java/pl/michalbzowski/windband/adapter/in/web/GroupController.java:41-51`

**Step 1: Add defensive check in `GroupCommandService`**

```java
public void addMemberToGroup(Long groupId, Long memberId) {
    Group group = groupRepository.findById(groupId).orElseThrow(...);
    if (group.isDynamic()) {
        throw new IllegalStateException(
            "Nie można ręcznie dodawać członków do grupy dynamicznej '" + group.getName() + "'. " +
            "Członkowie są zarządzani automatycznie przez atrybut '" + group.getName() + "'.");
    }
    // ... existing logic ...
}
// Same check in removeMemberFromGroup
```

**Step 2: Map exception to 409 in controller**

Add to `GlobalExceptionHandler` (find existing file):
```java
@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
    if (e.getMessage() != null && e.getMessage().startsWith("Nie można ręcznie")) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
}
```

**Step 3: Add failing test (RED)**

In `src/test/java/pl/michalbzowski/windband/application/command/member/DynamicGroupApiTest.java`:
```java
@Test
void manualAddToDynamicGroup_throws() {
    Band band = ensureBand();
    MemberAttributeDef def = attrCmd.createAttributeDef(band, "OSP", "BOOLEAN", false, false, 0, null);
    Member m = memberRepo.save(Member.create("Jan", "Kowalski", LocalDate.of(1990,1,1), band));
    Group g = groupRepo.findByDynamicSource(def).orElseThrow();
    assertThatThrownBy(() -> groupCmd.addMemberToGroup(g.getId(), m.getId()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("dynamiczną");
}
```

**Step 4: Verify test passes (GREEN after Step 1)**

Run: `mvn -q test -Dtest=DynamicGroupApiTest`

**Step 5: Commit**

```bash
git add src/test/java/pl/michalbzowski/windband/application/command/member/DynamicGroupApiTest.java \
        src/main/java/pl/michalbzowski/windband/application/command/member/GroupCommandService.java \
        src/main/java/pl/michalbzowski/windband/adapter/in/web/GlobalExceptionHandler.java
git commit -m "feat(groups-api): reject manual edits on dynamic groups (defense in depth)"
```

---

### Task 11: Backfill — spawn dynamic groups for existing BOOLEAN attributes

**Objective:** On application startup, ensure every existing BOOLEAN `MemberAttributeDef` has a corresponding dynamic group. This is the production data migration (one-shot) for bands that had BOOLEAN attributes before this feature shipped.

**Files:**
- Modify: `src/main/java/pl/michalbzowski/windband/application/command/band/MemberAttributeCommandService.java` (add `ensureDynamicGroupExists` method)
- Create: `src/main/main/java/pl/michalbzowski/windband/config/DynamicGroupBackfillRunner.java` (ApplicationRunner)

**Step 1: Add `ensureDynamicGroupExists` to command service**

```java
public void ensureDynamicGroupExists(MemberAttributeDef def) {
    if (!"BOOLEAN".equals(def.getType())) return;
    if (groupRepository.findByDynamicSource(def).isPresent()) return;
    groupCommandService.createDynamicGroupForAttribute(def);

    // Also sync existing attribute values into the group
    List<MemberAttributeValue> values = attributeValueRepository.findByAttributeDef(def);
    for (MemberAttributeValue v : values) {
        groupCommandService.syncMemberInDynamicGroup(def, v.getMember(), v.getValue());
    }
}
```

(Need to add `findByAttributeDef(MemberAttributeDef def)` to `MemberAttributeValueRepository`.)

**Step 2: Add the runner**

```java
@Component
@Profile("!test")  // skip in tests — they create their own data
@RequiredArgsConstructor
public class DynamicGroupBackfillRunner implements ApplicationRunner {

    private final MemberAttributeDefRepository attributeDefRepository;
    private final MemberAttributeCommandService memberAttributeCommandService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DynamicGroupBackfillRunner.class);

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Find all BOOLEAN attributes across all bands
        List<MemberAttributeDef> allBoolean = attributeDefRepository.findAll().stream()
                .filter(d -> "BOOLEAN".equals(d.getType()))
                .toList();
        log.info("[backfill] Found {} BOOLEAN member attributes; ensuring dynamic groups", allBoolean.size());
        for (MemberAttributeDef def : allBoolean) {
            memberAttributeCommandService.ensureDynamicGroupExists(def);
        }
    }
}
```

**Step 3: Manual verification on dev DB**

Run the app locally. Check the logs for `[backfill] Found N BOOLEAN...`. Then check the groups table for new groups. Spot-check: pick a member with a BOOLEAN attribute = "true", verify they're in the dynamic group.

**Step 4: Commit**

```bash
git add src/main/java/pl/michalbzowski/windband/application/command/band/MemberAttributeCommandService.java \
        src/main/java/pl/michalbzowski/windband/domain/band/MemberAttributeValueRepository.java \
        src/main/java/pl/michalbzowski/windband/adapter/out/persistence/MemberAttributeValueRepositoryAdapter.java \
        src/main/java/pl/michalbzowski/windband/config/DynamicGroupBackfillRunner.java
git commit -m "feat(backfill): spawn dynamic groups for existing BOOLEAN attributes on startup"
```

---

### Task 12: End-to-end UI regression test

**Objective:** Lock in the full user flow: create attribute → see group on list → set value → see member in group → change value → see removal.

**Files:**
- Create: `src/test/java/pl/michalbzowski/windband/adapter/in/web/DynamicGroupEndToEndUiTest.java`

**Step 1: Write the test**

Outline (full implementation will be done by the subagent — the structure below is the spec):
```java
class DynamicGroupEndToEndUiTest extends UiTestBase {

    @Test
    void createBooleanAttribute_dynamicGroupAppearsOnList() {
        // 1. loginAndNavigateTo("/band/inventory-attributes") (or wherever attribute defs are managed)
        // 2. create new BOOLEAN attribute "OspEndToEnd"
        // 3. navigate to /groups
        // 4. assert group "OspEndToEnd" with badge "🔄 dynamiczna" is in the list
        // 5. assert description starts with "Grupa dynamiczna..."
    }

    @Test
    void setAttributeValueTrue_memberAppearsInDynamicGroup() {
        // 1. create attribute + member
        // 2. edit member, set attribute to "true"
        // 3. navigate to /groups/{id} (the dynamic group)
        // 4. assert member is in the member list
        // 5. assert no "Dodaj"/"Usuń" buttons
    }

    @Test
    void setAttributeValueFalse_memberRemovedFromDynamicGroup() {
        // 1. create attribute + member + set true → assert in group
        // 2. edit member, set attribute to "false"
        // 3. navigate to /groups/{id}
        // 4. assert member is NOT in the member list
    }

    @Test
    void renameAttribute_groupRenamesToo() {
        // 1. create attribute "OldName"
        // 2. assert group "OldName" exists
        // 3. edit attribute, change name to "NewName"
        // 4. assert group "NewName" exists, "OldName" doesn't
    }
}
```

**Step 2: Run**

Run: `mvn -q test -Dtest=DynamicGroupEndToEndUiTest`
Expected: all 4 pass.

**Step 3: Commit**

```bash
git add src/test/java/pl/michalbzowski/windband/adapter/in/web/DynamicGroupEndToEndUiTest.java
git commit -m "test: end-to-end UI regression for dynamic groups"
```

---

### Task 13: Update PROJECT_DOCS.md + AI_HARNESS.md

**Objective:** Document the new feature so future agents and humans understand the dynamic group mechanism.

**Files:**
- Modify: `PROJECT_DOCS.md` — add to Member Context domain model section, add to Page Controllers table, update API endpoints list
- Modify: `AI_HARNESS.md` — add a one-liner to § 1.1 about dynamic groups being a domain rule (ArchUnit should already pass, but we want a written invariant)

**Step 1: PROJECT_DOCS changes**

In § Domain Model → Member Context, add a paragraph after Group's description:
```markdown
- **Group ↔ MemberAttributeDef (dynamic)**: A `Group` whose `dynamicSource` field is non-null is a "dynamic" group: it is auto-created for every `MemberAttributeDef` of type `BOOLEAN`, members are auto-added when their attribute value is `"true"`, and the group follows the attribute's name/type through rename/delete/type-change. Manual add/remove via the UI or API is rejected with 409 Conflict.
```

In § API Endpoints → `/api/groups`, add a note:
```markdown
**Dynamic groups**: POST/DELETE `/api/groups/{id}/members/{memberId}` returns 409 Conflict if the group is dynamic (auto-managed). Use the attribute value endpoint `/api/bands/{bandId}/attribute-defs/{attrId}/members/{memberId}` instead.
```

In § Page Controllers → GroupPageController, add a column "Dynamic badge" to the table mention.

**Step 2: AI_HARNESS changes**

In § 1.1, append:
```markdown
- **Dynamic groups are a domain invariant**: never manually insert into `member_groups` for an attribute-backed group. All changes flow through `MemberAttributeCommandService`, which calls `GroupCommandService.syncMemberInDynamicGroup`. UI/API must reject manual edits.
```

**Step 3: Commit**

```bash
git add PROJECT_DOCS.md AI_HARNESS.md
git commit -m "docs: document dynamic groups feature"
```

---

### Task 14: Update navigation + manual smoke

**Objective:** Make sure the groups nav link still works, and the user can complete a full happy-path flow in incognito mode (per user preference).

**Step 1: Spot-check in dev**

1. Open `/band/inventory-attributes`, create a new BOOLEAN attribute "Test Dynamic"
2. Navigate to `/groups`, see "Test Dynamic" with `🔄 dynamiczna` badge
3. Open `/members`, edit a member, tick the "Test Dynamic" attribute
4. Navigate to `/groups/Test Dynamic id` (or click the row), see the member listed
5. No "Dodaj"/"Usuń" buttons visible, info banner shown
6. Go back to the member, untick the attribute
7. Re-open the group, member is gone
8. Rename the attribute, verify the group is renamed
9. Delete the attribute, verify the group is gone
10. Test invite-from-group: open an event, "Zaproś grupę" should list dynamic groups correctly

**Step 2: No commit needed (this is verification).**

---

## Verification Checklist (run all before PR)

- [ ] `mvn -q test -Dtest='!*UiTest'` — all unit tests pass
- [ ] `mvn -q test -Dtest='*UiTest'` — all UI tests pass (134+ tests)
- [ ] `mvn -q -DskipTests package` — builds the JAR
- [ ] Manual smoke test in incognito (Task 14)
- [ ] Database migration V18 reviewed for prod (no destructive ops, no default value ambiguity)
- [ ] One human "feel" check: open a dynamic group's detail page and ask "is it obvious why I can't add a member?"

## Out-of-Scope Items (deferred to future work)

- Performance optimization: if 100+ BOOLEAN attributes exist, the backfill runner should be batched (currently O(n) single transaction)
- Bulk attribute value import: when bulk-importing values, the sync needs to happen for each row
- Audit log: "member added to OSP group" events are not logged
- ArchUnit test asserting no `groupRepository.save()` is called for dynamic groups from the UI controllers

---

*Plan created 2026-07-04. Implementation will use subagent-driven-development skill for task-by-task execution with two-stage review.*
