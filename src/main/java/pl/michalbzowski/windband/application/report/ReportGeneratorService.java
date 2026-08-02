package pl.michalbzowski.windband.application.report;

import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.pdf.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import org.springframework.stereotype.Service;
import pl.michalbzowski.windband.application.report.exception.ReportGenerationException;

/**
 * Generuje raporty Jasper (PDF) na podstawie skompilowanych szablonów.
 *
 * <p>Raporty korzystają z bezpośredniego połączenia JDBC ($P{REPORT_CONNECTION}
 * w .jrxml), dlatego wypełnianie odbywa się metodą
 * {@link JasperFillManager#fillReport(JasperReport, Map, Connection)} z
 * połączeniem pobranym z {@link DataSource} aplikacji (na produkcji: Postgres).
 */
@Service
@RequiredArgsConstructor
public class ReportGeneratorService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ReportGeneratorService.class);

    private static final String DATE_PATTERN = "yyyy-MM-dd";

    private final ReportCompiler reportCompiler;
    private final DataSource dataSource;

    /**
     * Generuje PDF dla zadanego raportu.
     *
     * @param reportKey klucz raportu (nazwa pliku .jrxml bez rozszerzenia)
     * @param rawParameters surowe parametry z formularza (wartości jako String)
     * @return zawartość wygenerowanego PDF
     * @throws ReportGenerationException gdy raport nie istnieje lub generowanie się nie powiedzie
     */
    public byte[] generatePdf(String reportKey, Map<String, Object> rawParameters) {
        JasperReport report = reportCompiler.getCompiledReport(reportKey);
        if (report == null) {
            log.warn("Report {} not compiled/available", reportKey);
            throw new ReportGenerationException("Raport niedostępny: " + reportKey);
        }

        Map<String, Object> parameters = convertParameters(report, rawParameters);

        try (Connection connection = dataSource.getConnection()) {
            JasperPrint print = JasperFillManager.fillReport(report, parameters, connection);
            return exportToPdf(print);
        } catch (Exception e) {
            log.error("Error generating report {}", reportKey, e);
            throw new ReportGenerationException("Błąd generowania raportu: " + reportKey, e);
        }
    }

    /**
     * Konwertuje surowe wartości parametrów (String z formularza) na typy Java
     * oczekiwane przez raport, na podstawie deklaracji parametrów w metadanych.
     */
    private Map<String, Object> convertParameters(JasperReport report, Map<String, Object> rawParameters) {
        Map<String, Object> converted = new HashMap<>();
        if (rawParameters == null) {
            return converted;
        }

        Map<String, String> paramTypes = new HashMap<>();
        if (report.getParameters() != null) {
            for (var p : report.getParameters()) {
                if (!p.isSystemDefined() && p.getValueClassName() != null) {
                    paramTypes.put(p.getName(), p.getValueClassName());
                }
            }
        }

        for (Map.Entry<String, Object> entry : rawParameters.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            String type = paramTypes.get(name);
            converted.put(name, coerce(value, type));
        }
        return converted;
    }

    /** Rzutuje pojedynczą wartość parametru na oczekiwany typ Java. */
    private Object coerce(Object value, String type) {
        if (value == null || type == null) {
            return value;
        }
        String str = value.toString().trim();
        if (str.isEmpty()) {
            return null;
        }
        try {
            return switch (type) {
                case "java.lang.Integer" -> Integer.valueOf(str);
                case "java.lang.Long" -> Long.valueOf(str);
                case "java.util.Date", "java.sql.Date", "java.sql.Timestamp" ->
                        new SimpleDateFormat(DATE_PATTERN).parse(str);
                default -> str;
            };
        } catch (NumberFormatException | ParseException e) {
            log.warn("Cannot coerce parameter value '{}' to {} — passing raw String", str, type);
            return str;
        }
    }

    private byte[] exportToPdf(JasperPrint print) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JRPdfExporter exporter = new JRPdfExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(baos));
        exporter.exportReport();
        return baos.toByteArray();
    }
}
