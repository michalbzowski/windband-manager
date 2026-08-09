package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.report.ReportQueryService;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;
import pl.michalbzowski.windband.application.report.ReportCompiler;
import pl.michalbzowski.windband.application.report.ReportGeneratorService;
import pl.michalbzowski.windband.application.report.ReportMetadata;
import pl.michalbzowski.windband.application.report.exception.ReportGenerationException;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportPageController {

    private static final Logger log = LoggerFactory.getLogger(ReportPageController.class);

    private final ReportQueryService reportQueryService;
    private final TeamQueryService teamQueryService;
    private final ReportCompiler reportCompiler; // JasperReports metadata
    private final ReportGeneratorService reportGeneratorService;

    @GetMapping
    public String reportsPage(Model model) {
        // Pobierz raporty Jasper (tylko publiczne - nie "members" wewn.)
        var reports = reportCompiler.getMetadataCache().values().stream()
            .filter(m -> !m.getKey().equals("members"))
            .toList();

        model.addAttribute("currentMonth", YearMonth.now());
        model.addAttribute("reports", reports);
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

        // Kontekst zespołu użytkownika — band_id i band_name dostarczane niewidocznie.
        // Parametry z forPrompting=false (band_id, band_name) NIE są pokazywane w UI;
        // ich wartości pochodzą z aktywnego zespołu zalogowanego użytkownika.
        Long activeBandId = oidcUser.getActiveTeamId();
        String activeBandName = teamQueryService.getBandName(activeBandId).orElse("");

        model.addAttribute("activeBandId", activeBandId);
        model.addAttribute("activeBandName", activeBandName);

        return "reports/configure";
    }

    /**
     * Generuje raport PDF i zwraca go jako plik do pobrania.
     *
     * <p>Parametry widoczne (forPrompting=true) przychodzą z formularza. Parametry
     * kontekstu zespołu (band_id, band_name) są NADPISYWANE po stronie serwera
     * wartościami z aktywnego zespołu zalogowanego użytkownika — nie ufamy
     * ukrytym polom formularza, aby uniemożliwić pobranie danych innego zespołu.
     */
    @PostMapping("/configure/{key}/download")
    public ResponseEntity<byte[]> downloadReport(
            @PathVariable String key,
            @RequestParam Map<String, String> formParams,
            @AuthenticationPrincipal WindbandOidcUser oidcUser) {

        ReportMetadata metadata = reportCompiler.getReportMetadata(key);
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }

        // Sprawdź czy użytkownik ma aktywny zespół
        Long activeBandId = oidcUser != null ? oidcUser.getActiveTeamId() : null;
        if (activeBandId == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }

        String activeBandName = teamQueryService.getBandName(activeBandId).orElse("");

        Map<String, Object> parameters = new HashMap<>(formParams);
        parameters.remove("format");
        parameters.remove("reportKey");

        // Wymuś kontekst zespołu po stronie serwera (bezpieczeństwo wielotenantowe)
        parameters.put("band_id", String.valueOf(activeBandId));
        parameters.put("band_name", activeBandName);

        try {
            byte[] pdf = reportGeneratorService.generatePdf(key, parameters);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", key + ".pdf");
            return ResponseEntity.ok().headers(headers).body(pdf);
        } catch (ReportGenerationException e) {
            log.error("Error generating report {}", key, e);
            // Return error message in response body for debugging
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            return ResponseEntity.internalServerError().headers(headers).body(
                    ("Błąd generowania raportu: " + e.getMessage()).getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
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
