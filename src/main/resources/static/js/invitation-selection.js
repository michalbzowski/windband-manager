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
 * Design goals (acceptance #5): framework-agnostic, no DOM access, fully
 * unit-testable without a browser. The module exposes a single plain class,
 * `InvitationSelection`, with an idempotent global export and a CommonJS hook
 * so Node can `require` it directly in tests while the browser gets it on
 * `window.InvitationSelection`.
 *
 * State is intentionally not observable / reactive: callers read via the
 * getters. Toggles return nothing; the UI rebuilds checkbox state by calling
 * `isGroupSelected` / `isMemberSelected`.
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

        /**
         * Returns a Set of member ids covered by the given group, from the
         * membership snapshot provided at construction. Used for reference
         * counting when a group is toggled off.
         */
        _membersOf(groupId) {
            const m = this._membershipMap[groupId];
            return m != null ? m : new Set();
        }

        /**
         * A member is still "covered" by some selected group other than the
         * one being removed if at least one currently-selected group lists it.
         */
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

        /**
         * Toggle a group's selection. On add: union its members into the
         * resolved set. On remove: drop only members that are no longer covered
         * by any other selected group and not individually selected.
         */
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
            this.selectedGroups = new Set();
            this.selectedMembers = new Set();
            this.resolvedMemberIds = new Set();
        }
    }

    // ── Export: browser global + Node CommonJS hook (idempotent). ─────────────
    const root = (typeof window !== 'undefined') ? window :
                 (typeof globalThis !== 'undefined' ? globalThis : this);

    if (root && !root.InvitationSelection) {
        root.InvitationSelection = InvitationSelection;
    }

    // CommonJS hook for Node testability (acceptance #5). Guarded so the file
    // is still a self-contained browser script when loaded via <script>.
    if (typeof module !== 'undefined' && module.exports != null) {
        module.exports = InvitationSelection;
    }
})();
