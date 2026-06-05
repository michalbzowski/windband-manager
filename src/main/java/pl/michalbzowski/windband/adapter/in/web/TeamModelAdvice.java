package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;

import java.util.List;

/**
 * Global model attributes for teams — adds user's teams to all templates.
 * This avoids duplicating team info setup in every page controller.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class TeamModelAdvice {

    private final TeamQueryService teamQueryService;

    @ModelAttribute("userTeams")
    public List<?> userTeams(@AuthenticationPrincipal OidcUser oidcUser) {
        if (oidcUser instanceof WindbandOidcUser wu) {
            return teamQueryService.getUserTeams(wu.getUserId());
        }
        return List.of();
    }
}