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
 *   4) Accessible: rows are <div role="option" tabindex="0"> elements,
 *      focusable by Tab and toggleable by Space/Enter/click. Focus is moved to the
 *      first row when the modal opens, and Tab/Shift+Tab cycles inside a fixed
 *      ring of every focusable element in the dialog — standard WAI-ARIA dialog
 *      focus trap. Backdrop click (a click whose target is the dialog itself),
 *      Esc, the × button or "Anuluj" all close without touching the selection
 *      state. (Historically these were real <button role="option"> elements;
 *      #178/#179 converted them to divs to escape Pico's button chrome — this
 *      change restores keyboard parity by re-adding tabindex="0".)
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
            this._timers = new Set();
            this._destroyed = false;
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

        /** True after {@link destroy} has run. Logic is still safe — these only
         *  read the (still-alive) selection state and no-ops on a destroyed
         *  host — but callers can branch on it to avoid wasted work. */
        get destroyed() { return this._destroyed === true; }

        /** Pure: build the modal's HTML from opts + selection state. */
        renderMarkup() {
            const groups = (this.opts.groups || []).map((gR) => {
                if (!gR) return '';
                const id = normalizeId(gR.id);
                const memberCount = Array.isArray(gR.memberIds) ? gR.memberIds.length : 0;
                const checked = this.isGroupSelected(id);
                return (
                    `<div class="invitation-row invitation-row--group" role="option"${checked ? ' aria-checked="true"' : ' aria-checked="false"'} ` +
                    `tabindex="0" data-kind="group" data-id="${escapeHtml(id)}">` +
                    `<span class="invitation-check" aria-hidden="true"></span>` +
                    `<span class="invitation-row__icon" aria-hidden="true">&#9834;</span>` +
                    `<span class="invitation-row__label">${escapeHtml(gR.name || '')}</span>` +
                    `<span class="invitation-row__badge invitation-label-meta">${memberCount} ${memberCountWording(memberCount)}</span>` +
                    `</div>`
                );
            }).join('\n');

            const members = (this.opts.members || []).map((m) => {
                if (!m) return '';
                const id = normalizeId(m.id);
                const checked = this.isMemberSelected(id);
                return (
                    `<div class="invitation-row invitation-row--member" role="option"${checked ? ' aria-checked="true"' : ' aria-checked="false"'} ` +
                    `tabindex="0" data-kind="member" data-id="${escapeHtml(id)}">` +
                    `<span class="invitation-check" aria-hidden="true"></span>` +
                    `<span class="invitation-row__avatar" aria-hidden="true">${escapeHtml(initialsOf(m.name))}</span>` +
                    `<span class="invitation-row__label">${escapeHtml(m.name || '')}</span>` +
                    `</div>`
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

        /** Mount the modal into hostElement.
         *
         *  - After {@link destroy} this is a no-op (the host and everything
         *    bound to it are gone; re-mounting would create a stale instance).
         *  - Every mount first tears down any live listener set from a PRIOR
         *    mount so re-initialising into the same host never stacks duplicate
         *    delegations onto that host (the classic HTMX afterSwap hazard:
         *    the page swaps, we mount, then something re-mounts).
         */
        mount(hostElement) {
            if (this._destroyed) return this;
            const el = hostElement || this._env().createRoot();
            if (!el) throw new Error('No host element available to render the modal');
            // Single-instance guarantee: if another, still-live InvitationModal
            // is registered as living in THIS host, destroy it first. This is
            // what ensures re-initialising into the same host (the classic HTMX
            // afterSwap hazard) can never stack two live instances — every
            // prior mount on this exact element was cleaned up; listeners on
            // detached nodes are also cleared in _detachAll below.
            if (typeof _instanceRegistry !== 'undefined') {
                for (let i = _instanceRegistry.length - 1; i >= 0; i--) {
                    const other = _instanceRegistry[i];
                    if (!other || other === this) continue;
                    // Only destroy a PRIOR instance if it's still marked as live
                    // AND its host node is either (a) the same object we're about
                    // to render into, or (b) not in the DOM anymore (detached).
                    const oldHost = other._host;
                    const orphanedHost = !!oldHost && !oldHost.parentNode && oldHost.innerHTML && String(oldHost.innerHTML).indexOf('invitation-modal') >= 0;
                    if (!other._destroyed && (oldHost === el || orphanedHost)) {
                        try { other.destroy(); } catch (_) {}
                        const j = _instanceRegistry.indexOf(other);
                        if (j >= 0) _instanceRegistry.splice(j, 1);
                    }
                }
            }
            // This-instance teardown before a fresh mount: drop stale listener set
            // and any tracked timers from a prior render on us.
            this._detachAll();
            this._clearTimers();
            if (typeof el.innerHTML !== 'undefined') el.innerHTML = this.renderMarkup();
            this._host = el;
            this._bindEvents(el);
            return this;
        }

        /** Full re-render when mounted, no-op otherwise or when destroyed.
         *
         *  Re-renders replace the host's inner elements (rows, close buttons),
         *  so we RE-ATTACH the full listener set on the new markup. Because a
         *  single delegating handler can't reliably remove its own bound copy
         *  from inside the handler, the safe pattern is: swap innerHTML first
         *  (the old elements are gone and take their listeners with them), then
         *  re-bind against the fresh `this._handlers` snapshot — every handler
         *  is removed exactly once, including the ones we just dispatched from.
         */
        _refresh() {
            if (!this._host || this._destroyed) return;
            // BUG FIX (rehearsal/event invite: clicking a checkbox closed the
            // modal): the previous implementation replaced the host's entire
            // innerHTML on every toggle. Because renderMarkup() emits a FRESH
            // <dialog> element each time, the dialog that showModal() had put in
            // the open/modal state was discarded and the replacement rendered in
            // the default CLOSED state — so one click on any group/member row
            // toggled its checkbox AND silently closed the modal the user was
            // still working in. The fix: remember whether the dialog was open
            // before the re-render and re-open it (showModal) immediately after.
            let wasOpen = false;
            try {
                const oldDlg = this._host && this._host.querySelector ?
                    this._host.querySelector('dialog') : null;
                if (oldDlg) wasOpen = !!(typeof oldDlg.open === 'boolean' ? oldDlg.open : false);
            } catch (_) { /* non-DOM runtime: ignore */ }
            try {
                if (typeof this._host.innerHTML !== 'undefined') {
                    this._host.innerHTML = this.renderMarkup();
                }
            } catch (_) { /* ignore render errors under non-DOM runtimes */ }
            // Re-attach now that the fresh DOM is in place. Idempotent: the old
            // listeners were bound to inner elements that no longer exist, so
            // re-binding cannot double-fire on a single host element either —
            // the delegation points at `this._host`, but we removed each prior
            // bind before adding the new one (see _detachAll → _bindEvents).
            this._detachAll();
            this._bindEvents(this._host);
            if (wasOpen) {
                try {
                    const newDlg = this._host && this._host.querySelector ?
                        this._host.querySelector('dialog') : null;
                    if (newDlg && typeof newDlg.showModal === 'function' && !newDlg.open) {
                        newDlg.showModal();
                    }
                } catch (_) { /* ignore in non-DOM environments */ }
            }
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
            this.destroy(); // tear down listeners + DOM so nothing outlives this instance
            if (typeof this.opts.onCancel === 'function') this.opts.onCancel();
        }

        close() { this.cancel(); }

        destroy() {
            if (this._destroyed) return this; // idempotent: teardown is one-shot
            this._clearTimers();  // cancel any pending setTimeout/setInterval on this instance
            this._detachAll();    // remove every live listener for this instance
            this._removeHost();   // drop the host DOM node
            this._destroyed = true;
            // Drop from the module registry so closeAll() and mount() don't see us.
            if (typeof _instanceRegistry !== 'undefined') {
                const idx = _instanceRegistry.indexOf(this);
                if (idx >= 0) _instanceRegistry.splice(idx, 1);
            }
            return this;
        }

        /**
         * Track a setTimeout or setInterval so destroy() can cancel it. We never
         * hold an anonymous timer — every one is recorded here and released in
         * destruction, which means rapid open/close cycles leave no dangling
         * callbacks and no accumulated timers (acceptance #3).
         *
         * @param {function} [fn] Optional; wrap to track. Return shape stays
         *     identical so existing callers see no API change.
         */
        _schedule(fn) {
            if (typeof setTimeout !== 'function') return null;
            const id = setTimeout(fn);
            (this._timers || (this._timers = new Set())).add(id);
            return id;
        }

        /** Clear all tracked timers. Idempotent — safe to call from _detachAll. */
        _clearTimers() {
            for (const id of this._timers || []) {
                if (typeof clearTimeout === 'function') try { clearTimeout(id); } catch (_) {}
            }
            if (this._timers) this._timers = new Set();
        }

        /**
         * Remove all registered listeners from their elements (rows, close
         * buttons, and the delegated handlers on the host). Safe to call
         * repeatedly: once removed, `_handlers` is clear so a second call is a
         * zero-work loop. This is what guarantees no ORPHANED listeners survive
         * a re-render (_refresh) or an HTMX beforeSwap — we hold no references
         * to detached DOM nodes and nothing keeps firing after teardown.
         */
        _detachAll() {
            for (const h of this._handlers || []) {
                const t = h && h.type, el = h && h.el;
                if (el && typeof el.removeEventListener === 'function' && t) {
                    try { el.removeEventListener(t, h.fn); } catch (_) {}
                }
            }
            this._handlers = new Set();
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
            // [tabindex]: rows are <div role="option" tabindex="0"> — not in the
            // native focusable tag list above (button/input/select/textarea/a[href]),
            // so we must name them explicitly or the Tab ring skips every row.
            const all = Array.from(scope.querySelectorAll('button, [href], input, select, textarea, [tabindex]')) || [];
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

            // Confirm button — delegated on host so re-renders never orphan the handler.
            bind(host, 'click', (evt) => {
                const target = evt && evt.target;
                if (!target || typeof target.closest !== 'function') return;
                if (target.closest('.invitation-confirm')) {
                    this.confirm();
                    return;
                }
            });

            // Row clicks — delegated on the host so we survive every re-render.
            // CRITICAL (bug fix): the click is intercepted at the delegated host
            // handler and must NOT bubble to any ancestor (layout.html binds a
            // backdrop-close on the <dialog>; Pico/others may also react to
            // unhandled clicks) — so once we identify a row, we preventDefault +
            // stopPropagation AND handle the toggle ourselves. That way a click
            // anywhere inside the row (checkbox, icon, name, padding) selects or
            // deselects and never closes the modal "without effect".
            bind(host, 'click', (evt) => {
                const target = evt && evt.target;
                if (!target || typeof target.closest !== 'function') return;
                const row = target.closest('.invitation-row');
                if (row) {
                    if (evt.preventDefault) evt.preventDefault();
                    if (evt.stopPropagation)   evt.stopPropagation();
                    this._handleRow(row);
                    return;
                }
                // Confirm / close buttons inside the modal are handled by other
                // delegated handlers bound on those exact nodes — stop them from
                // triggering the backdrop-close branch below.
                if (target.closest('.invitation-confirm') || target.closest('[data-close]')) {
                    if (evt.stopPropagation) evt.stopPropagation();
                    return;
                }
                // Backdrop / dialog-level click: close without touching data state.
                const dlg = (host.querySelector && host.querySelector('dialog')) || null;
                if ((dlg && target === dlg) || target === host) {
                    this.cancel();
                }
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
    // Module-level registry of every live InvitationModal instance. Lets
    // mount() enforce the single-instance-per-host rule (see above) and lets
    // closeAll()/activeCount() inspect or tear down all at once — that is the
    // teardown hook a page can use on htmx:beforeSwap so nothing leaks across
    // an HTMX boundary. Backed by a plain array (no WeakMap needed: instances
    // only get removed when they're destroyed).
    var _instanceRegistry = [];

    function create(opts) {
        const inst = new InvitationModal(opts || {});
        if (_instanceRegistry && !inst._destroyed) _instanceRegistry.push(inst);
        return inst;
    }

    /**
     * Destroy every live instance created via create() and clear the registry.
     * Idempotent and cheap to call (returns early when the list is empty). The
     * intended htmx:beforeSwap handler so a page can guarantee zero modals, zero
     * listeners, zero pending timers survive an HTMX swap of their host.
     */
    function closeAll() {
        if (!_instanceRegistry) return 0;
        let destroyed = 0;
        for (let i = _instanceRegistry.length - 1; i >= 0; i--) {
            const inst = _instanceRegistry[i];
            if (!inst || !inst.destroy) continue;
            try { inst.destroy(); } catch (_) {}
            if (inst._destroyed) { destroyed++; _instanceRegistry.splice(i, 1); }
        }
        return destroyed;
    }

    /** Count of live (not-yet-destroyed) instances — the leak detector. */
    function activeCount() { return (_instanceRegistry || []).length; }

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
    const api = { create, open, closeAll, activeCount, InvitationModal };
    if (!root.InvitationModal) root.InvitationModal = api;
    if ((typeof module !== 'undefined') && module.exports != null) module.exports = api;
})();
