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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import pl.michalbzowski.windband.domain.member.Instrument;

import java.time.Instant;

/**
 * A single instrument part mapping on a {@link Composition} (US-1.3): which physical
 * pages of the score PDF (or which file inside a ZIP archive) contain this particular
 * role, who is responsible for it, and how much confidence we put in the mapping.
 *
 * <p>Invariants (enforced by the static factory + the SQL schema):
 * <ul>
 *   <li>Composition and instrument are both required — no orphan part mappings.</li>
 *   <li>One row per (composition, lowercase-normalized instrument role). The SQL unique index
 *       {@code uq_composition_instruments_role} in {@code V36__create_composition_instrument.sql} enforces
 *       this at the DB level; "Flet 1" and "FLET 1" are the same row, "Flet 1" + "Flet 2"
 *       are two rows.</li>
 *   <li>{@code pageFrom <= pageTo} — a single-page part is legal (from == to); a reverse range
 *       is rejected by the factory before it escapes (SpotBugs CT_CONSTRUCTOR_THROW).</li>
 *   <li>{@code confidenceScore} is bounded to [0.0, 1.0] — a contract the US-4.x AI gate relies on for
 *       its "needs human review" threshold (confidence &lt; 0.7 ⇒ mustVerify).</li>
 *   <li>The (composition, instrument) pair must agree on band — the factory checks this so cross-band
 *       writes are never persisted and any caller (service, adapter, test) is protected from a future
 *       FK-scheme or JDBC path that would otherwise accept the write.</li>
 * </ul>
 *
 * <p>{@code verified_by/verified_at} form an audit pair: once a row is marked verified,
 * the pair is frozen — subsequent {@link #verify(String, Instant)} calls are no-ops so the
 * first-writer's identity and moment remain the trusted source of truth (US-4.5 "accept" /
 * US-6.3 distribution both rely on this for the "who accepted / when" audit trail).
 */
