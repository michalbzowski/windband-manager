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
import pl.michalbzowski.windband.domain.band.Band;

import java.time.Instant;
import java.util.Objects;

/**
 * Maps a band's member instrument tag (e.g. "Trąbka") to one or more composition
 * instrument roles ("Trąbka 1", "Trąbka 2", "Kornet 1") — US-1.4.
 *
 * <p>Business purpose: when a librarian distributes parts of a {@link Composition}, the
 * membership of "who plays this role" is resolved by comparing each role's free-form label
 * ({@code composition_instruments.instrument_role}) to the source tags stored here. A tag
 * written by one band manager ("TRĄBKA") should match another's ("trąbka"), and a single tag
 * may legally map to many roles — but never twice to the same role on the same band (the V39
 * unique index is the final net; see below).
 *
 * <p>Invariants (enforced by {@link #forBand} + the SQL schema in V39):
 * <ul>
 *   <li>{@code band_id} is required — this is a per-band config; no cross-band or "global" rows
 *       exist (unlike the shared instrument vocabulary, a tag→role mapping is always local).</li>
 *   <li>{@code sourceTag} and {@code targetRolePattern} are both required, trimmed, non-blank.
 *       The tag is matched case-insensitively — the unique index uses {@code lower(source_tag)}
 *       so "Trąbka" / "trąbka" / "TRĄBKA" are one logical tag.</li>
 *   <li>One row per (band, lower(sourceTag), targetRolePattern) — "Trąbka → Trąbka 1" and
 *       "Trąbka → Trąbka 2" are legal; a second "Trąbka → Trąbka 1" is rejected by the call site
 *       (the command service's pre-save guard) AND by the V39 unique index as defence in depth.</li>
 *   <li>{@code description} is optional free-form metadata for admins.</li>
 * </ul>
 */
@Entity
@Table(name = "instrument_role_map")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA: protected no-arg only; creation goes through the factory
public class InstrumentRoleMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "band_id", nullable = false)
    private Band band;

    /** Member's instrument tag (e.g. "Trąbka", "Flet", "Waltornia"). Matched case-insensitively. */
    @Column(name = "source_tag", nullable = false, length = 60)
    private String sourceTag;

    /** Composition instrument role label this tag maps to (e.g. "Trąbka 1"). */
    @Column(name = "target_role_pattern", nullable = false, length = 100)
    private String targetRolePattern;

    /** Optional admin-visible description of the mapping's intent. */
    @Column(length = 255)
    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected InstrumentRoleMap(Band band, String sourceTag, String targetRolePattern, String description) {
        this.band = band;
        this.sourceTag = sourceTag;
        this.targetRolePattern = targetRolePattern;
        this.description = description; // factory normalises (null or non-blank) before calling this
    }

    /**
     * Creates a new tag→role mapping for {@code band}. Validates every invariant BEFORE the
     * instance escapes: by refusing an invalid row here, we keep a bad row from ever reaching
     * JPA and out to the DB. Uniqueness (no duplicate tag→role on the same band) is deliberately
     * NOT checked here — it is the call site's (command service / repository) concern so this
     * entity stays free of repository dependencies and unit-testable without mocks.
     */
    public static InstrumentRoleMap forBand(Band band, String sourceTag, String targetRolePattern,
                                            String description) {
        Objects.requireNonNull(band, "band required");
        if (band.getId() == null) {
            throw new IllegalArgumentException("band.id required — pass a persisted Band instance");
        }
        String tag  = requireNotBlank(sourceTag, "sourceTag").trim();
        String role = requireNotBlank(targetRolePattern, "targetRolePattern").trim();
        String desc = requireNullableNotBlank(description);
        return new InstrumentRoleMap(band, tag, role, desc);
    }

    private static String requireNotBlank(String s, String field) {
        if (s == null || s.isBlank()) throw new NullPointerException(field + " required");
        return s;
    }

    private static String requireNullableNotBlank(String s) {
        if (s == null) return null;
        if (s.isBlank()) throw new IllegalArgumentException("description must be null or non-blank when set");
        return s.trim();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
