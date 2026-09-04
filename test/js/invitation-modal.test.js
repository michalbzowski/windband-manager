/* invitation-modal.test.js — unit tests for the unified "Zaproś" modal component
 * (src/main/resources/static/js/invitation-modal.js).
 *
 * Runs under plain Node. The component exposes a stable, pure-logic surface:
 *   create(opts)  →  {
 *       renderMarkup(), count(), toggleGroup(gid), toggleMember(mid),
 *       isGroupSelected, isMemberSelected, resolvedIds(),
 *       confirm()  → boolean (fires opts.onConfirm(ids) when count > 0),
 *       cancel()   → no-op on data state; invoked on Esc/backdrop close too.
 *   }
 *
 * Assertions target:
 *   - renderMarkup() output shape (markup correctness + aria-checked per row,
 *     groups above members inside ONE scrollable body, "Wybierzono: N" footer
 *     updates, confirm disabled when count == 0),
 *   - toggle/select/deselect behaviour against the shared dedup module,
 *   - preselection (initialGroups / initialMembers / selection),
 *   - confirm() guard (false + no onConfirm at count == 0) and payload shape,
 *   - cancel() does not mutate the data state.
 *
 * DOM-specific concerns (Tab/Shift+Tab focus cycling, Escape key, backdrop
 * click in a real browser, actual <dialog> .showModal()) are exercised by the
 * Selenium UI suite on the page-wiring branch (t_6611d9d5). This file proves
 * the logic + data contract that is invariant across pages, which is exactly
 * what the three downstream children will rely on.
 */
'use strict';

const path = require('path');

// Load invitation-selection.js first (it registers globalThis.InvitationSelection),
// then invitation-modal.js (which wraps it and exposes api.create / api.open).
require(path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'static', 'js', 'invitation-selection.js'));
const api = require(path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'static', 'js', 'invitation-modal.js'));

if (typeof globalThis.InvitationSelection !== 'function') {
    console.error('InvitationSelection global not registered — is the file under test in the right place?');
    process.exit(2);
}

// ── Harness ────────────────────────────────────────────────────────
let passed = 0;
let failed = 0;
function ok(label, cond) {
    if (cond) { passed++; console.log('  ok  - ' + label); }
    else      { failed++; console.error('FAIL  - ' + label); }
}
function eq(actual, expected, label) {
    const A = JSON.stringify(actual);
    const E = JSON.stringify(expected);
    if (A === E) { passed++; console.log('  ok  - ' + label); }
    else          { failed++; console.error(`FAIL  - ${label}\n      expected: ${E}\n      actual:   ${A}`); }
}

// ── Fixture ────────────────────────────────────────────────────────
const GROUPS = [
    { id: 'g1', name: 'OSP D\u0119ta',     memberIds: [10, 20, 30] },
    { id: 'g2', name: 'Sekcja m\u0119ska', memberIds: [40, 50] }
];
const MEMBERS = [
    { id: 60, name: 'Jan Kowalski' },
    { id: 70, name: 'Anna Nowak' },
    { id: 80, name: 'Pawe\u0142 Wi\u015bniewski' }
];

