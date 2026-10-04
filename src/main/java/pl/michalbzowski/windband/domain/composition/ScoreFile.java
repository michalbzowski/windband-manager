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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * Metadata of a single uploaded score file (PDF or ZIP) for one {@link Composition}.
 *
 * <p>Task 1.06 of the score-library plan: only the persistence side of file
 * storage is introduced here — the binary lives on disk and this row records
 * where, what it looks like (MIME, size, checksum, original name) and which
 * composition owns it. The write path (upload adapter with validation) and the
 * read endpoint land in later tasks; this entity is the stable contract they
 * build against.
 *
 * <p>Invariants:
 * <ul>
 *   <li>a file always belongs to exactly one composition ({@code composition_id}
 *       is non-nullable, cascade-deleted with the parent band);</li>
 *   <li>MIME, size, SHA-256 and storage path are never blank — the adapter that
 *       creates the row (Task 1.07) guarantees this before {@code save}.</li>
 * </ul>
 */
@Entity
@Table(name = "score_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoreFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "composition_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Composition composition;

    /** MIME type of the stored binary — {@code application/pdf} or {@code application/zip}. */
    @Column(nullable = false, length = 100)
    private String mimeType;

    /** Content length in bytes (capped upstream by the adapter limits). */
    @Column(nullable = false)
    private long sizeBytes;

    /** SHA-256 hex digest of the stored binary — integrity + dedup hook. */
    @Column(nullable = false, length = 64)
    private String sha256;

    /** Absolute path of the stored file inside the configured scores root. */
    @Column(nullable = false, length = 500)
    private String storagePath;

    /** User-submitted file name (display only, never used on read). */
    @Column(length = 300)
    private String originalName;

    /** Page count (PDF only). Null when non-applicable or unknown. */
    @Column(name = "page_count")
    private Integer pageCount;

    /** Parent ZIP row's id when this file was extracted from a ZIP (US-2.3). Null for standalone uploads. */
    @Column(name = "parent_file_id")
    private Long parentFileId;

    /**
     * US-7.15 — moment the bytes behind this row were swapped by a content replacement
     * ("Wymień plik"). Null when the file was never replaced. The id survives the swap on
     * purpose (part mappings keep pointing at this row); this column records that the
     * content behind them changed. See {@link #replaceContent}.
     */
    @Column(name = "replaced_at")
    private Instant replacedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // CT_CONSTRUCTOR_THROW guard: this constructor performs no validation on
    // purpose — all invariants are checked in the public factory below, so the
    // object never escapes partially-initialised. SpotBugs flags any throw path
    // inside a protected constructor; keeping the body assignment-only avoids it.
    protected ScoreFile(Composition composition, String mimeType, Long sizeBytes,
                        String sha256, String storagePath, String originalName,
                        Integer pageCount, Long parentFileId) {
        this.composition = composition;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storagePath = storagePath;
        this.originalName = originalName;
        this.pageCount = pageCount;
        this.parentFileId = parentFileId;
    }

    /**
     * Factory for a new standalone score-file record (no parent ZIP row).
     * Validates the invariants before the instance escapes.
     */
    public static ScoreFile forComposition(Composition composition, String mimeType,
                                           String originalName, long sizeBytes,
                                           String sha256, String storagePath,
                                           Integer pageCount) {
        return forComposition(composition, mimeType, originalName, sizeBytes, sha256, storagePath, pageCount, null);
    }

    /**
     * Factory for a score-file record extracted from a ZIP (US-2.3).
     * {@code parentFileId} links back to the parent ZIP's {@link ScoreFile} row.
     */
    public static ScoreFile forComposition(Composition composition, String mimeType,
                                           String originalName, long sizeBytes,
                                           String sha256, String storagePath,
                                           Integer pageCount, Long parentFileId) {
        if (composition == null) {
            throw new NullPointerException("composition required");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must be >= 0");
        }
        requireNonNegativePageCount(pageCount);
        return new ScoreFile(
                composition,
                requireNotBlank(mimeType, "mimeType"),
                sizeBytes,
                requireNotBlank(sha256, "sha256").toLowerCase(),
                requireNotBlank(storagePath, "storagePath"),
                originalName,
                pageCount,
                parentFileId);
    }

    private static void requireNonNegativePageCount(Integer pageCount) {
        if (pageCount != null && pageCount < 0) {
            throw new IllegalArgumentException("pageCount must be null or >= 0");
        }
    }

    /**
     * US-7.15 — swap this row's content IN PLACE: new bytes, new size/sha/path, new page count,
     * optionally a new display name ({@code newOriginalName} blank/null keeps the old one).
     * The identity ({@code id}) and the MIME type are deliberately NOT touched — mappings bound
     * to this row survive the swap, and the command service enforces "same type replaces same
     * type" before calling. {@code replacedAt} records the moment the content changed.
     *
     * <p>Validation mirrors the factory's invariants: blank sha/path and a negative page count
     * are rejected before the mutation is applied (SpotBugs CT_CONSTRUCTOR_THROW does not apply
     * to ordinary methods, but a half-swapped row would silently break downloads, so all
     * arguments are checked first).</p>
     */
    public void replaceContent(String newOriginalName, long newSizeBytes,
                               String newSha256, String newStoragePath, Integer newPageCount) {
        if (newSizeBytes <= 0) {
            throw new IllegalArgumentException("newSizeBytes must be > 0");
        }
        requireNonNegativePageCount(newPageCount);
        String sha = requireNotBlank(newSha256, "sha256");
        String path = requireNotBlank(newStoragePath, "storagePath");
        this.sizeBytes = newSizeBytes;
        this.sha256 = sha.toLowerCase();
        this.storagePath = path;
        this.pageCount = newPageCount;
        if (newOriginalName != null && !newOriginalName.isBlank()) {
            this.originalName = newOriginalName.trim();
        }
        this.replacedAt = java.time.Instant.now();
    }

    private static String requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
