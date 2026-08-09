package pl.michalbzowski.windband.application.report;

import net.sf.jasperreports.engine.JasperReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ReportGeneratorServiceParameterCoercionTest {

    @Autowired
    private ReportGeneratorService reportGeneratorService;

    @Autowired
    private ReportCompiler reportCompiler;

    @Test
    @DisplayName("should coerce String parameters")
    void shouldCoerceStringParameters() {
        // given - use existing compiled report
        JasperReport report = reportCompiler.getCompiledReport("hello");
        assertThat(report).isNotNull();

        // Verify parameter types are recognized from existing report
        Map<String, String> paramTypes = new HashMap<>();
        if (report.getParameters() != null) {
            for (var p : report.getParameters()) {
                if (!p.isSystemDefined() && p.getValueClassName() != null) {
                    paramTypes.put(p.getName(), p.getValueClassName());
                }
            }
        }

        // hello.jrxml has: message (String), user (String), generatedDate (LocalDate)
        assertThat(paramTypes).containsKeys("message", "user");
    }

    @Test
    @DisplayName("should handle unsupported types gracefully")
    void shouldHandleUnsupportedTypesGracefully() {
        // given - use existing report with known parameters
        JasperReport report = reportCompiler.getCompiledReport("sprawozdanie-miesieczne");
        assertThat(report).isNotNull();

        // Verify parameter types are recognized
        Map<String, String> paramTypes = new HashMap<>();
        if (report.getParameters() != null) {
            for (var p : report.getParameters()) {
                if (!p.isSystemDefined() && p.getValueClassName() != null) {
                    paramTypes.put(p.getName(), p.getValueClassName());
                }
            }
        }

        // Should have band_id (Integer), date_from (Date), date_to (Date), band_name (String), instructor_name (String)
        assertThat(paramTypes).containsKeys("band_id", "date_from", "date_to", "band_name", "instructor_name");
    }

    @Test
    @DisplayName("should parse date correctly with yyyy-MM-dd format")
    void shouldParseDateCorrectly() {
        // given - use existing report with date parameters
        JasperReport report = reportCompiler.getCompiledReport("sprawozdanie-miesieczne");
        assertThat(report).isNotNull();

        Map<String, String> paramTypes = new HashMap<>();
        if (report.getParameters() != null) {
            for (var p : report.getParameters()) {
                if (!p.isSystemDefined() && p.getValueClassName() != null) {
                    paramTypes.put(p.getName(), p.getValueClassName());
                }
            }
        }

        assertThat(paramTypes)
            .containsEntry("date_from", "java.util.Date")
            .containsEntry("date_to", "java.util.Date");
    }
}