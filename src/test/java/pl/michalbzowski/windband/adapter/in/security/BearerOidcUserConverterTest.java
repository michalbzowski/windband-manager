package pl.michalbzowski.windband.adapter.in.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.user.AppUser;
import pl.michalbzowski.windband.domain.user.AppUserRepository;
import pl.michalbzowski.windband.domain.user.UserTeamRole;
import pl.michalbzowski.windband.domain.user.UserTeamRoleRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Bearer -> WindbandOidcUser mapping for the v1 integration API (B10).
 * Security rule under test: a token can ONLY resolve to the caller's own
 * memberships; unknown/inactive Keycloak users come back WITHOUT the Windband
 * enrichment so downstream {@code instanceof WindbandOidcUser} checks 403.
 */
@ExtendWith(MockitoExtension.class)
class BearerOidcUserConverterTest {

    private static final String KC_SUBJECT = "kc-subj-123";

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private UserTeamRoleRepository userTeamRoleRepository;

    private BearerOidcUserConverter converter;

    @BeforeEach
    void setUp() {
        converter = new BearerOidcUserConverter(appUserRepository, userTeamRoleRepository);
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("token-value")
                .header("alg", "RS256")
                .subject(KC_SUBJECT)
                .claim("preferred_username", "michal")
                .claim("email", "michal@example.com")
                .build();
    }

    private AppUser appUser(String username) {
        return AppUser.create(username, username + "@example.com", "hash");
    }

    /** DB-generated id is always non-null in production; simulate it for unit scope. */
    private static Band band(long id, String name, String slug) {
        Band band = Band.create(name, slug);
        org.springframework.test.util.ReflectionTestUtils.setField(band, "id", id);
        return band;
    }

    @Test
    @DisplayName("token resolves via externalId to enriched WindbandOidcUser with correct role")
    void mapsKnownAdminUser() {
        AppUser user = appUser("michal");
        user.setExternalId(KC_SUBJECT);
        Band band = band(1L, "Orkiestra Dęta Miasta Kościerzyna", "kosciierzyna");

        lenient().when(appUserRepository.findByExternalId(KC_SUBJECT)).thenReturn(Optional.of(user));
        when(userTeamRoleRepository.loadWithBandsByUserId(null)).thenReturn(List.of(
                UserTeamRole.createAdmin(user, band),
                UserTeamRole.createMember(user, band(2L, "Big Band Powiśle", "bbp"))));

        Authentication result = converter.convert(jwt());

        assertThat(result).isInstanceOf(BearerOidcAuthentication.class);
        WindbandOidcUser principal = (WindbandOidcUser) result.getPrincipal();
        assertThat(principal.getUserId()).isEqualTo(user.getId());
        assertThat(principal.getWbUsername()).isEqualTo("michal");
        assertThat(principal.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_USER", "ROLE_ADMIN");
        assertThat(principal.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("unknown Keycloak user -> unenriched token (caller sees 403, not data)")
    void unknownUserIsNotEnriched() {
        when(appUserRepository.findByExternalId(KC_SUBJECT)).thenReturn(Optional.empty());
        when(appUserRepository.findByEmail("michal@example.com")).thenReturn(Optional.empty());

        Authentication result = converter.convert(jwt());

        assertThat(result.getPrincipal()).isNotInstanceOf(WindbandOidcUser.class);
    }

    @Test
    @DisplayName("inactive WM user -> unenriched token even if the Keycloak account resolves")
    void inactiveUserIsRejected() {
        AppUser user = appUser("michal");
        user.deactivate();
        lenient().when(appUserRepository.findByExternalId(KC_SUBJECT)).thenReturn(Optional.of(user));

        Authentication result = converter.convert(jwt());

        assertThat(result.getPrincipal()).isNotInstanceOf(WindbandOidcUser.class);
    }

    @Test
    @DisplayName("externalId miss falls back to email lookup")
    void fallsBackToEmailLookup() {
        AppUser user = appUser("michal"); // no externalId set
        lenient().when(appUserRepository.findByExternalId(KC_SUBJECT)).thenReturn(Optional.empty());
        when(appUserRepository.findByEmail("michal@example.com")).thenReturn(Optional.of(user));
        when(userTeamRoleRepository.loadWithBandsByUserId(null)).thenReturn(List.of());

        Authentication result = converter.convert(jwt());

        assertThat(result.getPrincipal()).isInstanceOf(WindbandOidcUser.class);
    }

    @Test
    @DisplayName("MEMBER-only user gets ROLE_USER without ROLE_ADMIN")
    void memberOnlyAuthorities() {
        AppUser user = appUser("kasia");
        lenient().when(appUserRepository.findByExternalId(KC_SUBJECT)).thenReturn(Optional.of(user));
        when(userTeamRoleRepository.loadWithBandsByUserId(null)).thenReturn(List.of(
                UserTeamRole.createMember(user, band(3L, "Fanfara", "fanfara"))));

        WindbandOidcUser principal = (WindbandOidcUser) converter.convert(jwt()).getPrincipal();

        assertThat(principal.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER");
    }

    // ------------------------------------------------------------------
    //  Synthetic "sub" naming chain. Keycloak access tokens of the PUBLIC
    //  inventory client carry NO "sub" claim while DefaultOidcUser requires
    //  one as name attribute. Chain: externalId -> token labels -> synthetic
    //  local id. The label-less tests build tokens WITHOUT .subject(...) —
    //  exactly what the real public-client JWT looks like.
    // ------------------------------------------------------------------

    private static String subOf(Authentication result) {
        return (String) ((OidcUser) result.getPrincipal()).getIdToken().getClaims().get("sub");
    }

    private static Jwt publicClientToken(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256");
        claims.forEach(b::claim);
        return b.build();
    }

    @Test
    @DisplayName("label-less token for legacy user row: synthetic wm-local-<id> sub, no crash")
    void subFallsBackToSyntheticLocalSubject() {
        AppUser user = appUser("ghost");
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 42L);
        when(appUserRepository.findByEmail("")).thenReturn(Optional.of(user));
        when(userTeamRoleRepository.loadWithBandsByUserId(42L)).thenReturn(List.of());

        // blank email claim is the only lookup key; no preferred_username, no sub
        Authentication result = converter.convert(publicClientToken(Map.of("email", "")));

        assertThat(subOf(result)).isEqualTo("wm-local-42");
    }

    @Test
    @DisplayName("deny path with label-less token names the principal unknown-inventory-user")
    void denyPathUsesUnknownLabel() {
        // Jwt requires at least one claim; "jti" carries no identity labels.
        Authentication result = converter.convert(publicClientToken(Map.of("jti", "bare-token")));

        assertThat(result.getPrincipal()).isNotInstanceOf(WindbandOidcUser.class);
        assertThat(subOf(result)).isEqualTo("unknown-inventory-user");
    }

    @Test
    @DisplayName("public-client token (no sub claim): sub synthesized from preferred_username")
    void subSynthesizedFromPreferredUsername() {
        AppUser user = appUser("michal"); // externalId NULL — legacy rows exist
        when(appUserRepository.findByEmail("michal@example.com")).thenReturn(Optional.of(user));
        when(userTeamRoleRepository.loadWithBandsByUserId(null)).thenReturn(List.of());

        Authentication result = converter.convert(publicClientToken(Map.of(
                "preferred_username", "michal", "email", "michal@example.com")));

        assertThat(subOf(result)).isEqualTo("michal");
    }
}
