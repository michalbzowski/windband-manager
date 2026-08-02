package pl.michalbzowski.windband.application.report.exception;

/**
 * Rzucany, gdy generowanie raportu Jasper nie powiedzie się
 * (brak skompilowanego szablonu, błąd wypełniania danych, błąd eksportu PDF).
 */
public class ReportGenerationException extends RuntimeException {

    public ReportGenerationException(String message) {
        super(message);
    }

    public ReportGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
