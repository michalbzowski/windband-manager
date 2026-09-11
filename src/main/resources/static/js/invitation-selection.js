/*
 * invitation-selection.js — pure-logic module (NO DOM) that manages the
 * invitation selection state used by the event/rehearsal invite modals.
 *
 * It tracks three collections (all keyed by id):
 *   - selectedGroups     : ids of groups the user checked
 *   - selectedMembers    : ids of individual members the user checked
 *   - resolvedMemberIds  : the deduplicated, reference-counted union of every
 *                          member covered by the currently selected groups PLUS
 *                          every individually-selected member.
 *
 * Reference-count style semantics: a member is present in resolvedMemberIds
 * while at least ONE source covers it. A source is either (a) a currently
 * selected group that lists the member, or (b) the member's own entry in
 * selectedMembers. Selecting a member X already covered by a group does not
 * change the count; deselecting one covering source while another still covers
 * the member keeps the member in the resolved set; deselecting the LAST source
 * drops it.
 *
 * Two exports:
 *   1) `InvitationSelection` CLASS — backwards-compatible, self-contained pure
 *      engine. Its public surface matches what this branch's pre-existing tests
 *      and modal consume: toggleGroup(gid, memberIds[]), toggleMember(id),
 *      getResolvedIds(), getSelectedCount(), isGroupSelected(isMemberSelected,
 *      clear().
 *   2) `useInvitationSelection({groups, members})` — the REACTIVE HOOK (task
 *      t_4fcc1f03 / consumed by t_9c915ab4). Returns a reactive view whose
 *      three Sets (`selectedGroupIds`, `selectedMemberIds`, `resolvedMemberIds`)
 *      are LIVE references over an internal state engine, plus methods:
 *          toggleGroup(groupId)         — single-arg; membership comes from the
 *                                         `groups` snapshot passed at hook call
 *                                          (not an inline members array).
 *          toggleMember(memberId)
 *          clearAll()
 *          getConfirmPayload()          — sorted, deduplicated member-id array
 *          isGroupSelected(id) / isMemberSelected(id) / count()
 *
 * Design goals (acceptance #5): framework-agnostic, no DOM access, fully
 * unit-testable without a browser. The module exposes both names and an
 * idempotent global export plus CommonJS hooks so tests under Node can target
 * either surface; the browser gets `window.<name>` directly.
 */
