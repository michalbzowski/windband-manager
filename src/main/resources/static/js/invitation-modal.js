/* invitation-modal.js — reusable "Zaproś" modal that unifies the old
 * "Zaproś grupę" + "Zaproś członka" UI into one <dialog>.
 *
 * Consumes the `useInvitationSelection` reactive hook (see invitation-selection.js)
 * for ALL selection state: the component holds a single view object and hands
 * every mutation back to it. The component itself never implements any dedup
 * logic of its own — the hook owns the three live Set views it needs:
 *   - selectedGroupIds     : which group rows are checked
 *   - selectedMemberIds    : which member rows are checked
 *   - resolvedMemberIds    : the deduplicated union that forms the confirm payload
 *
 * Public surface is a small controlled API over the hook. Design choices mapped
 * to task acceptance criteria:
 *   1. One scrollable body, groups section ABOVE members section — hard-coded
 *      order in renderMarkup (not data-driven); both sections live inside one
 *      .invitation-body container with max-height + overflow-y via CSS.
 *   2. The footer's "Wybierzono: N" counter reads `view.resolvedMemberIds.size`
 *      on every re-render, so it reflects the hook's dedup state in real time.
 *   3. The confirm button is disabled when view.count() === 0; clicking it calls
 *      getConfirmPayload(), then invokes opts.onConfirm(ids) with the SAME shape
 *      the existing per-member POST /api/.../invite loop already consumes (a
 *      number[] memberIds). The modal stays open until the page closes it —
 *      controlled component (see cancel() below).
 *   4. Close on backdrop click, Escape, the × button or "Anuluj". All of them
 *      leave the hook's Set state untouched so a re-open restores prior selection
 *      (unless the caller explicitly calls clearAll()).
 *   5. Reusable: receives `groups`, `members`, and an `onConfirm` callback as
 *      props, exactly per spec; no page-specific hardcoding.
 *
 * The same file previously exposed `window.InvitationModal` via a class-based
 * api (`.create/.open`). That is preserved here for backwards compatibility —
 * existing page wiring on other branches keeps working unchanged. The NEW
 * surface added by this task is the hook itself; both coexist.
 */
