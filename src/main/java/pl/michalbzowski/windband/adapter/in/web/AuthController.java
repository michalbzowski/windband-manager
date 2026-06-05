package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpSession;
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
import pl.michalbzowski.windband.domain.user.UserTeamRoleRepository;

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
    private final UserTeamRoleRepository userTeamRoleRepository;

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
     * Create a new team for the currently authenticated user (existing user).
     * Requires authentication — the user must be logged in via OIDC/Keycloak.
     */
    @PostMapping("/teams")
    public ResponseEntity<?> createTeam(
            @Valid @RequestBody CreateTeamRequest request,
            @AuthenticationPrincipal OidcUser oidcUser,
            HttpSession session) {
        if (!(oidcUser instanceof WindbandOidcUser wu)) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        try {
            var result = teamRegistrationService.createTeamForExistingUser(
                    wu.getUserId(), request.getTeamName(), request.getTeamSlug());
            // Set the new team as active in session
            session.setAttribute("activeTeamId", result.teamId());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "teamId", result.teamId(),
                    "teamSlug", result.teamSlug(),
                    "teamName", request.getTeamName()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Data
    public static class CreateTeamRequest {
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(min = 2, max = 128)
        private String teamName;

        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$")
        private String teamSlug;
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
     * Respects active team override from session (set by switch-team endpoint).
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal OidcUser oidcUser, HttpSession session) {
        if (oidcUser instanceof WindbandOidcUser wu) {
            // Check session override for active team.
            // Use DB-backed check (not cached WindbandOidcUser.teamIds) so
            // teams created after login are immediately recognized.
            Long sessionTeamId = (Long) session.getAttribute("activeTeamId");
            Long activeTeamId;
            if (sessionTeamId != null) {
                var team = teamQueryService.getUserTeam(wu.getUserId(), sessionTeamId);
                activeTeamId = team.isPresent() ? sessionTeamId : wu.getActiveTeamId();
            } else {
                activeTeamId = wu.getActiveTeamId();
            }

            String activeTeamSlug = null;
            String activeTeamRole = null;
            if (activeTeamId != null) {
                var role = userTeamRoleRepository.findByUserIdAndTeamId(wu.getUserId(), activeTeamId);
                if (role.isPresent()) {
                    var team = role.get().getTeam();
                    activeTeamSlug = team.getSlug();
                    activeTeamRole = role.get().getRole().name();
                }
            }

            var userTeams = teamQueryService.getUserTeams(wu.getUserId());

            return ResponseEntity.ok(Map.of(
                    "userId", wu.getUserId(),
                    "username", wu.getWbUsername(),
                    "email", wu.getWbEmail(),
                    "activeTeamId", activeTeamId,
                    "activeTeamSlug", activeTeamSlug,
                    "activeTeamRole", activeTeamRole,
                    "teamIds", wu.getTeamIds(),
                    "teams", userTeams,
                    "hasTeam", activeTeamId != null
            ));
        }
        if (oidcUser != null) {
            return ResponseEntity.ok(Map.of(
                    "username", oidcUser.getPreferredUsername(),
                    "email", oidcUser.getEmail(),
                    "hasTeam", false,
                    "teams", java.util.List.of()
            ));
        }
        return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
    }

    /**
     * Switch active team for the current session.
     */
    @PostMapping("/switch-team/{teamId}")
    public ResponseEntity<?> switchTeam(
            @PathVariable Long teamId,
            @AuthenticationPrincipal OidcUser oidcUser,
            HttpSession session) {
        if (!(oidcUser instanceof WindbandOidcUser wu)) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        if (!wu.belongsToTeam(teamId)) {
            // Also check DB in case team was created after login
            var fromDb = teamQueryService.getUserTeam(wu.getUserId(), teamId);
            if (fromDb.isEmpty()) {
                return ResponseEntity.status(403).body(Map.of("error", "Nie należysz do zespołu " + teamId));
            }
        }

        // Store in session
        session.setAttribute("activeTeamId", teamId);

        // Look up team info
        var role = userTeamRoleRepository.findByUserIdAndTeamId(wu.getUserId(), teamId);
        if (role.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nie znaleziono roli w zespole"));
        }

        var team = role.get().getTeam();
        return ResponseEntity.ok(Map.of(
                "activeTeamId", teamId,
                "activeTeamSlug", team.getSlug(),
                "activeTeamName", team.getName(),
                "activeTeamRole", role.get().getRole().name()
        ));
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
        String version = null;
        String name = null;
        String time = null;

        if (buildProperties != null) {
            version = buildProperties.getVersion();
            name = buildProperties.getName();
            time = buildProperties.getTime() != null ? buildProperties.getTime().toString() : null;
        }

        // Fallback: read from JAR MANIFEST.MF (works even when the build-info goal
        // places the file outside BOOT-INF/classes/ classpath in the fat JAR)
        if (version == null || "unknown".equals(version)) {
            try (var is = AuthController.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
                if (is != null) {
                    var manifest = new java.util.jar.Manifest(is);
                    var attrs = manifest.getMainAttributes();
                    if (version == null || "unknown".equals(version)) {
                        version = attrs.getValue("Implementation-Version");
                    }
                    if (name == null || "unknown".equals(name)) {
                        name = attrs.getValue("Implementation-Title");
                    }
                }
            } catch (Exception ignored) {
                // fall through with whatever we have
            }
        }

        return ResponseEntity.ok(Map.of(
                "version", version != null ? version : "unknown",
                "name", name != null ? name : "unknown",
                "time", time != null ? time : "unknown"
        ));
    }
}
