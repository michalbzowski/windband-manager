package pl.michalbzowski.windband.application.query.scoreanalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.michalbzowski.windband.application.command.scoreanalysis.JsonAiProposals;
import pl.michalbzowski.windband.application.dto.scoreanalysis.AIProposals;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysis;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysisRepository;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;

/**
 * Faza D / US-4.4 — read a finished analysis back for the human: parses the run's
 * {@code arrangement.json}, resolves every row to band instrument + members through
 * {@link RoleInstrumentResolver}, and returns the analyzer's work metadata (title/composer/arranger
 * from {@code analyzer-raw.json}) plus the band's instrument options for the accept form.
 *
 * <p>Band isolation mirrors the Phase B endpoints: the analysis must belong to a composition that
 * resolves in the caller's band — anything else is a 409-family condition via
 * {@link IllegalStateException}. Missing artefacts are the same (a SUCCEEDED row whose file
 * was deleted is an operational error, not a "no data" state).
 */
@Service
@RequiredArgsConstructor
public class ScoreAnalysisProposalsQueryService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CompositionRepository compositionRepository;
    private final ScoreAnalysisRepository analysisRepository;
    private final InstrumentRepository instrumentRepository;
    private final RoleInstrumentResolver resolver;

    /**
     * @throws IllegalArgumentException null path ids (→ 400)
     * @throws IllegalStateException cross-band composition/analysis or non-terminal state (→ 409)
     */
    public ProposalsPreviewDto preview(Long compositionId, Long bandId, Long analysisId) {
        if (compositionId == null || bandId == null || analysisId == null) {
            throw new IllegalArgumentException("compositionId, bandId i analysisId są wymagane.");
        }
        Composition composition = compositionRepository.findByIdAndBandId(compositionId, bandId)
                .orElseThrow(() -> new IllegalStateException(
                        "Utwór " + compositionId + " nie należy do zespołu " + bandId + "."));
        ScoreAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalStateException("Analiza " + analysisId + " nie znaleziona."));
        if (!composition.getId().equals(analysis.getComposition().getId())) {
            throw new IllegalStateException("Analiza " + analysisId + " nie należy do utworu " + compositionId + ".");
        }
        if (analysis.getPhase() != ScoreAnalysis.Phase.SUCCEEDED) {
            throw new IllegalStateException("Analiza nie została ukończona (faza: " + analysis.getPhase() + ").");
        }
        String artifactPath = analysis.getArrangementJsonPath();
        Path artifact = (artifactPath == null || artifactPath.isBlank())
                ? null : Path.of(artifactPath);
        if (artifact == null || !Files.isRegularFile(artifact)) {
            throw new IllegalStateException(
                    "Artefakt arrangement.json analizy " + analysisId + " nie istnieje na dysku.");
        }

        List<AIProposals> rows;
        try {
            rows = JsonAiProposals.parse(Files.readString(artifact, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Nie można odczytać artefaktu analizy " + analysisId + ".", e);
        }

        ProposalsPreviewDto.WorkMeta work = readWorkMeta(artifact.getParent());

        List<AiProposalPreview> previews = new ArrayList<>(rows.size());
        for (AIProposals row : rows) {
            RoleInstrumentResolver.Resolution resolution = resolver.resolve(bandId, row.role());
            Instrument instrument = resolution.instrumentOrNull();
            previews.add(new AiProposalPreview(
                    row.role(),
                    row.pageFrom(),
                    row.pageTo(),
                    row.confidence(),
                    instrument == null ? null : instrument.getId(),
                    instrument == null ? null : instrument.getName(),
                    resolution.memberNames()));
        }

        List<ProposalsPreviewDto.InstrumentOption> instruments =
                instrumentRepository.findAllOrderBySortPriorityByBandId(bandId).stream()
                        .map(i -> new ProposalsPreviewDto.InstrumentOption(i.getId(), i.getName()))
                        .toList();
        return new ProposalsPreviewDto(work, instruments, previews);
    }

    /** Work metadata from {@code analyzer-raw.json} next to the artefact — nulls when absent/malformed. */
    private ProposalsPreviewDto.WorkMeta readWorkMeta(Path workDir) {
        if (workDir == null) {
            return ProposalsPreviewDto.WorkMeta.EMPTY;
        }
        Path raw = workDir.resolve("analyzer-raw.json");
        if (!Files.isRegularFile(raw)) {
            return ProposalsPreviewDto.WorkMeta.EMPTY;
        }
        try {
            JsonNode root = MAPPER.readTree(Files.readString(raw, StandardCharsets.UTF_8));
            JsonNode work = root.path("work");
            if (!work.isObject()) {
                return ProposalsPreviewDto.WorkMeta.EMPTY;
            }
            return new ProposalsPreviewDto.WorkMeta(
                    textOrNull(work, "title"),
                    textOrNull(work, "composer"),
                    textOrNull(work, "arranger"));
        } catch (IOException e) {
            return ProposalsPreviewDto.WorkMeta.EMPTY; // metadata is best-effort — never fail the preview
        }
    }

    private static String textOrNull(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (node.isTextual() && !node.asText().isBlank()) {
            return node.asText().trim();
        }
        return null;
    }
}
