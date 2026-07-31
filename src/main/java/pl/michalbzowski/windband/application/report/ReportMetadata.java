package pl.michalbzowski.windband.application.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Metadane raportu Jasper pobrane z pliku .jrxml.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportMetadata {

    /** Klucz raportu (bez rozszerzenia) np. "sprawozdanie-miesieczne" */
    private String key;

    /** Nazwa wyświetlana użytkownikowi z <jasperReport name="..."> */
    private String displayName;

    /** Opis raportu z property com.jaspersoft.studio.report.description */
    private String description;

    /** Lista parametrów raportu */
    private List<ReportParameter> parameters;
}