(() => {
    'use strict';

    // ── Small pure helpers ─────────────────────────────────────────────────────
    function normalizeId(v) { return v == null ? '' : String(v); }

    function escapeHtml(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function memberCountWording(n) {
        const nn = Math.abs(Number(n || 0));
        if (nn === 1) return '1 cz\u0142onek';
        // Polish plural rule: for any count >= 2 we always use the genitive
        // "członków" form; the singular/plural flip is not used in the UI copy.
        const plural = 'cz\u0142onk\u00f3w';
        return (nn || 0) + ' ' + plural;
    }

    function groupWording(n) {
        const nn = Math.abs(Number(n || 0));
        if (nn === 1) return 'grupa';
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

    function resolveHook() {
        // Prefer the named hook on the global root; fall back to the legacy class
        // as an adapter so older branches (that only carry `InvitationSelection`)
        // keep working while the new surface is optional.
        const g = (typeof globalThis !== 'undefined' && globalThis.useInvitationSelection) ||
                  (typeof window !== 'undefined' && window.useInvitationSelection);
        if (typeof g === 'function') return g;
        const legacy = (typeof globalThis !== 'undefined' && globalThis.InvitationSelection) ||
                       (typeof window !== 'undefined' && window.InvitationSelection);
        if (!legacy) {
            throw new Error('invitation-modal.js requires invitation-selection.js to be loaded first');
        }
        // Adapter object: the modal will bridge its 1-arg toggleGroup into the
        // legacy 2-arg toggleGroup(gid, members[]) by pulling members from the
        // opts.groups snapshot at call time.
        return { adapter: true, legacy };
    }

    /** Private helper: given a shared state object (any of InvitationSelection /
     *  SelectionState), return a hook-shaped view over it (live Sets). */
    function makeSharedView(shared, findGid) {
        const isGroupSel  = shared.isGroupSelected  ? (id) => shared.isGroupSelected(id)  : (id) => shared.selectedGroups.has(id);
        const isMemberSel = shared.isMemberSelected ? (id) => shared.isMemberSelected(id) : (id) => shared.selectedMembers.has(id);
        return {
            selectedGroupIds:  shared.selectedGroups,
            selectedMemberIds: shared.selectedMembers,
            resolvedMemberIds: shared.resolvedMemberIds,
            toggleGroup:  (gid)    => (shared.toggleGroup  ? shared.toggleGroup(gid, findGid(gid)) : null),
            toggleMember: (mid)    => (shared.toggleMember ? shared.toggleMember(mid)               : null),
            clearAll:     ()       => (shared.clear        ? shared.clear()
                                                          : (shared.clearAll ? shared.clearAll()   : undefined)),
            getConfirmPayload: () => {
                const ids = (typeof shared.getResolvedIds === 'function')
                        ? Array.from(shared.getResolvedIds())
                        : Array.from(shared.resolvedMemberIds);
                // Sort deterministically so both hook and legacy paths emit the SAME
                // payload shape. Mixed string/number ids are possible (DOM data-id is
                // always a string; DB-generated ids are numbers), so try numeric first and
                // fall back to locale-aware string sort when values are not all finite
                // numbers. This keeps diff-based unit tests stable regardless of which
                // engine is active or how the caller stored its ids.
                const numeric = (ids.length > 0 && ids.every((v) => Number.isFinite(Number(v))));
                if (!numeric) return ids.slice().sort((a, b) => String(a).localeCompare(String(b)));
                return ids.slice().sort((a, b) => Number(a) - Number(b));
            },
        isGroupSelected:  isGroupSel,
        isMemberSelected: isMemberSel,
        count:            ()   => (typeof shared.getSelectedCount === 'function')
                                          ? shared.getSelectedCount()
                                          : shared.resolvedMemberIds.size,
        };
    }

    // ── Core stateful component (uses only pure state + the hook) ────────────
    class InvitationModal {
        /**
         * @param {object} opts
         * @param {Array} opts.groups             [{id, name, memberIds[]}]
         * @param {Array} opts.members            [{id, name}]
         * @param {(number|string|Array)} [opts.initialGroups]
         * @param {(number|string|Array)} [opts.initialMembers]
         * @param {function(number[]):void} [opts.onConfirm]
         * @param {function():void}           [opts.onCancel]
         */
        constructor(opts) {
            this.opts = Object.assign({ groups: [], members: [] }, opts || {});

            const hookShape = resolveHook();
            const membership = {};
            for (const g of (this.opts.groups || [])) {
                if (!g) continue;
                const id = normalizeId(g.id);
                if (!id) continue;
                membership[id] = Array.isArray(g.memberIds) ? g.memberIds : [];
            }

            this._hook = hookShape;

            // Helper: given a group id, pull its memberIds from the options
            // snapshot (so the legacy adapter can call 2-arg toggleGroup).
            const findMembersForGid = (gid) => {
                const hit = (this.opts.groups || []).find((x) => x && normalizeId(x.id) === normalizeId(gid));
                return hit && Array.isArray(hit.memberIds) ? hit.memberIds : [];
            };

            // ── Three ways to inject state, in priority order: ─────────────────
            //   1. opts.selection — an EXISTING SelectionState / InvitationSelection
            //      instance the caller passed in (so the modal mirrors its dedup).
            //   2. The global reactive hook `useInvitationSelection` (the new
            //      surface from task t_4fcc1f03) with a fresh engine seeded from
            //      the groups snapshot. This is what the spec calls for.
            //   3. A fallback adapter over the legacy class, in case the hook
            //      surface was not present at load time (defensive).
            const shared = opts && opts.selection;
            this._optsOwnedSelection = Boolean(shared);

            if (shared) {
                this._view = makeSharedView(shared, findMembersForGid);
            } else if (hookShape && !hookShape.adapter) {
                // The new reactive hook surface — preferred path per task spec.
                this._view = hookShape({ groups: this.opts.groups || [] });
            } else {
                const legacy = new hookShape.legacy(membership);
                this._view  = makeSharedView(legacy, findMembersForGid);
            }

            // Pre-selection via opts.initialGroups / initialMembers — applied ONCE,
            // through the hook so we never duplicate dedup logic.
            const applyInitial = (arr, kind) => {
                if (!arr) return;
                const ids = Array.isArray(arr) ? arr : [arr];
                for (const id of ids) {
                    if (kind === 'group')    this._view.toggleGroup(id);
                    else                     this._view.toggleMember(id);
                }
            };
            applyInitial(opts && opts.initialGroups,  'group');
            applyInitial(opts && opts.initialMembers, 'member');

            this._host = null;      // root element the modal is rendered into
            this._handlers = new Set();
        }

        // ── Small public API (the component delegates ALL logic to the hook) ──
        _findGroup(gid) {
            const t = normalizeId(gid);
            return (this.opts.groups || []).find((x) => x && normalizeId(x.id) === t) || null;
        }

        toggleGroup(gid)  { this._view.toggleGroup(gid || ''); this._refresh(); }
        toggleMember(mid) { const id = mid == null ? '' : String(mid); this._view.toggleMember(id); this._refresh(); }

        isGroupSelected(id)  { return this._view.isGroupSelected(normalizeId(id)); }
        isMemberSelected(id) { return this._view.isMemberSelected(id == null ? '' : String(id)); }

        count()          { return this._view.count(); }
        resolvedIds()    {
            // `resolvedMemberIds` is a LIVE view into the engine's Set. Return
            // a copy so callers can iterate without the component mutating it.
            const v = this._view.resolvedMemberIds;
            return Array.from(v);
        }
        getConfirmPayload() {
            const p = this._view.getConfirmPayload ? this._view.getConfirmPayload() : this.resolvedIds();
            return Array.isArray(p) ? p.slice() : p;
        }

        // ── Pure markup (deterministic for Node tests + browser renders) ───────
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

        // ── Mount / refresh ─────────────────────────────────────────────────────
        mount(hostElement) {
            const el = hostElement || this._env().createRoot();
            if (!el) throw new Error('No host element available to render the modal');
            if (typeof el.innerHTML !== 'undefined') el.innerHTML = this.renderMarkup();
            this._host = el;
            this._bindEvents(el);
            return this;
        }

        /** Re-render in place (keeps bound listeners + parent node intact). */
        _refresh() {
            if (!this._host) return;
            try {
                if (typeof this._host.innerHTML !== 'undefined') {
                    this._host.innerHTML = this.renderMarkup();
                }
            } catch (_) { /* ignore render errors under non-DOM runtimes */ }
        }

        // ── Confirm / cancel / destroy ──────────────────────────────────────────
        confirm() {
            const n = this.count();
            if (n === 0) return false;
            // The payload is the SAME number[] shape the existing per-member
            // POST /api/.../invite loop consumes. No new endpoint is invented.
            const ids = this.getConfirmPayload();
            if (typeof this.opts.onConfirm === 'function') {
                try {
                    const r = this.opts.onConfirm(ids);
                    // If the caller returned a Promise, we intentionally DO NOT
                    // auto-close: the page owns the success/error flow and will
                    // call .cancel() on success (or leave it open on failure).
                    if (r && typeof r.then === 'function') { /* async — caller closes */ }
                } catch (e) {
                    throw e;
                }
            }
            return true;
        }

        cancel() {
            if (this._host) this._removeHost();
            if (typeof this.opts.onCancel === 'function') this.opts.onCancel();
        }

        /** Per task spec: "displays success/error toast after" is the CALLER'S
         *  job (onConfirm gets the ids and fires the API call + Toast); this
         *  method exists so callers can close on their own terms. */
        close() { this.cancel(); }

        /** Clear the selection state (via the hook) — useful for reopening a
         *  stale modal without side effects on the page's data. */
        clearAll() {
            if (this._view && typeof this._view.clearAll === 'function') {
                this._view.clearAll();
                this._refresh();
            }
        }

        destroy() {
            for (const h of this._handlers || []) {
                const t = h && h.type, el = h && h.el;
                if (el && typeof el.removeEventListener === 'function' && t) el.removeEventListener(t, h.fn);
            }
            this._handlers = new Set();
            this._removeHost();
            // Clear the shared selection state. When the modal was constructed
            // with `opts.selection` (an EXISTING engine passed in by the caller),
            // that engine survives beyond this instance — wiping it here would
            // mutate a state object another consumer may still be using. The
            // caller is responsible for calling clearAll() when it wants a clean
            // slate on reopen; this only guarantees a fresh instance leaves no
            // residue if the engine was constructed internally.
            if (this._view && !this._optsOwnedSelection && typeof this._view.clearAll === 'function') {
                this._view.clearAll();
            }
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

        // ── Focus trap (Tab/Shift+Tab cycles among focusable elements). ────────
        focusTrap() {
            const scope = this._host || ((typeof document !== 'undefined' && document.body) || null);
            if (!scope || typeof scope.querySelectorAll !== 'function') return [];
            const all = Array.from(scope.querySelectorAll('button, [href], input, select, textarea')) || [];
            return all.filter((el) => !(el.disabled || el.getAttribute('disabled') === 'true' || (el.getAttribute && el.getAttribute('aria-hidden') === 'true')));
        }

        // ── Env / event binding (browser-only; safe under Node) ─────────────────
        _env() {
            const doc = (typeof document !== 'undefined') ? document : ((typeof globalThis !== 'undefined' && globalThis.__testDocument) || null);
            return {
                createRoot: () => doc && typeof doc.createElement === 'function' ? doc.createElement('div') : null,
                getDoc:     () => doc
            };
        }

        _bindEvents(host) {
            const bind = (el, type, fn, opts) => {
                if (!el || typeof el.addEventListener !== 'function') return;
                el.addEventListener(type, fn, opts);
                this._handlers.add({ el, type, fn });
            };

            // Close buttons (× and "Anuluj") — cancel without touching data.
            let closers = [];
            try { closers = Array.from(host.querySelectorAll('button[data-close]')) || []; } catch (_) {}
            for (const c of closers) bind(c, 'click', () => this.cancel());

            // Confirm button — delegated on the host so re-renders don't orphan it.
            bind(host, 'click', (evt) => {
                const target = evt && evt.target;
                if (!target || typeof target.closest !== 'function') return;
                if (target.closest('.invitation-confirm')) {
                    this.confirm();
                }
            });

            // Row clicks — delegated on the host so we survive every re-render.
            bind(host, 'click', (evt) => {
                const target = evt && evt.target;
                if (!target || typeof target.closest !== 'function') return;
                const row = target.closest('.invitation-row');
                if (!row) {
                    // Backdrop / dialog-level click: close without touching data.
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
                    const active  = this._activeElement();
                    let idx       = focusables.indexOf(active);
                    if (evt.preventDefault) evt.preventDefault();
                    const nextIdx = (idx < 0 ? 0 : idx + (evt.shiftKey ? -1 : 1)) % focusables.length;
                    if (nextIdx < 0) nextIdx += focusables.length;
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
            else                  this.toggleMember(idRaw);
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
     * Returns the same API object — callers use .confirm() / .cancel() etc.
     * `onConfirm` receives the hook's `getConfirmPayload()` (a sorted number[]).
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
