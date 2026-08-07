package pl.michalbzowski.windband.application.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReportGeneratorServiceTest {

    @Test
    @DisplayName("placeholder for local Jasper-only report generation")
    void placeholder() {
        Map<String, Object> params = new HashMap<>();
        params.put("bandName", "Test Band");
        assertThat(params).containsEntry("bandName", "Test Band");
    }
}
