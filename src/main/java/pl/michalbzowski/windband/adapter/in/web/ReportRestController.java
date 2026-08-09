package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;
import pl.michalbzowski.windband.application.report.ReportCompiler;
import pl.michalbzowski.windband.application.report.ReportGeneratorService;
import pl.michalbzowski.windband.application.report.ReportMetadata;
import pl.michalbzowski.windband.application.report.exception.ReportGenerationException;

/**
 * REST API do zarządzania raportami Jasper.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportRestController {

    private static final Logger log = LoggerFactory.getLogger(ReportRestController.class);
    private final ReportCompiler reportCompiler;
    private final ReportGeneratorService reportGeneratorService;
    private final TeamQueryService teamQueryService;

    /** GET /api/reports - zwraca listę wszystkich dostępnych raportów z ich metadanymi */
    @GetMapping
    public java.util.List<ReportMetadata> listReports() {
        return reportCompiler.getMetadataCache().values().stream()
                .filter(m -> !m.getKey().equals("members"))  // ukryj wewn. raporty
                .toList();
    }

    /** GET /api/reports/{key} - zwraca szczegółowe metadane danego raportu */
    @GetMapping("/{key}")
    public java.util.Optional<ReportMetadata> getReport(@PathVariable String key) {
        ReportMetadata metadata = reportCompiler.getReportMetadata(key);
        return metadata != null
                ? java.util.Optional.of(metadata)
                : java.util.Optional.empty();
    }

    /** POST /api/reports/{key}/generate - generuje raport i zwraca jako binary stream */
    @PostMapping("/{key}/generate")
    public ResponseEntity<byte[]> generateReport(
            @PathVariable String key,
            @RequestBody Map<String, Object> requestBody,
            @AuthenticationPrincipal WindbandOidcUser oidcUser) {

        ReportMetadata metadata = reportCompiler.getReportMetadata(key);
        if (metadata == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Sprawdź czy użytkownik ma aktywny zespół
        if (oidcUser == null || oidcUser.getActiveTeamId() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Pobierz format z requestBody (default PDF)
        Map<String, Object> parameters = new HashMap<>(requestBody);
        String format = (String) parameters.remove("format");
        if (format == null) {
            format = "PDF";
        }
        // Usuń pola techniczne formularza, które nie są parametrami raportu
        parameters.remove("reportKey");

        if (!"PDF".equalsIgnoreCase(format)) {
            log.info("Requested unsupported format {} for report {}", format, key);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Wymuś kontekst zespołu po stronie serwera (bezpieczeństwo wielotenantowe)
        Long activeBandId = oidcUser.getActiveTeamId();
        String activeBandName = teamQueryService.getBandName(activeBandId).orElse("");
        parameters.put("band_id", activeBandId);
        parameters.put("band_name", activeBandName);

        try {
            log.info("Generating PDF for report: {}", key);
            byte[] reportBytes = reportGeneratorService.generatePdf(key, parameters);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            String filename = key + ".pdf";
            headers.setContentDispositionFormData("attachment", filename);
            return ResponseEntity.ok().headers(headers).body(reportBytes);

        } catch (ReportGenerationException e) {
            log.error("Error generating report: {} format {}", key, format, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
