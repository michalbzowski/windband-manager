package pl.michalbzowski.windband.application.query.team;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * v1 inventory integration contract (B10): bands-with-roles for the requesting
 * user, and band roster with expected sets — including authz failure (403) for
 * foreign users.
 */
@ExtendWith(MockitoExtension.class)
class BandInventoryApiQueryServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long BAND_ID = 1L;
    private static final LocalDate DOB = LocalDate.of(1990, 5, 1);

    @Mock
    private TeamQueryService teamQueryService;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private BandRepository bandRepository;

    private BandInventoryApiQueryService service;

    @BeforeEach
    void setUp() {
        service = new BandInventoryApiQueryService(teamQueryService, memberRepository, bandRepository);
    }

    // --- GET /api/v1/me/bands ---

    @Test
    @DisplayName("myBands: maps the user's teams with their per-band roles, name order preserved")
    void myBands_mapsTeamsWithRoles() {
        when(teamQueryService.getUserTeams(USER_ID)).thenReturn(List.of(
                new TeamQueryService.UserTeamDto(1L, "Sokole", "sokole", "ADMIN"),
                new TeamQueryService.UserTeamDto(9L, "Echo", "echo", "MEMBER")));

        List<BandInventoryApiQueryService.BandView> bands = service.myBands(USER_ID);

        assertThat(bands).containsExactly(
                new BandInventoryApiQueryService.BandView(1L, "Sokole", "ADMIN"),
                new BandInventoryApiQueryService.BandView(9L, "Echo", "MEMBER"));
    }

    // --- GET /api/v1/bands/{bandId}/members ---

    @Test
    @DisplayName("bandMembers: roster mapped with expected sets (instrument + primary flag), active flag kept")
    void bandMembers_mapsRosterWithExpectedSet() {
        Band band = Band.create("Sokole", "sokole");
        Member anna = Member.create("Anna", "Kowalska", DOB, band);
        anna.addInstrument(Instrument.create("trąbka"), true);
        anna.addInstrument(Instrument.create("flugelhorn"), false);
        Member jan = Member.create("Jan", "Nowak", DOB, band);
        jan.update("Jan", "Nowak", DOB, false);

        when(teamQueryService.getUserTeam(USER_ID, BAND_ID))
                .thenReturn(Optional.of(new TeamQueryService.UserTeamDto(BAND_ID, "Sokole", "sokole", "ADMIN")));
        when(bandRepository.findById(BAND_ID)).thenReturn(Optional.of(band));
        when(memberRepository.findAllByBandOrderByLastNameAscFirstNameAsc(any()))
                .thenReturn(List.of(anna, jan));

        List<BandInventoryApiQueryService.MemberView> members =
                service.bandMembersWithExpectedSet(USER_ID, BAND_ID);

        assertThat(members).hasSize(2);
        assertThat(members.get(0).memberId()).isEqualTo(anna.getId());
        assertThat(members.get(0).active()).isTrue();
        assertThat(members.get(0).expectedSet()).containsExactlyInAnyOrder(
                new BandInventoryApiQueryService.ExpectedEntry("trąbka", true),
                new BandInventoryApiQueryService.ExpectedEntry("flugelhorn", false));
        // Primary entry comes first (stable tie-break by name when sortPriority equal)
        assertThat(members.get(0).expectedSet().get(0).primary()).isTrue();

        assertThat(members.get(1).active()).isFalse();
        assertThat(members.get(1).expectedSet()).isEmpty();
    }

    @Test
    @DisplayName("bandMembers: user without any membership in the band → NotBandMemberException (→403)")
    void bandMembers_foreignUser_denied() {
        when(teamQueryService.getUserTeam(USER_ID, 99L)).thenReturn(Optional.empty());

        BandInventoryApiQueryService.NotBandMemberException ex = catchThrowable(() ->
                service.bandMembersWithExpectedSet(USER_ID, 99L));

        assertThat(ex.getBandId()).isEqualTo(99L);
        verifyNoInteractions(memberRepository, bandRepository);
    }

    @Test
    @DisplayName("bandMembers: member of an unknown band id → IllegalArgumentException (→400/consistent repo mapping)")
    void bandMembers_unknownBand_throwsIllegalArgument() {
        when(teamQueryService.getUserTeam(USER_ID, 99L))
                .thenReturn(Optional.of(new TeamQueryService.UserTeamDto(99L, "Ghost", "ghost", "MEMBER")));
        when(bandRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bandMembersWithExpectedSet(USER_ID, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    private static BandInventoryApiQueryService.NotBandMemberException catchThrowable(Runnable r) {
        try {
            r.run();
        } catch (BandInventoryApiQueryService.NotBandMemberException expected) {
            return expected;
        }
        throw new AssertionError("expected NotBandMemberException");
    }
}
