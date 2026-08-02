package pl.michalbzowski.windband.application.report;

import net.sf.jasperreports.engine.JasperReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Weryfikuje, że raport "sprawozdanie-miesieczne" jest poprawnie skompilowany,
 * a jego metadane (parametry widoczne/ukryte) są prawidłowo sparsowane.
 *
 * <p>UWAGA: sam raport używa Postgres-owego SQL ($P{REPORT_CONNECTION}, INTERVAL,
 * ::TIMESTAMP), którego H2 (profil test) nie wykona — dlatego end-to-end
 * wypełnianie tego konkretnego raportu jest testowane na produkcji (Postgres),
 * a tutaj weryfikujemy kompilację + metadane + kontrakt parametrów.
 */
@SpringBootTest
@ActiveProfiles("test")
class SprawozdanieMiesieczneReportTest {

    @Autowired
    private ReportCompiler reportCompiler;

    @Test
    void shouldCompileSprawozdanieMiesieczneIntoCache() {
        JasperReport compiled = reportCompiler.getCompiledReport("sprawozdanie-miesieczne");
        assertThat(compiled).as("Raport sprawozdanie-miesieczne musi być skompilowany do cache").isNotNull();
        assertThat(compiled.getName()).contains("Sprawozdanie");
    }

    @Test
    void shouldExposeMetadataWithDisplayNameAndDescription() {
        ReportMetadata metadata = reportCompiler.getReportMetadata("sprawozdanie-miesieczne");
        assertThat(metadata).isNotNull();
        assertThat(metadata.getKey()).isEqualTo("sprawozdanie-miesieczne");
        assertThat(metadata.getDisplayName()).contains("Sprawozdanie");
        assertThat(metadata.getDescription()).contains("rady miasta");
    }

    @Test
    void bandIdAndBandNameParametersMustBeHiddenFromUi() {
        ReportMetadata metadata = reportCompiler.getReportMetadata("sprawozdanie-miesieczne");
        assertThat(metadata).isNotNull();

        List<ReportParameter> params = metadata.getParameters();

        ReportParameter bandId = findParam(params, "band_id");
        assertThat(bandId).as("band_id musi istnieć").isNotNull();
        assertThat(bandId.isForPrompting()).as("band_id ma być NIEWIDOCZNE (forPrompting=false)").isFalse();

        ReportParameter bandName = findParam(params, "band_name");
        assertThat(bandName).as("band_name musi istnieć").isNotNull();
        assertThat(bandName.isForPrompting()).as("band_name ma być NIEWIDOCZNE (forPrompting=false)").isFalse();
    }

    @Test
    void promptedParametersMustBeVisibleInUi() {
        ReportMetadata metadata = reportCompiler.getReportMetadata("sprawozdanie-miesieczne");
        assertThat(metadata).isNotNull();

        List<ReportParameter> params = metadata.getParameters();

        ReportParameter dateFrom = findParam(params, "date_from");
        assertThat(dateFrom).isNotNull();
        assertThat(dateFrom.isForPrompting()).as("date_from ma być WIDOCZNE").isTrue();
        assertThat(dateFrom.getInputType()).isEqualTo("date");

        ReportParameter dateTo = findParam(params, "date_to");
        assertThat(dateTo).isNotNull();
        assertThat(dateTo.isForPrompting()).isTrue();
        assertThat(dateTo.getInputType()).isEqualTo("date");

        ReportParameter instructor = findParam(params, "instructor_name");
        assertThat(instructor).isNotNull();
        assertThat(instructor.isForPrompting()).as("instructor_name ma być WIDOCZNE").isTrue();
        assertThat(instructor.getInputType()).isEqualTo("text");
    }

    private ReportParameter findParam(List<ReportParameter> params, String name) {
        return params.stream().filter(p -> name.equals(p.getName())).findFirst().orElse(null);
    }
}
