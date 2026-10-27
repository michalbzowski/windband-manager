package pl.michalbzowski.windband.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.band.Band;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "user_team_roles", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "team_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTeamRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Band team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TeamRole role;

    @Column(nullable = false)
    private LocalDateTime assignedAt = LocalDateTime.now();

    @Column(length = 64)
    private String invitationToken;

    private boolean invitationAccepted = false;

    private LocalDateTime invitationAcceptedAt;

    /**
     * Pentest P2 (A01): termin ważności zaproszenia. NULL = zaproszenie bez
     * wygasania (dziedzictwo sprzed migracji V50); nowe zaproszenia zawsze go
     * dostają (domyślnie 7 dni, patrz app.team.invitation-expiry-days).
     */
    @Column(name = "invitation_expires_at")
    private LocalDateTime invitationExpiresAt;

    private UserTeamRole(AppUser user, Band team, TeamRole role) {
        this.user = Objects.requireNonNull(user);
        this.team = Objects.requireNonNull(team);
        this.role = Objects.requireNonNull(role);
    }

    public static UserTeamRole createAdmin(AppUser user, Band team) {
        return new UserTeamRole(user, team, TeamRole.ADMIN);
    }

    public static UserTeamRole createMember(AppUser user, Band team) {
        return new UserTeamRole(user, team, TeamRole.MEMBER);
    }

    public static UserTeamRole createInvitation(AppUser user, Band team, TeamRole role, String invitationToken) {
        return createInvitation(user, team, role, invitationToken, null);
    }

    public static UserTeamRole createInvitation(AppUser user, Band team, TeamRole role,
                                                String invitationToken, LocalDateTime expiresAt) {
        var utr = new UserTeamRole(user, team, role);
        utr.invitationToken = invitationToken;
        utr.invitationAccepted = false;
        utr.invitationExpiresAt = expiresAt;
        return utr;
    }

    /** Prawda tylko dla aktywnego zaproszenia z wyznaczonym terminem (NULL = bezterminowe). */
    public boolean isInvitationExpired(LocalDateTime now) {
        return invitationExpiresAt != null && now.isAfter(invitationExpiresAt);
    }

    public void acceptInvitation() {
        this.invitationAccepted = true;
        this.invitationAcceptedAt = LocalDateTime.now();
        this.invitationToken = null;
        this.invitationExpiresAt = null;
    }

    public boolean isAdmin() {
        return role == TeamRole.ADMIN;
    }
}
