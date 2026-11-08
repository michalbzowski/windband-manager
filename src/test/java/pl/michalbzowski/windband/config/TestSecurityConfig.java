package pl.michalbzowski.windband.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
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
@EnableAsync
@Profile("test")
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http,
                                                       AuthenticationManager testAuthenticationManager) throws Exception {
        return http
                // Issue #167: the chain used the globally assembled AuthenticationManager, whose
                // provider order differs between a laptop and a CI runner — the principal that
                // ended up in the session was sometimes the plain User instead of WindbandOidcUser.
                // Mirror of the production chain: same CSRF contract so UI integration
                // tests exercise the real anti-forgery flow (cookie XSRF-TOKEN).
                // PLAIN request handler — see SecurityConfig for why the 5.8+ default
                // XOR-masked handler breaks the raw X-XSRF-TOKEN cookie/header pattern.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(
                                org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(
                                new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler()))
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
                                "/error",
                                "/public/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .authenticationManager(testAuthenticationManager)
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/api/auth/login")
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
     * Issue #167 — the principal is built by the {@link AuthenticationProvider} below, so the
     * {@code UsernamePasswordAuthenticationFilter} itself stores a {@link WindbandOidcUser}:
     * there is no second write that could lose the race against the filter's own save.
     *
     * <p>The handler only mirrors that authentication into the holder and the session (through
     * the security context repository, not a raw session attribute) and performs the redirect.
     */
    private AuthenticationSuccessHandler testAuthSuccessHandler() {
        return (HttpServletRequest request, HttpServletResponse response,
                org.springframework.security.core.Authentication authentication) -> {

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            new HttpSessionSecurityContextRepository().saveContext(context, request, response);

            response.sendRedirect("/");
        };
    }

    /**
     * Mock {@link WindbandOidcUser} for a form-login principal: the same data the previous
     * success handler hard-coded ({@code data.sql}: user id 1, band id 1, slug "test-band",
     * role ADMIN), now created while authenticating instead of afterwards.
     */
    static WindbandOidcUser asWindbandOidcUser(User user) {
        List<String> emails = user.getUsername().contains("@")
                ? List.of(user.getUsername())
                : List.of(user.getUsername() + "@test.com");

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

        DefaultOidcUser delegate = new DefaultOidcUser(user.getAuthorities(), idToken);

        return new WindbandOidcUser(
                delegate,
                1L,                     // userId — matches data.sql
                user.getUsername(),
                emails.getFirst(),
                true,                   // active
                false,                  // systemAdmin
                1L,                     // activeTeamId — matches data.sql
                "test-band",            // activeTeamSlug — matches data.sql
                "ADMIN",                // activeTeamRole
                List.of(1L)             // teamIds
        );
    }

    /**
     * Wraps the DAO provider so a successful form login is authenticated straight away with a
     * {@link WindbandOidcUser}. Authorities and the credentials check are untouched — only the
     * principal type changes, exactly as the old success handler did after the fact.
     */
    @Bean
    public AuthenticationProvider testOidcPrincipalProvider(UserDetailsService testUserDetailsService,
                                                            PasswordEncoder passwordEncoder) {
        // Zbudowany na beanach z tej samej klasy — konteksty testowe, które nie ładowują
        // UserDetailsConfig (np. SelfRegistrationRedirectTest), też go dostaną.
        final DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider();
        daoAuthenticationProvider.setUserDetailsService(testUserDetailsService);
        daoAuthenticationProvider.setPasswordEncoder(passwordEncoder);
        return new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                Authentication result = daoAuthenticationProvider.authenticate(authentication);
                if (result == null || !(result.getPrincipal() instanceof User user)) {
                    return result;
                }
                UsernamePasswordAuthenticationToken mapped = new UsernamePasswordAuthenticationToken(
                        asWindbandOidcUser(user), result.getCredentials(), user.getAuthorities());
                mapped.setDetails(result.getDetails());
                return mapped;
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return daoAuthenticationProvider.supports(authentication);
            }
        };
    }

    /**
     * Single, explicit manager for the test chain. The globally assembled one contained both the
     * DAO provider and the {@code testUserDetailsService} provider, and which one answered first
     * differed between machines — that is the “Stored plain User / Retrieved WindbandOidcUser”
     * flip described in issue #167.
     */
    @Bean
    @Primary
    public AuthenticationManager testAuthenticationManager(
            AuthenticationProvider testOidcPrincipalProvider) {
        return new ProviderManager(testOidcPrincipalProvider);
    }

    @Bean
    @Primary
    public JavaMailSender javaMailSender() {
        return Mockito.mock(JavaMailSender.class);
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
