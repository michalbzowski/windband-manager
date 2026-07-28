package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.report.MonthlyReport;
import pl.michalbzowski.windband.application.query.report.ReportQueryService;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;

import java.time.YearMonth;

@Controller
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportPageController {

    private final ReportQueryService reportQueryService;
    private final TeamQueryService teamQueryService;

    @GetMapping
    public String reportsPage(Model model) {
        model.addAttribute("currentMonth", YearMonth.now());
        return "reports/list";
    }

    @GetMapping("/list")
    public String listFragment(Model model) {
        model.addAttribute("currentMonth", YearMonth.now());
        return "reports/list :: #reports-content";
    }

    @GetMapping("/sprawozdanie/customize")
    public String sprawozdanieCustomize(Model model) {
        model.addAttribute("currentMonth", YearMonth.now());
        return "reports/sprawozdanie-customize";
    }

    @GetMapping("/generate")
    public String generateReport(@AuthenticationPrincipal OidcUser oidcUser, HttpSession session,
                                  @RequestParam int year, @RequestParam int month, Model model) {
        YearMonth ym = YearMonth.of(year, month);
        Long activeTeamId = resolveActiveTeamId(oidcUser, session);
        MonthlyReport report = reportQueryService.generateMonthlyReport(ym, activeTeamId);
        model.addAttribute("report", report);
        return "reports/view";
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
