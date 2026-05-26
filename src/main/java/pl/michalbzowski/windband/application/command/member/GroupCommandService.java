package pl.michalbzowski.windband.application.command.member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.member.Group;
import pl.michalbzowski.windband.domain.member.GroupRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class GroupCommandService {

    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;

    public Group createGroup(CreateGroupCommand cmd) {
        Group group = new Group(cmd.getName(), cmd.getDescription());
        return groupRepository.save(group);
    }

    public void addMemberToGroup(Long groupId, Long memberId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        group.addMember(member);
        groupRepository.save(group);
    }

    public void removeMemberFromGroup(Long groupId, Long memberId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
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
}
