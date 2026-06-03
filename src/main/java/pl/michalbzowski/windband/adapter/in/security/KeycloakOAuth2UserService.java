package pl.michalbzowski.windband.adapter.in.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import pl.michalbzowski.windband.domain.user.AppUser;
import pl.michalbzowski.windband.domain.user.AppUserRepository;
import pl.michalbzowski.windband.domain.user.UserTeamRoleRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Custom OAuth2UserService that bridges Keycloak OIDC authentication
 * with windband-manager's domain model.
 *
 * On first login from Keycloak:
 *  1. Looks up AppUser by Keycloak subject ID (stored as "externalId")
 *  2. Falls back to lookup by email
 *  3. If not found, creates a new AppUser (auto-provisioning)
 *
 * Loads team roles from windband-manager's database.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakOAuth2UserService extends OidcUserService {

    private final AppUserRepository appUserRepository;
    private final UserTeamRoleRepository userTeamRoleRepository;

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        log.info("KeycloakOAuth2UserService.loadUser called for client: {}", userRequest.getClientRegistration().getRegistrationId());

        // Manually fetch user-info from Keycloak to avoid OidcUserService using
        // the discovery document's userinfo_endpoint (keycloak.michalbzowski.pl)
        // which is unreachable from the container. We use the configured
        // user-info-uri (localhost:8180) instead.
        String userInfoUri = userRequest.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUri();
        log.info("Fetching user-info from: {}", userInfoUri);

        Map<String, Object> claims;
        try {
            claims = RestClient.create()
                    .get()
                    .uri(userInfoUri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + userRequest.getAccessToken().getTokenValue())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            log.info("User-info claims: {}", claims);
        } catch (Exception e) {
            log.error("Failed to fetch user-info from {}", userInfoUri, e);
            throw new OAuth2AuthenticationException(
                    new org.springframework.security.oauth2.core.OAuth2Error("user_info_error", "Failed to fetch user-info: " + e.getMessage(), null),
                    e);
        }

        var userInfo = new OidcUserInfo(claims);
        var oidcUser = new DefaultOidcUser(Set.of(), userRequest.getIdToken(), userInfo);
        log.info("OIDC user built: subject={}, email={}", oidcUser.getSubject(), oidcUser.getEmail());
        String subjectId = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String username = oidcUser.getPreferredUsername();
        if (username == null) {
            username = email;
        }
        String firstName = oidcUser.getGivenName();
        String lastName = oidcUser.getFamilyName();

        // Find or create AppUser
        AppUser appUser = findOrCreateAppUser(subjectId, email, username, firstName, lastName);

        // Load team roles
        var teamRoles = userTeamRoleRepository.findByUserId(appUser.getId());

        Long activeTeamId = teamRoles.stream()
                .filter(r -> r.isAdmin())
                .map(r -> r.getTeam().getId())
                .findFirst()
                .orElseGet(() -> teamRoles.stream()
                        .map(r -> r.getTeam().getId())
                        .findFirst()
                        .orElse(null));

        String activeTeamSlug = null;
        String activeTeamRole = null;

        if (activeTeamId != null) {
            var role = teamRoles.stream()
                    .filter(r -> r.getTeam().getId().equals(activeTeamId))
                    .findFirst();
            if (role.isPresent()) {
                activeTeamSlug = role.get().getTeam().getSlug();
                activeTeamRole = role.get().getRole().name();
            }
        }

        List<Long> teamIds = teamRoles.stream()
                .map(r -> r.getTeam().getId())
                .toList();

        // Build enriched OidcUser that also carries our domain data
        return new WindbandOidcUser(
                oidcUser,
                appUser.getId(),
                appUser.getUsername(),
                email,
                appUser.isActive(),
                activeTeamId,
                activeTeamSlug,
                activeTeamRole,
                teamIds
        );
    }

    private AppUser findOrCreateAppUser(String subjectId, String email, String username,
                                         String firstName, String lastName) {
        // 1. Try by external ID (Keycloak subject)
        var byExternalId = appUserRepository.findByExternalId(subjectId);
        if (byExternalId.isPresent()) {
            return byExternalId.get();
        }

        // 2. Try by email
        var byEmail = appUserRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            AppUser user = byEmail.get();
            // Link to Keycloak subject for future logins
            user.setExternalId(subjectId);
            appUserRepository.save(user);
            return user;
        }

        // 3. Try by username
        var byUsername = appUserRepository.findByUsername(username);
        if (byUsername.isPresent()) {
            return byUsername.get();
        }

        // 4. Auto-provision: create new AppUser
        // User exists in Keycloak but not in windband-manager yet.
        // They'll need to create a team after first login.
        // Mark as inactive until team is created.
        log.info("Auto-provisioning AppUser from Keycloak: {} (subject: {})", username, subjectId);
        AppUser newUser = AppUser.createExternalUser(username, email, subjectId, firstName, lastName);
        return appUserRepository.save(newUser);
    }
}
