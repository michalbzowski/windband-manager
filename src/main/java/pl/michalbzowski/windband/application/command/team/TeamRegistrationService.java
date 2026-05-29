package pl.michalbzowski.windband.application.command.team;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.user.*;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TeamRegistrationService {

    private final BandRepository bandRepository;
    private final AppUserRepository appUserRepository;
    private final UserTeamRoleRepository userTeamRoleRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Public team registration. Creates a Band + admin AppUser + UserTeamRole(ADMIN).
     * A single person can register multiple teams.
     */
    public TeamRegistrationResult registerTeam(RegisterTeamCommand cmd) {
        // Validate uniqueness
        if (bandRepository.existsBySlug(cmd.getTeamSlug())) {
            throw new IllegalArgumentException("Team slug already taken: " + cmd.getTeamSlug());
        }
        if (appUserRepository.existsByUsername(cmd.getAdminUsername())) {
            throw new IllegalArgumentException("Username already taken: " + cmd.getAdminUsername());
        }
        if (appUserRepository.existsByEmail(cmd.getAdminEmail())) {
            throw new IllegalArgumentException("Email already in use: " + cmd.getAdminEmail());
        }

        // Create team/band
        Band band = Band.create(cmd.getTeamName(), cmd.getTeamSlug());
        band = bandRepository.save(band);

        // Create admin user
        String passwordHash = passwordEncoder.encode(cmd.getAdminPassword());
        AppUser admin = AppUser.create(cmd.getAdminUsername(), cmd.getAdminEmail(), passwordHash);
        admin = appUserRepository.save(admin);

        // Link user to team as ADMIN
        UserTeamRole role = UserTeamRole.createAdmin(admin, band);
        userTeamRoleRepository.save(role);

        return new TeamRegistrationResult(band.getId(), band.getSlug(), admin.getId(), admin.getUsername());
    }

    /**
     * Invite a user to a team. Only admins of that team can invite.
     * Returns an invitation token that the user must accept.
     */
    public String inviteUserToTeam(Long adminUserId, Long teamId, InviteUserCommand cmd) {
        AppUser admin = appUserRepository.findById(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));

        if (!admin.isAdminOf(teamId)) {
            throw new SecurityException("User is not admin of team " + teamId);
        }

        Band team = bandRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        // If user already exists, add them; otherwise create invitation record
        AppUser user = appUserRepository.findByEmail(cmd.getEmail())
                .orElseGet(() -> {
                    // Create inactive user - they will set password on acceptance
                    String placeholderHash = passwordEncoder.encode(UUID.randomUUID().toString());
                    AppUser newUser = AppUser.create(
                            cmd.getEmail().split("@")[0] + "_" + System.currentTimeMillis(),
                            cmd.getEmail(),
                            placeholderHash
                    );
                    newUser.deactivate();
                    return appUserRepository.save(newUser);
                });

        if (userTeamRoleRepository.existsByUserIdAndTeamId(user.getId(), teamId)) {
            throw new IllegalStateException("User already belongs to this team");
        }

        String invitationToken = UUID.randomUUID().toString();
        UserTeamRole role = UserTeamRole.createInvitation(user, team, cmd.getRole(), invitationToken);
        userTeamRoleRepository.save(role);

        return invitationToken;
    }

    /**
     * Accept an invitation. User sets their password and activates account.
     */
    public void acceptInvitation(String token, String newPassword, String username) {
        UserTeamRole role = userTeamRoleRepository.findByInvitationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired invitation"));

        if (role.isInvitationAccepted()) {
            throw new IllegalStateException("Invitation already accepted");
        }

        AppUser user = role.getUser();
        String newHash = passwordEncoder.encode(newPassword);

        // Need to update password hash on user - add method to AppUser
        user.acceptInvitation(newHash, username);

        role.acceptInvitation();
        userTeamRoleRepository.save(role);
    }

    // --- DTOs ---

    public record TeamRegistrationResult(Long teamId, String teamSlug, Long adminUserId, String adminUsername) {}
}
