package pl.michalbzowski.windband.adapter.in.web;

import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.team.InviteUserCommand;
import pl.michalbzowski.windband.application.command.team.TeamRegistrationService;

import java.util.Map;

@RestController
@RequestMapping("/api/teams/{teamId}")
@RequiredArgsConstructor
public class TeamInviteController {

    private final TeamRegistrationService teamRegistrationService;

    /**
     * Invite a user to a team. Only admins can invite.
     */
    @PostMapping("/admin/invite")
    public ResponseEntity<?> inviteUser(
            @PathVariable Long teamId,
            @Valid @RequestBody InviteUserCommand cmd,
            @AuthenticationPrincipal OidcUser oidcUser) {

        Long adminUserId = null;
        if (oidcUser instanceof WindbandOidcUser wu) {
            adminUserId = wu.getUserId();
            if (!wu.belongsToTeam(teamId)) {
                return ResponseEntity.status(403).body(Map.of("error", "You don't belong to this team"));
            }
        }
        if (adminUserId == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            String invitationToken = teamRegistrationService.inviteUserToTeam(adminUserId, teamId, cmd);
            return ResponseEntity.ok(Map.of(
                    "message", "Invitation sent",
                    "invitationToken", invitationToken  // In production, send via email
            ));
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Accept an invitation (public endpoint with token).
     */
    @PostMapping("/accept-invitation/{token}")
    public ResponseEntity<?> acceptInvitation(
            @PathVariable String token,
            @RequestBody AcceptInvitationRequest request) {
        try {
            teamRegistrationService.acceptInvitation(token, request.getPassword(), request.getUsername());
            return ResponseEntity.ok(Map.of("message", "Invitation accepted. You can now log in."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * List all members of a team.
     */
    @GetMapping("/members")
    public ResponseEntity<?> listMembers(
            @PathVariable Long teamId,
            @AuthenticationPrincipal OidcUser oidcUser) {

        if (oidcUser instanceof WindbandOidcUser wu) {
            if (!wu.belongsToTeam(teamId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
            }
        }

        // Return team members list
        return ResponseEntity.ok(Map.of("teamId", teamId, "members", "TODO"));
    }

    @Data
    public static class AcceptInvitationRequest {
        private String password;
        private String username;
    }
}
