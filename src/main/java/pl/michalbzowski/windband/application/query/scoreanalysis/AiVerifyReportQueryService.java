package pl.michalbzowski.windband.application.query.scoreanalysis;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysis;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysisRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;
import pl.michalbzowski.windband.domain.composition.ScorePageExclusion;
import pl.michalbzowski.windband.domain.composition.ScorePageExclusionRepository;

/**
 * Flagged feature — "raport spójności" read model: the legacy proposal preview (unchanged)
 * plus the pre-acceptance consistency verdicts from {@link ProposalConsistency} and the
 * score-file page count used both for the beyond-pages rule and the UI's page thumbnails.
 *
 * <p>Deliberately composed ON TOP of {@link ScoreAnalysisProposalsQueryService} (band isolation,
 * terminal-phase check, artefact parsing, role resolution) so the existing contract keeps serving
 * the legacy table while this view adds global checks. The legacy service is not modified.
 *
 * <p>Read-only transaction: composition/member associations are lazy and {@code open-in-view}
 * is off — same reason as in {@link RoleInstrumentResolver}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiVerifyReportQueryService {

    private static final Logger log = LoggerFactory.getLogger(AiVerifyReportQueryService.class);

    private final ScoreAnalysisProposalsQueryService previewQuery;
    private final ScoreAnalysisRepository analysisRepository;
    private final ScoreFileRepository scoreFileRepository;
    private final CompositionInstrumentRepository partRepository;
    private final ScorePageExclusionRepository exclusionRepository;

    /**
     * Builds the report for one explicit analysis.
     *
     * @throws IllegalArgumentException null ids (→ 400)
     * @throws IllegalStateException cross-band composition/analysis, non-terminal phase or missing
     *         artefact (→ 409) — inherited from {@code preview()}
     */
    public AiVerifyReportDto report(Long compositionId, Long bandId, Long analysisId) {
        if (compositionId == null || bandId == null || analysisId == null) {
            throw new IllegalArgumentException("compositionId, bandId i analysisId są wymagane.");
        }
        ProposalsPreviewDto base = previewQuery.preview(compositionId, bandId, analysisId);

        ScoreAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalStateException("Analiza " + analysisId + " nie znaleziona."));
        ScoreFile scoreFile = scoreFileRepository.findById(analysis.getScoreFileId())
                .orElseThrow(() -> new IllegalStateException(
                        "Plik nut analizy " + analysisId + " nie istnieje (id: " + analysis.getScoreFileId() + ")."));

        List<ProposalConsistency.Row> checkRows = base.proposals().stream()
                .map(p -> new ProposalConsistency.Row(
                        p.role(), p.pageFrom(), p.pageTo(), p.instrumentIdOrNull() != null))
                .toList();
        ProposalConsistency.Report verdicts = ProposalConsistency.check(checkRows, scoreFile.getPageCount());

        List<AiVerifyReportDto.AiVerifyRow> rows = new java.util.ArrayList<>(base.proposals().size());
        for (int i = 0; i < base.proposals().size(); i++) {
            AiProposalPreview p = base.proposals().get(i);
            List<ProposalConsistency.Issue> issues =
                    verdicts.issues().size() > i ? verdicts.issues().get(i) : List.of();
            rows.add(new AiVerifyReportDto.AiVerifyRow(
                    p.role(), p.pageFrom(), p.pageTo(), p.confidence(), p.teamTag(),
                    p.instrumentIdOrNull(), p.instrumentNameOrNull(), p.memberNames(), issues));
        }

        List<AiVerifyReportDto.PageRange> existingPartRanges = partRangesOf(analysis, scoreFile);

        List<AiVerifyReportDto.ExcludedRange> excludedRanges = exclusionRepository
                .findAllByScoreFile(scoreFile).stream()
                .sorted(Comparator.comparingInt(ScorePageExclusion::getPageFrom))
                .map(x -> new AiVerifyReportDto.ExcludedRange(
                        x.getPageFrom(), x.getPageTo(), x.getMarkedBy(), x.getMarkedAt().toString()))
                .toList();

        return new AiVerifyReportDto(
                analysisId,
                scoreFile.getId(),
                scoreFile.getPageCount(), // may be null — non-PDF uploads carry none
                base.work(),
                base.instruments(),
                base.simulated(),
                verdicts.errorRows(),
                verdicts.warningRows(),
                rows,
                List.copyOf(existingPartRanges),
                excludedRanges);
    }

    /**
     * Pages already owned by stored parts of the composition — the UI's gap-coverage baseline.
     * Preference goes to parts bound to THIS score file (page numbers are per-file); when the
     * composition has no such binding (legacy rows) all part ranges take the baseline role.
     */
    private List<AiVerifyReportDto.PageRange> partRangesOf(ScoreAnalysis analysis, ScoreFile file) {
        var parts = partRepository.findAllByComposition(analysis.getComposition());
        Long fileId = file.getId();
        List<CompositionInstrument> bound = new java.util.ArrayList<>();
        for (CompositionInstrument p : parts) {
            if (p.getScoreFile() != null && fileId.equals(p.getScoreFile().getId())) {
                bound.add(p);
            }
        }
        Collection<CompositionInstrument> used = bound.isEmpty() ? parts : bound;
        List<AiVerifyReportDto.PageRange> out = new java.util.ArrayList<>(used.size());
        for (CompositionInstrument p : used) {
            if (p.getPageFrom() != null && p.getPageTo() != null && p.getPageFrom() <= p.getPageTo()) {
                out.add(new AiVerifyReportDto.PageRange(p.getPageFrom(), p.getPageTo()));
            }
        }
        return out;
    }

    /**
     * Report for the most recent SUCCEEDED analysis of the composition (the page's entry point).
     * Empty when no analysis exists, none finished successfully, or the finished one is unreadable
     * (operational failure → the view renders its "no data" state instead of a 500).
     */
    public Optional<AiVerifyReportDto> reportForLatestSucceeded(Long compositionId, Long bandId) {
        if (compositionId == null || bandId == null) {
            throw new IllegalArgumentException("compositionId i bandId są wymagane.");
        }
        return analysisRepository.findLatestByCompositionIdAndBandId(compositionId, bandId)
                .filter(a -> a.getPhase() == ScoreAnalysis.Phase.SUCCEEDED)
                .map(a -> {
                    try {
                        return report(compositionId, bandId, a.getId());
                    } catch (IllegalStateException | IllegalArgumentException e) {
                        log.warn("Niedostępny raport spójności utworu {} (analiza {}): {}",
                                compositionId, a.getId(), e.getMessage());
                        return null; // cross-band id or broken artefact — empty view beats a stack trace
                    }
                })
                .flatMap(Optional::ofNullable);
    }
}
