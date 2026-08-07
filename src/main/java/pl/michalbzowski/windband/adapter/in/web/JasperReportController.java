package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.Map;

import pl.michalbzowski.windband.application.report.ReportCompiler;
import pl.michalbzowski.windband.application.report.ReportGeneratorService;
import pl.michalbzowski.windband.application.report.exception.ReportGenerationException;

/**
 * Controller do bezpośredniego generowania raportów JasperReports.
 * Endpointy typu: /jasper-report/{key}?param1=value1&param2=value2
 */
@Controller
@RequestMapping("/jasper-report")
@RequiredArgsConstructor
public class JasperReportController {

    private static final Logger log = LoggerFactory.getLogger(JasperReportController.class);

    private final ReportCompiler reportCompiler;
    private final ReportGeneratorService reportGeneratorService;

    /**
     * Generuje raport PDF bezpośrednio w przeglądarce.
     * Przykład: GET /jasper-report/hello?name=Michał
     */
    @GetMapping("/{key}")
    public void generateReport(
            @PathVariable String key,
            @RequestParam Map<String, String> stringParameters,
            HttpServletResponse response) throws IOException {

        log.info("Generating Jasper report: {} with params: {}", key, stringParameters);

        // Konwersja Map<String, String> do Map<String, Object>
        Map<String, Object> parameters = new java.util.HashMap<>();
        for (Map.Entry<String, String> entry : stringParameters.entrySet()) {
            parameters.put(entry.getKey(), entry.getValue());
        }

        try {
            byte[] pdf = reportGeneratorService.generatePdf(key, parameters);

            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "inline; filename=\"" + key + ".pdf\"");
            response.setContentLength(pdf.length);
            response.getOutputStream().write(pdf);
            response.getOutputStream().flush();

        } catch (ReportGenerationException e) {
            log.error("Error generating Jasper report: {}", key, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Błąd generowania raportu: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            if ("Raport nie znaleziony".equals(e.getMessage().split(":")[0])) {
                log.error("Report not found: {}", key);
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    "Raport nie znaleziony: " + key);
            } else {
                throw e;
            }
        }
    }

}
