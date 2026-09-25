package pl.michalbzowski.windband.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import pl.michalbzowski.windband.application.report.ReportGeneratorService;

/**
 * Test fixes related to issue #196: cross-tenant data leak due to hardcoded bandId=1L.
 * Now band_id is pulled from Keycloak/OIDC security context instead of being hardcoded.
 */
class ReportControllerTest {

    @Test
    @DisplayName("should generate PDF using local Jasper service with band from security context")
    void generateReport_shouldReturnPdfResponseWithBandFromSecurityContext() {
        ReportGeneratorService service = mock(ReportGeneratorService.class);
        when(service.generatePdf(org.mockito.ArgumentMatchers.eq("sprawozdanie-miesieczne"),
                org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn("%PDF-1.4 fake content".getBytes());

        ReportController controller = new ReportController(service);

        ReportController.ReportGenerationRequest request = new ReportController.ReportGenerationRequest();
        request.setBandName("Orkiestra Dęta Test");
        request.setInstructorName("Jan Kowalski");
        request.setPeriodYear(2026);
        request.setPeriodMonth(7);

        // Create a mock WindbandOidcUser with an active team ID
        // In real app, this comes from Spring Security @AuthenticationPrincipal
        pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser oidcUser =
                org.mockito.Mockito.mock(pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser.class);
        when(oidcUser.getActiveTeamId()).thenReturn(3L);

        ResponseEntity<byte[]> result = controller.generateReport(request, oidcUser);

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

        ReportController.ReportGenerationRequest request = new ReportController.ReportGenerationRequest();
        // NO periodYear/periodMonth here: this test is the DEFAULT-range case ("when no period
        // specified" — see DisplayName). Setting an explicit period would force the controller's
        // explicit-period branch (2026-07-01) and break the last-month assertion below. The
        // explicit-period path is covered by the sibling test above.

        pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser oidcUser =
                org.mockito.Mockito.mock(pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser.class);
        when(oidcUser.getActiveTeamId()).thenReturn(2L);

        ResponseEntity<byte[]> result = controller.generateReport(request, oidcUser);

        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(capturedKey[0]).isEqualTo("sprawozdanie-miesieczne");
        // band_id should be 2L (from security context), not hardcoded 1L
        assertThat(capturedParams[0]).containsEntry("band_id", 2L);
        assertThat(capturedParams[0]).containsKeys("date_from", "date_to", "band_name", "instructor_name");
        LocalDate expectedFrom = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        java.util.Date fromDateExpected = new java.sql.Date(expectedFrom.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        assertThat(capturedParams[0].get("date_from")).isEqualTo(fromDateExpected);
    }
}