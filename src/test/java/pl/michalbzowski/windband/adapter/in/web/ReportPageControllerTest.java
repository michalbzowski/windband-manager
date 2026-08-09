package pl.michalbzowski.windband.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.Model;

import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;
import pl.michalbzowski.windband.application.report.ReportCompiler;
import pl.michalbzowski.windband.application.report.ReportGeneratorService;
import pl.michalbzowski.windband.application.report.ReportMetadata;
import pl.michalbzowski.windband.application.report.ReportParameter;

class ReportPageControllerTest {

    @Test
    @DisplayName("should populate model with dynamic Jasper reports list")
    void reportsPage_shouldAddReportsToModel() {
        // given
        ReportCompiler reportCompiler = mock(ReportCompiler.class);
        ReportGeneratorService reportGeneratorService = mock(ReportGeneratorService.class);
        TeamQueryService teamQueryService = mock(TeamQueryService.class);

        ReportParameter param1 = new ReportParameter("date_from", "java.util.Date", true);
        ReportParameter param2 = new ReportParameter("instructor_name", "java.lang.String", true);

        ReportMetadata report1 = ReportMetadata.builder()
            .key("sprawozdanie-miesieczne")
            .displayName("Sprawozdanie miesięczne do rady miasta")
            .description("Raport składany co miesiąc na potrzeby rady miasta")
            .parameters(List.of(param1, param2))
            .build();

        ReportMetadata report2 = ReportMetadata.builder()
            .key("hello")
            .displayName("HelloWorld")
            .description("Test raportu")
            .parameters(List.of(new ReportParameter("message", "java.lang.String", true)))
            .build();

        ReportMetadata internalReport = ReportMetadata.builder()
            .key("members")
            .displayName("Members Report")
            .description("Internal report")
            .parameters(List.of())
            .build();

        when(reportCompiler.getMetadataCache()).thenReturn(Map.of(
            "sprawozdanie-miesieczne", report1,
            "hello", report2,
            "members", internalReport
        ));

        ReportPageController controller = new ReportPageController(
            mock(pl.michalbzowski.windband.application.query.report.ReportQueryService.class),
            teamQueryService,
            reportCompiler,
            reportGeneratorService
        );

        Model model = mock(Model.class);

        // when
        String view = controller.reportsPage(model);

        // then
        assertThat(view).isEqualTo("reports/list");
    }

    @Test
    @DisplayName("should return 404 for non-existent report key")
    void configureReport_shouldRedirectForNonExistentReport() {
        // given
        ReportCompiler reportCompiler = mock(ReportCompiler.class);
        when(reportCompiler.getReportMetadata("non-existent")).thenReturn(null);

        ReportPageController controller = new ReportPageController(
            mock(pl.michalbzowski.windband.application.query.report.ReportQueryService.class),
            mock(TeamQueryService.class),
            reportCompiler,
            mock(ReportGeneratorService.class)
        );

        Model model = mock(Model.class);

        // when
        String view = controller.configureReport("non-existent", model, null);

        // then
        assertThat(view).isEqualTo("redirect:/reports/jasper");
    }

    @Test
    @DisplayName("should add report metadata and team context to model")
    void configureReport_shouldAddReportAndTeamContextToModel() {
        // given
        ReportCompiler reportCompiler = mock(ReportCompiler.class);
        TeamQueryService teamQueryService = mock(TeamQueryService.class);

        ReportParameter param1 = new ReportParameter("date_from", "java.util.Date", true);
        ReportParameter param2 = new ReportParameter("band_id", "java.lang.Integer", false);

        ReportMetadata report = ReportMetadata.builder()
            .key("sprawozdanie-miesieczne")
            .displayName("Sprawozdanie miesięczne")
            .description("Opis raportu")
            .parameters(List.of(param1, param2))
            .build();

        when(reportCompiler.getReportMetadata("sprawozdanie-miesieczne")).thenReturn(report);
        when(teamQueryService.getBandName(1L)).thenReturn(java.util.Optional.of("Test Band"));

        ReportPageController controller = new ReportPageController(
            mock(pl.michalbzowski.windband.application.query.report.ReportQueryService.class),
            teamQueryService,
            reportCompiler,
            mock(ReportGeneratorService.class)
        );

        Model model = mock(Model.class);
        WindbandOidcUser oidcUser = createTestOidcUser(1L, "Test Band");

        // when
        String view = controller.configureReport("sprawozdanie-miesieczne", model, oidcUser);

        // then
        assertThat(view).isEqualTo("reports/configure");
    }

    @Test
    @DisplayName("should block report generation when user has no active team")
    void downloadReport_shouldReturn403WhenNoActiveTeam() {
        // given
        ReportCompiler reportCompiler = mock(ReportCompiler.class);
        ReportGeneratorService reportGeneratorService = mock(ReportGeneratorService.class);

        ReportParameter param1 = new ReportParameter("date_from", "java.util.Date", true);

        ReportMetadata report = ReportMetadata.builder()
            .key("sprawozdanie-miesieczne")
            .displayName("Sprawozdanie miesięczne")
            .description("Opis raportu")
            .parameters(List.of(param1))
            .build();

        when(reportCompiler.getReportMetadata("sprawozdanie-miesieczne")).thenReturn(report);

        ReportPageController controller = new ReportPageController(
            mock(pl.michalbzowski.windband.application.query.report.ReportQueryService.class),
            mock(TeamQueryService.class),
            reportCompiler,
            reportGeneratorService
        );

        // WindbandOidcUser with no active team
        WindbandOidcUser oidcUser = createTestOidcUser(null, null);

        // when
        var response = controller.downloadReport("sprawozdanie-miesieczne", Map.of(), oidcUser);

        // then
        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
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