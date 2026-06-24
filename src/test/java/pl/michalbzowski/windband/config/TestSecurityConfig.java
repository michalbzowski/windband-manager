package pl.michalbzowski.windband.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test security configuration — bypasses real Keycloak OIDC and uses
 * form-based login, but still produces a proper {@link WindbandOidcUser}
 * (implementing {@code OidcUser}) so controllers that accept
 * {@code @AuthenticationPrincipal OidcUser} work correctly.
 *
 * <p>After form login succeeds, a custom success handler replaces the
 * plain Spring Security {@link User} principal with a mock
 * {@link WindbandOidcUser} containing test team data from
 * {@code data.sql} (band id = 1, slug = "test-band").</p>
 */
@Configuration
@EnableWebSecurity
@Profile("test")
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
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
                                "/error"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(testAuthSuccessHandler())
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login")
                        .permitAll()
                )
                .build();
    }

    /**
     * After form login, replaces the {@link User} principal with a
     * {@link WindbandOidcUser} that matches the test seed data
     * ({@code data.sql}: band id=1, slug="test-band", user id=1,
     * username="admin", role="ADMIN").
     *
     * <p>The mock OidcUser is stored in the HTTP session so it
     * persists across Selenium browser requests.</p>
     */
    private AuthenticationSuccessHandler testAuthSuccessHandler() {
        return (HttpServletRequest request, HttpServletResponse response,
                org.springframework.security.core.Authentication authentication) -> {

            User user = (User) authentication.getPrincipal();
            List<String> emails = user.getUsername().contains("@")
                    ? List.of(user.getUsername())
                    : List.of(user.getUsername() + "@test.com");

            // Minimal OidcIdToken with required claims
            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", "test-subject-" + user.getUsername());
            claims.put("preferred_username", user.getUsername());
            claims.put("email", emails.getFirst());
            claims.put("name", user.getUsername());
            OidcIdToken idToken = new OidcIdToken(
                    "mock-token",
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    claims
            );

            DefaultOidcUser delegate = new DefaultOidcUser(
                    user.getAuthorities(),
                    idToken
            );

            WindbandOidcUser wu = new WindbandOidcUser(
                    delegate,
                    1L,                    // userId — matches data.sql
                    user.getUsername(),     // username
                    emails.getFirst(),      // email
                    true,                   // active
                    false,                  // systemAdmin
                    1L,                     // activeTeamId — matches data.sql
                    "test-band",            // activeTeamSlug — matches data.sql
                    "ADMIN",                // activeTeamRole
                    List.of(1L)             // teamIds
            );

            UsernamePasswordAuthenticationToken newAuth =
                    new UsernamePasswordAuthenticationToken(wu, null, wu.getAuthorities());

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(newAuth);
            SecurityContextHolder.setContext(context);

            request.getSession(true).setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    context
            );

            response.sendRedirect("/");
        };
    }

    @Bean
    public UserDetailsService testUserDetailsService(PasswordEncoder encoder) {
        var admin = User.builder()
                .username("admin")
                .password(encoder.encode("admin"))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }
}
