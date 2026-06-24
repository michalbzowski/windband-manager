package pl.michalbzowski.windband.adapter.in.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Enriched OIDC user that wraps a standard OidcUser
 * and adds windband-manager domain data (team info on it).
 */
public class WindbandOidcUser implements OidcUser {

    private final OidcUser delegate;
    private final Long userId;
    private final String username;
    private final String email;
    private final boolean active;
    private final boolean systemAdmin;
    private final Long activeTeamId;
    private final String activeTeamSlug;
    private final String activeTeamRole;
    private final List<Long> teamIds;

    public WindbandOidcUser(OidcUser delegate, Long userId, String username, String email,
                            boolean active, boolean systemAdmin, Long activeTeamId, String activeTeamSlug,
                            String activeTeamRole, List<Long> teamIds) {
        this.delegate = delegate;
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.active = active;
        this.systemAdmin = systemAdmin;
        this.activeTeamId = activeTeamId;
        this.activeTeamSlug = activeTeamSlug;
        this.activeTeamRole = activeTeamRole;
        this.teamIds = teamIds;
    }

    // === Windband domain data ===
    public Long getUserId() { return userId; }
    public String getWbUsername() { return username; }
    public String getWbEmail() { return email; }
    public boolean isWbActive() { return active; }
    public Long getActiveTeamId() { return activeTeamId; }
    public String getActiveTeamSlug() { return activeTeamSlug; }
    public String getActiveTeamRole() { return activeTeamRole; }
    public List<Long> getTeamIds() { return teamIds; }

    public boolean isAdmin() {
        return "ADMIN".equals(activeTeamRole);
    }

    public boolean isSystemAdmin() {
        return systemAdmin;
    }

    /**
     * Returns true if user is admin of the active team OR is system admin.
     * Used for general admin checks (e.g., /admin/** endpoints).
     */
    public boolean hasAnyAdminRole() {
        return isAdmin() || isSystemAdmin();
    }

    public boolean belongsToTeam(Long teamId) {
        return teamIds != null && teamIds.contains(teamId);
    }

    // === Delegate OidcUser methods ===
    @Override public Map<String, Object> getClaims() { return delegate.getClaims(); }
    @Override public OidcUserInfo getUserInfo() { return delegate.getUserInfo(); }
    @Override public OidcIdToken getIdToken() { return delegate.getIdToken(); }
    @Override public Map<String, Object> getAttributes() { return delegate.getAttributes(); }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return delegate.getAuthorities(); }
    @Override public String getName() { return delegate.getName(); }
}
