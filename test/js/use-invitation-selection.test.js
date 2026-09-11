/*
 * use-invitation-selection.test.js — unit tests for the reactive hook
 * `useInvitationSelection` (src/main/resources/static/js/invitation-selection.js).
 *
 * This is the hook contract from parent task t_4fcc1f03 that THIS task
 * (t_9c915ab4) consumes inside `<InvitationModal>`. Mirrors the 29-assertion
 * suite on the parent branch so both handoffs pin down identical behaviour:
 *
 *   (a) group + member already in group → counted once
 *   (b) two overlapping groups share 2 members → shared members appear once
 *   (c) deselect one overlapping group while the other stays checked → shared remain
 *   Plus:
 *      - clearAll() resets everything (LIVE Sets — no stale copies)
 *      - getConfirmPayload() is sorted + deduplicated member-id array
 *      - input validation throws TypeError on bad shapes
 *      - the hook hands back LIVE Set views (mutating after capture is reflected)
 *      - `window.InvitationSelection` legacy class still works unchanged
 *
 * Run with: node test/js/use-invitation-selection.test.js
 */
'use strict';

const path = require('path');
const selFile = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'static', 'js', 'invitation-selection.js');

// The CommonJS export is the legacy `InvitationSelection` class (existing tests rely on this).
const legacyClass = require(selFile);
if (typeof legacyClass.useInvitationSelection !== 'function') {
    console.error('invitation-selection.js is missing `module.useInvitationSelection` — hook surface was not added?');
    process.exit(2);
}
const useInvitationSelection = legacyClass.useInvitationSelection;
if (typeof globalThis.useInvitationSelection !== 'function') {
    console.error('Browser global `useInvitationSelection` was not registered on root — the modal cannot load it.');
    process.exit(2);
}

let passed = 0, failed = 0;
function eq(actual, expected, label) {
    const a = typeof actual === 'function' ? '[fn]' : JSON.stringify(actual);
    const e = typeof expected === 'function' ? '[fn]' : JSON.stringify(expected);
    if (a === e) { passed++; console.log('  ok  - ' + label); }
    else         { failed++; console.error(`FAIL  - ${label}\n      expected: ${e}\n      actual:   ${a}`); }
}
function truthy(v, label) { eq(!!v, true, label); }

// ── (a) group + member already in that group → 5 unique ids (not 6) ──────────
{
    const h = useInvitationSelection({
        groups: [{ id: 1, name: 'A', memberIds: [10, 11, 12, 13, 14] }],
        members: [10, 11, 12, 13, 14].map((id) => ({ id, name: `m${id}` })),
    });
    h.toggleGroup(1);
    eq(h.count(), 5, 'a.1: count == 5 after selecting group A alone');
    h.toggleMember(10);
    eq(h.count(), 5, "a.2: adding member 10 (already in A) doesn't bump count");
    eq([...h.resolvedMemberIds].sort((a, b) => a - b), [10, 11, 12, 13, 14], 'a.3: resolved id set == exactly the 5 group ids');
    eq(h.getConfirmPayload().length, 5, 'a.4: confirm payload size == 5');
}

// ── (b) two overlapping groups share 2 → shared counted once ────────────────
{
    const h = useInvitationSelection({
        groups: [
            { id: 1, name: 'OSP',      memberIds: [1, 2] },
            { id: 2, name: 'Kompania', memberIds: [2, 3] },
        ],
    });
    h.toggleGroup(1);
    h.toggleGroup(2);
    eq([...h.resolvedMemberIds].sort((a, b) => a - b), [1, 2, 3], 'b.1: shared member 2 appears once');
    eq(h.count(), 3, 'b.2: count == 3 (union, not per-group sum of 4)');
    eq(h.getConfirmPayload(), [1, 2, 3], 'b.3: confirm payload == sorted, deduped [1,2,3]');
}

// ── (c) deselect one group while the other stays selected → shared remain ──
{
    const h = useInvitationSelection({
        groups: [
            { id: 1, name: 'OSP',      memberIds: [10, 20] },
            { id: 2, name: 'Kompania', memberIds: [20, 30] },
        ],
    });
    h.toggleGroup(1);
    h.toggleGroup(2);
    truthy(h.resolvedMemberIds.has(20), 'c.1: shared member 20 covered before deselect');
    h.toggleGroup(1); // deselect OSP (the one that did NOT add the unique 30)
    eq(h.isGroupSelected(1), false, 'c.2: group 1 deselected');
    eq(h.isGroupSelected(2), true,  'c.3: group 2 still selected');
    truthy(h.resolvedMemberIds.has(20), 'c.4: shared member 20 STILL in resolved set (covered by group 2)');
    eq([...h.resolvedMemberIds].sort((a, b) => a - b), [20, 30], 'c.5: exactly {20, 30} remain');
    eq(h.getConfirmPayload(), [20, 30], 'c.6: confirm payload reflects the surviving coverage');
}

