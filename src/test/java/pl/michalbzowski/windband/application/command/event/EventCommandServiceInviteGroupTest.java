package pl.michalbzowski.windband.application.command.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.band.MemberAttributeValueRepository;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.EventRepository;
import pl.michalbzowski.windband.domain.event.EventType;
import pl.michalbzowski.windband.domain.event.PaymentType;
import pl.michalbzowski.windband.domain.member.Group;
import pl.michalbzowski.windband.domain.member.GroupRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventCommandServiceInviteGroupTest {

    @Mock EventRepository eventRepository;
    @Mock MemberRepository memberRepository;
    @Mock GroupRepository groupRepository;
    @Mock BandRepository bandRepository;
    @Mock MemberAttributeValueRepository memberAttributeValueRepository;
    @Mock NotificationCommandService notificationCommandService;

    @InjectMocks EventCommandService commandService;

    @Test
    void inviteGroup_forDynamicGroup_queriesBandMembersNotGroupMembers() {
        // Dynamic group (attribute-backed) has NO explicit members in member_groups_members
        Band band = Band.create("Test Band", "test-band");
        MemberAttributeDef attr = MemberAttributeDef.create(band, "Grający", "BOOLEAN", false, 1, null);
        Group dynamicGroup = Group.createDynamic("Grający", band, attr);

        Member alfa = Member.create("Alfa", "Kowalski", java.time.LocalDate.now(), band);
        Member beta = Member.create("Beta", "Nowak", java.time.LocalDate.now(), band);
        setId(alfa, 1L);
        setId(beta, 2L);

        when(memberAttributeValueRepository.findByMemberAndAttributeDef(any(), eq(attr)))
                .thenReturn(Optional.of(value(attr, "true")));
        when(memberRepository.findAllActiveByBandId(band.getId())).thenReturn(List.of(alfa, beta));
        when(groupRepository.findById(10L)).thenReturn(Optional.of(dynamicGroup));

        BandEvent event = BandEvent.create("Wydarzenie", java.time.LocalDate.now(),
                java.time.LocalTime.of(18, 0), "Rynek",
                EventType.CONCERT, band, PaymentType.FREE, java.math.BigDecimal.ZERO);
        when(eventRepository.findById(99L)).thenReturn(Optional.of(event));

        InviteGroupCommand cmd = new InviteGroupCommand();
        cmd.setEventId(99L);
        cmd.setGroupId(10L);
        commandService.inviteGroup(cmd);

        // Critical: for a dynamic group the members must come from the BAND query,
        // not from group.getMembers() (which is empty for dynamic groups).
        verify(memberRepository).findAllActiveByBandId(band.getId());
        verify(eventRepository).save(event);
        verify(notificationCommandService).createInvitationsForEvent(99L);
    }

    private pl.michalbzowski.windband.domain.band.MemberAttributeValue value(MemberAttributeDef def, String v) {
        return pl.michalbzowski.windband.domain.band.MemberAttributeValue.create(
                Member.create("X", "Y", java.time.LocalDate.now(), Band.create("b", "b")), def, v);
    }

    private static void setId(Member m, Long id) {
        try {
            var f = Member.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(m, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
