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
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.michalbzowski.windband.application.report.ReportGeneratorService;

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

    public ResponseEntity<byte[]> generateReport(ReportGenerationRequest request) {
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

        // Parametr band_id jest wymagany do filtrowania danych w zapytaniu SQL
        Long bandId = 1L; // TODO: pobrać z kontekstu sesji/autentyzacji dla multi-tenant

        params.put("band_id", bandId);
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
