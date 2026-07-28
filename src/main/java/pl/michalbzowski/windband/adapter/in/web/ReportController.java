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
    private final JacpsReportController jacpsReportController; // For PDF generation via JasperReports

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

    /**
     * Generate PDF report using jacps-report-adapter (JasperReports ACL).
     * Accepts JSON body with: bandName, instructorName, periodYear, periodMonth, etc.
     * Returns binary PDF attachment for download.
     */
    @PostMapping("/pdf")
    public ResponseEntity<byte[]> generatePdfReport(
            @AuthenticationPrincipal OidcUser oidcUser, HttpSession session,
            @RequestBody JacpsReportController.ReportGenerationRequest request) {
        return jacpsReportController.generateReport(request);
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
