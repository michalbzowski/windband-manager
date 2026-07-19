package pl.michalbzowski.windband.application.command.member;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.band.MemberAttributeValueRepository;
import pl.michalbzowski.windband.domain.member.DynamicGroupSource;
import pl.michalbzowski.windband.domain.member.DynamicSourceType;
import pl.michalbzowski.windband.domain.member.Group;
import pl.michalbzowski.windband.domain.member.GroupRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberFieldSource;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class GroupCommandService {

    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final MemberAttributeValueRepository valueRepository;

    public Group createGroup(CreateGroupCommand cmd, Band band) {
        Group group = new Group(cmd.getName(), cmd.getDescription(), band);
        return groupRepository.save(group);
    }

    public void addMemberToGroup(Long groupId, Long memberId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
        if (group.isDynamic()) {
            throw new IllegalStateException(
                "Nie można ręcznie dodawać członków do grupy dynamicznej '" + group.getName() + "'. " +
                "Członkowie są zarządzani automatycznie przez atrybut '" + group.getName() + "'.");
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        group.addMember(member);
        groupRepository.save(group);
    }

    public void removeMemberFromGroup(Long groupId, Long memberId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
        if (group.isDynamic()) {
            throw new IllegalStateException(
                "Nie można ręcznie usuwać członków z grupy dynamicznej '" + group.getName() + "'. " +
                "Członkowie są zarządzani automatycznie przez atrybut '" + group.getName() + "'.");
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        group.removeMember(member);
        groupRepository.save(group);
    }

    public void deleteGroup(Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
        groupRepository.delete(group);
    }

    /**
     * Create the dynamic group backed by the given BOOLEAN attribute. Idempotent.
     * <p>
     * Joins the caller's transaction (default REQUIRED propagation) so the attribute
     * def and the dynamic group are committed atomically. This is the right semantics
     * for both flows:
     * <ul>
     *   <li><b>User-initiated {@code createAttributeDef}:</b> the def and the dynamic
     *       group are persisted in the same outer transaction (started by the class-level
     *       {@code @Transactional} on {@code MemberAttributeCommandService}). If the
     *       group insert fails, the def insert also rolls back — no orphan attributes.</li>
     *   <li><b>Backfill runner:</b> the runner has NO outer transaction of its own, so
     *       each call to {@code ensureDynamicGroupExists} starts a fresh transaction
     *       (via its class-level {@code @Transactional}). The runner's {@code try/catch}
     *       loop then contains any failure to that single attribute's transaction;
     *       the next attribute gets a brand-new transaction. This was the production
     *       bug (2026-07-04): wrapping the whole loop in one transaction meant one
     *       name collision poisoned all subsequent attributes.</li>
     * </ul>
     * <p>
     * Earlier iterations of this method used {@code REQUIRES_NEW} for hard isolation.
     * That backfired in two ways: (a) a foreign-key violation when the parent def was
     * flushed-but-not-committed in the caller's session; (b) tests that wrap methods
     * in {@code @Transactional} ran into session-attach issues. Joining the caller's
     * transaction is the correct default here.
     * <p>
     * On a name collision with a manual group, append a numeric suffix: "Gość" → "Gość (2)"
     * → "Gość (3)" … This keeps the dynamic group's name recognisable to the user
     * (better UX than a generic "dynamic_Gość" prefix).
     */
    public Group createDynamicGroupForAttribute(MemberAttributeDef def) {
        Optional<Group> existing = groupRepository.findByDynamicSource(def);
        if (existing.isPresent()) {
            return existing.get();
        }
        String baseName = def.getName();
        Band band = def.getBand();
        String resolvedName = resolveNameCollision(baseName, band);
        if (!resolvedName.equals(baseName)) {
            log.warn("[dynamic-groups] Name collision for attribute '{}' (band {}): a manual group already uses that name. " +
                    "Creating dynamic group as '{}' instead.", baseName, band.getId(), resolvedName);
        }
        return groupRepository.save(Group.createDynamic(resolvedName, band, def));
    }

    /**
     * If a group with the given name already exists in the band, append " (2)", " (3)", …
     * until a free name is found. Bounded by MAX_SUFFIX_ATTEMPTS so we never loop forever
     * on a fully-saturated namespace.
     */
    private String resolveNameCollision(String baseName, Band band) {
        Long bandId = band.getId();
        if (!groupRepository.existsByNameAndBandId(baseName, bandId)) {
            return baseName;
        }
        final int MAX_SUFFIX_ATTEMPTS = 1000;
        for (int i = 2; i <= MAX_SUFFIX_ATTEMPTS + 1; i++) {
            String candidate = baseName + " (" + i + ")";
            if (!groupRepository.existsByNameAndBandId(candidate, bandId)) {
                return candidate;
            }
        }
        log.error("[dynamic-groups] Could not find a free group name for base '{}' in band {} after {} attempts. " +
                "Falling back to UUID suffix to avoid blocking startup.", baseName, bandId, MAX_SUFFIX_ATTEMPTS);
        return baseName + " (" + java.util.UUID.randomUUID() + ")";
    }

    /**
     * Sync a single member's membership in the dynamic group backed by the given
     * polymorphic {@link DynamicGroupSource}:
     *  - if {@code source.memberMatches(member)} and member not in group → add
     *  - if not matches and member in group → remove
     * No-op if no dynamic group corresponds to the source.
     * <p>
     * This is written against the {@code DynamicGroupSource} interface only — it does
     * not branch on whether the source is an attribute or a member field, so new
     * source kinds are added without touching this method (OCP).
     * <p>
     * Joins the caller's transaction (default REQUIRED) — see
     * {@link #createDynamicGroupForAttribute} for why this is preferred over
     * REQUIRES_NEW here.
     */
    public void syncMemberInDynamicGroup(DynamicGroupSource source, Member member) {
        Optional<Group> maybeGroup = findDynamicGroupForSource(source);
        if (maybeGroup.isEmpty()) return;
        Group group = maybeGroup.get();
        boolean shouldBeMember = source.memberMatches(member);
        boolean isMember = group.getMembers().stream()
                .anyMatch(gm -> gm.getMember().equals(member));
        if (shouldBeMember && !isMember) {
            group.addMember(member);
            groupRepository.save(group);
        } else if (!shouldBeMember && isMember) {
            group.removeMember(member);
            groupRepository.save(group);
        }
    }

    /**
     * Resolve the dynamic group that corresponds to a polymorphic source.
     * Attribute-backed sources are found via the attribute def; member-field-backed
     * sources via (type, key).
     */
    private Optional<Group> findDynamicGroupForSource(DynamicGroupSource source) {
        if (source instanceof pl.michalbzowski.windband.domain.member.AttributeDefSource attr) {
            return groupRepository.findByDynamicSource(attr.getDef());
        }
        if (source instanceof MemberFieldSource field) {
            return groupRepository.findByDynamicSourceTypeAndDynamicSourceKey(DynamicSourceType.MEMBER_FIELD, field.getField());
        }
        return Optional.empty();
    }

    /**
     * Convenience for syncing a member against the fixed {@code active} field.
     * Used by the group-sync event listener when a member is activated/deactivated.
     */
    public void syncMemberForActiveField(Member member) {
        syncMemberInDynamicGroup(new MemberFieldSource(MemberFieldSource.ACTIVE), member);
    }

    public void renameDynamicGroup(MemberAttributeDef def) {
        groupRepository.findByDynamicSource(def).ifPresent(g -> {
            g.renameForDynamicSource(def.getName());
            groupRepository.save(g);
        });
    }

    public void deleteDynamicGroup(MemberAttributeDef def) {
        groupRepository.findByDynamicSource(def).ifPresent(groupRepository::delete);
    }

    /**
     * Create the dynamic group backed by a fixed member field (e.g. "active").
     * Idempotent: returns the existing group if one already exists for that field.
     */
    public Group createDynamicGroupForMemberField(String field, Band band) {
        Optional<Group> existing = groupRepository.findByDynamicSourceTypeAndDynamicSourceKey(DynamicSourceType.MEMBER_FIELD, field);
        if (existing.isPresent()) {
            return existing.get();
        }
        String baseName = new MemberFieldSource(field).getName();
        String resolvedName = resolveNameCollision(baseName, band);
        if (!resolvedName.equals(baseName)) {
            log.warn("[dynamic-groups] Name collision for member field '{}' (band {}): creating dynamic group as '{}' instead.",
                    field, band.getId(), resolvedName);
        }
        return groupRepository.save(Group.createDynamicForMemberField(field, band));
    }
}
