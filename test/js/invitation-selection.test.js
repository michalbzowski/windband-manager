/*
 * invitation-selection.test.js — browser-free unit tests for the pure-logic
 * invitation-selection module (src/main/resources/static/js/invitation-selection.js).
 *
 * Kept under test/js/ (NOT under src/main/resources) so it is NOT packaged into
 * the production jar. Runs with plain Node, no framework:
 *
 *     node test/js/invitation-selection.test.js
 *
 * Exercises all five acceptance criteria from task t_2e9238fa plus a few edge
 * cases (re-select after deselect, free-standing members) to prove the
 * reference-count semantics are symmetric in both directions.
 */
'use strict';

const path = require('path');
const InvitationSelection = require(path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'static', 'js', 'invitation-selection.js'));

let passed = 0;
let failed = 0;

function assertEqual(actual, expected, label) {
    if (Object.is(actual, expected)) {
        passed++;
        console.log(`  ok  - ${label}`);
    } else {
        failed++;
        console.error(`FAIL  - ${label}\n      expected: ${JSON.stringify(expected)}\n      actual:   ${JSON.stringify(actual)}`);
    }
}

function assertSet(actual, expectedSet, label) {
    const a = new Set(actual);
    if (a.size === expectedSet.size && [...expectedSet].every((x) => a.has(x))) {
        passed++;
        console.log(`  ok  - ${label}`);
    } else {
        failed++;
        console.error(`FAIL  - ${label}\n      expected: ${JSON.stringify([...expectedSet].sort())}\n      actual:   ${JSON.stringify([...a].sort())}`);
    }
}

// Case 1 — group A (5 members) + member X already in A -> count stays 5.
console.log('\nCase 1: select group A (5 members), then member X already in A -> count stays 5');
{
    const s = new InvitationSelection({ A: ['m1', 'm2', 'm3', 'm4', 'm5'] });
    s.toggleGroup('A', ['m1', 'm2', 'm3', 'm4', 'm5']);
    assertEqual(s.getSelectedCount(), 5, 'group A selected gives count 5');
    assertSet(s.getResolvedIds(), new Set(['m1', 'm2', 'm3', 'm4', 'm5']), 'resolved ids == group members');
    s.toggleMember('m3');                       // member already in A
    assertEqual(s.getSelectedCount(), 5, 'count stays 5 after selecting a member already in A');
    assertSet(s.getResolvedIds(), new Set(['m1', 'm2', 'm3', 'm4', 'm5']), 'resolved ids unchanged');
    assertEqual(s.isMemberSelected('m3'), true, 'member X flagged selected individually');
}

// Case 2 — overlapping groups (share 2) -> shared members appear once.
console.log('\nCase 2: select overlapping groups OSP & Kompania (share 2) -> shared members appear once');
{
    const s = new InvitationSelection({
        OSP:      ['o1', 'o2', 's1', 's2'],
        Kompania: ['k1', 'k2', 's1', 's2'],
    });
    s.toggleGroup('OSP', ['o1', 'o2', 's1', 's2']);
    assertEqual(s.getSelectedCount(), 4, 'OSP alone -> 4 members');
    s.toggleGroup('Kompania', ['k1', 'k2', 's1', 's2']);
    // 4 + 4 - 2 shared = 6 unique
    assertEqual(s.getSelectedCount(), 6, 'two overlapping groups share 2 -> 6 unique');
    assertSet(
        s.getResolvedIds(),
        new Set(['o1', 'o2', 's1', 's2', 'k1', 'k2']),
        'resolved set is the union (each member appears once)'
    );
    assertEqual(new Set(s.getResolvedIds()).size, 6, 'no duplicates in resolved set');
}

// Case 3 — deselect OSP while Kompania selected -> shared 2 remain, unique drop.
console.log('\nCase 3: deselect OSP while Kompania still selected -> shared 2 remain, unique OSP members drop');
{
    const s = new InvitationSelection({
        OSP:      ['o1', 'o2', 's1', 's2'],
        Kompania: ['k1', 'k2', 's1', 's2'],
    });
    s.toggleGroup('OSP', ['o1', 'o2', 's1', 's2']);
    s.toggleGroup('Kompania', ['k1', 'k2', 's1', 's2']);
    assertEqual(s.getSelectedCount(), 6, 'both groups -> 6');
    s.toggleGroup('OSP', ['o1', 'o2', 's1', 's2']);       // deselect OSP
    assertEqual(s.isGroupSelected('OSP'), false, 'OSP deselected');
    assertSet(
        s.getResolvedIds(),
        new Set(['k1', 'k2', 's1', 's2']),
        'shared 2 remain; unique OSP members (o1,o2) dropped'
    );
    assertEqual(s.getSelectedCount(), 4, 'count drops to Kompania only');
}

