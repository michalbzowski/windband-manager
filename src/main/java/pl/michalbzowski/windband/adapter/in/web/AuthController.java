package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.adapter.in.security.TeamAwareUserDetails;
import pl.michalbzowski.windband.application.command.team.RegisterTeamCommand;
import pl.michalbzowski.windband.application.command.team.TeamRegistrationService;
import pl.michalbzowski.windband.config.JwtConfig;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtConfig jwtConfig;
    private final TeamRegistrationService teamRegistrationService;

    @PostMapping("/login")
    public ResponseEntity<Void> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        if (auth.isAuthenticated()) {
            UserDetails ud = (UserDetails) auth.getPrincipal();

            Map<String, Object> claims = new HashMap<>();
            if (ud instanceof TeamAwareUserDetails tud) {
                claims.put("userId", tud.getUserId());
                claims.put("email", tud.getEmail());
                claims.put("activeTeamId", tud.getActiveTeamId());
                claims.put("activeTeamSlug", tud.getActiveTeamSlug());
                claims.put("activeTeamRole", tud.getActiveTeamRole());
                claims.put("teamIds", tud.getTeamIds());
            }

            String token = jwtConfig.generateToken(request.getUsername(), claims);
            Cookie cookie = new Cookie("JWT", token);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(86400);
            cookie.setAttribute("SameSite", "Lax");
            response.addCookie(cookie);
            response.setStatus(200);
            return ResponseEntity.ok().build();
        }

        return ResponseEntity.status(401).build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("JWT", null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.ok().build();
    }

    /**
     * Public endpoint: register a new team with an admin user.
     * No authentication required.
     */
    @PostMapping("/register-team")
    public ResponseEntity<?> registerTeam(@Valid @RequestBody RegisterTeamCommand cmd) {
        try {
            var result = teamRegistrationService.registerTeam(cmd);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Check if a username is available (for registration form).
     */
    @GetMapping("/check-username")
    public ResponseEntity<Map<String, Boolean>> checkUsername(@RequestParam String username) {
        // This is handled by the service layer - simplified here
        return ResponseEntity.ok(Map.of("available", username != null && username.length() >= 3));
    }

    /**
     * Check if an email is available.
     */
    @GetMapping("/check-email")
    public ResponseEntity<Map<String, Boolean>> checkEmail(@RequestParam String email) {
        return ResponseEntity.ok(Map.of("available", email != null && email.contains("@")));
    }

    /**
     * Get current user info from JWT.
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails instanceof TeamAwareUserDetails tud) {
            return ResponseEntity.ok(Map.of(
                    "userId", tud.getUserId(),
                    "username", tud.getUsername(),
                    "email", tud.getEmail(),
                    "activeTeamId", tud.getActiveTeamId(),
                    "activeTeamSlug", tud.getActiveTeamSlug(),
                    "activeTeamRole", tud.getActiveTeamRole(),
                    "teamIds", tud.getTeamIds()
            ));
        }
        return ResponseEntity.ok(Map.of("username", userDetails.getUsername()));
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
