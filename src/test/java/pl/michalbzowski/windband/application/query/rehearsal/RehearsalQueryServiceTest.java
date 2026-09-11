package pl.michalbzowski.windband.application.query.rehearsal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.michalbzowski.windband.application.dto.InviteOptionsDto;
import pl.michalbzowski.windband.application.dto.MemberDto;
import pl.michalbzowski.windband.application.query.member.GroupQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.domain.member.GroupRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.rehearsal.Attendance;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;
import pl.michalbzowski.windband.domain.rehearsal.RehearsalRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * t_5c77c4cb — unit tests for {@link RehearsalQueryService#getInviteOptions},
 * mirroring the event-side contract ({@code EventQueryServiceTest}). The method
 * returns the band's groups (with their member id lists for client-side dedup)
 * plus the active members <i>NOT</i> yet invited to this rehearsal.
 */
class RehearsalQueryServiceTest {

    private RehearsalRepository rehearsalRepository;
    private GroupQueryService groupQueryService;
    private MemberQueryService memberQueryService;
    private RehearsalQueryService service;

    @BeforeEach
    void setUp() {
        rehearsalRepository = mock(RehearsalRepository.class);
        groupQueryService = mock(GroupQueryService.class);
        memberQueryService = mock(MemberQueryService.class);

        Member invited = mock(Member.class);
        given(invited.getId()).willReturn(10L);
        Attendance attendance = mock(Attendance.class);
        given(attendance.getMember()).willReturn(invited);

        Rehearsal rehearsal = mock(Rehearsal.class);
        given(rehearsal.getAttendances()).willReturn(List.of(attendance));
        given(rehearsal.getBand()).willReturn(null);
        given(rehearsalRepository.findById(1L)).willReturn(Optional.of(rehearsal));

        service = new RehearsalQueryService(
                rehearsalRepository, groupQueryService, memberQueryService,
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
