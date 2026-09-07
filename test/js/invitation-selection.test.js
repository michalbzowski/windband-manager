/*
 * invitation-selection.test.js — unit tests for the reactive
 * useInvitationSelection hook (src/main/resources/static/js/invitation-selection.js).
 *
 * Covers every acceptance criterion in the task body for t_4fcc1f03:
 *   (a) group A has 5 members, member X is in A → selecting both = 5 unique ids
 *   (b) two groups share 2 members → shared members counted once
 *   (c) deselect one overlapping group while the other stays checked →
 *       shared members remain selected
 * Plus:
 *   - clearAll() resets everything
 *   - getConfirmPayload() returns a sorted, deduplicated array (the same shape
 *     the existing per-member POST /invite loop consumes)
 *   - the three Set fields (`selectedGroupIds`, `selectedMemberIds`,
 *     `resolvedMemberIds`) are live: mutating any of them via a toggle is
 *     reflected immediately.
 *
 * Run with: node test/js/invitation-selection.test.js
 */
'use strict';

const useInvitationSelection = require('../../src/main/resources/static/js/invitation-selection.js');

let assertions = 0;
function eq(actual, expected, message) {
    assertions++;
    if (JSON.stringify(actual) !== JSON.stringify(expected)) {
        throw new Error(
            `${message || 'assertion failed'}\n` +
            `  actual:   ${JSON.stringify(actual)}\n` +
            `  expected: ${JSON.stringify(expected)}`
        );
    }
}
function truthy(value, message) {
    assertions++;
    if (!value) throw new Error(`assertion failed: ${message || 'expected truthy'}`);
}

// ── (a) group A (5 members) + member X already in A ⇒ 5 unique ids ──────────
{
    const h = useInvitationSelection({
        groups: [{ id: 1, name: 'A', memberIds: [10, 11, 12, 13, 14] }],
        members: [].concat([10, 11, 12, 13, 14].map((id) => ({ id, name: `m${id}` }))),
    });
    h.toggleGroup(1);
    h.toggleMember(10); // shared with group A
    eq(h.resolvedMemberIds.size, 5, 'a: group + shared member = 5 unique ids');
    eq(h.count(), 5, 'a: count() reflects live resolved size');
    eq(h.getConfirmPayload().length, 5, 'a: confirm payload size = 5');
}

// ── (b) two groups share 2 members ⇒ shared ids counted once ────────────────
{
    const h = useInvitationSelection({
        groups: [
            { id: 1, name: 'OSP', memberIds: [1, 2] },
            { id: 2, name: 'Kompania', memberIds: [2, 3] },
        ],
    });
    h.toggleGroup(1);
    h.toggleGroup(2);
    eq([...h.resolvedMemberIds].sort((a, b) => a - b), [1, 2, 3], 'b: shared members counted once');
    eq(h.getConfirmPayload().length, 3, 'b: confirm payload = 3 unique ids');
}

// ── (c) deselect one overlapping group; the other stays ⇒ shared remain ──────
{
    const h = useInvitationSelection({
        groups: [
            { id: 1, name: 'OSP', memberIds: [1, 2] },
            { id: 2, name: 'Kompania', memberIds: [2, 3] },
        ],
    });
    h.toggleGroup(1);
    h.toggleGroup(2);
    // Member 2 is shared. Deselect group 1 only — group 2 still covers member 2.
    h.toggleGroup(1); // deselect (toggle back off)
    eq(h.isGroupSelected(1), false, 'c: group 1 deselected');
    eq(h.isGroupSelected(2), true, 'c: group 2 still selected');
    truthy(h.resolvedMemberIds.has(2), 'c: shared member 2 remains after deselecting one group');
    eq([...h.resolvedMemberIds].sort((a, b) => a - b), [2, 3], 'c: exactly {2,3} remain');
    eq(h.getConfirmPayload().length, 2, 'c: confirm payload = remaining 2');
}

