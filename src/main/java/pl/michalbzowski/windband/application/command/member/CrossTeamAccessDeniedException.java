package pl.michalbzowski.windband.application.command.member;

/**
 * Wyrzucany, gdy uwierzytelniony użytkownik próbuje odczytać lub modyfikować zasób
 * (np. członka zespołu), który należy do innego bandu niż jego aktywny zespół.
 *
 * <p>Pentest P0 (A01/BOLA): per-id endpointy /api/members/{id} akceptowały surowy
 * identyfikator bez weryfikacji przynależności — członkowie dowolnego bandu mogli
 * czytać/edytować/dezaktywować obcych członków. Teraz warstwa application odrzuca
 * takie żądania, a {@code GlobalExceptionHandler} mapuje wyjątek na HTTP 403
 * (jednolita odpowiedź: nie potwierdzamy istnienia zasobu w bandzie po drugiej stronie).
 */
public class CrossTeamAccessDeniedException extends SecurityException {

    private final Long memberId;
    private final Long activeTeamId;

    public CrossTeamAccessDeniedException(Long memberId, Long activeTeamId) {
        super("Member " + memberId + " is not accessible from team " + activeTeamId);
        this.memberId = memberId;
        this.activeTeamId = activeTeamId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getActiveTeamId() {
        return activeTeamId;
    }
}
