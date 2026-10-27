package pl.michalbzowski.windband.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@Profile("!test")
// Pentest P1: w profilu testowym łańcuch pochodzi z TestSecurityConfig (mirror
// tej samej konfiguracji CSRF/login), aby nie rejestrować dwóch SecurityFilterChain
// o identycznym zakresie.
public class SecurityConfig {

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /** Pentest P2 (A01): limit prób auth per okno (brute-force) — patrz LoginRateLimitFilter. */
    @Value("${app.security.login.max-attempts-per-window:10}")
    private int loginMaxAttemptsPerWindow;

    @Value("${app.security.login.window-seconds:60}")
    private long loginWindowSeconds;

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

    /** Pentest P2 (A01): throttle brute-force — patrz komentarz przy polach loginMaxAttempts*. */
    @Bean
    public pl.michalbzowski.windband.adapter.in.security.LoginRateLimitFilter loginRateLimitFilter() {
        return new pl.michalbzowski.windband.adapter.in.security.LoginRateLimitFilter(
                loginMaxAttemptsPerWindow, loginWindowSeconds);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Pentest P1 (A01): CSRF protection. The token lives in a readable cookie
                // (XSRF-TOKEN) so the existing JS helpers (windband-utils.js) and the
                // htmx:configRequest hook in fragments/layout.html can send X-XSRF-TOKEN;
                // Thymeleaf forms with th:action get an automatic hidden field.
                // csrfTokenRequestHandler = PLAIN handler: Spring Security >= 5.8 masks
                // tokens in forms via XorCsrfTokenRequestAttributeHandler and then only
                // ACCEPTS the XOR-masked form — a raw X-XSRF-TOKEN header (the documented
                // AngularJS pattern of CookieCsrfTokenRepository.withHttpOnlyFalse()) gets
                // 403. The plain handler accepts raw tokens from both hidden fields and
                // headers, which is what our JS contract requires.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(
                                org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(
                                new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler()))

                // Pentest P2 (A02): nagłówki bezpieczeństwa. Spring Security domyślnie
                // stawia X-Content-Type-Options: nosniff oraz X-Frame-Options: SAMEORIGIN
                // (frame-ancestors 'self'); tu HSTS po pełnym łańcuchu TLS
                // (Cloudflare Tunnel — prod), 1 rok, z poddomenami + Referrer-Policy.
                // Profil testowy (TestSecurityConfig) działa pod http://localhost i HSTS
                // celowo nie ustawia.
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                )

                // Pentest P2 (A01): rate limiting brute-force na auth-endpointach
                // (login + register-team), per IP, stałe okno.
                .addFilterBefore(
                        loginRateLimitFilter(),
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class
                )

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
                                // Pentest P1: swagger-ui / api-docs / build-info / debug-dns
                                // NIE są publikowane anonimowo (default-off; enable per env).
                                "/public/**",
                                "/consent",
                                "/consent/**"
                        ).permitAll()
                        // Team admin endpoints
                        .requestMatchers("/api/teams/*/admin/**").hasRole("ADMIN")
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "SYSTEM_ADMIN")
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )

                // OIDC Authorization Code Flow (primary)
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

                // Form login (fallback for tests and emergency DB access)
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/api/auth/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error")
                        .permitAll()
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
                    "%s%s/realms/%s/protocol/openid-connect/logout?client_id=%s&post_logout_redirect_uri=%s",
                    scheme,
                    keycloakPublicUrl,
                    keycloakRealm,
                    URLEncoder.encode("windband-manager", StandardCharsets.UTF_8),
                    URLEncoder.encode(baseUrl, StandardCharsets.UTF_8)
            );
            response.sendRedirect(keycloakLogoutUrl);
        };
    }
}
