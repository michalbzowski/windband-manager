package pl.michalbzowski.windband.adapter.in.security;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.application.common.CrossTeamAccessDeniedException;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;

/**
 * Jedno-source resolver aktywnego zespołu żądania (sesja &gt; identyfikator OIDC).
 *
 * <p>Pentest P0 (A01/BOLA): endpointy per-id i raporty akceptowały wartości
 * wybierane po stronie klienta, co pozwalało operować na zasobach obcych bandów.
 * Od tej pory warstwa web pobiera identyfikator zespołu wyłącznie stąd —
 * z sesji (z rewalidacją przynależności) albo z tożsamości windband — nigdy
 * z parametrów żądania.
 */
@Component
@RequiredArgsConstructor
public class ActiveTeamResolver {

    private static final String SESSION_ACTIVE_TEAM = "activeTeamId";

    private final TeamQueryService teamQueryService;

    /**
     * Zwraca aktywny zespół dla podanego żądania albo {@code null}, gdy nie da się
     * ustalić wiarygodnego kontekstu (inna tożsamość niż windband / brak zespołu).
     */
    public Long resolve(OidcUser oidcUser, HttpSession session) {
        if (!(oidcUser instanceof WindbandOidcUser wu)) {
            return null;
        }
        Object sessionTeamId = session.getAttribute(SESSION_ACTIVE_TEAM);
        if (sessionTeamId instanceof Long candidate
                && teamQueryService.getUserTeam(wu.getUserId(), candidate).isPresent()) {
            return candidate;
        }
        return wu.getActiveTeamId();
    }

    /** Wariant blokujący: brak aktywnego zespołu → 403 ({@link AccessDeniedException}). */
    public long require(OidcUser oidcUser, HttpSession session) {
        Long teamId = resolve(oidcUser, session);
        if (teamId == null) {
            throw new AccessDeniedException("Brak aktywnego zespołu w bieżącej sesji");
        }
        return teamId;
    }

    /**
     * Pentest P1 (A01/BOLA): zasób adresowany w ścieżce {@code /bands/{bandId}/...} musi
     * należeć do aktywnego zespołu żądającego. Brak zespołu → 403
     * ({@link AccessDeniedException}); rozbieżność bandu i zespołu → 403
     * ({@link CrossTeamAccessDeniedException}, jednolita odpowiedź bez potwierdzania istnienia).
     */
    public long requireBandAccess(OidcUser oidcUser, HttpSession session, Long requestedBandId) {
        long active = require(oidcUser, session);
        if (requestedBandId == null || !requestedBandId.equals(active)) {
            throw new CrossTeamAccessDeniedException(requestedBandId, active);
        }
        return active;
    }
}
