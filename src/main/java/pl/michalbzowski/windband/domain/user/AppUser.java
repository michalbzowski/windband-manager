package pl.michalbzowski.windband.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "app_users", uniqueConstraints = {
    @UniqueConstraint(columnNames = "email"),
    @UniqueConstraint(columnNames = "username")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, unique = true, length = 128)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime lastLoginAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserTeamRole> teamRoles = new HashSet<>();

    private AppUser(String username, String email, String passwordHash) {
        this.username = Objects.requireNonNull(username, "username required");
        this.email = Objects.requireNonNull(email, "email required");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash required");
    }

    public static AppUser create(String username, String email, String passwordHash) {
        return new AppUser(username, email, passwordHash);
    }

    public void markEmailVerified() {
        this.emailVerified = true;
    }

    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.active = false;
    }

    public void reactivate() {
        this.active = true;
    }

    public void updatePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "passwordHash required");
    }

    public void updateUsername(String newUsername) {
        this.username = Objects.requireNonNull(newUsername, "username required");
    }

    /**
     * Accept invitation: set password, username, activate account.
     */
    public void acceptInvitation(String newPasswordHash, String newUsername) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash);
        this.username = Objects.requireNonNull(newUsername);
        this.active = true;
        this.emailVerified = true;
    }

    public boolean hasTeam(Long teamId) {
        return teamRoles.stream().anyMatch(t -> t.getTeam().getId().equals(teamId));
    }

    public boolean isAdminOf(Long teamId) {
        return teamRoles.stream()
                .filter(t -> t.getTeam().getId().equals(teamId))
                .anyMatch(t -> t.getRole() == TeamRole.ADMIN);
    }

    public String getDisplayName() {
        return username;
    }
}
