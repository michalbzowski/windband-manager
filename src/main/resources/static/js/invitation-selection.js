/*
 * invitation-selection.js — pure-logic module (NO DOM) that powers the unified
 * invitation modal for events and rehearsals ("proba").
 *
 * Two layers, both testable without a browser:
 *
 *   SelectionState          — the state engine. Owns the three Sets that ARE the
 *                             source of truth: selectedGroups, selectedMembers,
 *                             resolvedMemberIds (the deduplicated, reference-
 *                             counted union). Exposes toggleGroup/toggleMember/
 *                             clear/getResolvedIds/getSelectedCount and the
 *                             selection getters. No DOM, no React.
 *
 *   useInvitationSelection  — the reactive "hook". Takes { groups, members }
 *                             (the input shape this task specifies), wraps a
 *                             SelectionState, and returns exactly:
 *                                 selectedGroupIds   : Set
 *                                 selectedMemberIds  : Set
 *                                 resolvedMemberIds  : Set  (the deduped union)
 *                                 toggleGroup(groupId)
 *                                 toggleMember(memberId)
 *                                 clearAll()
 *                                 getConfirmPayload()
 *                             Reads are live views over the Sets, so the hook is
 *                             "reactive" in the sense that every mutation of any
 *                             set is immediately reflected. It never copies the
 *                             sets — it returns the real references — matching
 *                             how a React hook would hand back live state.
 *
 * Reference-count coverage (acceptance criteria 3–5): a member stays in
 * resolvedMemberIds while at least ONE source covers it, where a source is
 *   (a) a currently-selected group that lists the member, or
 *   (b) the member's own entry in selectedMembers.
 * Selecting a member already covered by a group adds no new id (dedup);
 * deselecting one covering source keeps the member while another still covers;
 * deselecting the LAST source drops it. This is what makes "deselecting one of
 * two overlapping groups keeps the shared members selected" work for free —
 * the shared members are still covered by the remaining group (and/or by an
 * explicit individual check), so they survive.
 *
 * Membership comes from the input `groups` array ({ id, name, memberIds[] }) —
 * toggleGroup(groupId) looks the group up and reasons about its members for
 * both add and remove. This differs from the earlier v2 module (t_2e9238fa),
 * whose toggleGroup(gid, members[]) took members inline at add-time and had no
 * durable membership map when removing, which is what let AC#5 regress there.
 *
 * Confirm payload: the existing backend contract accepts per-member
 * POST /api/events/{id}/invite ({eventId, memberId}) loops — i.e. an ARRAY of
 * member ids is what the consumer iterates over. getConfirmPayload() therefore
 * returns that array (sorted ascending for determinism). It does NOT fabricate
 * any new endpoint shape: the same endpoints are reused by the page wiring.
 */