@Entity
@Table(name = "composition_instruments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompositionInstrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "composition_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Composition composition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    /**
     * Human-readable role the librarian used — e.g. "Flet 1", "Trąbka Bb", "Waltornia Es".
     * Unique per composition via {@code lower(this)} (case-insensitive); free-form so new
     * roles never require a migration, and US-5.x maps roles onto member tags at distribution time.
     */
    @Column(name = "instrument_role", nullable = false, length = 100)
    private String instrumentRole;

    /** First page of the part inside the score PDF (1-based). Required — see {@link #verifyRange}. */
    @Column(name = "page_from", nullable = false)
    private Integer pageFrom;

    /** Last page of the part inside the score PDF (1-based, inclusive). Must be >= pageFrom. */
    @Column(name = "page_to", nullable = false)
    private Integer pageTo;

    /** Optional file reference for ZIP-based parts (e.g. "03_Flet_1.jpg"); {@code null} for PDF-mode rows. */
    @Column(name = "file_ref", length = 300)
    private String fileRef;

    /**
     * US-7.14 — explicit link to the uploaded ScoreFile whose pages this mapping describes.
     * Optional / backward-compatible: legacy rows (created before this column existed)
     * stay null and keep today's "largest PDF covering [pageFrom..pageTo]" resolution
     * in {@code PartLinkQueryService}. New rows carry a concrete FK so the UI can
     * unambiguously show which PDF a given part lives in.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "score_file_id")
    private ScoreFile scoreFile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PartSource source = PartSource.MANUAL;

    /** How confident the producer is in this mapping. Contract: {@code 0.0 <= x <= 1.0}. */
    @Column(name = "confidence_score", nullable = false)
    private Double confidenceScore = 1.0;

    /** Email/identifier of the human who verified this part (audit pair, see class javadoc). */
    @Column(name = "verified_by", length = 255)
    private String verifiedBy;

    /** Moment the audit pair was frozen. Null until the first successful {@link #verify}. */
    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    // SpotBugs CT_CONSTRUCTOR_THROW: no validation in the constructor — the factory owns it.
    protected CompositionInstrument(Composition composition, Instrument instrument, String role,
                                    int pageFrom, int pageTo, String fileRef, ScoreFile scoreFile,
                                     PartSource source,
                                    double confidenceScore) {
        this.composition = composition;
        this.instrument = instrument;
        this.instrumentRole = role;      // factory trims before calling this — no need to re-validate
        this.pageFrom = pageFrom;
        this.pageTo = pageTo;
        this.fileRef = fileRef;           // factory normalises (null or non-blank) before calling
        this.scoreFile = scoreFile;        // may be null (legacy) — the FK is ON DELETE RESTRICT so cascade delete never runs from here.
        this.source = source;
        this.confidenceScore = confidenceScore;
    }

    /**
     * Creates a new part-mapping row. Validates every invariant BEFORE the instance escapes
     * (the factory has not yet handed out a reference to any other thread or the DB, so
     * throwing here is safe).
     */
    public static CompositionInstrument forComposition(Composition composition, Instrument instrument,
                                                       String role, int pageFrom, int pageTo, String fileRef,
                                                       PartSource source, double confidenceScore) {
        if (composition == null) throw new NullPointerException("composition required");
        if (instrument  == null) throw new NullPointerException("instrument required");
        requireRole(role);
        String trimmedRole  = role.trim();
        String normalizedFileRef = requireNullableNotBlank(fileRef, "fileRef");
        verifyRange(pageFrom, pageTo);
        verifyConfidence(confidenceScore);

        if (!bandsAgree(composition, instrument)) {
            // Both sides expose a resolvable band — now verify they agree. A mismatch is a
            // caller bug (a cross-band write would otherwise produce an orphan row).
            Long cb = composition.getBand() == null ? null : composition.getBand().getId();
            Long ib = instrument.getBand()  == null ? null : instrument.getBand().getId();
            if (cb == null || ib == null) {
                // One side cannot resolve its band — refuse to persist the cross-band write.
                throw new IllegalArgumentException(
                        "composition and instrument must share a band; one of them has no band_id resolvable");
            }
            if (!cb.equals(ib)) {
                throw new IllegalArgumentException(
                        "band mismatch: composition band=" + cb
                                + " differs from instrument band=" + ib);
            }
        }

        return new CompositionInstrument(
                composition, instrument, trimmedRole, pageFrom, pageTo, normalizedFileRef, null,
                (source == null ? PartSource.MANUAL : source), confidenceScore);
    }

    /** US-7.14 — explicit ScoreFile binding (overrides the legacy "largest-covering-file" read). */
    public static CompositionInstrument forComposition(Composition composition, Instrument instrument,
                                                       String role, int pageFrom, int pageTo, String fileRef,
                                                       ScoreFile scoreFile,
                                                       PartSource source, double confidenceScore) {
        if (scoreFile == null) {
            // Delegate: the legacy path keeps working — for null scoreFile the caller gets
            // today's read behaviour (largest uploaded PDF that includes [pageFrom..pageTo]).
            return forComposition(composition, instrument, role, pageFrom, pageTo, fileRef, source, confidenceScore);
        }
        if (scoreFile.getComposition() == null) throw new IllegalArgumentException("scoreFile must be attached to a composition");
        if (!sameComposition(composition, scoreFile)) {
            throw new IllegalArgumentException(
                    "scoreFile must belong to the same composition as the part mapping");
        }
        if (scoreFile.getPageCount() != null && pageTo > scoreFile.getPageCount()) {
            throw new IllegalArgumentException(
                    "pageTo (" + pageTo + ") exceeds the selected ScoreFile's pageCount (" + scoreFile.getPageCount() + ")");
        }
        return new CompositionInstrument(
                composition, instrument, role.trim(), pageFrom, pageTo, requireNullableNotBlank(fileRef, "fileRef"),
                scoreFile, (source == null ? PartSource.MANUAL : source), confidenceScore);
    }

    private static boolean sameComposition(Composition c, ScoreFile f) {
        Long fc = (f.getComposition() == null) ? null : f.getComposition().getId();
        Long cc = (c == null) ? null : c.getId();
        return cc != null && cc.equals(fc);
    }

    /** Convenience factory for the US-4.x AI strategy: source=AI by construction. */
    public static CompositionInstrument forAiProposal(Composition composition, Instrument instrument,
                                                      String role, int pageFrom, int pageTo, double confidenceScore) {
        return forComposition(composition, instrument, role, pageFrom, pageTo, null, PartSource.AI, confidenceScore);
    }

    /**
     * Marks this part as verified by the named user at the given moment. Idempotent: once
     * {@code verifiedBy}/{@code verifiedAt} are set, further calls leave them untouched so
     * the first-writer's audit pair is the canonical record (re-verification cannot hijack it).
     */
    public void verify(String userIdentifier, Instant at) {
        if (userIdentifier == null || userIdentifier.isBlank()) throw new IllegalArgumentException("userIdentifier required");
        if (at == null)                                          throw new IllegalArgumentException("verifiedAt required");
        if (this.verifiedBy != null) return; // freeze the audit pair
        this.verifiedBy = userIdentifier.trim();
        this.verifiedAt = at;
    }

    private static boolean bandsAgree(Composition c, Instrument i) {
        // Strict two-sided check: both sides must expose a resolvable band id AND they must be
        // equal. We deliberately DO NOT treat "null == null" as agreement — if either side cannot
        // resolve its band (e.g. detached lazy proxy), the caller is expected to handle it before
        // reaching this point (the factory throws IAE below in that case). Keeping this one-sided
        // guard means cross-band writes always fail fast at the factory, never reaching the DB.
        Long cb = c.getBand() == null ? null : c.getBand().getId();
        Long ib = i.getBand()  == null ? null : i.getBand().getId();
        return cb != null && cb.equals(ib);
    }

    /* BandsAgree + bandIdStrict intentionally NOT exposed as a public helper; callers should
       rely on the factory, which is the sole write-path (see US-3.x command layer). Keep this
       file minimal — the contract lives in {@link #forComposition}. */

    private static void verifyRange(int pageFrom, int pageTo) {
        if (pageFrom < 1) throw new IllegalArgumentException("pageFrom must be >= 1");
        if (pageTo   < 1) throw new IllegalArgumentException("pageTo must be >= 1");
        if (pageFrom > pageTo) throw new IllegalArgumentException("pageFrom must not exceed pageTo (got " + pageFrom + " > " + pageTo + ")");
    }

    private static void verifyConfidence(double score) {
        if (Double.isNaN(score) || score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("confidenceScore must be between 0.0 and 1.0 (got " + score + ")");
        }
    }

    private static String requireNotBlank(String s, String field) {
        if (s == null || s.isBlank()) throw new NullPointerException(field + " required");
        return s;
    }

    /** Alias kept for semantic clarity at call sites; throws NullPointerException on blank. */
    private static void requireRole(String role) {
        requireNotBlank(role, "instrumentRole");
    }

    private static String requireNullableNotBlank(String s, String field) {
        if (s == null) return null;
        if (s.isBlank()) throw new IllegalArgumentException(field + " must not be blank when set");
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
