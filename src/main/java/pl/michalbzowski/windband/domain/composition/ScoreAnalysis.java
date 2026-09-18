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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One AI-analysis run bound to a composition + score file (US-4.1).
 *
 * <p>State machine (immutable transitions — every call returns a new instance):
 * <pre>
 *   PENDING ──► RUNNING ──► SUCCEEDED
 *                  │
 *                  └──────► FAILED
 *   (SUCCEEDED / FAILED are terminal.)
 * </pre>
 *
 * <p>Band isolation: {@code composition} is non-null; the score-file FK lives in the
 * application layer (the band-scoped read is always through the composition).
 *
 * <p>State transitions and artefact population are mutually exclusive per-phase
 * (see {@link ScoreAnalysis#markRunning}, {@link ScoreAnalysis#succeed}, {@link ScoreAnalysis#fail}).
 * Illegal transitions throw {@link IllegalStateException} — callers are expected to map
 * that to HTTP 409/500.
 */
@Entity
@Table(name = "score_analysis")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoreAnalysis {

    public enum Phase {
        /** Created, process not yet started (no runner_ref). */
        PENDING,
        /** Process spawned, running in the background. */
        RUNNING,
        /** Process finished successfully; artefacts populated. */
        SUCCEEDED,
        /** Process failed or was cancelled; {@code errorMessage} carries the reason. */
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "composition_id", nullable = false)
    private Composition composition;

    /** The score file that triggered this analysis (PDF or ZIP of PDFs). */
    @Column(name = "score_file_id", nullable = false)
    private Long scoreFileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Phase phase;

    /** Process/handle identifier from the runner (PID or job id). Null until RUNNING. */
    @Column(name = "runner_ref", length = 255)
    private String runnerRef;

    /** User-visible failure reason in Polish — populated only when phase = FAILED. */
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    // Artefact paths (populated only on SUCCEEDED — all four null before that).
    @Column(name = "arrangement_json_path", length = 1024)
    private String arrangementJsonPath;

    @Column(name = "arrangement_musicxml_path", length = 1024)
    private String arrangementMusicxmlPath;

    @Column(name = "arrangement_mid_path", length = 1024)
    private String arrangementMidPath;

    @Column(name = "validation_txt_path", length = 1024)
    private String validationTxtPath;

    @Column(nullable = false)
    private Instant startedAt;

    /** Populated on the terminal transition (SUCCEEDED or FAILED) — null before that. */
    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected ScoreAnalysis(Composition composition, Long scoreFileId, Phase phase, String runnerRef,
                            String errorMessage, String arrangementJsonPath, String arrangementMusicxmlPath,
                            String arrangementMidPath, String validationTxtPath,
                            Instant startedAt, Instant finishedAt) {
        this(null, composition, scoreFileId, phase, runnerRef, errorMessage,
                arrangementJsonPath, arrangementMusicxmlPath,
                arrangementMidPath, validationTxtPath, startedAt, finishedAt);
    }

    protected ScoreAnalysis(Long id, Composition composition, Long scoreFileId, Phase phase, String runnerRef,
                            String errorMessage, String arrangementJsonPath, String arrangementMusicxmlPath,
                            String arrangementMidPath, String validationTxtPath,
                            Instant startedAt, Instant finishedAt) {
        this.id = id;
        this.composition = composition;
        this.scoreFileId = scoreFileId;
        this.phase = phase;
        this.runnerRef = runnerRef;
        this.errorMessage = errorMessage;
        this.arrangementJsonPath = arrangementJsonPath;
        this.arrangementMusicxmlPath = arrangementMusicxmlPath;
        this.arrangementMidPath = arrangementMidPath;
        this.validationTxtPath = validationTxtPath;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public static ScoreAnalysis pending(Composition composition, Long scoreFileId) {
        if (composition == null) throw new NullPointerException("composition required");
        if (scoreFileId == null) throw new NullPointerException("scoreFileId required");
        return new ScoreAnalysis(composition, scoreFileId, Phase.PENDING,
                null, null, null, null, null, null, Instant.now(), null);
    }

    /** Terminal transition → RUNNING (the process has been spawned). */
    public ScoreAnalysis markRunning(String runnerRef) {
        requirePhase(Phase.PENDING, "markRunning");
        return new ScoreAnalysis(id, composition, scoreFileId, Phase.RUNNING,
                runnerRef, this.errorMessage, this.arrangementJsonPath,
                this.arrangementMusicxmlPath, this.arrangementMidPath, this.validationTxtPath,
                this.startedAt, null);
    }

    /** Terminal transition → SUCCEEDED (artefacts fully populated). */
    public ScoreAnalysis succeed(String arrangementJsonPath, String musicxmlPath, String midPath, String validationPath) {
        requirePhase(Phase.RUNNING, "succeed");
        return new ScoreAnalysis(id, composition, scoreFileId, Phase.SUCCEEDED,
                runnerRef, null, arrangementJsonPath, musicxmlPath, midPath, validationPath,
                startedAt, Instant.now());
    }

    /** Terminal transition → FAILED (error message must be non-null). */
    public ScoreAnalysis fail(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("fail() requires a non-blank errorMessage");
        }
        requirePhase(Phase.RUNNING, "fail");
        return new ScoreAnalysis(id, composition, scoreFileId, Phase.FAILED,
                runnerRef, errorMessage, this.arrangementJsonPath,
                this.arrangementMusicxmlPath, this.arrangementMidPath, this.validationTxtPath,
                startedAt, Instant.now());
    }

    public boolean isTerminal() {
        return phase == Phase.SUCCEEDED || phase == Phase.FAILED;
    }

    private void requirePhase(Phase expected, String transition) {
        if (phase != expected) {
            throw new IllegalStateException("Illegal transition " + phase + " -> "
                    + transition + " (expected " + expected + ")");
        }
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
