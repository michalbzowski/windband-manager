package pl.michalbzowski.windband.adapter.in.web;

import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.team.RegisterTeamCommand;
import pl.michalbzowski.windband.application.command.team.TeamRegistrationService;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;

import java.util.Map;

/**
 * Authentication controller.
 *
 * Login and logout are now handled by Spring Security OIDC (Keycloak).
 * This controller only handles:
 * - Team registration (public, no auth required)
 * - Username/email/slug availability checks
 * - Current user info
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TeamRegistrationService teamRegistrationService;
    private final TeamQueryService teamQueryService;

    @Autowired(required = false)
    private BuildProperties buildProperties;

    /**
     * Public endpoint: register a new team with an admin user.
     * The user must already exist in Keycloak (authenticated via OIDC).
     * No authentication required — this is called after Keycloak registration
     * from the team creation page.
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
     * Check if a username is available (real-time validation).
     */
    @GetMapping("/check-username")
    public ResponseEntity<Map<String, Boolean>> checkUsername(@RequestParam String username) {
        return ResponseEntity.ok(Map.of("available", teamQueryService.isUsernameAvailable(username)));
    }

    /**
     * Check if an email is available.
     */
    @GetMapping("/check-email")
    public ResponseEntity<Map<String, Boolean>> checkEmail(@RequestParam String email) {
        return ResponseEntity.ok(Map.of("available", teamQueryService.isEmailAvailable(email)));
    }

    /**
     * Check if a team slug is available.
     */
    @GetMapping("/check-slug")
    public ResponseEntity<Map<String, Boolean>> checkSlug(@RequestParam String slug) {
        return ResponseEntity.ok(Map.of("available", teamQueryService.isSlugAvailable(slug)));
    }

    /**
     * Get current user info from OIDC authentication.
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal OidcUser oidcUser) {
        if (oidcUser instanceof WindbandOidcUser wu) {
            return ResponseEntity.ok(Map.of(
                    "userId", wu.getUserId(),
                    "username", wu.getWbUsername(),
                    "email", wu.getWbEmail(),
                    "activeTeamId", wu.getActiveTeamId(),
                    "activeTeamSlug", wu.getActiveTeamSlug(),
                    "activeTeamRole", wu.getActiveTeamRole(),
                    "teamIds", wu.getTeamIds(),
                    "hasTeam", wu.getActiveTeamId() != null
            ));
        }
        if (oidcUser != null) {
            return ResponseEntity.ok(Map.of(
                    "username", oidcUser.getPreferredUsername(),
                    "email", oidcUser.getEmail(),
                    "hasTeam", false
            ));
        }
        return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    /**
     * Get build information for debugging.
     */
    @GetMapping("/build-info")
    public ResponseEntity<?> buildInfo() {
        if (buildProperties != null) {
            return ResponseEntity.ok(Map.of(
                    "version", buildProperties.getVersion() != null ? buildProperties.getVersion() : "unknown",
                    "name", buildProperties.getName() != null ? buildProperties.getName() : "unknown",
                    "time", buildProperties.getTime() != null ? buildProperties.getTime().toString() : "unknown"
            ));
        }
        return ResponseEntity.ok(Map.of(
                "version", "unknown",
                "name", "unknown",
                "time", "unknown"
        ));
    }
}
