package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.report.MonthlyReport;
import pl.michalbzowski.windband.application.query.report.ReportQueryService;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;
import pl.michalbzowski.windband.application.report.ReportService;

import java.time.YearMonth;

@Controller
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportPageController {

    private final ReportQueryService reportQueryService;
    private final TeamQueryService teamQueryService;
    private final ReportService reportService; // JasperReports service

    @GetMapping
    public String reportsPage(Model model) {
        model.addAttribute("currentMonth", YearMonth.now());
        return "reports/list";
    }

    @GetMapping("/jasper")
    public String jasperReportsPage() {
        return "reports/jasper-list";
    }

    /**
     * Endpoint pobierający PDF raport członków zespołu.
     */
    @GetMapping("/jasper/members-pdf")
    public void downloadMembersPdf(
            HttpServletResponse response,
            @AuthenticationPrincipal OidcUser oidcUser,
            HttpSession session) throws Exception {

        Long teamId = resolveActiveTeamId(oidcUser, session);
        if (teamId == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Brak dostępu do zespołu");
            return;
        }

        byte[] pdfBytes = reportService.generateMembersPdf(teamId);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"czlonkowie-team-" + teamId + ".pdf\"");
        response.setContentLengthLong(pdfBytes.length);

        try (var outputStream = response.getOutputStream()) {
            outputStream.write(pdfBytes);
            outputStream.flush();
        }
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