(() => {
    'use strict';

    function createSet(iterable) {
        const s = new Set();
        if (iterable != null) {
            for (const v of iterable) s.add(v);
        }
        return s;
    }

    // ══════════════ surface #1: the legacy `InvitationSelection` class ═══════
    // Self-contained, stateful. Membership is registered lazily via the
    // 2-arg toggleGroup(gid, members[]), or up-front via the optional
    // groupMembership constructor arg (a map gid -> iterable of mids).
    class InvitationSelection {
        /**
         * @param {Object<string, Iterable<any>>} [groupMembership] Optional map
         *   of groupId -> iterable of member ids, the membership snapshot used to
         *   reason about *coverage* when a group is later toggled off. Toggling a
         *   group on also accepts its members inline (see `toggleGroup`), so this
         *   parameter is only needed when you want the module to know memberships
         *   up front without passing them again at toggle time.
         */
        constructor(groupMembership) {
            this.selectedGroups = new Set();
            this.selectedMembers = new Set();
            this.resolvedMemberIds = new Set();

            const membershipMap = {};
            if (groupMembership) {
                for (const [gid, mids] of Object.entries(groupMembership)) {
                    membershipMap[gid] = createSet(mids);
                }
            }
            this._membershipMap = membershipMap;
        }

        /** Returns a Set of member ids covered by the given group. */
        _membersOf(groupId) {
            const m = this._membershipMap[groupId];
            return m != null ? m : new Set();
        }

        _stillCoveredByAnyOtherGroup(memberId, exceptGroupId) {
            for (const gid of this.selectedGroups) {
                if (gid === exceptGroupId) continue;
                if (this._membersOf(gid).has(memberId)) return true;
            }
            return false;
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
            // Drop members that this group covered AND that are no longer
            // covered by any other selected group AND not individually selected.
            for (const mid of this._membersOf(groupId)) {
                const coveredByOtherGroup = this._stillCoveredByAnyOtherGroup(mid, groupId);
                const individuallySelected = this.selectedMembers.has(mid);
                if (!coveredByOtherGroup && !individuallySelected) {
                    this.resolvedMemberIds.delete(mid);
                }
            }
        }

        /** Toggle a group's selection (membership passed inline on add). */
        toggleGroup(groupId, groupMembers) {
            if (this.selectedGroups.has(groupId)) {
                this._removeGroup(groupId);
                return;
            }
            // Register the membership for this group so removals can reason
            // about coverage even if it was not part of the initial snapshot.
            this._membershipMap[groupId] = createSet(groupMembers);
            this._addGroup(groupId);
        }

        _addMember(memberId) {
            this.selectedMembers.add(memberId);
            this.resolvedMemberIds.add(memberId);
        }

        _removeMember(memberId) {
            if (!this.selectedMembers.has(memberId)) return;
            this.selectedMembers.delete(memberId);
            // Only drop from resolved if no currently-selected group still covers it.
            let coveredByAnyGroup = false;
            for (const gid of this.selectedGroups) {
                if (this._membersOf(gid).has(memberId)) { coveredByAnyGroup = true; break; }
            }
            if (!coveredByAnyGroup) {
                this.resolvedMemberIds.delete(memberId);
            }
        }

        /** Toggle an individual member's selection. */
        toggleMember(memberId) {
            if (this.selectedMembers.has(memberId)) {
                this._removeMember(memberId);
                return;
            }
            this._addMember(memberId);
        }

        /** Final deduplicated array of member ids for the confirm payload. */
        getResolvedIds() {
            return Array.from(this.resolvedMemberIds);
        }

        /** Member count for the footer counter. */
        getSelectedCount() {
            return this.resolvedMemberIds.size;
        }

        isGroupSelected(groupId) {
            return this.selectedGroups.has(groupId);
        }

        isMemberSelected(memberId) {
            return this.selectedMembers.has(memberId);
        }

        /** Convenience reset. */
        clear() {
            this.selectedGroups.clear();
            this.selectedMembers.clear();
            this.resolvedMemberIds.clear();
        }
    }

    // ══════════════ surface #2: the reactive hook (t_4fcc1f03 / t_9c915ab4) ═══
    // `SelectionState` is the internal state engine: three Sets + a persistent
    // group→members snapshot. `useInvitationSelection` wraps it and hands back
    // LIVE references to those Sets (not copies), matching how a React hook
    // would hand back live state that re-renders reflect immediately.
    class SelectionState {
        /**
         * @param {Array<{id, memberIds?:Iterable}>} [groups] Snapshot of group
         *   membership used for reference counting on deselect. Pass this if you
         *   will call toggleGroup(groupId) after construction (acceptance 4–5).
         */
        constructor(groups) {
            this.selectedGroups = new Set();      // gids of checked groups
            this.selectedMembers = new Set();     // mids individually selected
            this.resolvedMemberIds = new Set();   // deduped union — THE source of truth

            const membership = Object.create(null); // gid -> Set<mid>
            if (Array.isArray(groups)) {
                for (const g of groups) {
                    if (!g || g.id == null) continue;
                    const ids = Array.isArray(g.memberIds) ? g.memberIds : [];
                    const set = new Set();
                    for (const v of ids) set.add(v);
                    membership[g.id] = set;
                }
            }
            this._membership = membership;
        }

        _mids(gid) { return this._membership[gid] || new Set(); }

        _coveredByAnyOtherGroup(mid, exceptGid) {
            for (const g of this.selectedGroups) { if (g !== exceptGid && this._mids(g).has(mid)) return true; }
            return false;
        }
        _coveredByAnyGroup(mid) {
            for (const g of this.selectedGroups) { if (this._mids(g).has(mid)) return true; }
            return false;
        }

        /** Toggle a group. Membership comes from the snapshot at construction. */
        toggleGroup(groupId) {
            if (this.selectedGroups.has(groupId)) {
                // Deselect: drop members not still covered by another source.
                this.selectedGroups.delete(groupId);
                for (const mid of this._mids(groupId)) {
                    const still = this._coveredByAnyOtherGroup(mid, groupId) ||
                                 this.selectedMembers.has(mid);
                    if (!still) this.resolvedMemberIds.delete(mid);
                }
            } else {
                // Select: union its members into the resolved set (idempotent).
                this.selectedGroups.add(groupId);
                for (const mid of this._mids(groupId)) this.resolvedMemberIds.add(mid);
            }
        }

        /** Toggle a member. Add: ensure present. Remove: keep if group covers. */
        toggleMember(memberId) {
            if (this.selectedMembers.has(memberId)) {
                this.selectedMembers.delete(memberId);
                if (!this._coveredByAnyGroup(memberId)) this.resolvedMemberIds.delete(memberId);
            } else {
                this.selectedMembers.add(memberId);
                this.resolvedMemberIds.add(memberId);
            }
        }

        /** Reset all three sets. IN PLACE (mutates, does not reassign), so the
         *  LIVE views handed out by useInvitationSelection keep reflecting it. */
        clearAll() {
            this.selectedGroups.clear();
            this.selectedMembers.clear();
            this.resolvedMemberIds.clear();
        }

        isGroupSelected(id)  { return this.selectedGroups.has(id); }
        isMemberSelected(id) { return this.selectedMembers.has(id); }

        // Convenience (mirrors the legacy class surface so old code keeps working).
        getResolvedIds()       { return Array.from(this.resolvedMemberIds); }
        getSelectedCount()     { return this.resolvedMemberIds.size; }
    }

    /**
     * useInvitationSelection — reactive hook that wraps a SelectionState and
     * hands back LIVE Set views + methods. See the file header for the full
     * contract. `groups` is required when you will call toggleGroup() with the
     * snapshot's ids (otherwise the engine cannot reason about coverage);
     * `members` is accepted + shape-validated but not used for computation
     * (only its ids are relevant, and those live in groups[].memberIds for
     * coverage purposes).
     */
    function useInvitationSelection(opts) {
        const options = opts || {};
        if (options.groups != null && !Array.isArray(options.groups)) {
            throw new TypeError('useInvitationSelection: `groups` must be an array.');
        }
        if (options.members != null && !Array.isArray(options.members)) {
            throw new TypeError('useInvitationSelection: `members` must be an array.');
        }
        const engine = new SelectionState(options.groups);
        return {
            // LIVE Set views — the source of truth. Mutating any toggle keeps
            // these reflected immediately, matching React-hook semantics.
            selectedGroupIds:  engine.selectedGroups,
            selectedMemberIds: engine.selectedMembers,
            resolvedMemberIds: engine.resolvedMemberIds,

            toggleGroup:  (groupId)  => engine.toggleGroup(groupId),
            toggleMember: (memberId) => engine.toggleMember(memberId),
            clearAll:     ()         => engine.clearAll(),

            // Sorted, deduplicated member-id array — the SAME shape the existing
            // per-member POST /api/.../invite loop consumes. (No new endpoint.)
            getConfirmPayload: ()    => Array.from(engine.resolvedMemberIds).sort((a, b) => a - b),

            isGroupSelected:  (id)   => engine.isGroupSelected(id),
            isMemberSelected: (id)   => engine.isMemberSelected(id),
            count:            ()     => engine.resolvedMemberIds.size,
        };
    }

    // ── Export: browser global + Node CommonJS hook (idempotent). ─────────────
    const root = (typeof window !== 'undefined') ? window :
                 (typeof globalThis !== 'undefined' ? globalThis : this);

    if (root) {
        // The stable cross-branch alias for the legacy engine — preserved so
        // existing consumers on this branch keep working unchanged.
        if (root.InvitationSelection === undefined)   root.InvitationSelection    = InvitationSelection;
        // New reactive surfaces (t_4fcc1f03): both names land only once.
        if (root.SelectionState === undefined)         root.SelectionState         = SelectionState;
        if (root.useInvitationSelection === undefined) root.useInvitationSelection = useInvitationSelection;
    }

    // CommonJS hook for Node testability (acceptance #5). Guarded so the file
    // is still a self-contained browser script when loaded via <script>.
    if (typeof module !== 'undefined' && module.exports != null) {
        // `module.exports` = the legacy CLASS (existing consumers do
        // `const InvitationSelection = require(...)`). The hook surface rides
        // on NAMED PROPERTIES ON THE EXPORT OBJECT — Node caches module.exports,
        // so we must attach them directly to that object (NOT to `module`).
        const exportTarget = InvitationSelection;
        exportTarget.InvitationSelection    = InvitationSelection;
        exportTarget.SelectionState         = SelectionState;
        exportTarget.useInvitationSelection = useInvitationSelection;
        module.exports = exportTarget;
    }
})();
