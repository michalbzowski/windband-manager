package pl.michalbzowski.windband.application.command.member;

import lombok.RequiredArgsConstructor;
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
     * Create the dynamic group backed by the given BOOLEAN attribute.
     * Idempotent: if a group already exists for this attribute, returns it.
     */
    public Group createDynamicGroupForAttribute(MemberAttributeDef def) {
        return groupRepository.findByDynamicSource(def).orElseGet(() -> {
            String desiredName = def.getName();
            Band band = def.getBand();
            try {
                return groupRepository.save(Group.createDynamic(desiredName, band, def));
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Name collision with a manual group — prefix to keep both.
                return groupRepository.save(Group.createDynamic("dynamic_" + desiredName, band, def));
            }
        });
    }

    /**
     * Sync a single member's membership in the dynamic group backed by `def`:
     *  - if value == "true" and member not in group → add
     *  - if value != "true" and member in group → remove
     * No-op if the attribute has no dynamic group, or is not BOOLEAN.
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
