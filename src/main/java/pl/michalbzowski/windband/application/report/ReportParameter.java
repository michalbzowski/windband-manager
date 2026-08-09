package pl.michalbzowski.windband.application.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Parametr raportu Jasper pobrany z pliku .jrxml.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportParameter {

    /** Nazwa parametru */
    private String name;

    /** Typ Java (np. "java.lang.String", "java.util.Date") */
    @Builder.Default
    private String className = "java.lang.String";

    /** Czy parametr ma być wyświetlany na formularzu użytkownika
     * forPrompting="false" oznacza hidden input z wartością domyślną */
    @Builder.Default
    private boolean forPrompting = true;

    /** Typ wyświetlany w UI (np. "date", "text", "number", "boolean", "datetime-local") */
    public String getInputType() {
        if ("java.util.Date".equals(className) || "java.sql.Date".equals(className)) {
            return "date";
        } else if ("java.sql.Timestamp".equals(className)) {
            return "datetime-local";
        } else if ("java.lang.Integer".equals(className)
                || "java.lang.Long".equals(className)
                || "java.lang.Double".equals(className)
                || "java.math.BigDecimal".equals(className)) {
            return "number";
        } else if ("java.lang.Boolean".equals(className)) {
            return "checkbox";
        } else {
            return "text";
        }
    }
}
