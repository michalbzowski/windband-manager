package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.domain.user.AppUser;
import pl.michalbzowski.windband.domain.user.AppUserRepository;

import java.util.List;
import java.util.Map;

/**
 * System admin controller for managing system-level roles.
 * Only users with ROLE_SYSTEM_ADMIN can access these endpoints.
 */
@RestController
@RequestMapping("/api/admin/system-admins")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class SystemAdminController {

    private final AppUserRepository appUserRepository;

    /**
     * Lists all users with their system admin status.
     */
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> listUsers() {
        List<AppUser> users = appUserRepository.findAll();
        List<Map<String, Object>> result = users.stream()
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "email", u.getEmail(),
                        "displayName", u.getDisplayName(),
                        "active", u.isActive(),
                        "systemAdmin", u.isSystemAdmin()
                ))
                .toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Grants system admin role to a user.
     */
    @PostMapping("/users/{userId}/grant")
    public ResponseEntity<Map<String, String>> grantSystemAdmin(@PathVariable Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setSystemAdmin(true);
        appUserRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "System admin granted to " + user.getUsername()));
    }

    /**
     * Revokes system admin role from a user.
     * Prevents revoking your own role.
     */
    @PostMapping("/users/{userId}/revoke")
    public ResponseEntity<Map<String, String>> revokeSystemAdmin(
            @PathVariable Long userId,
            @AuthenticationPrincipal OidcUser oidcUser) {
        if (oidcUser instanceof WindbandOidcUser wu && wu.getUserId().equals(userId)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Cannot revoke your own system admin role"));
        }
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setSystemAdmin(false);
        appUserRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "System admin revoked from " + user.getUsername()));
    }

    /**
     * Lists all current system admins.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listSystemAdmins() {
        List<AppUser> admins = appUserRepository.findBySystemAdminTrue();
        List<Map<String, Object>> result = admins.stream()
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "email", u.getEmail(),
                        "displayName", u.getDisplayName()
                ))
                .toList();
        return ResponseEntity.ok(result);
    }
}