(() => {
    'use strict';

    // ── helpers -------------------------------------------------------------

    function asNumberArray(value) {
        if (!value || !Array.isArray(value)) return [];
        const out = [];
        for (const v of value) {
            const n = Number(v);
            if (Number.isFinite(n)) out.push(n);
        }
        return out;
    }

    function createSet(iterable) {
        const s = new Set();
        if (iterable != null) {
            for (const v of iterable) s.add(v);
        }
        return s;
    }

    // ── state engine --------------------------------------------------------

    class SelectionState {
        /**
         * @param {Array<{id:number, name?:string, memberIds:Iterable<number>}>} [groups]
         *   Optional snapshot of groups and their members used to reason about
         *   coverage when a group is later toggled off. Pass this if you will
         *   toggle groups after construction (acceptance criteria 4–5).
         */
        constructor(groups) {
            this.selectedGroups = new Set();       // Set<number>
            this.selectedMembers = new Set();      // Set<number>
            this.resolvedMemberIds = new Set();    // Set<number> — the deduped union
            this._membershipMap = Object.create(null); // id -> Set<number>

            if (Array.isArray(groups)) {
                for (const g of groups) {
                    if (!g || g.id == null) continue;
                    this._membershipMap[g.id] = createSet(asNumberArray(g.memberIds));
                }
            }
        }

        _membersOf(groupId) {
            const m = this._membershipMap[groupId];
            return m != null ? m : new Set();
        }

        // Still covered by some OTHER selected group? (for remove-time refcount)
        _coveredByOtherGroup(memberId, exceptGroupId) {
            for (const gid of this.selectedGroups) {
                if (gid === exceptGroupId) continue;
                if (this._membersOf(gid).has(memberId)) return true;
            }
            return false;
        }

        // Still covered by ANY selected group? (for remove-time refcount, member)
        _coveredByAnyGroup(memberId) {
            for (const gid of this.selectedGroups) {
                if (this._membersOf(gid).has(memberId)) return true;
            }
            return false;
        }

        /** Toggle a group. Add: union its members. Remove: drop only uncovered. */
        toggleGroup(groupId) {
            if (this.selectedGroups.has(groupId)) {
                this._removeGroup(groupId);
            } else {
                this._addGroup(groupId);
            }
        }

        _addGroup(groupId) {
            this.selectedGroups.add(groupId);
            for (const mid of this._membersOf(groupId)) {
                this.resolvedMemberIds.add(mid);
            }
        }

        _removeGroup(groupId) {
            if (!this.selectedGroups.has(groupId)) return;
            this.selectedGroups.delete(groupId);
            // A member covered only by this group is dropped unless it is also
            // individually selected. Shared members survive (covered elsewhere).
            for (const mid of this._membersOf(groupId)) {
                const stillCovered =
                    this._coveredByOtherGroup(mid, groupId) ||
                    this.selectedMembers.has(mid);
                if (!stillCovered) {
                    this.resolvedMemberIds.delete(mid);
                }
            }
        }

        /** Toggle a member. Add: ensure present. Remove: keep if group-covers. */
        toggleMember(memberId) {
            if (this.selectedMembers.has(memberId)) {
                this._removeMember(memberId);
            } else {
                this._addMember(memberId);
            }
        }

        _addMember(memberId) {
            this.selectedMembers.add(memberId);
            this.resolvedMemberIds.add(memberId); // dedup: idempotent add
        }

        _removeMember(memberId) {
            if (!this.selectedMembers.has(memberId)) return;
            this.selectedMembers.delete(memberId);
            // Drop from resolved only if no selected group still covers it.
            if (!this._coveredByAnyGroup(memberId)) {
                this.resolvedMemberIds.delete(memberId);
            }
        }

        /** Reset all three sets. Mutates IN PLACE (clear(), not reassignment)
         *  so the live Set references handed out by useInvitationSelection keep
         *  pointing at the same objects — clearing is reflected immediately. */
        clear() {
            this.selectedGroups.clear();
            this.selectedMembers.clear();
            this.resolvedMemberIds.clear();
        }

        /** Final deduplicated array of member ids for the confirm payload. */
        getResolvedIds() {
            return Array.from(this.resolvedMemberIds);
        }

        /** Member count (size of resolved union) — for the footer counter. */
        getSelectedCount() {
            return this.resolvedMemberIds.size;
        }

        isGroupSelected(groupId)  { return this.selectedGroups.has(groupId); }
        isMemberSelected(memberId){ return this.selectedMembers.has(memberId); }
    }

    // ── reactive hook -------------------------------------------------------

    /**
     * useInvitationSelection — the client-side hook that encapsulates all
     * selection + dedup logic for the unified invitation modal.
     *
     * @param {Object}   opts
     * @param {Array<{id:number, name?:string, memberIds:Array<number>}>} [opts.groups]
     * @param {Array<{id:number, name?:string}>} [opts.members]
     *   `members` is accepted and validated per the accepted contract; the hook
     *   does NOT need to enumerate them for coverage computation — only their
     *   ids matter, which come from `groups[].memberIds`. It is kept so callers
     *   can pass the exact shape their page already has.
     * @returns {{
     *   selectedGroupIds:Set, selectedMemberIds:Set, resolvedMemberIds:Set,
     *   toggleGroup:Function, toggleMember:Function, clearAll:Function,
     *   getConfirmPayload:Function, isGroupSelected:Function, isMemberSelected:Function,
     *   count:Function
     * }} a reactive view. The three set fields are LIVE references over the
     * engine's Sets — any subsequent toggle mutates them in place (matching how
     * a React hook's returned object would expose live state).
     */
    function useInvitationSelection(opts) {
        const options = opts || {};
        validateGroups(options.groups);
        // `members` is validated but not needed for coverage; we just make sure
        // it has the right shape when callers pass it. (Some modals render member
        // rows from this list; the hook is agnostic to that rendering.)
        if (options.members != null && !Array.isArray(options.members)) {
            throw new TypeError('useInvitationSelection: `members` must be an array.');
        }

        const engine = new SelectionState(options.groups);

        return {
            // LIVE Sets — the source of truth for "who is invited".
            selectedGroupIds:  engine.selectedGroups,
            selectedMemberIds: engine.selectedMembers,
            resolvedMemberIds: engine.resolvedMemberIds,

            toggleGroup:  (groupId)  => engine.toggleGroup(groupId),
            toggleMember: (memberId) => engine.toggleMember(memberId),
            clearAll:     ()         => engine.clear(),

            // Final, deduplicated, sorted member-id array — the same shape the
            // existing per-member POST /api/events/{id}/invite loop consumes.
            getConfirmPayload: ()    => Array.from(engine.resolvedMemberIds).sort((a, b) => a - b),

            isGroupSelected:  (groupId)  => engine.isGroupSelected(groupId),
            isMemberSelected: (memberId) => engine.isMemberSelected(memberId),
            count:            ()         => engine.getSelectedCount(),
        };
    }

    function validateGroups(groups) {
        if (groups == null) return;
        if (!Array.isArray(groups)) {
            throw new TypeError('useInvitationSelection: `groups` must be an array.');
        }
    }

    // ── export: browser global + CommonJS hook (idempotent). ----------------
    const root = (typeof window !== 'undefined') ? window :
                 (typeof globalThis !== 'undefined' ? globalThis : this);
    if (root && !root.InvitationSelection) {
        // Expose BOTH names so downstream branches converge on one API:
        //   - SelectionState         (pure engine, framework-agnostic)
        //   - InvitationSelection    (same class — a stable cross-branch alias)
        //   - useInvitationSelection (the reactive "hook" per task t_4fcc1f03)
        root.SelectionState = SelectionState;
        root.InvitationSelection = SelectionState;
        root.useInvitationSelection = useInvitationSelection;
    }

    if (typeof module !== 'undefined' && module.exports != null) {
        module.exports = useInvitationSelection;
        module.SelectionState = SelectionState;
        module.InvitationSelection = SelectionState;
        module.useInvitationSelection = useInvitationSelection;
    }
})();