// ── Symmetric case: individually-selected + group-covers — deselect group keeps member if individually selected ───
{
    const h = useInvitationSelection({
        groups: [{ id: 1, name: 'A', memberIds: [10, 12] }],
    });
    h.toggleGroup(1);
    truthy(h.resolvedMemberIds.has(12), 'd.1: member in group');
    h.toggleMember(12);
    eq(h.isMemberSelected(12), true, 'd.2: individually selected AFTER');
    h.toggleMember(12); // deselect individual — but group 1 still covers it
    eq(h.isMemberSelected(12), false, 'd.3: individual flag cleared');
    truthy(h.resolvedMemberIds.has(12), 'd.4: member STAYS in resolved set (still covered by selected group 1)');
}

// ── Deselect the last covering source → the member drops ─────────────────────
{
    const h = useInvitationSelection({
        groups: [
            { id: 1, name: 'A', memberIds: [10] },
            { id: 2, name: 'B', memberIds: [10] },
        ],
    });
    h.toggleGroup(1);
    h.toggleGroup(2);
    truthy(h.resolvedMemberIds.has(10), 'e.1: covered before any deselect');
    h.toggleGroup(1); // only group 2 still covers it
    truthy(h.resolvedMemberIds.has(10), 'e.2: still in resolved set after deselecting A (B covers)');
    h.toggleGroup(2); // no source covers 10 any more
    eq(h.isGroupSelected(1), false, 'e.3: group A deselected');
    eq(h.isMemberSelected(10), false, 'e.4: member not individually selected');
    eq(h.resolvedMemberIds.has(10), false, 'e.5: dropped once no source covers it');
    eq(h.count(), 0, 'e.6: count == 0 (all coverage gone)');
}

// ── clearAll() resets everything — LIVE Sets must reflect the clear ─────────
{
    const h = useInvitationSelection({
        groups: [
            { id: 1, name: 'A', memberIds: [1, 2] },
            { id: 2, name: 'B', memberIds: [2, 3] },
        ],
    });
    h.toggleGroup(1);
    h.toggleGroup(2);
    h.toggleMember(4);
    eq(h.count(), 4, 'f.1: pre-clear state has 4 ids (1,2,3,4)');
    // Capture the LIVE Set refs BEFORE clear; after clear they must be empty too.
    const capturedGroupIds  = h.selectedGroupIds;
    const capturedMemberIds = h.selectedMemberIds;
    const capturedResolved  = h.resolvedMemberIds;
    h.clearAll();
    eq(capturedGroupIds.size, 0, 'f.2: captured selectedGroupIds reflects clear (LIVE, not copy)');
    eq(capturedMemberIds.size, 0, 'f.3: captured selectedMemberIds reflects clear');
    eq(capturedResolved.size, 0, 'f.4: captured resolvedMemberIds reflects clear');
    eq(h.getConfirmPayload().length, 0, 'f.5: confirm payload empty after clearAll()');
}

// ── getConfirmPayload is sorted + deduplicated even on weird input ───────────
{
    const h = useInvitationSelection({
        groups: [
            { id: 1, name: 'A', memberIds: [9, 1] },
            { id: 2, name: 'B', memberIds: [5, 1] }, // share member 1 with A
        ],
    });
    h.toggleGroup(1);
    h.toggleGroup(2);
    eq(h.getConfirmPayload(), [1, 5, 9], 'g.1: payload sorted ascending, deduplicated');
}

// ── Input validation (TypeError on bad shapes) ───────────────────────────────
{
    let threw = false;
    try { useInvitationSelection({ groups: 'nope' }); } catch (e) { void e; threw = true; }
    truthy(threw, 'h.1: `groups` must be an array — throws');

    let threw2 = false;
    try { useInvitationSelection({ members: 42 }); } catch (e) { void e; threw2 = true; }
    truthy(threw2, 'h.2: `members` must be an array when supplied — throws');
}

// ── Cross-branch compatibility: the legacy Class is STILL exported ───────────
{
    const Legacy = require(selFile);
    eq(typeof Legacy, 'function', 'i.1: module.exports is still the legacy class (existing tests pass unchanged)');
    truthy(typeof globalThis.InvitationSelection === 'function', 'i.2: browser global `InvitationSelection` still registered');

    // Verify the legacy 2-arg toggleGroup path works exactly as it did before
    // we added the hook surface — no regression.
    const legacy = new Legacy({ A: ['x', 'y'] });
    legacy.toggleGroup('A', ['x', 'y']);
    eq(legacy.getSelectedCount(), 2, 'i.3: legacy class: toggleGroup(gid, members) still adds 2 ids');
    legacy.toggleMember('z');
    eq(legacy.getResolvedIds().slice().sort().join(','), 'x,y,z'.split(',').sort().join(','), 'i.4: legacy resolved union == {x,y,z}');
    eq(legacy.isGroupSelected('A'), true, 'i.5: legacy isGroupSelected true');
}

console.log('\n==========');
console.log(`Passed ${passed}, failed ${failed}`);
process.exit(failed === 0 ? 0 : 1);
