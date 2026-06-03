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

import org.springframework.context.annotation.Profile;

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

    public SecurityConfig(KeycloakOAuth2UserService keycloakOAuth2UserService) {
        this.keycloakOAuth2UserService = keycloakOAuth2UserService;
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
                                "/v3/api-docs/**"
                        ).permitAll()
                        // Team admin endpoints
                        .requestMatchers("/api/teams/*/admin/**").hasRole("ADMIN")
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )

                // OIDC Authorization Code Flow
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oidcSuccessHandler())
                        .failureHandler(oidcFailureHandler())
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(keycloakOAuth2UserService)
                        )
                )

                // Also accept JWT Bearer tokens for API calls (resource server)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> {})
                )

                // Logout — clear session + redirect to Keycloak logout
                .logout(logout -> logout
                        .logoutUrl("/logout")
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
        return (request, response, authentication) -> response.sendRedirect("/");
    }

    private AuthenticationFailureHandler oidcFailureHandler() {
        return (request, response, exception) -> response.sendRedirect("/login?error");
    }

    private LogoutSuccessHandler oidcLogoutSuccessHandler() {
        return (request, response, authentication) -> {
            String keycloakLogoutUrl = String.format(
                    "%s/realms/%s/protocol/openid-connect/logout?redirect_uri=%s/login",
                    keycloakPublicUrl, keycloakRealm, baseUrl
            );
            response.sendRedirect(keycloakLogoutUrl);
        };
    }
}
