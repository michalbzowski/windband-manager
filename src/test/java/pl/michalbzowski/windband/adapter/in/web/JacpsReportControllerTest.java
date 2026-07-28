package pl.michalbzowski.windband.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for JacpsReportController report generation via jacps-report-adapter.
 */
class JacpsReportControllerTest {

    private pl.michalbzowski.windband.infrastructure.jacps.ReportApiClient reportApiClient;
    private JacpsReportController controller;

    @BeforeEach
    void setUp() {
        // Create mock for ReportApiClient
        reportApiClient = mock(pl.michalbzowski.windband.infrastructure.jacps.ReportApiClient.class);
        
        // Instantiate and inject dependency via setter
        controller = new JacpsReportController();
        controller.setReportApiClient(reportApiClient);
    }

    @Test
    @DisplayName("should generate PDF with valid band name, instructor and date range")
    void generatePDF_reportWithFullParameters_shouldReturnOkResponse() {
        // Given: complete report request
        JacpsReportController.ReportGenerationRequest request = new JacpsReportController.ReportGenerationRequest();
        request.setBandName("Orkiestra Dęta Test");
        request.setInstructorName("Jan Kowalski");
        request.setPeriodYear(2026);
        request.setPeriodMonth(7);

        // Mock ReportApiClient to return valid PDF bytes
        byte[] mockPdfBytes = "%PDF-1.4 fake content".getBytes();
        when(reportApiClient.generateReport(anyString(), anyString(), any(Map.class)))
            .thenReturn(mockPdfBytes);

        // When: call generate report method
        ResponseEntity<byte[]> result = controller.generateReport(request);

        // Then: assert response is successful with PDF headers
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getBody()).isEqualTo(mockPdfBytes);
        assertThat(result.getHeaders().getContentType())
            .satisfies(ct -> ct.toString().contains("application/pdf"));
        
        verify(reportApiClient, atLeastOnce()).generateReport(
            anyString(), 
            eq("PDF"), 
            any(Map.class)
        );
    }

    @Test
    @DisplayName("should calculate correct date range from year/month parameters")
    void generatePDF_withYearMonthParameters_shouldCalculateDateRange() {
        // Given: request with specific period (July 2026)
        JacpsReportController.ReportGenerationRequest request = new JacpsReportController.ReportGenerationRequest();
        request.setPeriodYear(2026);
        request.setPeriodMonth(7);

        byte[] mockBytes = "test".getBytes();
        when(reportApiClient.generateReport(anyString(), anyString(), any(Map.class)))
            .then(invocation -> {
                Map<String, String> params = invocation.getArgument(2);
                
                // Assert date range parameters are correctly calculated (July 1 to July 28)
                assertThat(params).containsKey("from");
                assertThat(params).containsKey("to");
                assertThat(params.get("from")).isEqualTo("2026-07-01");
                assertThat(params.get("to")).isEqualTo("2026-07-28");
                
                return mockBytes;
            });

        controller.generateReport(request);

        verify(reportApiClient, atLeastOnce()).generateReport(anyString(), anyString(), any(Map.class));
    }

    @Test
    @DisplayName("should use last month as default when no period specified")
    void generatePDF_withEmptyParameters_shouldUseDefaultDateRange() {
        // Given: empty request (uses defaults) - but band name is required for report to make sense
        JacpsReportController.ReportGenerationRequest request = new JacpsReportController.ReportGenerationRequest();

        byte[] mockBytes = "test".getBytes();
        when(reportApiClient.generateReport(anyString(), anyString(), any(Map.class)))
            .then(invocation -> {
                Map<String, String> params = invocation.getArgument(2);
                
                // Default should be last month
                LocalDate expectedFrom = LocalDate.now().minusMonths(1).withDayOfMonth(1);
                assertThat(params.get("from")).isEqualTo(expectedFrom.toString());
                
                return mockBytes;
            });

        controller.generateReport(request);

        verify(reportApiClient, atLeastOnce()).generateReport(anyString(), anyString(), any(Map.class));
    }

}
