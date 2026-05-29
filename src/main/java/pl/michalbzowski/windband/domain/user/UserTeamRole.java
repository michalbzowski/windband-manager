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
        var utr = new UserTeamRole(user, team, role);
        utr.invitationToken = invitationToken;
        utr.invitationAccepted = false;
        return utr;
    }

    public void acceptInvitation() {
        this.invitationAccepted = true;
        this.invitationAcceptedAt = LocalDateTime.now();
        this.invitationToken = null;
    }

    public boolean isAdmin() {
        return role == TeamRole.ADMIN;
    }
}
