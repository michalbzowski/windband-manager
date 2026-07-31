package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.report.ReportQueryService;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;
import pl.michalbzowski.windband.application.report.ReportCompiler;
import pl.michalbzowski.windband.application.report.ReportMetadata;

import java.time.YearMonth;
import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportPageController {

    private final ReportQueryService reportQueryService;
    private final TeamQueryService teamQueryService;
    private final ReportCompiler reportCompiler; // JasperReports metadata
    
    @GetMapping
    public String reportsPage(Model model) {
        model.addAttribute("currentMonth", YearMonth.now());
        return "reports/list";
    }

    @GetMapping("/jasper")
    public String jasperReportsPage(Model model, ObjectMapper objectMapper) throws Exception {
        // Pobierz raporty (tylko publiczne - nie "members" wewn.)
        var reports = reportCompiler.getMetadataCache().values().stream()
            .filter(m -> !m.getKey().equals("members"))
            .toList();
        
        // Zserializuj do JSON dla szablonu HTML
        String jsonReports = objectMapper.writeValueAsString(reports);
        model.addAttribute("jsonReports", jsonReports);
        
        return "reports/jasper-list";
    }

    @GetMapping("/sprawozdanie/customize")
    public String sprawozdanieCustomizePage(Model model) {
        model.addAttribute("currentYear", java.time.Year.now().getValue());
        return "reports/sprawozdanie-customize";
    }

    /** Konfiguracja parametrów raportu */
    @GetMapping("/configure/{key}")
    public String configureReport(@PathVariable String key, Model model, 
                                  @AuthenticationPrincipal WindbandOidcUser oidcUser) {
        ReportMetadata metadata = reportCompiler.getReportMetadata(key);
        if (metadata == null) {
            return "redirect:/reports/jasper"; // Wróć jeśli nie znaleziono raportu  
        }

        model.addAttribute("report", metadata);
        
        // Pobierz kontekst użytkownika — band ID i nazwa zespołu  
        Long activeBandId = oidcUser.getActiveTeamId();
        String activeBandName = teamQueryService.getBandName(activeBandId).orElse("Unknown Band");  // fetch from repo
        
        model.addAttribute("activeBandId", activeBandId);
        model.addAttribute("activeBandName", activeBandName);
        
        return "reports/configure";
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
