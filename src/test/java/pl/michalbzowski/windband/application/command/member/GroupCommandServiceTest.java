package pl.michalbzowski.windband.application.command.member;

import org.junit.jupiter.api.Test;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.band.MemberAttributeValueRepository;
import pl.michalbzowski.windband.domain.member.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GroupCommandServiceTest {

    private final GroupRepository groupRepository = mock(GroupRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final MemberAttributeValueRepository valueRepository = mock(MemberAttributeValueRepository.class);
    private final GroupCommandService service =
            new GroupCommandService(groupRepository, memberRepository, valueRepository);

    private static final Band BAND = Band.create("Test Band", "test-band");

    private Member activeMember() {
        return Member.create("Anna", "Active", LocalDate.now().minusYears(20), BAND);
    }

    @Test
    void syncForActiveField_addsActiveMemberAndRemovesInactive() {
        Member active = activeMember();
        Group activeGroup = Group.createDynamicForMemberField(MemberFieldSource.ACTIVE, BAND);
        when(groupRepository.findByDynamicSourceTypeAndDynamicSourceKey(DynamicSourceType.MEMBER_FIELD, MemberFieldSource.ACTIVE))
                .thenReturn(Optional.of(activeGroup));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));

        // Active member → added
        service.syncMemberForActiveField(active);
        assertThat(activeGroup.getMembers()).hasSize(1);
        assertThat(activeGroup.getMembers().get(0).getMember()).isEqualTo(active);

        // Deactivate → removed
        active.deactivate();
        service.syncMemberForActiveField(active);
        assertThat(activeGroup.getMembers()).isEmpty();
    }

    @Test
    void syncForActiveField_isNoOpWhenNoGroupExists() {
        Member active = activeMember();
        when(groupRepository.findByDynamicSourceTypeAndDynamicSourceKey(DynamicSourceType.MEMBER_FIELD, MemberFieldSource.ACTIVE))
                .thenReturn(Optional.empty());

        service.syncMemberForActiveField(active); // must not throw
        verify(groupRepository, never()).save(any());
    }

    @Test
    void createDynamicGroupForMemberField_isIdempotent() {
        when(groupRepository.findByDynamicSourceTypeAndDynamicSourceKey(DynamicSourceType.MEMBER_FIELD, MemberFieldSource.ACTIVE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(Group.createDynamicForMemberField(MemberFieldSource.ACTIVE, BAND)));
        when(groupRepository.existsByNameAndBandId(any(), any())).thenReturn(false);
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));

        Group first = service.createDynamicGroupForMemberField(MemberFieldSource.ACTIVE, BAND);
        Group second = service.createDynamicGroupForMemberField(MemberFieldSource.ACTIVE, BAND);

        assertThat(first.getId()).isNull(); // not asserted further; identity not persisted in unit test
        assertThat(first.getDynamicSourceType()).isEqualTo(DynamicSourceType.MEMBER_FIELD);
        assertThat(first.getDynamicSourceKey()).isEqualTo(MemberFieldSource.ACTIVE);
        assertThat(first.getName()).isEqualTo("Aktywni");
        verify(groupRepository, times(1)).save(any());
    }
}