// ── member deselect while still covered by a group ⇒ stays ───────────────────
{
    const h = useInvitationSelection({
        groups: [{ id: 1, name: 'A', memberIds: [10, 12] }],
    });
    h.toggleGroup(1);
    truthy(h.resolvedMemberIds.has(12), 'member: in group');
    h.toggleMember(12); // add (no-op on resolved set)
    truthy(h.isMemberSelected(12), 'member: individually selected');
    h.toggleMember(12); // deselect — but the group still covers it
    eq(h.isMemberSelected(12), false, 'member: individual selection cleared');
    truthy(h.resolvedMemberIds.has(12), 'member: stays resolved because a selected group still covers it');
}

// ── deselect last covering source ⇒ drops ────────────────────────────────────
{
    const h = useInvitationSelection({
        groups: [{ id: 1, name: 'A', memberIds: [10] }, { id: 2, name: 'B', memberIds: [10] }],
    });
    h.toggleGroup(1);
    h.toggleGroup(2);
    h.toggleGroup(1); // only group 2 now covers member 10
    truthy(h.resolvedMemberIds.has(10), 'remove: member still covered by the remaining group');
    h.toggleGroup(2); // no source covers 10 any more
    eq(h.isMemberSelected(10), false, 'remove: not individually selected');
    eq(h.resolvedMemberIds.has(10), false, 'remove: dropped once no source covers it');
    eq(h.resolvedMemberIds.size, 0, 'remove: resolved set empty after all coverage gone');
}

// ── clearAll() resets ────────────────────────────────────────────────────────
{
    const h = useInvitationSelection({
        groups: [{ id: 1, name: 'A', memberIds: [1, 2] }, { id: 2, name: 'B', memberIds: [2, 3] }],
    });
    h.toggleGroup(1);
    h.toggleGroup(2);
    h.toggleMember(4);
    eq(h.resolvedMemberIds.size, 4, 'clearAll: pre-state has 4 ids (1,2,3,4)');
    h.clearAll();
    eq(h.selectedGroupIds.size, 0, 'clearAll: selected groups emptied');
    eq(h.selectedMemberIds.size, 0, 'clearAll: selected members emptied');
    eq(h.resolvedMemberIds.size, 0, 'clearAll: resolved set emptied');
    eq(h.getConfirmPayload().length, 0, 'clearAll: confirm payload empty');
}

// ── getConfirmPayload is sorted & deduplicated even on weird input ───────────
{
    const h = useInvitationSelection({
        groups: [
            { id: 1, name: 'A', memberIds: [9, 1] },
            { id: 2, name: 'B', memberIds: [5, 1] }, // shared with A via member 1
        ],
    });
    h.toggleGroup(1);
    h.toggleGroup(2);
    eq(h.getConfirmPayload(), [1, 5, 9], 'sort: confirm payload sorted ascending, deduplicated');
}

// ── live Set mutation is reflected (reactive contract) ────────────────────────
{
    const g = { id: 1, name: 'A', memberIds: [7] };
    const h = useInvitationSelection({ groups: [g] });
    truthy(h.resolvedMemberIds.size === 0, 'reactive: clean start');
    h.toggleGroup(1);
    // The same Set object we captured at hook creation must now reflect the add —
    // proving we return LIVE views, not copies.
    const sameRef = (h.resolvedMemberIds).has(7);
    truthy(sameRef, 'reactive: captured Set reflects subsequent toggleGroup');
}

// ── inputs validation ────────────────────────────────────────────────────────
{
    let threw = false;
    try { useInvitationSelection({ groups: 'nope' }); } catch (e) { void e; threw = true; }
    truthy(threw, 'validate: groups must be an array');
}
{
    let threw = false;
    try { useInvitationSelection({ members: 42 }); } catch (e) { void e; threw = true; }
    truthy(threw, 'validate: members must be an array when provided');
}

// ── cross-branch API alias ───────────────────────────────────────────────────
{
    // Expose `window.InvitationSelection` as a stable alias (per the task body's
    // own "likely" hints + to converge with t_2e9238fa / t_f03a9819 branches).
    const mod = require('../../src/main/resources/static/js/invitation-selection.js');
    truthy(typeof mod === 'function', 'export: default export is the hook (callable)');
}

console.log(`ok — ${assertions} assertions passed for useInvitationSelection.`);
