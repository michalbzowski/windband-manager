package pl.michalbzowski.windband.application.query.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.michalbzowski.windband.application.dto.InviteOptionsDto;
import pl.michalbzowski.windband.application.dto.MemberDto;
import pl.michalbzowski.windband.application.query.member.GroupQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.EventInvitationRepository;
import pl.michalbzowski.windband.domain.event.EventParticipation;
import pl.michalbzowski.windband.domain.event.EventRepository;
import pl.michalbzowski.windband.domain.member.GroupRepository;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventQueryServiceTest {

    private GroupQueryService groupQueryService;
    private MemberQueryService memberQueryService;
    private EventRepository eventRepository;
    private EventQueryService service;


    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        groupQueryService = mock(GroupQueryService.class);
        memberQueryService = mock(MemberQueryService.class);

        BandEvent event = mock(BandEvent.class);
        Member invited = mock(Member.class);
        EventParticipation participation = mock(EventParticipation.class);
        when(invited.getId()).thenReturn(10L);
        when(participation.getMember()).thenReturn(invited);
        when(event.getParticipations()).thenReturn(List.of(participation));
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        service = new EventQueryService(
                eventRepository, groupQueryService,
                mock(InstrumentRepository.class),
                mock(EventInvitationRepository.class),
                null, memberQueryService,
                mock(GroupRepository.class));
    }

    @Test
    void getInviteOptions_excludesAlreadyInvitedMembers() {
        MemberDto invitedDto = dto(10L, "Ala", "Kowalska");
        MemberDto newDto = dto(20L, "Nowy", "Czlonk");
        given(memberQueryService.getAllActiveMembers(0L)).willReturn(List.of(invitedDto, newDto));
        given(groupQueryService.getAllGroups(0L)).willReturn(List.of());

        InviteOptionsDto opts = service.getInviteOptions(1L, null);

        assertThat(opts.members()).hasSize(1);
        assertThat(opts.members().get(0).id()).isEqualTo(20L);
    }

    private static MemberDto dto(Long id, String firstName, String lastName) {
        return new MemberDto(
                id, firstName, lastName, null, 0, false, false,
                null, null, true, null, List.of(),
                null, null, null, null, true);
    }

}
