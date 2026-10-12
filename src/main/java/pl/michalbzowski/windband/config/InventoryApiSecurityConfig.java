package pl.michalbzowski.windband.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import pl.michalbzowski.windband.adapter.in.security.BearerOidcUserConverter;
import pl.michalbzowski.windband.domain.user.AppUserRepository;
import pl.michalbzowski.windband.domain.user.UserTeamRoleRepository;

/**
 * Dedicated security chain for the v1 read-only integration API
 * ({@code /api/v1/**}, contract B1/B5/B8/B10) so windband-inventory can call it.
 *
 * <p>WM's main chain is session-based (OIDC login). External apps have no WM
 * session, so the v1 surface speaks pure Bearer JWT: same Keycloak realm
 * {@code windband}, but tokens minted to the inventory client.
 *
 * <p><b>JWT validation</b> — two URLs, one on purpose:
 * <ul>
 *   <li>{@code windband.inventory.api.jwk-set-uri} — where THIS process fetches the
 *       realm's signing keys. It must be reachable from the app runtime (inside
 *       docker-compose that is {@code http://keycloak:8180/...}, not the host port),</li>
 *   <li>{@code spring.security.oauth2.resourceserver.jwt.issuer-uri} — the trusted
 *       {@code iss} claim value, enforced by
 *       {@link JwtValidators#createDefaultValidators}. Tokens signed by a foreign
 *       realm with a matching name are rejected.</li>
 * </ul>
 * After validation, {@link BearerOidcUserConverter} maps the token to a
 * {@link pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser}; band scoping
 * itself stays 100% server-side in {@code BandInventoryApiQueryService} (403 for
 * foreign bands), so no cross-user data can leak even if a client calls with someone
 * else's valid token — the caller only ever sees rows scoped to that caller's own
 * memberships.
 *
 * <p>Excluded from the {@code test} profile like the main chain (no live issuer
 * available in unit/IT contexts).
 */
@Configuration
@EnableWebSecurity
@Profile("!test")
public class InventoryApiSecurityConfig {

    @Order(1)
    @Bean
    public SecurityFilterChain inventoryV1ApiChain(HttpSecurity http,
                                                    AppUserRepository appUserRepository,
                                                    UserTeamRoleRepository userTeamRoleRepository,
                                                    JwtDecoder windbandInventoryJwtDecoder) throws Exception {
        Converter<Jwt, AbstractAuthenticationToken> bearerConverter = new BearerOidcUserConverter(
                appUserRepository, userTeamRoleRepository);
        return http
                .securityMatcher("/api/v1/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAnyAuthority("ROLE_USER", "ROLE_ADMIN"))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(windbandInventoryJwtDecoder)
                                .jwtAuthenticationConverter(bearerConverter)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                        .accessDeniedHandler(new BearerTokenAccessDeniedHandler()))
                .build();
    }

    /**
     * Runtime-tuned decoder: JWKS endpoint taken from the reachable URL configured by
     * the host (container DNS in compose), issuer pinned to the trusted realm. Kept as
     * a distinct bean name so Spring Boot's autoconfigured issuer-based decoder does not
     * take over resolution against {@code localhost}.
     */
    @Bean
    public JwtDecoder windbandInventoryJwtDecoder(
            @Value("${windband.inventory.api.jwk-set-uri}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String trustedIssuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(trustedIssuer));
        return decoder;
    }
}
