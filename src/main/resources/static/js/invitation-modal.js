/* invitation-modal.js — reusable "Zaproś" modal that unifies the old
 * "Zaproś grupę" + "Zaproś cz\u0142onka" UI into one <dialog>.
 *
 * Consumes window.InvitationSelection (see invitation-selection.js) for the
 * group/member dedup logic. The modal is a thin, controlled view over that
 * module: it renders two sections in ONE scrollable body (groups above
 * members), exposes a live "Wybierzono: N" footer counter, and calls back with
 * a resolved, deduplicated number[] via opts.onConfirm.
 *
 * Design choices (mapped to task acceptance criteria):
 *   1) Groups always render ABOVE members inside a single scroll area — the
 *      markup below emits a groups <section> then a members <section>; order is
 *      hard-coded, not data-driven.
 *   2) Real-time checkbox + count updates: every toggle FULLY RE-RENDERS the
 *      modal from its current Selection state. This guarantees the DOM always
 *      matches the dedup module under any environment (real browser, Node shim).
 *   3) Controlled component: create({groups,members,onConfirm,...}) returns an
 *      API object; the page owns its lifecycle. A helper open() exists to mount
 *      a one-shot modal from a single call.
 *   4) Accessible: rows are real <button role="option" aria-checked> elements
 *      focusable by Tab, toggleable by Space/Enter/click. Focus is moved to the
 *      first row when the modal opens, and Tab/Shift+Tab cycles inside a fixed
 *      ring of every focusable element in the dialog — standard WAI-ARIA dialog
 *      focus trap. Backdrop click (a click whose target is the dialog itself),
 *      Esc, the × button or "Anuluj" all close without touching the selection
 *      state.
 *   5) Testability: pure logic (renderMarkup, count, toggle*, confirm, cancel,
 *      resolvedIds) is callable from Node with just a minimal DOM shim; the
 *      browser-only event handlers degrade gracefully when their target methods
 *      are undefined. Same dual-export pattern as invitation-selection.js.
 *
 * Pre-selection in "initial state": optional initialGroups / initialMembers are
 * passed through the dedup module on construction so checkbox states + count
 * line up from the very first render.
 */
