package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.report.MonthlyReport;
import pl.michalbzowski.windband.application.query.report.ReportQueryService;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportQueryService reportQueryService;
    private final TeamQueryService teamQueryService;

    @GetMapping("/monthly")
    public ResponseEntity<MonthlyReport> getMonthlyReport(
            @AuthenticationPrincipal OidcUser oidcUser, HttpSession session,
            @RequestParam int year,
            @RequestParam int month) {
        YearMonth ym = YearMonth.of(year, month);
        Long activeTeamId = resolveActiveTeamId(oidcUser, session);
        MonthlyReport report = reportQueryService.generateMonthlyReport(ym, activeTeamId);
        return ResponseEntity.ok(report);
    }

    private Long resolveActiveTeamId(OidcUser oidcUser, HttpSession session) {
        if (!(oidcUser instanceof WindbandOidcUser wu)) {
            return null;
        }
        Long sessionTeamId = (Long) session.getAttribute("activeTeamId");
        if (sessionTeamId != null) {
            boolean stillBelongs = teamQueryService.getUserTeam(wu.getUserId(), sessionTeamId).isPresent();
            if (stillBelongs) {
                return sessionTeamId;
            }
        }
        return wu.getActiveTeamId();
    }
}
