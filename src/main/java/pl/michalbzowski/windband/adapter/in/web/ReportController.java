package pl.michalbzowski.windband.adapter.in.web;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import pl.michalbzowski.windband.application.report.ReportGeneratorService;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;

@Component
class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ReportGeneratorService reportGeneratorService;

    ReportController(ReportGeneratorService reportGeneratorService) {
        this.reportGeneratorService = reportGeneratorService;
    }

    @GetMapping("/generate")
    public String generateMonthlyReport(@RequestParam int year, @RequestParam int month) {
        return "reports/monthly-report";
    }

    @ModelAttribute("reportGenerationRequest")
    public ReportGenerationRequest reportGenerationRequest(@AuthenticationPrincipal WindbandOidcUser oidcUser) {
        // Model attribute to ensure band context is available in the form
        return new ReportGenerationRequest();
    }

    public ResponseEntity<byte[]> generateReport(ReportGenerationRequest request,
                                                 @AuthenticationPrincipal WindbandOidcUser oidcUser) {
        LocalDate now = LocalDate.now();
        LocalDate from = request.getPeriodYear() != null && request.getPeriodMonth() != null
                ? LocalDate.of(request.getPeriodYear(), request.getPeriodMonth(), 1)
                : now.minusMonths(1).withDayOfMonth(1);
        LocalDate to = request.getPeriodYear() != null && request.getPeriodMonth() != null
                ? LocalDate.of(request.getPeriodYear(), request.getPeriodMonth(), 28)
                : now.minusMonths(1).withDayOfMonth(now.minusMonths(1).lengthOfMonth());

        // Konwertuj na java.util.Date (wymagane przez JasperReports dla parametru klasy java.util.Date)
        java.util.Date fromDate = java.sql.Date.valueOf(from);
        java.util.Date toDate = java.sql.Date.valueOf(to);

        Map<String, Object> params = new HashMap<>();

        // Pobierz band_id z kontekstu sesji/autentyzacji (Keycloak token) - zastępuje hardcodowane 1L
        Long activeBandId = oidcUser != null ? oidcUser.getActiveTeamId() : null;
        if (activeBandId == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        params.put("band_id", activeBandId);
        params.put("date_from", fromDate);
        params.put("date_to", toDate);
        params.put("band_name", request.getBandName() != null ? request.getBandName() : "");
        params.put("instructor_name", request.getInstructorName() != null ? request.getInstructorName() : "");

        try {
            byte[] bytes = reportGeneratorService.generatePdf("sprawozdanie-miesieczne", params);
            if (bytes == null || bytes.length == 0) {
                return ResponseEntity.status(500).build();
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.builder("attachment")
                    .filename("sprawozdanie-miesieczne.pdf")
                    .build());
            return ResponseEntity.ok().headers(headers).body(bytes);
        } catch (Exception e) {
            log.error("Report generation failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Data
    public static class ReportGenerationRequest {
        private String bandName;
        private String instructorName;
        private Integer periodYear;
        private Integer periodMonth;
        private Integer activeMembersCount;
        private Integer minorCount;
        private Integer senior60PlusCount;
        private Integer rehearsalsCount;
    }
}