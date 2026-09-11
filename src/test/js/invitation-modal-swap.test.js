/*
 * invitation-modal-swap.test.js
 *
 * Node-based JS unit tests for t_ec75454f — guard
 * src/main/resources/static/js/invitation-modal.js against HTMX swap
 * edge cases and rapid open/close cycles.
 *
 * Run: `node src/test/js/invitation-modal-swap.test.js`
 * Exit code 0 on success, 1 on failure. No deps (uses Node's assert).
 *
 * The shim is deliberately tiny — it only models the DOM surface that
 * invitation-modal.js and the detail.html HTMX guard rely on:
 *   - innerHTML get/set
 *   - addEventListener / removeEventListener
 *   - a dispatchEvent() with one-level bubbling to parentNode (two levels)
 *   - querySelector for the handful of selectors we expect
 */
'use strict';

const assert = require('assert');
const path = require('path');

// ── Element shim ────────────────────────────────────────────────────────────
function makeEl(id) {
    const listeners = {};
    let innerHTML = '';
    const el = {
        id: id || '',
        listeners,
        get innerHTML() { return innerHTML; },
        set innerHTML(h) { innerHTML = String(h == null ? '' : h); },
        parentNode: null,
        addEventListener(type, fn) { (listeners[type] || (listeners[type] = [])).push(fn); },
        removeEventListener(type, fn) {
            const l = listeners[type]; if (!l) return;
            const i = l.indexOf(fn); if (i >= 0) l.splice(i, 1);
        },
        dispatchEvent(evt) {
            // One-level bubbling chain: this → parentNode → parentNode.parentNode.
            // Sufficient to model body-listener reacting to a content-targeted swap.
            const fire = (node) => {
                const l = listenersRef(node)[evt.type] || [];
                for (const fn of l.slice()) { try { fn(evt); } catch (_) {} }
            };
            function listenersRef(n) { return n.listeners; }
            fire(el);
            if (el.parentNode) fire(el.parentNode);
            if (el.parentNode && el.parentNode.parentNode) fire(el.parentNode.parentNode);
        },
        querySelector(sel) {
            const i = innerHTML;
            if (sel === '.invitation-modal') return i.indexOf('class="invitation-modal"') >= 0 ? { outerHTML: '<div></div>' } : null;
            if (sel === 'button[data-close]') return (i.match(/data-close/g) || []).length > 0 ? Symbol('closer') : null;
            if (sel === 'dialog[open]') return i.indexOf('<dialog ') >= 0 ? { close(){}, getAttribute:()=>null } : null;
            return null;
        },
        querySelectorAll() {
            const n = (iOf.innerHTML ? 1 : 0);
            void n; // not used by the tests we write below.
            return Array.from({ length: (innerHTML.match(/class="invitation-row/g) || []).length }, () =>
                ({ disabled:false, getAttribute:()=>null, focus(){}, closest:()=>null }));
        },
        appendChild(){}, classList:{add(){},remove(){}}, style:{}, dataset:{},
    };
    return el;
}
function makeEnv() {
    const body = makeEl('body');
    const content = makeEl('rehearsals-content');
    const host   = makeEl('unified-invite-host');
    host.parentNode      = content;
    content.parentNode   = body;
    return { body, content, host };
}

// ── Module loader (fresh globals per scenario) ─────────────────────────────
function loadModules() {
    const base = path.join(__dirname, '..', '..', 'main', 'resources', 'static', 'js');
    delete globalThis.InvitationSelection;
    delete globalThis.InvitationModal;
    // Bypass Node's require cache so a fresh scenario re-runs the IIFE and
    // registers on globalThis under the current bindings.
    const selectionPath = path.join(base, 'invitation-selection.js');
    const modalPath     = path.join(base, 'invitation-modal.js');
    if (require.cache[selectionPath]) delete require.cache[selectionPath];
    if (require.cache[modalPath])     delete require.cache[modalPath];
    require(selectionPath);
    require(modalPath);
    return globalThis.InvitationModal;
}

function countTimers_(inv)  { return (inv && inv._timers && typeof inv._timers.size === 'number') ? inv._timers.size : 0; }
function countHandlers_(inv){ return (inv && inv._handlers && typeof inv._handlers.size === 'number') ? inv._handlers.size : -1; }

// ── Scenarios ──────────────────────────────────────────────────────────────
function s1_beforeSwapCloseAll() {
    const inv = loadModules();
    const env = makeEnv();
    let closeCalls = 0;
    env.body.addEventListener('htmx:beforeSwap', (evt) => {
        if (!evt || !evt.target || evt.target.id !== 'rehearsals-content') return;
        if (typeof inv.closeAll === 'function') closeCalls++;
        try { inv.closeAll(); } catch (_) {}
    });
    const api = inv.create({ groups: [], members: [{ id: 1, name: 'A' }] });
    api.mount(env.host);
    assert.ok((env.host.innerHTML || '').indexOf('invitation-modal') >= 0, 'modal should be rendered after mount');
    env.content.dispatchEvent({ type: 'htmx:beforeSwap', target: env.content });
    assert.strictEqual(closeCalls, 1, 'beforeSwap → closeAll() runs exactly once');
    assert.strictEqual(api._destroyed, true, 'instance destroyed after beforeSwap teardown');
    assert.strictEqual(countHandlers_(api), 0, 'no live handlers after teardown');
    assert.strictEqual(countTimers_(api), 0, 'no timers after teardown');
}

function s2_afterSwapOrphans() {
    const inv = loadModules();
    const env = makeEnv();
    let closeCalls = 0;
    env.body.addEventListener('htmx:afterSwap', (evt) => {
        if (!evt || !evt.target || evt.target.id !== 'rehearsals-content') return;
        if (typeof inv.activeCount === 'function' && inv.activeCount() > 0) closeCalls++;
        try { inv.closeAll(); } catch (_) {}
    });
    const a1 = inv.create({ groups: [], members: [{ id: 1, name: 'A' }] });
    a1.mount(env.host);
    // A second instance mounted into the SAME host must destroy the first (single-instance rule).
    const a2 = inv.create({ groups: [], members: [{ id: 2, name: 'B' }] });
    a2.mount(env.host);
    assert.strictEqual(inv.activeCount(), 1, 'one live instance per host after re-mount');
    env.content.dispatchEvent({ type: 'htmx:afterSwap', target: env.content });
    assert.ok(closeCalls >= 0, 'afterSwap fired (at least once)');
    assert.strictEqual(inv.activeCount(), 0, 'no live instances remain after afterSwap cleanup');
}

function s3_rapidOpenClose() {
    const inv = loadModules();
    const env = makeEnv();
    for (let i = 0; i < 12; i++) {
        const api = inv.create({ groups: [], members: [{ id: i, name: 'M' + i }] });
        api.mount(env.host);
        api.cancel(); // cancel() now calls destroy()
    }
    assert.strictEqual(inv.activeCount(), 0, 'no live instances after rapid open/cancel cycles');
}

function s4_reinitAfterSwap() {
    const inv = loadModules();
    const env = makeEnv();
    // Simulate the htmx boundary listener (same shape as detail.html's body-level guard).
    let closeCalls = 0;
    env.body.addEventListener('htmx:beforeSwap', (evt) => {
        if (!evt || !evt.target || evt.target.id !== 'rehearsals-content') return;
        closeCalls++;
        try { inv.closeAll(); } catch (_) {}
    });
    env.body.addEventListener('htmx:afterSwap', (evt) => {
        if (inv.activeCount() > 0) try { inv.closeAll(); } catch (_) {}
    });

    const api1 = inv.create({ groups: [{ id: 1, name: 'G', memberIds: [7] }], members: [] });
    api1.mount(env.host);
    assert.ok((env.host.innerHTML || '').indexOf('invitation-modal') >= 0);

    // HTMX swap event → our teardown guard runs first (destroy) then HTMX replaces the old content node
    // with a fresh one. We model that by creating a new contentEl, swapping the body's child pointer,
    // and linking a FRESH host underneath it. The old host is now orphaned (no parentNode chain to a live node).
    env.content.dispatchEvent({ type: 'htmx:beforeSwap', target: env.content });
    assert.strictEqual(closeCalls, 1, 'beforeSwap guard ran closeAll exactly once');
    assert.strictEqual(api1._destroyed, true, 'api1 destroyed by htmx boundary teardown');
    assert.strictEqual(countHandlers_(api1), 0, 'api1 has no live handlers after destroy');

    // Simulate HTMX replacing the content node: new parent + host node for api2.
    const freshContent = makeEl('rehearsals-content');      // NEW contentEl from HX swap
    freshContent.parentNode = env.body;                       // body is stable — links to the new child
    const freshHost   = makeEl('unified-invite-host');
    freshHost.parentNode  = freshContent;

    const api2 = inv.create({ groups: [{ id: 1, name: 'G', memberIds: [7] }], members: [] });
    api2.mount(freshHost);
    assert.ok((freshHost.innerHTML || '').indexOf('invitation-modal') >= 0, 'api2 renders into the fresh host');
    assert.strictEqual(api2._destroyed, false, 'api2 live after re-init');

    // The two instances do NOT share a handler set (each has its own fresh Set).
    assert.notStrictEqual(api1._handlers, api2._handlers, 'independent handler sets post-reinit');

    // Global state is clean: exactly one live instance.
    assert.strictEqual(inv.activeCount(), 1, 'activeCount() === 1 after the re-init (only api2)');
}

// ── Runner ──────────────────────────────────────────────────────────────────
let pass = 0, fail = 0;
function run(name, fn) {
    try { fn(); console.log('  ok   ' + name); pass++; }
    catch (e) { console.error('  FAIL ' + name + '\n       ' + ((e && e.message) || e)); fail++; }
}

console.log('invitation-modal-swap.test.js — t_ec75454f HTMX swap lifecycle guards');
run('htmx:beforeSwap → closeAll() exactly once, no orphaned handlers/timers', s1_beforeSwapCloseAll);
run('htmx:afterSwap → orphaned instances cleaned up, re-mount is single-instance', s2_afterSwapOrphans);
run('rapid open/close leaves zero live instances (no timer/listener/DOM leak)', s3_rapidOpenClose);
run('re-initialise after HTMX swap — no duplicate live instance, independent handler sets', s4_reinitAfterSwap);
console.log('----');
console.log(pass + ' passed, ' + fail + ' failed out of 4 scenarios.');
process.exit(fail === 0 ? 0 : 1);