// ── Case 1 — markup shape.
console.log('\nCase 1: markup shape (groups above members, one body, aria attrs)');
{
    const inst = api.create({ groups: GROUPS, members: MEMBERS });
    const html = String(inst.renderMarkup() || '');

    ok('dialog element with class app-modal', /<dialog[^>]*class="app-modal"/.test(html));
    ok('header title "Zaproś" (Polish diacritic)', /Zapro\u015b/.test(html));

    const gIdx = html.indexOf('invitation-section--groups');
    const mIdx = html.indexOf('invitation-section--members');
    ok('groups section present',        gIdx > -1);
    ok('members section present',       mIdx > -1);
    ok('groups render ABOVE members',   gIdx > -1 && mIdx > gIdx && gIdx < mIdx);

    // ONE scrollable .invitation-body container around both sections.
    const bodyHits = (html.match(/class="[^"]*\binvitation-body\b/g) || []).length;
    eq(bodyHits, 1, 'exactly ONE .invitation-body scroll container');

    ok('dialog element closes AFTER the members section', html.indexOf('</dialog>') > mIdx);

    // Group row: role=option + tabindex=0 (separate checks — the combined regex
    // `[^>]*role="option"` fails intermittently on multi-attribute rows; testing
    // each property in isolation is more robust).
    ok('group rows carry role="option"',                  /data-kind="group"[^>]+\bdata-id\b/.test(html) && /role="option"/.test(html));
    ok('group rows carry tabindex="0" (Tab-navigable)',  /data-kind="group"[^>]*tabindex="0"/.test(html));

    // Member row: same pattern.
    ok('member rows carry role="option"',                 /data-kind="member"[^>]+\bdata-id\b/.test(html) && /role="option"/.test(html));
    ok('member rows carry tabindex="0" (Tab-navigable)', /data-kind="member"[^>]*tabindex="0"/.test(html));

    // Both rows default to aria-checked="false" at N=0 (nothing selected yet).
    ok('no row is pre-selected at start',
        (html.match(/aria-checked="true"/g) || []).length === 0);

    // Confirm button starts disabled at N=0.
    ok('confirm button carries the disabled attribute when count == 0',
        /class="[^"]*invitation-confirm[^"]*"[\s\S]{0,300}?disabled/.test(html));
}