// Case 4 — deselect the last covering source for a member -> member removed.
console.log('\nCase 4: deselect the last covering source for a member -> member removed');
{
    const s = new InvitationSelection({ A: ['x', 'y'] });
    s.toggleGroup('A', ['x', 'y']);
    s.toggleMember('z');                     // individually select z (no group covers it)
    assertSet(s.getResolvedIds(), new Set(['x', 'y', 'z']), 'group + individual union');
    s.toggleGroup('A', ['x', 'y']);          // last covering source for x,y is gone
    assertEqual(s.isGroupSelected('A'), false, 'group A deselected');
    // z is still individually selected, so it must stay
    assertSet(s.getResolvedIds(), new Set(['z']), 'only the individually-selected member remains');
    assertEqual(s.getSelectedCount(), 1, 'count == 1 (z only)');
    s.toggleMember('z');                     // remove the last covering source for z
    assertEqual(s.isMemberSelected('z'), false, 'member z deselected');
    assertSet(s.getResolvedIds(), new Set([]), 'resolved set empty after all sources gone');
    assertEqual(s.getSelectedCount(), 0, 'count == 0');
}

// Case 5 — framework-agnostic proof: add / remove / add round-trips cleanly.
console.log('\nCase 5: toggling is symmetric - add / remove / add round-trips cleanly');
{
    const s = new InvitationSelection({
        OSP:      ['o1', 'o2', 's1'],
        Kompania: ['k1', 's1'],
    });
    s.toggleGroup('OSP', ['o1', 'o2', 's1']);
    s.toggleGroup('Kompania', ['k1', 's1']);
    assertEqual(s.getSelectedCount(), 4, '4 unique after both');
    s.toggleGroup('OSP', ['o1', 'o2', 's1']);           // off
    assertSet(s.getResolvedIds(), new Set(['k1', 's1']), 'kompania subset remains');
    // reselect OSP: o1,o2 return, s1 already present (no double count)
    s.toggleGroup('OSP', ['o1', 'o2', 's1']);           // on again
    assertSet(s.getResolvedIds(), new Set(['o1', 'o2', 's1', 'k1']), 'roundtrip restores o1,o2');
    assertEqual(s.getSelectedCount(), 4, 'count returns to 4 after re-add');
    s.toggleGroup('OSP', ['o1', 'o2', 's1']);           // off
    s.toggleGroup('Kompania', ['k1', 's1']);            // off
    assertSet(s.getResolvedIds(), new Set([]), 'empty after every source cleared');
}

// Extra — individually-selected members survive without any group coverage info.
console.log('\nExtra: individually-selected members survive without group coverage info');
{
    const s = new InvitationSelection();        // no group-membership snapshot at all
    s.toggleMember('free1');
    s.toggleMember('free2');
    assertSet(s.getResolvedIds(), new Set(['free1', 'free2']), 'individuals work with zero group data');
    assertEqual(s.getSelectedCount(), 2, 'count == 2');
    s.toggleMember('free1');
    assertSet(s.getResolvedIds(), new Set(['free2']), 'removing a free-standing member drops it');
}

// Extra — the module registers itself as a browser global AND as a CommonJS export.
console.log('\nExtra: dual-environment export (Node global + CommonJS) both land');
{
    // When loaded under Node, require() gives us the class via module.exports.
    assertEqual(typeof InvitationSelection, 'function', 'CommonJS export is the class');
    // The root-fallback branch also attached it to globalThis.InvitationSelection.
    assertEqual(typeof globalThis.InvitationSelection, 'function', 'browser global registered on root');
    // Both point to the same constructor (idempotent guard, not a re-copy).
    // In a real browser `window.InvitationSelection === require(...)`.
    // eslint-disable-next-line no-unused-expressions
}

// Summary.
console.log('\n==========');
console.log(`Passed ${passed}, failed ${failed}`);
process.exit(failed === 0 ? 0 : 1);
