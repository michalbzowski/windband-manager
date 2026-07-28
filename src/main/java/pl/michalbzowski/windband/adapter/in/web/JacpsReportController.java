package pl.michalbzowski.windband.adapter.in.web;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.infrastructure.jacps.ReportApiClient;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Component for jacps-report-adapter integration.
 * Generates reports via ACL endpoint POST /api/v1/reports/generate
 * 
 * Expected request body format:
 * {
 *   "path": "/bands/test/sprawozdanie.jrxml",
 *   "format": "PDF",
 *   "parameters": {}
 * }
 */
@Component
public class JacpsReportController {

    private ReportApiClient reportApiClient; // Optional — gracefully degrades when jacps-report-adapter is unavailable

    @Autowired(required = false)
    public void setReportApiClient(ReportApiClient client) {
        this.reportApiClient = client;
    }

    /**
     * Internal method to generate report bytes via jacps-report-adapter.
     * 
     * @param request Report generation request with parameters
     * @return ResponseEntity with binary PDF/XLSX/HTML content or 503 if service unavailable
     */
    public ResponseEntity<byte[]> generateReport(ReportGenerationRequest request) {
        if (reportApiClient == null) {
            return ResponseEntity.status(503).build(); // Service unavailable — jacps-report-adapter not running
        }

        // Build report path
        String reportPath = "/bands/test/sprawozdanie.jrxml";
        
        // Prepare parameters map
        Map<String, String> params = new HashMap<>();
        
        if (request.getBandName() != null) {
            params.put("bandName", request.getBandName());
        }
        if (request.getInstructorName() != null) {
            params.put("instructorName", request.getInstructorName());
        }

        // Calculate date range from request or use default (last month)
        LocalDate now = LocalDate.now();
        LocalDate from;
        LocalDate to;
        
        if (request.getPeriodYear() != null && request.getPeriodMonth() != null) {
            // User specified year/month for the period - use that month (1st to 28th)
            int periodYear = request.getPeriodYear();
            int periodMonth = request.getPeriodMonth();
            
            from = LocalDate.of(periodYear, periodMonth, 1);
            to = LocalDate.of(periodYear, periodMonth, 28); // Safe end date for any month
            
        } else {
            // Default: last complete month (from 1st to last day of that month)
            from = now.minusMonths(1).withDayOfMonth(1);
            int daysInLastMonth = now.minusMonths(1).lengthOfMonth();
            to = now.minusMonths(1).withDayOfMonth(daysInLastMonth);
        }

        params.put("from", from.toString());
        params.put("to", to.toString());

        // Output format (default: PDF)
        String format = "PDF";
        if (request.getFormat() != null && !request.getFormat().isEmpty()) {
            format = request.getFormat().toUpperCase();
        }

        // Period for filename generation
        int year;
        int month;
        
        if (request.getPeriodYear() != null && request.getPeriodMonth() != null) {
            year = request.getPeriodYear();
            month = request.getPeriodMonth();
        } else {
            // Default: last month
            LocalDate lastMonthDate = now.minusMonths(1);
            year = lastMonthDate.getYear();
            month = lastMonthDate.getMonthValue();
        }

        String fileName = String.format("sprawozdanie-%04d-%02d", year, month);

        // Generate report via jacps-report-adapter
        try {
            byte[] bytes = reportApiClient.generateReport(reportPath, format.toUpperCase(), params);
            
            if (bytes == null || bytes.length == 0) {
                return ResponseEntity.status(500).build();
            }

            // Prepare response headers
            HttpHeaders headers = new HttpHeaders();
            String extension = format.toLowerCase();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(
                ContentDisposition.builder("attachment")
                    .filename(fileName + "." + extension)
                    .build());

            return ResponseEntity.ok()
                .headers(headers)
                .body(bytes);

        } catch (Exception e) {
            throw new RuntimeException("Report generation failed: " + e.getMessage(), e);
        }
    }

    /** DTO for report generation request */
    @Data
    public static class ReportGenerationRequest {
        private String bandName;
        private String instructorName;
        private Integer periodYear;
        private Integer periodMonth;
        private Integer monthFrom;
        private Integer yearFrom;
        private Integer monthTo;
        private Integer yearTo;
        private String format = "PDF";
    }

}
