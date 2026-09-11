package pl.michalbzowski.windband.application.query.rehearsal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.michalbzowski.windband.application.dto.InviteOptionsDto;
import pl.michalbzowski.windband.application.dto.MemberDto;
import pl.michalbzowski.windband.application.query.member.GroupQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.domain.member.GroupRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.rehearsal.AttendanceStatus;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;
import pl.michalbzowski.windband.domain.rehearsal.Attendance;
import pl.michalbzowski.windband.domain.rehearsal.RehearsalRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * t_5c77c4cb: the unified invite modal on the rehearsal detail page sources
 * its data from {@code GET /api/rehearsals/{id}/invite-options}. This test
 * guards that the options endpoint excludes members who already have an
 * {@code Attendance} row (the "invited" set) — same contract as the events
 * page's equivalent ({@code EventQueryServiceTest}).
 */
class RehearsalQueryServiceInviteOptionsTest {

    private GroupQueryService groupQueryService;
    private MemberQueryService memberQueryService;
    private RehearsalRepository rehearsalRepository;
    private RehearsalQueryService service;

    @BeforeEach
    void setUp() {
        groupQueryService = mock(GroupQueryService.class);
        memberQueryService = mock(MemberQueryService.class);
        rehearsalRepository = mock(RehearsalRepository.class);

        Rehearsal rehearsal = mock(Rehearsal.class);
        Member invited = mock(Member.class);
        when(invited.getId()).thenReturn(10L);
        Attendance attendance = mock(Attendance.class);
        when(attendance.getMember()).thenReturn(invited);
        when(attendance.getStatus()).thenReturn(AttendanceStatus.NO_RESPONSE);
        when(rehearsal.getAttendances()).thenReturn(List.of(attendance));
        when(rehearsalRepository.findById(5L)).thenReturn(Optional.of(rehearsal));

        service = new RehearsalQueryService(
                rehearsalRepository, groupQueryService, memberQueryService,
                mock(GroupRepository.class));
    }

    @Test
    void getInviteOptions_excludesAlreadyInvitedMembers() {
        given(memberQueryService.getAllActiveMembers(0L))
                .willReturn(List.of(dto(10L, "Ala", "Kowalska"), dto(20L, "Nowy", "Czlonk")));
        given(groupQueryService.getAllGroups(0L)).willReturn(List.of());

        InviteOptionsDto opts = service.getInviteOptions(5L, null);

        assertThat(opts.members()).hasSize(1);
        assertThat(opts.members().get(0).id()).isEqualTo(20L);
        assertThat(opts.groups()).isEmpty();
    }

    private static MemberDto dto(Long id, String firstName, String lastName) {
        return new MemberDto(
                id, firstName, lastName, null, 0, false, false,
                null, null, true, null, List.of(),
                null, null, null, null, true);
    }
}
