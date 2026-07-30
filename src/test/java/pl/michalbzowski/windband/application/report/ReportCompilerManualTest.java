package pl.michalbzowski.windband.application.report;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ReportCompilerManualTest {

    @Autowired
    private ResourceLoader resourceLoader;

    @Test
    void shouldCompileReportManually() throws Exception {
        var source = resourceLoader.getResource("classpath:reports/members.jrxml");
        assertThat(source.exists()).as("JRXML file must exist").isTrue();

        try (var inputStream = source.getInputStream()) {
            System.out.println("Attempting to compile report...");
            var jasperReport = JasperCompileManager.compileReport(inputStream);
            assertThat(jasperReport).as("Compiled report must not be null").isNotNull();
            System.out.println("SUCCESS: Report compiled!");
            System.out.println("Report name: " + jasperReport.getName());
        } catch (JRException e) {
            // Print full stack trace with root cause chain
            System.err.println("===== ERROR COMPILING REPORT =====");
            e.printStackTrace(System.err);

            var suppressed = e.getSuppressed();
            if (suppressed != null && suppressed.length > 0) {
                System.err.println("\n=== Suppressed exceptions ===");
                for (var s : suppressed) {
                    s.printStackTrace(System.err);
                }
            }

            // Re-throw assertion error with more context
            throw new AssertionError("Failed to compile report", e);
        }
    }
}
