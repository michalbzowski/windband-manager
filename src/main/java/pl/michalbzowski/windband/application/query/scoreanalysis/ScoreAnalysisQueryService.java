package pl.michalbzowski.windband.application.query.scoreanalysis;

import java.time.Instant;
import java.util.Optional;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysis;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysisRepository;

/**
 * US-4.1 — read side for the AI-analysis lifecycle. Band-isolated via composition:
 * every lookup resolves through {@code findByIdAndCompositionId} (no naked id read).
 */
@org.springframework.stereotype.Service
public class ScoreAnalysisQueryService {

    private final CompositionRepository compositionRepository;
    private final ScoreAnalysisRepository analysisRepository;

    public ScoreAnalysisQueryService(CompositionRepository compositionRepository,
                                     ScoreAnalysisRepository analysisRepository) {
        this.compositionRepository = compositionRepository;
        this.analysisRepository = analysisRepository;
    }

    /** Latest analysis for a band-scoped composition — 404-safe. */
    public Optional<LatestScoreAnalysisDto> latestFor(Long compositionId, Long bandId) {
        // Two-step: composition must be in this band first (the band is the trust boundary),
        // then fetch the most recent analysis row for that composition via the band-scoped JPQL.
        compositionRepository.findByIdAndBandId(compositionId, bandId)
                .orElseThrow(() -> new IllegalStateException(
                        "Utwór " + compositionId + " nie należy do zespołu " + bandId + "."));
        return analysisRepository.findLatestByCompositionIdAndBandId(compositionId, bandId)
                .map(LatestScoreAnalysisDto::of);
    }

    /** Project the domain row to a DTO that is safe across lazy boundaries. */
    public static final class LatestScoreAnalysisDto {
        private final Long id;
        private final String phase;
        private final String runnerRef;
        private final String errorMessage;
        private final String arrangementJsonPath;
        private final String arrangementMusicxmlPath;
        private final String arrangementMidPath;
        private final String validationTxtPath;
        private final java.time.Instant startedAt;
        private final java.time.Instant finishedAt;
        private final Long compositionId;

        public LatestScoreAnalysisDto(Long id, String phase, String runnerRef, String errorMessage,
                                      String arrangementJsonPath, String arrangementMusicxmlPath,
                                      String arrangementMidPath, String validationTxtPath,
                                      java.time.Instant startedAt, java.time.Instant finishedAt,
                                      Long compositionId) {
            this.id = id;
            this.phase = phase;
            this.runnerRef = runnerRef;
            this.errorMessage = errorMessage;
            this.arrangementJsonPath = arrangementJsonPath;
            this.arrangementMusicxmlPath = arrangementMusicxmlPath;
            this.arrangementMidPath = arrangementMidPath;
            this.validationTxtPath = validationTxtPath;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
            this.compositionId = compositionId;
        }

        private LatestScoreAnalysisDto(ScoreAnalysis a) {
            this(a.getId(), a.getPhase().name(), a.getRunnerRef(), a.getErrorMessage(),
                 a.getArrangementJsonPath(), a.getArrangementMusicxmlPath(),
                 a.getArrangementMidPath(), a.getValidationTxtPath(),
                 a.getStartedAt(), a.getFinishedAt(),
                 a.getComposition() != null ? a.getComposition().getId() : null);
        }

        public static LatestScoreAnalysisDto of(ScoreAnalysis a) { return new LatestScoreAnalysisDto(a); }
        public Long id() { return id; }
        public String phase() { return phase; }
        public String runnerRef() { return runnerRef; }
        public String errorMessage() { return errorMessage; }
        public String arrangementJsonPath() { return arrangementJsonPath; }
        public String arrangementMusicxmlPath() { return arrangementMusicxmlPath; }
        public String arrangementMidPath() { return arrangementMidPath; }
        public String validationTxtPath() { return validationTxtPath; }
        public Instant startedAt() { return startedAt; }
        public Instant finishedAt() { return finishedAt; }
        public Long compositionId() { return compositionId; }
    }
}