(() => {
    'use strict';

    // ── Small pure helpers ───────────────────────────────────────────────────
    function normalizeId(v) { return v == null ? '' : String(v); }

    function escapeHtml(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function memberCountWording(n) {
        const nn = Math.abs(Number(n || 0));
        if (nn === 1) return '1 cz\u0142onek';
        const last = nn % 10;
        const teen = Math.floor(nn / 10) % 10;
        // Polish rule: 2-4 (not 12-14) → "cz\u0142onk\u00f3w"; the rest → "cz\u0142onk\u00f3w".
        const plural = 'cz\u0142onk\u00f3w';
        return (nn || 0) + ' ' + plural;
    }

    function groupWording(n) {
        const nn = Math.abs(Number(n || 0));
        if (nn === 1) return 'grupa';
        const last = nn % 10;
        const teen = Math.floor(nn / 10) % 10;
        if (nn >= 2 && nn <= 4 && !(nn >= 12 && nn <= 14)) return 'grupy';
        return 'grup';
    }

    function initialsOf(name) {
        const parts = String(name || '').trim().split(/\s+/).filter(Boolean);
        if (parts.length === 0) return '?';
        if (parts.length === 1) return (parts[0].slice(0, 2) || '?').toUpperCase();
        return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    // ── Core stateful component ──────────────────────────────────────────────
    class InvitationModal {
        /**
         * @param {object} opts
         * @param {Array} [opts.groups]   [{id,name,memberIds[]}] — groups to render in section 1.
         * @param {Array} [opts.members]  [{id,name}]            — members for section 2.
         * @param {number|string|Array} [opts.initialGroups]     group ids preselected on open (optional).
         * @param {number|string|Array} [opts.initialMembers]    member ids preselected on open (optional).
         * @param {InvitationSelection} [opts.selection] shared state; defaults to a fresh one seeded from `groups`.
         * @param {function(number[]):void} [opts.onConfirm]     invoked on Confirm with resolvedMemberIds.
         * @param {function():void}            [opts.onCancel]   invoked before/after close (no side-effect on data).
         */
        constructor(opts) {
            this.opts = Object.assign({ groups: [], members: [] }, opts || {});

            // Pull the dedup module out of the current environment. Under Node
            // the global is set by invitation-selection.js's dual-export hook.
            const g = (typeof globalThis !== 'undefined' && globalThis.InvitationSelection) ||
                      (typeof window !== 'undefined' && window.InvitationSelection);
            if (!g) throw new Error('InvitationModal requires invitation-selection.js to be loaded first');

            // Build an up-front group-membership snapshot for the dedup module,
            // so toggling a group on/off reasons about coverage correctly from
            // the first toggle.
            const membership = {};
            for (const entry of this.opts.groups || []) {
                if (!entry) continue;
                const id = normalizeId(entry.id);
                if (!id) continue;
                membership[id] = Array.isArray(entry.memberIds) ? entry.memberIds : [];
            }
            this.selection = this.opts.selection || new g(membership);

            // Pre-selection.
            if (opts && opts.initialGroups) {
                const ids = Array.isArray(opts.initialGroups) ? opts.initialGroups : [opts.initialGroups];
                for (const gid of ids) {
                    const group = (this.opts.groups || []).find((x) => x && normalizeId(x.id) === normalizeId(gid));
                    const midList = (group && Array.isArray(group.memberIds)) ? group.memberIds : [];
                    this.selection.toggleGroup(normalizeId(gid), midList);
                }
            }
            if (opts && opts.initialMembers) {
                const ids = Array.isArray(opts.initialMembers) ? opts.initialMembers : [opts.initialMembers];
                for (const mid of ids) this.selection.toggleMember(mid);
            }

            this._host = null; // root element the modal is rendered into
            this._handlers = new Set();
        }

        // ── Public API (logic only — callable under Node with any DOM shim) ──
        _findGroup(gid) {
            const t = normalizeId(gid);
            return (this.opts.groups || []).find((x) => x && normalizeId(x.id) === t) || null;
        }

        toggleGroup(gid) {
            const g = this._findGroup(gid);
            this.selection.toggleGroup(normalizeId(gid), g && Array.isArray(g.memberIds) ? g.memberIds : []);
            this._refresh();
        }

        toggleMember(mid) { this.selection.toggleMember(mid); this._refresh(); }

        isGroupSelected(id) { return this.selection.isGroupSelected(normalizeId(id)); }
        isMemberSelected(id){ return this.selection.isMemberSelected(id); }

        count()          { return this.selection.getSelectedCount(); }
        resolvedIds()    { return this.selection.getResolvedIds(); }

        /** Pure: build the modal's HTML from opts + selection state. */
        renderMarkup() {
            const groups = (this.opts.groups || []).map((gR) => {
                if (!gR) return '';
                const id = normalizeId(gR.id);
                const memberCount = Array.isArray(gR.memberIds) ? gR.memberIds.length : 0;
                const checked = this.isGroupSelected(id);
                return (
                    `<button type="button" role="option"` +
                    ` class="invitation-row invitation-row--group"` +
                    ` data-kind="group" data-id="${escapeHtml(id)}"` +
                    ` aria-checked="${checked ? 'true' : 'false'}" tabindex="0">` +
                    `<span class="invitation-row__icon" aria-hidden="true">&#9834;</span>` +
                    `<strong class="invitation-row__label">${escapeHtml(gR.name || '')}</strong>` +
                    `<span class="invitation-row__badge">${memberCount} ${memberCountWording(memberCount)}</span>` +
                    `</button>`
                );
            }).join('\n');

            const members = (this.opts.members || []).map((m) => {
                if (!m) return '';
                const id = normalizeId(m.id);
                const checked = this.isMemberSelected(id);
                return (
                    `<button type="button" role="option"` +
                    ` class="invitation-row invitation-row--member"` +
                    ` data-kind="member" data-id="${escapeHtml(id)}"` +
                    ` aria-checked="${checked ? 'true' : 'false'}" tabindex="0">` +
                    `<span class="invitation-row__avatar" aria-hidden="true">${escapeHtml(initialsOf(m.name))}</span>` +
                    `<span class="invitation-row__label">${escapeHtml(m.name || '')}</span>` +
                    `</button>`
                );
            }).join('\n');

            const groupsN   = (this.opts.groups || []).length;
            const membersN  = (this.opts.members || []).length;
            const count     = this.count();
            const confirmDisabled = count === 0 ? 'disabled' : '';

            let body = '';
            if (groupsN > 0) {
                body += (
                    `<section class="invitation-section invitation-section--groups" role="group" aria-label="Grupy">\n` +
                    `  <h4 class="invitation-section__title">${memberCountWording(groupsN)} ${groupsN === 1 ? 'grupa' : 'grupy'}</h4>\n` +
                    `  <div class="invitation-list" role="listbox" aria-label="${groupsN} ${groupsN === 1 ? 'grupa' : 'grupy'}">\n${groups}\n  </div>\n` +
                    `</section>`
                );
            }
            if (membersN > 0) {
                body += (
                    `<section class="invitation-section invitation-section--members" role="group" aria-label="Cz\u0142onkowie">\n` +
                    `  <h4 class="invitation-section__title">${memberCountWording(membersN)}</h4>\n` +
                    `  <div class="invitation-list" role="listbox" aria-label="${membersN} ${groupWording(membersN)}">\n${members}\n  </div>\n` +
                    `</section>`
                );
            }
            if (!body) {
                body = `<p class="invitation-empty">Brak dost\u0119pnych grup ani cz\u0142onk\u00f3w.</p>`;
            }

            return (
                `<div class="invitation-modal" data-invitation-unified>\n` +
                `  <dialog id="invitation-unified-modal" class="app-modal">\n` +
                `    <div class="app-modal-content">\n` +
                `      <header class="app-modal-header invitation-header">\n` +
                `        <h3 class="invitation-title">Zapro\u015b</h3>\n` +
                `        <button type="button" class="app-modal-close invitation-close" data-close aria-label="Zamknij">&times;</button>\n` +
                `      </header>\n` +
                `      <div class="app-modal-body invitation-body">\n${body}\n</div>\n` +
                `      <footer class="app-modal-footer invitation-footer">\n` +
                `        <span class="invitation-count" aria-live="polite">Wybierzono: ${count}</span>\n` +
                `        <span class="invitation-actions">\n` +
                `          <button type="button" class="outline invitation-cancel" data-close>Anuluj</button>\n` +
                `          <button type="button" class="primary invitation-confirm" ${confirmDisabled} aria-disabled="${count === 0}">Potwierd\u017a</button>\n` +
                `        </span>\n` +
                `      </footer>\n` +
                `    </div>\n` +
                `  </dialog>\n` +
                `</div>`
            );
        }

        /** Mount the modal into hostElement (creates fresh DOM on every mount). */
        mount(hostElement) {
            const el = hostElement || this._env().createRoot();
            if (!el) throw new Error('No host element available to render the modal');
            if (typeof el.innerHTML !== 'undefined') el.innerHTML = this.renderMarkup();
            this._host = el;
            this._bindEvents(el);
            return this;
        }

        /** Full re-render when mounted, no-op when not. */
        _refresh() {
            if (!this._host) return;
            try {
                const inner = this._host.querySelector ? this._host.querySelector('.invitation-modal') : null;
                if (inner && typeof inner.outerHTML === 'string') {
                    // Replace the wrapper div's content in place — keeps our listener set intact.
                    this._host.innerHTML = this.renderMarkup();
                } else {
                    this._host.innerHTML = this.renderMarkup();
                }
            } catch (_) { /* ignore render errors under non-DOM runtimes */ }
        }

        // ── Confirm / cancel / destroy ───────────────────────────────────────
        confirm() {
            const n = this.count();
            if (n === 0) return false;
            const ids = this.resolvedIds();
            if (typeof this.opts.onConfirm === 'function') this.opts.onConfirm(ids);
            return true;
        }

        cancel() {
            if (this._host) this._removeHost();
            if (typeof this.opts.onCancel === 'function') this.opts.onCancel();
        }

        close() { this.cancel(); }

        destroy() {
            for (const h of this._handlers || []) {
                const t = h && h.type, el = h && h.el;
                if (el && typeof el.removeEventListener === 'function' && t) el.removeEventListener(t, h.fn);
            }
            this._handlers = new Set();
            this._removeHost();
        }

        _removeHost() {
            const host = this._host;
            if (!host) return;
            if (host.parentNode && typeof host.parentNode.removeChild === 'function') {
                try { host.parentNode.removeChild(host); } catch (_) {}
            }
            if (typeof host.innerHTML !== 'undefined') host.innerHTML = '';
            this._host = null;
        }

        // ── Focus-trap exposure for tests / callers. ─────────────────────────
        focusTrap() {
            const scope = this._host || ((typeof document !== 'undefined' && document.body) || null);
            if (!scope || typeof scope.querySelectorAll !== 'function') return [];
            const all = Array.from(scope.querySelectorAll('button, [href], input, select, textarea')) || [];
            return all.filter((el) => !(el.disabled || el.getAttribute('disabled') === 'true' || (el.getAttribute && el.getAttribute('aria-hidden') === 'true')));
        }

        // ── Internal: event binding (browser-only; safe to no-op under Node) ─
        _env() {
            const doc = (typeof document !== 'undefined') ? document : ((typeof globalThis !== 'undefined' && globalThis.__testDocument) || null);
            return {
                createRoot: () => doc && typeof doc.createElement === 'function' ? doc.createElement('div') : null,
                getDoc: () => doc
            };
        }

        _bindEvents(host) {
            const bind = (el, type, fn, opts) => {
                if (!el || typeof el.addEventListener !== 'function') return;
                el.addEventListener(type, fn, opts);
                this._handlers.add({ el, type, fn });
            };

            // Close buttons (× and "Anuluj").
            let closers = [];
            try { closers = Array.from(host.querySelectorAll('button[data-close]')) || []; } catch (_) {}
            for (const c of closers) bind(c, 'click', () => this.cancel());

            // Row clicks — delegated on the host so we survive every re-render.
            bind(host, 'click', (evt) => {
                const target = evt && evt.target;
                if (!target || typeof target.closest !== 'function') return;
                const row = target.closest('.invitation-row');
                if (!row) {
                    // Backdrop / dialog-level click: close without touching data state.
                    const dlg = (host.querySelector && host.querySelector('dialog')) || null;
                    if ((dlg && target === dlg) || target === host) this.cancel();
                    return;
                }
                this._handleRow(row);
            });

            // Keyboard: Escape closes, Tab traps, Space/Enter toggles.
            bind(host, 'keydown', (evt) => {
                const k = evt && evt.key;
                if (!k) return;

                if (k === 'Escape') {
                    if (evt && evt.preventDefault) evt.preventDefault();
                    this.cancel();
                    return;
                }
                if (k === 'Tab') {
                    const focusables = this.focusTrap();
                    if (!focusables.length) return;
                    const scopeActive = this._activeElement();
                    let idx = focusables.indexOf(scopeActive);
                    if (evt.preventDefault) evt.preventDefault();
                    const nextIdx = (idx < 0 ? 0 : idx + (evt.shiftKey ? -1 : 1)) % focusables.length;
                    if (nextIdx < 0) { nextIdx += focusables.length; }
                    const el = focusables[nextIdx];
                    if (el && typeof el.focus === 'function') el.focus();
                    return;
                }
                if (k === ' ' || k === 'Enter') {
                    const target = evt.target;
                    if (!target || typeof target.closest !== 'function') return;
                    const row = target.closest('.invitation-row');
                    if (!row) return;
                    if (evt.preventDefault) evt.preventDefault();
                    this._handleRow(row);
                }
            });
        }

        _handleRow(row) {
            const kind = row && row.getAttribute ? row.getAttribute('data-kind') : null;
            const idRaw = row && row.getAttribute ? row.getAttribute('data-id') : null;
            if (!kind || !idRaw) return;
            if (kind === 'group') this.toggleGroup(idRaw);
            else this.toggleMember(idRaw);
        }

        _activeElement() {
            if (typeof document !== 'undefined' && document.activeElement) return document.activeElement;
            const g = (typeof globalThis !== 'undefined') ? globalThis : null;
            return (g && g.__testActiveElement) || null;
        }
    }

    // ── Factory + exports ────────────────────────────────────────────────────
    function create(opts) { return new InvitationModal(opts || {}); }

    /**
     * One-shot convenience: mount a fresh modal into a host (or append to body).
     * Returns the same API object — callers can call .confirm() / .cancel() etc.
     */
    function open({ groups, members, onConfirm, onCancel, initialGroups, initialMembers, host } = {}) {
        const api = create({
            groups: groups || [], members: members || [],
            onConfirm: onConfirm || null, onCancel: onCancel || null,
            initialGroups: initialGroups || null, initialMembers: initialMembers || null
        });
        if (host) {
            api.mount(host);
        } else if ((typeof document !== 'undefined') && document.body && typeof document.createElement === 'function') {
            const h = document.createElement('div');
            document.body.appendChild(h);
            api.mount(h);
        }
        return api;
    }

    const root = (typeof globalThis !== 'undefined') ? globalThis : this;
    const api = { create, open, InvitationModal };
    if (!root.InvitationModal) root.InvitationModal = api;
    if ((typeof module !== 'undefined') && module.exports != null) module.exports = api;
})();
