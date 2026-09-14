package pl.michalbzowski.windband.domain.composition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.band.Band;

import java.time.Instant;

/**
 * A musical composition (score) catalogued in the band's score library.
 *
 * <p>Business rules:
 * <ul>
 *   <li>Title is required and unique within a band.</li>
 *   <li>Status transitions: DRAFT -> READY -> ARCHIVED (READY requires a verified
 *       part map first — see {@code PartMapping} gate).</li>
 *   <li>Strict band isolation: a composition always belongs to exactly one band,
 *       repositories never return compositions of other bands.</li>
 * </ul>
 */
@Entity
@Table(name = "compositions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"band_id", "title"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Composition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(length = 150)
    private String composer;

    @Column(length = 150)
    private String arranger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompositionStatus status = CompositionStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "band_id", nullable = false)
    private Band band;

    @Column(nullable = false, updatable = false)
    protected Instant createdAt;

    @Column(nullable = false)
    protected Instant updatedAt;

    /**
     * Package-private constructor. Deliberately performs no validation: SpotBugs
     * {@code CT_CONSTRUCTOR_THROW} forbids escaping from a constructor before the
     * object is fully initialised (Finalizer-attack surface). All invariants are
     * enforced by the static factories and instance mutators below.
     */
    protected Composition(String title, Band band) {
        this.title = title;
        this.band = band;
    }

    /**
     * Factory for a new draft composition. Validates the title before publishing the
     * instance — throwing from here is safe because the object has not yet escaped.
     */
    public static Composition create(String title, String description,
                                     String composer, String arranger, Band band) {
        requireTitle(title);
        if (band == null) {
            throw new NullPointerException("band required");
        }
        Composition composition = new Composition(title, band);
        composition.description = description;
        composition.composer = composer;
        composition.arranger = arranger;
        return composition;
    }

    /**
     * Partial update of the textual metadata. A {@code null} argument simply means
     * "leave unchanged" so that UI form partial-submissions preserve existing values.
     */
    public void updateTexts(String title, String description, String composer, String arranger) {
        if (title != null) {
            this.title = requireTitle(title);
        }
        if (description != null) {
            this.description = description;
        }
        if (composer != null) {
            this.composer = composer;
        }
        if (arranger != null) {
            this.arranger = arranger;
        }
    }

    public void archive() {
        this.status = CompositionStatus.ARCHIVED;
    }

    public void restore() {
        this.status = CompositionStatus.READY;
    }

    /** Only legal once the part map is verified (US-3.03 gate lives in the command service). */
    public void markReady() {
        this.status = CompositionStatus.READY;
    }

    public boolean isArchived() {
        return this.status == CompositionStatus.ARCHIVED;
    }

    private static String requireTitle(String title) {
        if (title == null) {
            throw new NullPointerException("title required");
        }
        if (title.trim().isEmpty()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (title.length() > 200) {
            throw new IllegalArgumentException("title must be at most 200 characters");
        }
        return title;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
