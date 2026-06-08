package pl.michalbzowski.windband.application.command.member;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.application.dto.GroupDetailDto;
import pl.michalbzowski.windband.application.query.member.GroupQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.Group;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.BaseIntegrationTest;

/**
 * Regression test for GitHub Issue #7:
 * "Raz dodany do grupy muzyk, ciągle jest widoczny na liście do dodania"
 *
 * Verifies that when a member is added to a group, the group detail
 * query correctly tracks which members are already in the group,
 * so the UI can filter them out from the "add member" dropdown.
 */
@Transactional
class GroupMemberFilterRegressionTest extends BaseIntegrationTest {

    @Autowired
    private GroupCommandService groupCommandService;

    @Autowired
    private GroupQueryService groupQueryService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BandRepository bandRepository;

    @Test
    void addedMemberShouldNotAppearInAvailableMembers() {
        Band band = ensureDefaultBand();

        // Create a group
        CreateGroupCommand groupCmd = new CreateGroupCommand();
        groupCmd.setName("Test Grupa");
        groupCmd.setDescription("Test opis");
        Group group = groupCommandService.createGroup(groupCmd);
        Long groupId = group.getId();

        // Create two members
        Member member1 = memberRepository.save(
                Member.create("Jan", "Kowalski", LocalDate.of(1990, 1, 1), band));
        Member member2 = memberRepository.save(
                Member.create("Anna", "Nowak", LocalDate.of(1992, 5, 15), band));

        // Before adding anyone, group has no members
        GroupDetailDto detailBefore = groupQueryService.getGroupDetailById(groupId);
        assertThat(detailBefore.members()).isEmpty();

        // Add member1 to the group
        groupCommandService.addMemberToGroup(groupId, member1.getId());

        // Refresh the group detail
        GroupDetailDto detailAfter = groupQueryService.getGroupDetailById(groupId);

        // member1 should now be in the group members list
        assertThat(detailAfter.members()).hasSize(1);
        assertThat(detailAfter.members().get(0).memberId()).isEqualTo(member1.getId());

        // Verify the controller logic: member1's ID should be in the group member IDs set
        Set<Long> memberIdsInGroup = detailAfter.members().stream()
                .map(m -> m.memberId())
                .collect(Collectors.toSet());
        assertThat(memberIdsInGroup).contains(member1.getId()).doesNotContain(member2.getId());
    }

    @Test
    void removingMemberFromGroupShouldReturnThemToAvailableList() {
        Band band = ensureDefaultBand();

        CreateGroupCommand groupCmd = new CreateGroupCommand();
        groupCmd.setName("Test Grupa 2");
        groupCmd.setDescription("Test opis 2");
        Group group = groupCommandService.createGroup(groupCmd);
        Long groupId = group.getId();

        Member member1 = memberRepository.save(
                Member.create("Piotr", "Wiśniewski", LocalDate.of(1988, 3, 20), band));

        // Add and then remove
        groupCommandService.addMemberToGroup(groupId, member1.getId());
        groupCommandService.removeMemberFromGroup(groupId, member1.getId());

        GroupDetailDto detail = groupQueryService.getGroupDetailById(groupId);
        assertThat(detail.members()).isEmpty();

        // Now the controller would NOT filter out member1 since they're not in the group anymore
        Set<Long> memberIdsInGroup = detail.members().stream()
                .map(m -> m.memberId())
                .collect(Collectors.toSet());
        assertThat(memberIdsInGroup).doesNotContain(member1.getId());
    }

    private Band ensureDefaultBand() {
        return bandRepository.findById(1L).orElseGet(() -> {
            Band band = Band.create("Default Band", "default-band");
            band.update("Default Band", "default-band", "Band for testing");
            return bandRepository.save(band);
        });
    }
}
