package pl.michalbzowski.windband.application.common;

/**
 * Wyrzucany, gdy uwierzytelniony użytkownik próbuje odczytać lub modyfikować zasób
 * (członka zespołu, plik nutowy), który należy do bandu innego niż jego aktywny zespół.
 *
 * <p>Pentest (A01/BOLA): endpointy akceptowały surowe identyfikatory zasobów bez
 * weryfikacji przynależności żądającego do owning-bandu — członek jednego bandu mógł
 * czytać/edytować/dezaktywować/usuwać zasoby obcych bandów. Warstwa application/web
 * odrzuca takie żądania, a {@code GlobalExceptionHandler} mapuje wyjątek na HTTP 403
 * (jednolita odpowiedź: nie potwierdzamy istnienia zasobu po drugiej stronie).
 */
public class CrossTeamAccessDeniedException extends SecurityException {

    private final Long resourceId;
    private final Long activeTeamId;

    public CrossTeamAccessDeniedException(Long resourceId, Long activeTeamId) {
        super("Resource " + resourceId + " is not accessible from team " + activeTeamId);
        this.resourceId = resourceId;
        this.activeTeamId = activeTeamId;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public Long getActiveTeamId() {
        return activeTeamId;
    }
}
