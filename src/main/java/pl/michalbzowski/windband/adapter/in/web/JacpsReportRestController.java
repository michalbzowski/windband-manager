package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/jacps/reports")
@RequiredArgsConstructor
public class JacpsReportRestController {

    private static final Logger log = LoggerFactory.getLogger(JacpsReportRestController.class);
    private final JacpsReportController jacpsReportController;

    @PostMapping("/generate")
    public ResponseEntity<byte[]> generateReport(@RequestBody JacpsReportController.ReportGenerationRequest request) {
        return jacpsReportController.generateReport(request);
    }

    @GetMapping("/test")
    public ResponseEntity<String> testEndpoint() {
        log.info("Test endpoint called - generating sample report via JacpsReportController");
        JacpsReportController.ReportGenerationRequest request = new JacpsReportController.ReportGenerationRequest();
        request.setBandName("Test Band");
        request.setInstructorName("Test Instructor");
        request.setFormat("PDF");
        // periodYear/periodMonth will default to last month in controller
        ResponseEntity<byte[]> result = jacpsReportController.generateReport(request);
        if (result.getStatusCode().is2xxSuccessful()) {
            byte[] body = result.getBody();
            if (body != null) {
                log.info("Sample report generated successfully, size: {} bytes", body.length);
            } else {
                log.info("Sample report generated successfully but body is null");
            }
            return ResponseEntity.ok("Test endpoint executed. Check logs for JacpsReportController activity.");
        } else {
            log.warn("Sample report generation returned status: {}", result.getStatusCode());
            return ResponseEntity.status(result.getStatusCode()).body("Test endpoint executed but report generation failed with status " + result.getStatusCode());
        }
    }
}
