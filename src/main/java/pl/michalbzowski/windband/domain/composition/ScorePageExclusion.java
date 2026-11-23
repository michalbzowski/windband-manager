package pl.michalbzowski.windband.domain.composition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * Jedno znaczenie stron utworu „nie wykorzystam" (raport spójności AI).
 *
 * <p>Naprzeciwko {@link CompositionInstrument} — który mówi, które strony NARZĄD
 * obsadza — ten obiekt mówi, że kapelmistrz ŚWIADOMIE nie chce użyć stron bez głosu
 * (np. dodatkowe głosy utworu, z których nie będzie rozsyłany). Strony oznaczone
 * nie wracają do kolejki luk widoku /verify-ai i nie są męczone nawigacją „Następny".
 *
 * <p>Inwarianty (factory + schema V52):
 * <ul>
 *   <li>pageFrom, pageTo są oba wymagane; pageFrom&nbsp;&ge;&nbsp;1, pageFrom&nbsp;&le;&nbsp;pageTo
 *       (jednostronne znaczenie jest legalne: from == to);</li>
 *   <li>kompozycja i plik nut muszą się zgadzać — strony należą do jednego pliku;
 *       weryfikuje warstwa komendowa (repository nie ma widoku na oba obiekty
 *       bez załadowania);</li>
 *   <li>markedBy / markedAt są parą audytową ostatniego pisarza — „Przywróć" usuwa
 *       wiersz całkowicie, więc znaczenie jest odwracalne tylko pod warunkiem, że
 *       użytkownik ma do niego dostęp w sekcji widoku (tabela bez soft-delete).</li>
 * </ul>
 */
@Entity
@Table(name = "score_page_exclusions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScorePageExclusion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "composition_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Composition composition;

    /** Plik, do którego należą strony; FK RESTRICT (patrz komentarz w V52). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "score_file_id", nullable = false)
    private ScoreFile scoreFile;

    /** Pierwsza oznaczona strona (1-based, z pliku nut). */
    @Column(name = "page_from", nullable = false)
    private Integer pageFrom;

    /** Ostatnia oznaczona strona (włącznie); musi być ≥ pageFrom. */
    @Column(name = "page_to", nullable = false)
    private Integer pageTo;

    /** Audyt: id użytkownika, który ostatnio oznaczył (nowe znaczenie przepisuje parę). */
    @Column(name = "marked_by", nullable = false, length = 255)
    private String markedBy;

    /** Audyt: chwila ostatniego oznaczenia. */
    @Column(name = "marked_at", nullable = false)
    private Instant markedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private ScorePageExclusion(Composition composition,
                               ScoreFile scoreFile,
                               int pageFrom,
                               int pageTo,
                               String markedBy,
                               Instant markedAt) {
        this.composition = composition;
        this.scoreFile = scoreFile;
        this.pageFrom = pageFrom;
        this.pageTo = pageTo;
        this.markedBy = markedBy;
        this.markedAt = markedAt;
    }

    /**
     * Buduje znaczenie po walidacji wejściowych (factory = bramka inwariantów,
     * jak {@link CompositionInstrument} — SpotBugs CT_CONSTRUCTOR_THROW zakazuje
     * rzutow w konstruktorze).
     */
    public static ScorePageExclusion mark(Composition composition,
                                          ScoreFile scoreFile,
                                          int pageFrom,
                                          int pageTo,
                                          String markedBy,
                                          Instant markedAt) {
        if (composition == null || scoreFile == null) {
            throw new IllegalArgumentException("Kompozycja i plik nut są wymagane do oznaczenia stron");
        }
        if (pageFrom < 1) {
            throw new IllegalArgumentException("Pierwsza strona znaczenia nie może być mniejsza niż 1");
        }
        if (pageTo < pageFrom) {
            throw new IllegalArgumentException(
                    "Zakres stron jest odwrócony (od=" + pageFrom + ", do=" + pageTo + ").");
        }
        if (markedBy == null || markedBy.isBlank()) {
            throw new IllegalArgumentException("Oznaczenie wymaga autora (para audytowa)");
        }
        if (markedAt == null) {
            throw new IllegalArgumentException("Oznaczenie wymaga chwili oznaczenia (para audytowa)");
        }
        return new ScorePageExclusion(composition, scoreFile, pageFrom, pageTo, markedBy, markedAt);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