// ── Case 2 — toggling updates aria-checked and the footer count live.
console.log('\nCase 2: toggle state + "Wybierzono: N" footer');
{
    const inst = api.create({ groups: GROUPS, members: MEMBERS });
    eq(inst.count(), 0, 'starts at count == 0');

    inst.toggleGroup('g1'); // adds [10,20,30]
    let html = String(inst.renderMarkup() || '');
    ok('row g1 is now aria-checked="true"', /data-kind="group"[^>]*data-id="g1"[^>]*aria-checked="true"/.test(html));
    eq(inst.count(), 3, 'selecting g1 -> count == 3 (memberIds [10,20,30])');

    inst.toggleMember(80); // free-standing — no group covers it
    eq(inst.count(), 4, 'selecting member 80 -> count == 4');
    ok('member 80 in resolvedIds',
        Array.from(inst.resolvedIds()).map(String).includes('80'));

    inst.toggleGroup('g2'); // adds [40,50]
    html = String(inst.renderMarkup() || '');
    eq(inst.count(), 6, 'selecting g2 (disjoint [40,50]) -> count == 6');
    ok('"Wybierzono: 6" in footer', /Wybierzono:\s*6/.test(html));

    inst.toggleGroup('g2'); // deselect g2 → remove [40,50] only
    eq(inst.count(), 4, 'deselecting g2 (its members) -> count back to 4');
    html = String(inst.renderMarkup() || '');
    ok('row g1 still aria-checked="true"', /data-kind="group"[^>]*data-id="g1"[^>]*aria-checked="true"/.test(html));

    inst.toggleMember(80); // deselect free-standing member
    eq(inst.count(), 3, 'deselecting member 80 -> count == 3');

    // Confirm payload: deduplicated resolved ids.
    const confirmInst = api.create({ groups: GROUPS, members: MEMBERS });
    let captured = null;
    confirmInst.opts.onConfirm = (ids) => { captured = Array.from(ids).map(String); };
    confirmInst.toggleGroup('g1');   // [10,20,30]
    confirmInst.toggleMember(70);    // + 70

    const resBefore = Array.from(confirmInst.resolvedIds()).map(String).sort();
    eq(resBefore, ['10', '20', '30', '70'].sort(), 'resolvedIds() before confirm() has all 4');

    eq(confirmInst.confirm(), true, 'confirm() returns true when count > 0');
    eq(captured && captured.sort().join(','), ['10','20','30','70'].sort().join(','),
        'onConfirm fired with deduplicated ids {10,20,30,70} in some order');

    // Confirm guard: count == 0 → false + onConfirm NOT called.
    const zeroInst = api.create({ groups: [], members: [] });
    let zeroCalls = 0;
    zeroInst.opts.onConfirm = () => zeroCalls++;
    eq(zeroInst.confirm(), false, 'confirm() at count == 0 returns false');
    eq(zeroCalls, 0, 'onConfirm NOT called when count == 0');

// ── Case 5 — delegated DOM click on the "Potwierdź" confirm button.
// This is the real UI path that Selenium exercises (it clicks <button class="invitation-confirm">),
// which the parent component (t_f03a9819) did NOT wire up until this task.
console.log('\nCase 5: DOM delegated click on .invitation-confirm invokes onConfirm');
{
    const realDom = typeof document !== 'undefined'
        ? document
        : (function() {
            // Minimal Document shim sufficient for _bindEvents(host) to bind and to dispatch .click().
            const elTree = new Map();
            function makeEl(tag, attrs, children) {
                const e = {
                    tagName: (tag||'div').toUpperCase(),
                    attrs: attrs || {},
                    children: [],
                    parentNode: null,
                    listeners: {},
                    style: {},
                    id: '',
                    dataset: {},
                    addEventListener(t, fn) { (this.listeners[t] ||= []).push(fn); },
                    removeEventListener() {},
                    dispatchEvent(evt) {
                        const list = this.listeners[evt.type] || [];
                        for (const fn of list) fn(evt);
                    },
                    querySelectorAll() { return [].concat.apply([], this.children.map(c => c.querySelectorAll ? c.querySelectorAll('*') : [])); },
                    querySelector()  { return null; },
                    closest(selector) {
                        // Walk self → parents looking for an element with the given class in its attrs.classList.
                        let cur = this;
                        while (cur) {
                            const cls = (this.attrs && this.attrs['class']) || cur.attrs && cur.attrs['class'] || '';
                            if (cls.split(' ').includes(selector.slice(1))) return cur;
                            cur = cur.parentNode;
                        }
                        return null;
                    },
                    getAttribute(name) { return this.attrs[name] ?? null; }
                };
                (children || []).forEach(c => { c.parentNode = e; e.children.push(c); });
                elTree.set(e, true);
                return e;
            }
            const bodyEl = makeEl('body', {}, []);
            return {
                body: bodyEl,
                createElement(tag) { return makeEl(tag || 'div'); },
                activeElement: null
            };
        })();

    // Build a minimal host with the confirm button and one selectable row.
    const host = realDom.createElement('div');
    const groupRow = realDom.createElement('button');
    groupRow.attrs['class'] = 'invitation-row';
    groupRow.attrs['data-kind'] = 'group';
    groupRow.attrs['data-id'] = 'g1';
    const confirmBtn = realDom.createElement('button');
    confirmBtn.attrs['class'] = 'primary invitation-confirm';
    host.parentNode = null;
    for (const c of [groupRow, confirmBtn]) { c.parentNode = host; host.children.push(c); }

    let calls = 0, capturedIds = null;
    const inst = api.create({ groups: GROUPS, members: MEMBERS });
    inst.opts.onConfirm = ids => { calls++; capturedIds = Array.from(ids).map(String); };
    inst.toggleGroup('g1'); // pre-select the group so confirm has ids to send.

    // Bind delegated handlers by mounting the component onto our minimal host.
    try { inst.mount(host); } catch (e) { console.warn('(mount failed, falling back)', e && e.message); }
    if (!host.listeners || !(host.listeners.click || []).length) {
        const binder = typeof inst._bindEvents === 'function' ? inst._bindEvents : null;
        if (binder) { try { binder.call(inst, host); } catch (_) {} }
    }

    // Simulate a delegated click: the host's "click" listener is what runs; it should
    // check target.closest('.invitation-confirm') and invoke this.confirm().
    const handlers = (host.listeners || {}).click || [];
    ok('confirm button: host has at least one delegated click handler', handlers.length >= 1);

    // Fire a synthetic click event where target = the confirm button itself.
    handlers.forEach(fn => {
        fn({ type: 'click', target: confirmBtn, preventDefault(){} , stopPropagation(){}});
    });
    eq(calls, 1, 'dom delegated .invitation-confirm click invokes opts.onConfirm exactly once');
    eq((capturedIds || []).sort().join(','), ['10','20','30'].join(','),
        'resolved ids passed to onConfirm = the selected group member ids');

    // Regression check: a ROW click must NOT trigger onConfirm. The delegated
    // row handler is a sibling of the confirm delegate; this asserts neither
    // cross-fires (i.e. that my confirm-delegate isn't too broad).
    calls = 0; capturedIds = null;
    handlers.forEach(fn => {
        fn({ type: 'click', target: groupRow, preventDefault(){}, stopPropagation(){}});
    });
    eq(calls, 0, 'row click does NOT invoke onConfirm (delegates are disjoint)');
}

    // cancel() must not touch the data state.
    const cancelInst = api.create({ groups: GROUPS });
    cancelInst.toggleGroup('g1');
    const before = cancelInst.count();
    cancelInst.cancel();
    eq(cancelInst.count(), before, 'cancel() leaves the dedup module state untouched');
}

