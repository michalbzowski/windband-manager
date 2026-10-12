package pl.michalbzowski.windband.adapter.in.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * Authentication for the v1 Bearer API: principal is an {@code OidcUser}
 * (enriched {@link WindbandOidcUser} when the caller resolved to a WM user),
 * authorities come from WM's own roles — never from token claims.
 */
public class BearerOidcAuthentication extends AbstractAuthenticationToken {

    private final Object principal;

    public BearerOidcAuthentication(Object principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities == null ? List.of() : List.copyOf(authorities));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public Object getCredentials() {
        return null;
    }
}
