package pl.michalbzowski.windband.application.report;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test weryfikujący poprawność składni wszystkich raportów Jasper.
 * Kompiluje każdy plik .jrxml z classpath:reports/ używając legacy XML parsera
 * (Jackson XML parser jest wyłączony przez system property).
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportCompilationTest {

    @Autowired
    private ResourceLoader resourceLoader;

    private static final List<String> REPORT_FILES = Arrays.asList(
            "sprawozdanie-miesieczne.jrxml",
            "sprawozdanie.jrxml",
            "members.jrxml",
            "hello.jrxml"
    );

    @Test
    void shouldCompileAllReports() throws IOException {
        for (String reportFile : REPORT_FILES) {
            Resource source = resourceLoader.getResource("classpath:reports/" + reportFile);

            if (!source.exists()) {
                System.out.println("SKIP: Report not found: " + reportFile);
                continue;
            }

            try (InputStream inputStream = source.getInputStream()) {
                System.out.println("Compiling report: " + reportFile);

                // Use JRXmlLoader directly with legacy parser (Jackson disabled via system property)
                JasperDesign design = (JasperDesign) JRXmlLoader.load(inputStream);
                JasperReport jasperReport = JasperCompileManager.compileReport(design);

                assertThat(jasperReport).as("Compiled report must not be null: " + reportFile).isNotNull();
                assertThat(jasperReport.getName()).as("Report name must not be empty: " + reportFile).isNotEmpty();
                System.out.println("SUCCESS: " + reportFile + " -> " + jasperReport.getName());
            } catch (JRException e) {
                System.err.println("FAILED: " + reportFile);
                e.printStackTrace(System.err);
                throw new AssertionError("Failed to compile report: " + reportFile, e);
            }
        }
    }
}