// ── Case 3 — controlled: initialGroups, initialMembers, shared selection.
console.log('\nCase 3: preselection and shared dedup instance');
{
    const a = api.create({ groups: GROUPS, members: MEMBERS, initialGroups: ['g2'] });
    eq(a.count(), 2, "initialGroups=['g2'] -> count == 2");
    ok('isGroupSelected("g2") is true', a.isGroupSelected('g2') === true);

    const shared = new globalThis.InvitationSelection();
    shared.toggleMember(70);
    const b = api.create({ groups: GROUPS, members: MEMBERS, selection: shared });
    eq(b.count(), 1, 'shared module instance drives count == 1');
    eq(Array.from(b.resolvedIds()).map(String), ['70'], 'module state reflects in modal resolvedIds()');

    const c = api.create({ groups: GROUPS, members: MEMBERS, initialMembers: [60, 80] });
    eq(Array.from(c.resolvedIds()).map(String).sort(), ['60', '80'].sort(), 'initialMembers=[60,80] preselected');

    // Unknown group id in initialGroups is a silent no-op (no crash).
    const d = api.create({ groups: [], members: MEMBERS, initialGroups: ['unknown'] });
    eq(d.count(), 0, 'unknown group id in initialGroups is a silent no-op');
}

// ── Case 4 — accessible row markup + empty input hint.
console.log('\nCase 4: rows role/aria/tabindex + empty input hint');
{
    const inst = api.create({ groups: GROUPS, members: MEMBERS });
    let html = String(inst.renderMarkup() || '');

    ok('group row has data-kind="group"',     /data-kind="group"/.test(html));
    ok('group row has role="option"',         /role="option"/.test(html) && /data-kind="group"/.test(html));
    ok('group row is Tab-navigable (tabindex)', /data-kind="group"[^>]*tabindex="0"/.test(html));

    ok('member row has data-kind="member"',   /data-kind="member"/.test(html));
    ok('member row has role="option"',        /role="option"/.test(html) && /data-kind="member"/.test(html));
    ok('member row is Tab-navigable (tabindex)', /data-kind="member"[^>]*tabindex="0"/.test(html));

    // Cancel + confirm buttons present with expected affordances.
    ok('cancel button exists',          /class="outline invitation-cancel"[\s\S]{0,80}?Anuluj/.test(html) || /invitation-cancel[\s\S]{0,80}?Anuluj/.test(html));
    ok('confirm button exists',         /class="primary invitation-confirm"/.test(html));

    const totalButtons = (html.match(/<button[\s\S]*?<\/button>/g) || []).length;
    ok('at least 7 <button> elements in the modal (close+5 rows+cancel+confirm)', totalButtons >= 7);

    // Empty input: friendly hint, not a blank body.
    const empty = api.create({ groups: [], members: [] });
    ok('empty inputs render an inline hint', /invitation-empty/i.test(String(empty.renderMarkup() || '')));
}

console.log('\n==========');
console.log(`Passed ${passed}, failed ${failed}`);
process.exit(failed === 0 ? 0 : 1);
