package pl.michalbzowski.windband.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;
import pl.michalbzowski.windband.application.report.ReportCompiler;
import pl.michalbzowski.windband.application.report.ReportGeneratorService;
import pl.michalbzowski.windband.application.report.ReportMetadata;

class ReportRestControllerTest {

    @Test
    @DisplayName("should return list of reports excluding internal ones")
    void listReports_shouldExcludeInternalReports() {
        // given
        ReportCompiler reportCompiler = mock(ReportCompiler.class);
        ReportGeneratorService reportGeneratorService = mock(ReportGeneratorService.class);
        TeamQueryService teamQueryService = mock(TeamQueryService.class);

        ReportMetadata publicReport = ReportMetadata.builder()
            .key("sprawozdanie-miesieczne")
            .displayName("Sprawozdanie")
            .description("Opis")
            .parameters(List.of())
            .build();

        ReportMetadata internalReport = ReportMetadata.builder()
            .key("members")
            .displayName("Members")
            .description("Internal")
            .parameters(List.of())
            .build();

        when(reportCompiler.getMetadataCache()).thenReturn(Map.of(
            "sprawozdanie-miesieczne", publicReport,
            "members", internalReport
        ));

        ReportRestController controller = new ReportRestController(
            reportCompiler, reportGeneratorService, teamQueryService
        );

        // when
        var reports = controller.listReports();

        // then
        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).getKey()).isEqualTo("sprawozdanie-miesieczne");
    }

    @Test
    @DisplayName("should return 404 for non-existent report")
    void getReport_shouldReturnEmptyForNonExistent() {
        // given
        ReportCompiler reportCompiler = mock(ReportCompiler.class);
        when(reportCompiler.getReportMetadata("non-existent")).thenReturn(null);

        ReportRestController controller = new ReportRestController(
            reportCompiler, mock(ReportGeneratorService.class), mock(TeamQueryService.class)
        );

        // when
        var result = controller.getReport("non-existent");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return 403 when user has no active team")
    void generateReport_shouldReturn403WhenNoActiveTeam() throws Exception {
        // given
        ReportCompiler reportCompiler = mock(ReportCompiler.class);
        ReportGeneratorService reportGeneratorService = mock(ReportGeneratorService.class);
        TeamQueryService teamQueryService = mock(TeamQueryService.class);

        ReportMetadata report = ReportMetadata.builder()
            .key("test-report")
            .displayName("Test Report")
            .description("Test")
            .parameters(List.of())
            .build();

        when(reportCompiler.getReportMetadata("test-report")).thenReturn(report);

        ReportRestController controller = new ReportRestController(
            reportCompiler, reportGeneratorService, teamQueryService
        );

        WindbandOidcUser oidcUser = createTestOidcUser(null, null);

        // when
        ResponseEntity<byte[]> response = controller.generateReport("test-report", Map.of(), oidcUser);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(reportGeneratorService, never()).generatePdf(anyString(), anyMap());
    }

    @Test
    @DisplayName("should inject band_id and band_name from user context")
    void generateReport_shouldInjectTeamContext() throws Exception {
        // given
        ReportCompiler reportCompiler = mock(ReportCompiler.class);
        ReportGeneratorService reportGeneratorService = mock(ReportGeneratorService.class);
        TeamQueryService teamQueryService = mock(TeamQueryService.class);

        ReportMetadata report = ReportMetadata.builder()
            .key("test-report")
            .displayName("Test Report")
            .description("Test")
            .parameters(List.of())
            .build();

        when(reportCompiler.getReportMetadata("test-report")).thenReturn(report);
        when(teamQueryService.getBandName(42L)).thenReturn(java.util.Optional.of("My Band"));
        when(reportGeneratorService.generatePdf(eq("test-report"), anyMap()))
            .thenReturn("%PDF-1.4 test".getBytes());

        ReportRestController controller = new ReportRestController(
            reportCompiler, reportGeneratorService, teamQueryService
        );

        WindbandOidcUser oidcUser = createTestOidcUser(42L, "My Band");

        // when
        ResponseEntity<byte[]> response = controller.generateReport("test-report", Map.of("format", "PDF"), oidcUser);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).contains("application/pdf");
        assertThat(response.getHeaders().getContentDisposition().toString()).contains("test-report.pdf");

        // Verify team context was injected
        verify(reportGeneratorService).generatePdf(
            eq("test-report"),
            argThat(params -> {
                if (!(params instanceof Map)) return false;
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) params;
                return map.get("band_id").equals(42L) && map.get("band_name").equals("My Band");
            })
        );
    }

    @Test
    @DisplayName("should return 404 for non-existent report in generate")
    void generateReport_shouldReturn404ForNonExistent() throws Exception {
        // given
        ReportCompiler reportCompiler = mock(ReportCompiler.class);
        when(reportCompiler.getReportMetadata("non-existent")).thenReturn(null);

        ReportRestController controller = new ReportRestController(
            reportCompiler, mock(ReportGeneratorService.class), mock(TeamQueryService.class)
        );

        WindbandOidcUser oidcUser = createTestOidcUser(1L, "Test Band");

        // when
        ResponseEntity<byte[]> response = controller.generateReport("non-existent", Map.of(), oidcUser);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("should return 400 for unsupported format")
    void generateReport_shouldReturn400ForUnsupportedFormat() throws Exception {
        // given
        ReportCompiler reportCompiler = mock(ReportCompiler.class);
        ReportGeneratorService reportGeneratorService = mock(ReportGeneratorService.class);
        TeamQueryService teamQueryService = mock(TeamQueryService.class);

        ReportMetadata report = ReportMetadata.builder()
            .key("test-report")
            .displayName("Test Report")
            .description("Test")
            .parameters(List.of())
            .build();

        when(reportCompiler.getReportMetadata("test-report")).thenReturn(report);

        ReportRestController controller = new ReportRestController(
            reportCompiler, reportGeneratorService, teamQueryService
        );

        WindbandOidcUser oidcUser = createTestOidcUser(1L, "Test Band");

        // when
        ResponseEntity<byte[]> response = controller.generateReport("test-report", Map.of("format", "EXCEL"), oidcUser);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(reportGeneratorService, never()).generatePdf(anyString(), anyMap());
    }

    @Test
    @DisplayName("should handle ReportGenerationException")
    void generateReport_shouldHandleGenerationException() throws Exception {
        // given
        ReportCompiler reportCompiler = mock(ReportCompiler.class);
        ReportGeneratorService reportGeneratorService = mock(ReportGeneratorService.class);
        TeamQueryService teamQueryService = mock(TeamQueryService.class);

        ReportMetadata report = ReportMetadata.builder()
            .key("test-report")
            .displayName("Test Report")
            .description("Test")
            .parameters(List.of())
            .build();

        when(reportCompiler.getReportMetadata("test-report")).thenReturn(report);
        when(teamQueryService.getBandName(1L)).thenReturn(java.util.Optional.of("Test Band"));
        // Use any() for the second parameter to match any Map (including those with band_id/band_name)
        when(reportGeneratorService.generatePdf(eq("test-report"), any()))
            .thenThrow(new pl.michalbzowski.windband.application.report.exception.ReportGenerationException("Błąd bazy danych"));

        ReportRestController controller = new ReportRestController(
            reportCompiler, reportGeneratorService, teamQueryService
        );

        WindbandOidcUser oidcUser = createTestOidcUser(1L, "Test Band");

        // when
        ResponseEntity<byte[]> response = controller.generateReport("test-report", Map.of(), oidcUser);

        // then - exception should be caught and handled (returns 500)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private WindbandOidcUser createTestOidcUser(Long teamId, String teamName) {
        return new WindbandOidcUser(
            mock(org.springframework.security.oauth2.core.oidc.user.OidcUser.class),
            1L, "testuser", "test@example.com",
            true, false, teamId, teamName != null ? teamName.toLowerCase() : null,
            "MEMBER", teamId != null ? List.of(teamId) : List.of()
        );
    }
}