package pl.michalbzowski.windband.application.report;

import jakarta.annotation.PostConstruct;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class ReportCompiler {

    private static final Logger log = LoggerFactory.getLogger(ReportCompiler.class);

    private final ResourceLoader resourceLoader;

    public ReportCompiler(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void compileReports() {
        List<String> reports = List.of("members.jrxml"); // add more as needed

        for (String reportName : reports) {
            try {
                compileReport(reportName);
            } catch (Exception e) {
                log.error("Failed to compile report: {}", reportName, e);
            }
        }
    }

    private void compileReport(String reportName) throws JRException, IOException {
        Resource source = resourceLoader.getResource("classpath:reports/" + reportName);
        if (!source.exists()) {
            log.warn("Report source not found: classpath:reports/{}", reportName);
            return;
        }

        String jasperName = reportName.replace(".jrxml", ".jasper");
        Path outputPath = Path.of("target/classes/reports", jasperName);
        Files.createDirectories(outputPath.getParent());

        try (InputStream inputStream = source.getInputStream();
             OutputStream outputStream = Files.newOutputStream(outputPath)) {
            JasperCompileManager.compileReportToStream(inputStream, outputStream);
            log.info("Compiled report: {} -> {}", reportName, outputPath);
        }
    }
}