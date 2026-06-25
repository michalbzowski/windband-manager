package pl.michalbzowski.windband.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import pl.michalbzowski.windband.adapter.in.security.KeycloakOAuth2UserService;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

@Configuration
@EnableWebSecurity
@Profile("!test")
public class SecurityConfig {

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${KEYCLOAK_URL:http://localhost:8180}")
    private String keycloakUrl;

    @Value("${KEYCLOAK_PUBLIC_URL:http://localhost:8180}")
    private String keycloakPublicUrl;

    @Value("${KEYCLOAK_REALM:windband}")
    private String keycloakRealm;

    private final KeycloakOAuth2UserService keycloakOAuth2UserService;
    private final ClientRegistrationRepository clientRegistrationRepository;

    public SecurityConfig(KeycloakOAuth2UserService keycloakOAuth2UserService,
                          ClientRegistrationRepository clientRegistrationRepository) {
        this.keycloakOAuth2UserService = keycloakOAuth2UserService;
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    /**
     * Custom authorization request resolver that overrides redirect_uri with the
     * public BASE_URL. Without this, Spring Security generates redirect_uri
     * from the incoming request (http://localhost:8080 behind Cloudflare Tunnel),
     * which the browser cannot follow.
     */
    @Bean
    public OAuth2AuthorizationRequestResolver authorizationRequestResolver() {
        var defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, "/oauth2/authorization");
        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                OAuth2AuthorizationRequest original = defaultResolver.resolve(request);
                if (original == null) return null;
                return OAuth2AuthorizationRequest.from(original)
                        .redirectUri(baseUrl + "/login/oauth2/code/keycloak")
                        .build();
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                OAuth2AuthorizationRequest original = defaultResolver.resolve(request, clientRegistrationId);
                if (original == null) return null;
                return OAuth2AuthorizationRequest.from(original)
                        .redirectUri(baseUrl + "/login/oauth2/code/keycloak")
                        .build();
            }
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)

                // Session-based auth (OIDC login creates a session)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                .authorizeHttpRequests(auth -> auth
                        // Public endpoints — no auth required
                        .requestMatchers(
                                "/api/auth/register-team",
                                "/api/auth/check-username",
                                "/api/auth/check-email",
                                "/api/auth/check-slug",
                                "/login",
                                "/register",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/webjars/**",
                                "/favicon.ico",
                                "/error",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/api/auth/build-info",
                                "/api/auth/debug-dns"
                        ).permitAll()
                        // Team admin endpoints
                        .requestMatchers("/api/teams/*/admin/**").hasRole("ADMIN")
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "SYSTEM_ADMIN")
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )

                // OIDC Authorization Code Flow
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(auth -> auth
                                .authorizationRequestResolver(authorizationRequestResolver())
                        )
                        .successHandler(oidcSuccessHandler())
                        .failureHandler(oidcFailureHandler())
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(keycloakOAuth2UserService)
                        )
                )

                // Logout — clear session + redirect to Keycloak logout
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(oidcLogoutSuccessHandler())
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            if (request.getRequestURI().startsWith("/api/")) {
                                response.sendError(401, "Unauthorized");
                            } else {
                                response.sendRedirect("/oauth2/authorization/keycloak");
                            }
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            if (request.getRequestURI().startsWith("/api/")) {
                                response.sendError(403, "Forbidden");
                            } else {
                                response.sendRedirect("/");
                            }
                        })
                )

                .build();
    }

    private AuthenticationSuccessHandler oidcSuccessHandler() {
        return (request, response, authentication) -> {
            if (authentication.getPrincipal() instanceof WindbandOidcUser wu) {
                if (wu.getActiveTeamId() == null) {
                    response.sendRedirect("/register");
                    return;
                }
            }
            response.sendRedirect("/");
        };
    }

    private AuthenticationFailureHandler oidcFailureHandler() {
        return (request, response, exception) -> response.sendRedirect("/login?error");
    }

    private LogoutSuccessHandler oidcLogoutSuccessHandler() {
        return (request, response, authentication) -> {
            // Build absolute Keycloak logout URL
            // keycloakPublicUrl may already include https:// or be just a hostname
            String scheme = keycloakPublicUrl.startsWith("http") ? "" : "https://";
            String keycloakLogoutUrl = String.format(
                    "%s%s/realms/%s/protocol/openid-connect/logout?post_logout_redirect_uri=%s",
                    scheme, keycloakPublicUrl, keycloakRealm, baseUrl
            );
            response.sendRedirect(keycloakLogoutUrl);
        };
    }
}
