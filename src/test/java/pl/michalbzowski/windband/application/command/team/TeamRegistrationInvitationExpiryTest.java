package pl.michalbzowski.windband.application.command.team;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.user.AppUser;
import pl.michalbzowski.windband.domain.user.TeamRole;
import pl.michalbzowski.windband.domain.user.UserTeamRole;
import pl.michalbzowski.windband.domain.user.UserTeamRoleRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pentest P2 (A01): tokeny zaproszeń bez expiary.
 *
 * <p>Testy czarne: wygasłe zaproszenie musi zostać odrzucone z tą samą, generyczną
 * odpowiedzią co nieważny token (brak orakla), a aktywnego nie wolno blokować.
 */
class TeamRegistrationInvitationExpiryTest {

    private static final Long TEAM_ID = 1L;

    private BandRepository bandRepository;
    private pl.michalbzowski.windband.domain.user.AppUserRepository appUserRepository;
    private UserTeamRoleRepository userTeamRoleRepository;
    private PasswordEncoder passwordEncoder;
    private TeamRegistrationService service;

    @BeforeEach
    void setUp() {
        bandRepository = mock(BandRepository.class);
        appUserRepository = mock(pl.michalbzowski.windband.domain.user.AppUserRepository.class);
        userTeamRoleRepository = mock(UserTeamRoleRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();

        service = new TeamRegistrationService(
                bandRepository, appUserRepository, userTeamRoleRepository, passwordEncoder);
        ReflectionTestUtils.setField(service, "invitationExpiryDays", 7L);
    }

    private AppUser existingUser() {
        AppUser user = AppUser.create("jan_kowalski", "jan@example.com", "hash");
        return user;
    }

    @Test
    @DisplayName("P2: zaproszenie w terminie — akceptacja przechodzi")
    void acceptInvitation_withinExpiry_succeeds() {
        AppUser invitee = existingUser();
        Band team = Band.create("Zespół Testowy", "testy");
        UserTeamRole role = UserTeamRole.createInvitation(
                invitee, team, TeamRole.MEMBER, "token-aktywny",
                LocalDateTime.now().plusDays(1));

        when(userTeamRoleRepository.findByInvitationToken("token-aktywny"))
                .thenReturn(java.util.Optional.of(role));

        service.acceptInvitation("token-aktywny", "new-password", "janek");

        assertThat(role.isInvitationAccepted()).isTrue();
        assertThat(role.getInvitationToken()).isNull();
        verify(userTeamRoleRepository).save(role);
    }

    @Test
    @DisplayName("P2: wygasłe zaproszenie — 403/400 z generycznym komunikatem (bez orakla)")
    void acceptInvitation_expiredToken_rejectedLikeInvalid() {
        AppUser invitee = existingUser();
        Band team = Band.create("Zespół Testowy", "testy");
        UserTeamRole role = UserTeamRole.createInvitation(
                invitee, team, TeamRole.MEMBER, "token-stary",
                LocalDateTime.now().minusDays(1));

        when(userTeamRoleRepository.findByInvitationToken("token-stary"))
                .thenReturn(java.util.Optional.of(role));

        assertThatThrownBy(() -> service.acceptInvitation("token-stary", "new-password", "janek"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid or expired invitation");

        assertThat(role.isInvitationAccepted()).isFalse();
        verify(userTeamRoleRepository, Mockito.never()).save(any(UserTeamRole.class));
    }

    @Test
    @DisplayName("P2: legacy-token bez expiary (NULL) — grandfathering, wciąż akceptowalny")
    void acceptInvitation_nullExpiry_stillAccepted() {
        AppUser invitee = existingUser();
        Band team = Band.create("Zespół Testowy", "testy");
        UserTeamRole role = UserTeamRole.createInvitation(
                invitee, team, TeamRole.MEMBER, "token-legacy", null);

        when(userTeamRoleRepository.findByInvitationToken("token-legacy"))
                .thenReturn(java.util.Optional.of(role));

        service.acceptInvitation("token-legacy", "new-password", "janek");

        assertThat(role.isInvitationAccepted()).isTrue();
    }

    @Test
    @DisplayName("P2: nowe zaproszenie dostaje expires_at = teraz + 7 dni (domyślnie)")
    void inviteUserToTeam_setsSevenDayExpiry() {
        AppUser admin = mock(AppUser.class);
        when(admin.getId()).thenReturn(99L);
        when(admin.isAdminOf(TEAM_ID)).thenReturn(true);
        when(appUserRepository.findById(99L)).thenReturn(java.util.Optional.of(admin));

        Band team = Band.create("Zespół Testowy", "testy");
        ReflectionTestUtils.setField(team, "id", TEAM_ID);
        when(bandRepository.findById(TEAM_ID)).thenReturn(java.util.Optional.of(team));

        AppUser invitee = existingUser();
        when(appUserRepository.findByEmail("jan@example.com")).thenReturn(java.util.Optional.of(invitee));
        when(userTeamRoleRepository.existsByUserIdAndTeamId(any(), any())).thenReturn(false);
        when(userTeamRoleRepository.save(any(UserTeamRole.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var cmd = new InviteUserCommand();
        cmd.setEmail("jan@example.com");
        cmd.setRole(TeamRole.MEMBER);

        service.inviteUserToTeam(99L, TEAM_ID, cmd);

        org.mockito.ArgumentCaptor<UserTeamRole> captor =
                org.mockito.ArgumentCaptor.forClass(UserTeamRole.class);
        verify(userTeamRoleRepository).save(captor.capture());

        LocalDateTime saved = captor.getValue().getInvitationExpiresAt();
        assertThat(saved).isNotNull();
        assertThat(saved).isAfter(LocalDateTime.now().plusDays(6));
        assertThat(saved).isBefore(LocalDateTime.now().plusDays(8));
    }
}
