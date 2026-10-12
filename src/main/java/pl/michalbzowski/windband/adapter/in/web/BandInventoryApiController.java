package pl.michalbzowski.windband.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.team.BandInventoryApiQueryService;

/**
 * v1 read-only integration API consumed by the windband-inventory app.
 *
 * <p>Contract (frozen, B1/B5/B8/B10):
 * <ul>
 *   <li>{@code GET /api/v1/me/bands} — requesting user's bands + per-band role;</li>
 *   <li>{@code GET /api/v1/bands/{bandId}/members} — roster with expected sets,
 *       any band membership required (foreign users get 403).</li>
 * </ul>
 *
 * <p>Responses are rendered by a shared {@link ObjectMapper} and returned as
 * plain JSON strings on purpose: the standalone MockMvc tests pin the exact
 * JSON contract without web-layer message converters (same house pattern as
 * {@code StartScoreAnalysisRestController}), while in production Spring serves
 * the byte-identical body.
 *
 * <p>Authentication is Keycloak realm {@code windband}; band roles are resolved
 * from the WM database ({@code UserTeamRole}), never from OIDC claims. Band members
 * are roster entries and need no windband accounts — there is deliberately no
 * member-to-user mapping here. Write access to any of this data stays in inventory.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BandInventoryApiController {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final BandInventoryApiQueryService queryService;

    @GetMapping("/me/bands")
    public ResponseEntity<String> myBands(@AuthenticationPrincipal OidcUser oidcUser) {
        return json(queryService.myBands(requireUserId(oidcUser)));
    }

    @GetMapping("/bands/{bandId}/members")
    public ResponseEntity<String> bandMembers(
            @PathVariable Long bandId,
            @AuthenticationPrincipal OidcUser oidcUser) {
        return json(queryService.bandMembersWithExpectedSet(requireUserId(oidcUser), bandId));
    }

    private static ResponseEntity<String> json(Object payload) {
        try {
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(JSON.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to render inventory API response", ex);
        }
    }

    private static Long requireUserId(OidcUser oidcUser) {
        if (oidcUser instanceof WindbandOidcUser wu) {
            return wu.getUserId();
        }
        throw new AccessDeniedException("Request is not authenticated as a windband user");
    }
}
