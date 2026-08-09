package pl.michalbzowski.windband.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import pl.michalbzowski.windband.application.report.ReportGeneratorService;

class ReportControllerTest {

    @Test
    @DisplayName("should generate PDF using local Jasper service")
    void generateReport_shouldReturnPdfResponse() {
        ReportGeneratorService service = mock(ReportGeneratorService.class);
        when(service.generatePdf(org.mockito.ArgumentMatchers.eq("sprawozdanie-miesieczne"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn("%PDF-1.4 fake content".getBytes());
        ReportController controller = new ReportController(service);

        ReportController.ReportGenerationRequest request = new ReportController.ReportGenerationRequest();
        request.setBandName("Orkiestra Dęta Test");
        request.setInstructorName("Jan Kowalski");
        request.setPeriodYear(2026);
        request.setPeriodMonth(7);

        ResponseEntity<byte[]> result = controller.generateReport(request);

        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getHeaders().getContentType()).isNotNull();
        assertThat(result.getHeaders().getContentType().toString()).contains("application/pdf");
        assertThat(result.getBody()).isEqualTo("%PDF-1.4 fake content".getBytes());
    }

    @Test
    @DisplayName("should default to last month when no period specified")
    void generateReport_shouldDefaultDateRange() {
        final String[] capturedKey = new String[1];
        final Map[] capturedParams = new Map[1];
        ReportGeneratorService service = mock(ReportGeneratorService.class);
        when(service.generatePdf(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap()))
                .thenAnswer(invocation -> {
                    capturedKey[0] = invocation.getArgument(0);
                    capturedParams[0] = invocation.getArgument(1);
                    return "test".getBytes();
                });
        ReportController controller = new ReportController(service);

        ResponseEntity<byte[]> result = controller.generateReport(new ReportController.ReportGenerationRequest());

        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(capturedKey[0]).isEqualTo("sprawozdanie-miesieczne");
        assertThat(capturedParams[0]).containsKeys("paramFrom", "paramTo", "bandName", "instructorName");
        LocalDate expectedFrom = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        assertThat(capturedParams[0].get("paramFrom")).isEqualTo(expectedFrom.toString());
    }
}
