package pl.michalbzowski.windband.application.command.member;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.member.Group;
import pl.michalbzowski.windband.domain.member.GroupRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class GroupCommandService {

    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;

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
     * Sync a single member's membership in the dynamic group backed by `def`:
     *  - if value == "true" and member not in group → add
     *  - if value != "true" and member in group → remove
     * No-op if the attribute has no dynamic group, or is not BOOLEAN.
     * Joins the caller's transaction (default REQUIRED) — see
     * {@link #createDynamicGroupForAttribute} for why this is preferred over
     * REQUIRES_NEW here.
     */
    public void syncMemberInDynamicGroup(MemberAttributeDef def, Member member, String newValue) {
        if (!"BOOLEAN".equals(def.getType())) return;
        Optional<Group> maybeGroup = groupRepository.findByDynamicSource(def);
        if (maybeGroup.isEmpty()) return;
        Group group = maybeGroup.get();
        boolean shouldBeMember = "true".equalsIgnoreCase(newValue);
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

    public void renameDynamicGroup(MemberAttributeDef def) {
        groupRepository.findByDynamicSource(def).ifPresent(g -> {
            g.renameForDynamicSource(def.getName());
            groupRepository.save(g);
        });
    }

    public void deleteDynamicGroup(MemberAttributeDef def) {
        groupRepository.findByDynamicSource(def).ifPresent(groupRepository::delete);
    }
}
