package pl.michalbzowski.windband.adapter.in.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import pl.michalbzowski.windband.domain.user.AppUser;
import pl.michalbzowski.windband.domain.user.AppUserRepository;
import pl.michalbzowski.windband.domain.user.UserTeamRole;
import pl.michalbzowski.windband.domain.user.UserTeamRoleRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Maps a Keycloak-validated Bearer JWT onto an OIDC principal for the v1
 * inventory integration API (B10). Plugged into the resource-server chain via
 * {@code jwt.jwtAuthenticationConverter(...)}.
 *
 * <p>Read-only by design (unlike {@link KeycloakOAuth2UserService}, which
 * auto-provisions on session login): a token that cannot be linked to an
 * existing active WM user is returned WITHOUT enrichment and without any
 * ROLE_* authorities, so every endpoint in the v1 chain denies it (403) and
 * no other user's data can leak — not even accidentally.
 */
@Slf4j
public class BearerOidcUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AppUserRepository appUserRepository;
    private final UserTeamRoleRepository userTeamRoleRepository;

    public BearerOidcUserConverter(AppUserRepository appUserRepository, UserTeamRoleRepository userTeamRoleRepository) {
        this.appUserRepository = appUserRepository;
        this.userTeamRoleRepository = userTeamRoleRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AppUser appUser = resolveAppUser(jwt);
        Collection<? extends GrantedAuthority> authorities;
        OidcUser principal;

        if (appUser != null && appUser.isActive()) {
            List<UserTeamRole> teamRoles = userTeamRoleRepository.loadWithBandsByUserId(appUser.getId());
            authorities = KeycloakOAuth2UserService.authoritiesFor(appUser, teamRoles);
            principal = new DefaultOidcUser(authorities, toIdToken(jwt, subjectLabel(appUser, jwt)), toUserInfo(jwt));
            principal = KeycloakOAuth2UserService.enrich(principal, appUser, teamRoles);
        } else {
            authorities = List.of();
            log.warn("Bearer token of unresolvable or inactive WM user (subject={}); denying v1 API access",
                    jwt.getSubject());
            String label = firstNonNull(jwt.getClaims().get("preferred_username"), jwt.getClaims().get("email"));
            principal = new DefaultOidcUser(authorities, toIdToken(jwt, label), toUserInfo(jwt));
        }

        return new BearerOidcAuthentication(principal, authorities);
    }

    /** Stable principal name for a resolved WM user whose token lacks "sub": linked
     * Keycloak ID first, then token labels, then a synthetic local id — never null. */
    private static String subjectLabel(AppUser appUser, Jwt jwt) {
        String label = firstNonNull(appUser.getExternalId(),
                jwt.getClaims().get("preferred_username"), jwt.getClaims().get("email"));
        if (label != null) {
            return label;
        }
        log.warn("WM user id={} has no externalId and the token carries no username/email; "
                + "falling back to synthetic local subject", appUser.getId());
        return "wm-local-" + appUser.getId();
    }

    private OidcIdToken toIdToken(Jwt jwt, String fallbackSubject) {
        Map<String, Object> claims = new java.util.HashMap<>(jwt.getClaims());
        if (!claims.containsKey("sub") && fallbackSubject != null) {
            // Keycloak access tokens for the public inventory client do not carry a
            // "sub" claim (only the id_token does), while DefaultOidcUser requires one
            // as its name attribute. Use the linked WM user's stable external ID when
            // resolvable, the token label otherwise. This is principal naming, not
            // identity: signature + email lookup already proved who this is.
            claims.put("sub", fallbackSubject);
        }
        return new OidcIdToken(jwt.getTokenValue(),
                claimInstant(claims, "iat"), claimInstant(claims, "exp"), claims);
    }

    private static String firstNonNull(Object... values) {
        for (Object v : values) {
            if (v != null && !v.toString().isBlank()) {
                return v.toString();
            }
        }
        return "unknown-inventory-user";
    }

    private OidcUserInfo toUserInfo(Jwt jwt) {
        return new OidcUserInfo(jwt.getClaims());
    }

    /** Read-only lookup: Keycloak subject (externalId) → email. No auto-provisioning here. */
    private AppUser resolveAppUser(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject != null) {
            var byExternalId = appUserRepository.findByExternalId(subject);
            if (byExternalId.isPresent()) {
                return byExternalId.get();
            }
        }
        Object emailClaim = jwt.getClaims().get("email");
        if (emailClaim instanceof String email) {
            return appUserRepository.findByEmail(email).orElse(null);
        }
        return null;
    }

    private static Instant claimInstant(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        if (value instanceof Number n) {
            return Instant.ofEpochSecond(n.longValue());
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return null;
    }
}
