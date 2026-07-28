package pl.michalbzowski.windband.infrastructure.jacps;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Client for jacps-report-adapter ACL integration.
 *
 * Endpoints:
 * - List reports: GET /api/v1/reports?band={name}
 * - Report metadata: GET /api/v1/reports/metadata?path={path}
 * - Generate report: POST /api/v1/report/generate → byte[] (PDF/XLS/HTML)
 *
 * Gracefully degrades to "Report generation service unavailable" when jacps-report-adapter is down.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportApiClient {

    private final RestTemplate restTemplate;

    @Value("${jacps.report.base-url:http://localhost:8081}")
    private String baseUrl;

    /**
     * Generates report (PDF/XLSX/HTML/XML) based on given path and parameters.
     *
     * @param reportPath .jrxml file path (e.g.: "/bands/test/sprawozdanie.jrxml")
     * @param format Output format: "PDF", "HTML", "XLSX", "XML"
     * @param parameters Report parameters (optional, can be null or empty map)
     * @return Byte array with generated report content or null if service unavailable
     */
    public byte[] generateReport(String reportPath, String format, Map<String, String> parameters) {
        if (parameters == null) {
            parameters = new HashMap<>();
        }

        // Build JSON body for POST request: {"path", "format": ..., "parameters": {...}}
        Map<String, Object> requestBody = new HashMap<>(3);
        requestBody.put("path", reportPath);
        requestBody.put("format", format.toUpperCase());
        requestBody.put("parameters", parameters);

        String url = baseUrl + "/api/v1/reports/generate";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        log.info("Generating report: path={}, format={}", reportPath, format);

        try {
            ResponseEntity<byte[]> response = restTemplate.postForEntity(url, entity, byte[].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("Report generated successfully - {} bytes", response.getBody().length);

                // Extract filename from Content-Disposition header for logging
                String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
                if (disposition != null) {
                    log.debug("Content-Disposition: {}", disposition);
                }

                return response.getBody();
            } else {
                log.warn("Report generation failed with status: {} - headers: {}",
                        response.getStatusCode(), response.getHeaders());
                return null;
            }

        } catch (HttpClientErrorException e) {
            // 4xx errors — invalid parameters, unsupported format, etc.
            log.error("HTTP error generating report {}: {}: {}",
                    reportPath, e.getStatusCode(), e.getMessage());
            throw new ReportGenerationException(
                String.format("Failed to generate %s report (path: %s): %s",
                    format, reportPath, e.getMessage()));

        } catch (ResourceAccessException e) {
            // Network issues — jacps-report-adapter unavailable
            log.error("Cannot reach jacps-report-adapter at {}: {}", baseUrl, e.getMessage());
            throw new ReportGenerationException(
                "Report generation service temporarily unavailable. Please try again later.");

        } catch (Exception e) {
            // Unexpected errors
            log.error("Unexpected error during report generation for path {}", reportPath, e);
            throw new ReportGenerationException(
                String.format("Unexpected error generating %s report: %s",
                    format, e.getMessage()));
        }
    }

}
