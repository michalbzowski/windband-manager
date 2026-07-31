package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

import pl.michalbzowski.windband.application.report.ReportCompiler;
import pl.michalbzowski.windband.application.report.ReportGeneratorService;
import pl.michalbzowski.windband.application.report.ReportMetadata;

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
    public void generateReport(
            @PathVariable String key,
            @RequestBody Map<String, Object> requestBody,
            HttpServletResponse response) throws IOException {

        ReportMetadata metadata = reportCompiler.getReportMetadata(key);
        if (metadata == null) {
            response.sendError(HttpStatus.NOT_FOUND.value(), "Raport nie znaleziony: " + key);
            return;
        }

        // Pobierz format z requestBody (default PDF)
        String format = (String) requestBody.remove("format");
        if (format == null) {
            format = "PDF";
        }

        try {
            byte[] reportBytes = switch (format.toUpperCase()) {
                case "DOCX" -> {
                    log.info("Generating DOCX: {} - NOT IMPLEMENTED", key);
                    throw new UnsupportedOperationException("DOC export not implemented");
                }
                default -> {
                    log.info("Generating PDF for report: {}", key);
                    yield reportGeneratorService.generatePdf(key, requestBody);
                }
            };

            // Ustaw nagłówki odpowiedzi
            String extension = format.equals("PDF") ? "pdf" : "docx";
            response.setContentType(MediaType.APPLICATION_PDF_VALUE);
            String filename = key + "." + extension;
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + filename + "\"");
            response.setStatus(HttpStatus.OK.value());
            response.getOutputStream().write(reportBytes);
            response.getOutputStream().flush();

        } catch (IOException e) {
            log.error("Error generating report: {} format {}", key, format, e);
            try {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Błąd generowania raportu: " + e.getMessage());
            } catch (IOException ex) {
                log.error("Failed to send error response", ex);
            }
        }
    }
}
