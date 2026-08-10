package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Global model attributes for teams — adds user's teams and active team info
 * to all templates. Respects session override (set by switch-team) for the
 * active team, so the nav reflects team switches even though the Principal
 * (WindbandOidcUser) was set at login time.
 *
 * Uses TeamQueryService (which is @Transactional) for all DB access to avoid
 * LazyInitializationException from accessing lazy-loaded entity proxies.
 *
 * Also assigns each team a deterministic, evenly-distributed HSL color so the
 * top-nav can render a colored avatar chip per team. When the user belongs to
 * only one team, no color is assigned (the chip falls back to a neutral color)
 * — this avoids the visual noise of "colored avatars" when there is nothing to
 * distinguish.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class TeamModelAdvice {

    private final TeamQueryService teamQueryService;

    /**
     * View-side record — wraps the application-layer {@link TeamQueryService.UserTeamDto}
     * and adds a CSS color used by the top-nav team switcher / team modal.
     * The record accessors (id, name, slug, role, color) mirror the existing
     * template usages of {@code team.id} / {@code team.name} / {@code team.role}.
     */
    public record TeamView(Long id, String name, String slug, String role, String color) {}

    @ModelAttribute("userTeams")
    public List<TeamView> userTeams(@AuthenticationPrincipal OidcUser oidcUser) {
        if (!(oidcUser instanceof WindbandOidcUser wu)) {
            return List.of();
        }
        var dtos = teamQueryService.getUserTeams(wu.getUserId());
        if (dtos.isEmpty()) {
            return List.of();
        }
        // Stable ordering: by name (TeamQueryService already sorts, but be defensive)
        var sorted = dtos.stream()
                .sorted(Comparator.comparing(TeamQueryService.UserTeamDto::name))
                .toList();
        List<TeamView> views = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            var dto = sorted.get(i);
            views.add(new TeamView(
                    dto.id(),
                    dto.name(),
                    dto.slug(),
                    dto.role(),
                    teamColor(i, sorted.size())));
        }
        return views;
    }

    @ModelAttribute("activeTeamId")
    public Long activeTeamId(@AuthenticationPrincipal OidcUser oidcUser, HttpSession session) {
        if (!(oidcUser instanceof WindbandOidcUser wu)) {
            return null;
        }
        Long sessionTeamId = (Long) session.getAttribute("activeTeamId");
        // Check against DB instead of cached WindbandOidcUser.teamIds — the
        // principal is frozen at login time and doesn't know about teams
        // created later in the same session.
        if (sessionTeamId != null) {
            boolean stillBelongs = teamQueryService.getUserTeam(wu.getUserId(), sessionTeamId).isPresent();
            if (stillBelongs) {
                return sessionTeamId;
            }
        }
        return wu.getActiveTeamId();
    }

    @ModelAttribute("activeTeamSlug")
    public String activeTeamSlug(@AuthenticationPrincipal OidcUser oidcUser, HttpSession session) {
        TeamQueryService.UserTeamDto info = getActiveTeamInfo(oidcUser, session);
        return info != null ? info.slug() : null;
    }

    @ModelAttribute("activeTeamName")
    public String activeTeamName(@AuthenticationPrincipal OidcUser oidcUser, HttpSession session) {
        TeamQueryService.UserTeamDto info = getActiveTeamInfo(oidcUser, session);
        return info != null ? info.name() : null;
    }

    @ModelAttribute("activeTeamRole")
    public String activeTeamRole(@AuthenticationPrincipal OidcUser oidcUser, HttpSession session) {
        TeamQueryService.UserTeamDto info = getActiveTeamInfo(oidcUser, session);
        return info != null ? info.role() : null;
    }

    /**
     * Display name of the currently logged-in user — used in the top-nav second
     * row (right-aligned pill) to show who is signed in, replacing the previous
     * "role" badge which duplicated the active team role.
     */
    @ModelAttribute("currentUserName")
    public String currentUserName(@AuthenticationPrincipal OidcUser oidcUser) {
        if (oidcUser == null) {
            return null;
        }
        // Prefer wbUsername (the app-level login) over the raw Keycloak email
        if (oidcUser instanceof WindbandOidcUser wu && wu.getWbUsername() != null) {
            return wu.getWbUsername();
        }
        String preferred = oidcUser.getPreferredUsername();
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return oidcUser.getName();
    }

    /**
     * CSS color (HSL) for the active team's avatar chip in the top-nav.
     * Returns {@code null} when the user has 0 or 1 teams — the chip then
     * uses a neutral PicoCSS variable so a single-team user doesn't get a
     * "look how special this team is" colored circle.
     */
    @ModelAttribute("activeTeamColor")
    public String activeTeamColor(@AuthenticationPrincipal OidcUser oidcUser, HttpSession session) {
        List<TeamView> teams = userTeams(oidcUser);
        if (teams.size() < 2) {
            return null;
        }
        Long activeId = activeTeamId(oidcUser, session);
        if (activeId == null) {
            return null;
        }
        for (TeamView t : teams) {
            if (t.id().equals(activeId)) {
                return t.color();
            }
        }
        return null;
    }

    @ModelAttribute("today")
    public LocalDate today() {
        return LocalDate.now();
    }

    /**
     * Even-distribution HSL palette over {@code count} hues. Saturation 58%
     * and lightness 50% read well in both PicoCSS light and dark themes and
     * keep the white "first letter" inside the circle legible.
     */
    private static String teamColor(int index, int count) {
        if (count <= 1) {
            return "hsl(0, 0%, 50%)";
        }
        int hue = (int) Math.round((index * 360.0) / count);
        return String.format("hsl(%d, 58%%, 50%%)", hue);
    }

    private TeamQueryService.UserTeamDto getActiveTeamInfo(OidcUser oidcUser, HttpSession session) {
        if (!(oidcUser instanceof WindbandOidcUser wu)) {
            return null;
        }
        Long sessionTeamId = (Long) session.getAttribute("activeTeamId");
        // Check against DB instead of cached WindbandOidcUser.teamIds
        if (sessionTeamId != null) {
            var team = teamQueryService.getUserTeam(wu.getUserId(), sessionTeamId);
            if (team.isPresent()) {
                return team.get();
            }
        }
        Long teamId = wu.getActiveTeamId();
        if (teamId == null) return null;
        return teamQueryService.getUserTeam(wu.getUserId(), teamId).orElse(null);
    }
}
