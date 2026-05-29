package pl.michalbzowski.windband.adapter.in.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class TeamAwareUserDetails implements UserDetails {

    private final Long userId;
    private final String username;
    private final String email;
    private final String password;
    private final boolean active;
    private final Long activeTeamId;
    private final String activeTeamSlug;
    private final String activeTeamRole;
    private final List<Long> teamIds;

    public TeamAwareUserDetails(Long userId, String username, String email, String password,
                                 boolean active, Long activeTeamId, String activeTeamSlug,
                                 String activeTeamRole, List<Long> teamIds) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.password = password;
        this.active = active;
        this.activeTeamId = activeTeamId;
        this.activeTeamSlug = activeTeamSlug;
        this.activeTeamRole = activeTeamRole;
        this.teamIds = teamIds;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (activeTeamRole != null) {
            return List.of(new SimpleGrantedAuthority("ROLE_" + activeTeamRole));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_MEMBER"));
    }

    @Override
    public String getPassword() { return password; }

    @Override
    public String getUsername() { return username; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return active; }

    public Long getUserId() { return userId; }

    public String getEmail() { return email; }

    public Long getActiveTeamId() { return activeTeamId; }

    public String getActiveTeamSlug() { return activeTeamSlug; }

    public String getActiveTeamRole() { return activeTeamRole; }

    public List<Long> getTeamIds() { return teamIds; }

    public boolean isAdmin() {
        return "ADMIN".equals(activeTeamRole);
    }

    public boolean belongsToTeam(Long teamId) {
        return teamIds != null && teamIds.contains(teamId);
    }
}
