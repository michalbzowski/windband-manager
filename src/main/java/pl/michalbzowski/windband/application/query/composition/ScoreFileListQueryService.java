package pl.michalbzowski.windband.application.query.composition;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.dto.composition.CompositionInstrumentDto;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;

import java.util.List;
import java.util.Objects;

/**
 * Read-only query service that lists every uploaded score file of one
 * composition (newest first) — scoped to the band, so a foreign caller cannot
 * enumerate the library of another band.
 *
 * <p>US-4.3 (UI modal "Analizuj utwór") needs a stable snapshot of the
 * composition's files so the user can pick which one should be analyzed; this
 * endpoint is the single source that backs both that dropdown and any future
 * file management view on the detail page.
 *
 * <p>The service is deliberately thin: it validates band ownership (throws
 * when the composition does not belong to {@code bandId}) and then defers to
 * the repository. It is NOT a write path; mutation stays in the upload
 * adapter ({@code pl.michalbzowski.windband.adapter.in.web.ScoreFileUploadRestController}).
 *
 * <p>ArchUnit: this class lives under {@code pl..application.query.composition..}
 * which may use {@code org.springframework.web..}? NO — by convention query
 * services stay HTTP-free so tests can drive them from any layer. This type
 * references only domain + Spring stereotype.
 */
@Service
public class ScoreFileListQueryService {

    private final CompositionRepository compositionRepository;
    private final ScoreFileRepository scoreFileRepository;
    private final CompositionInstrumentRepository compositionInstrumentRepository;

    public ScoreFileListQueryService(CompositionRepository compositionRepository,
                                     ScoreFileRepository scoreFileRepository,
                                     CompositionInstrumentRepository compositionInstrumentRepository) {
        this.compositionRepository = Objects.requireNonNull(compositionRepository, "compositionRepository");
        this.scoreFileRepository   = Objects.requireNonNull(scoreFileRepository, "scoreFileRepository");
        this.compositionInstrumentRepository = Objects.requireNonNull(
                compositionInstrumentRepository, "compositionInstrumentRepository");
    }

    /** All files of {@code compositionId} for {@code bandId}, newest first. Empty list if none. */
    @Transactional(readOnly = true)
    public List<ScoreFile> listByComposition(Long compositionId, Long bandId) {
        Objects.requireNonNull(compositionId, "compositionId");
        Objects.requireNonNull(bandId, "bandId");
        var composition = compositionRepository.findByIdAndBandId(compositionId, bandId)
                .orElseThrow(() -> new CompositionNotFoundException(compositionId));
        return scoreFileRepository.findAllByComposition(composition);
    }

    /**
     * US-7.1 — "Oznacz głosy na stronach nut": all part mappings (composition → instrument + role
     * + page range) for a given composition, scoped to the band. Mirrors {@link #listByComposition}
     * scope semantics: throws {@link CompositionNotFoundException} when the composition
     * does not belong to the caller's band, refuses on cross-band enumeration.
     *
     * <p><b>Shape C (DTO projection):</b> returns {@link CompositionInstrumentDto}s, never raw JPA
     * entities. The {@code detail.html#existing-parts} table renders {@code p.scoreFileName} and
     * {@code p.instrumentName}, which exist only on the DTO; projecting entities made Thymeleaf
     * fail mid-row (EL1008E on line 690) and cut the response off before the "Dodaj głos" button
     * and the layout footer-scripts — every bug after the failing cell was a symptom of the same
     * error. Lazy {@code instrument} / {@code scoreFile} associations are resolved INSIDE this
     * {@code @Transactional(readOnly = true)} boundary, so only scalars survive into the template.
     */
    @Transactional(readOnly = true)
    public List<CompositionInstrumentDto> partsFor(Long compositionId, Long bandId) {
        Objects.requireNonNull(compositionId, "compositionId");
        Objects.requireNonNull(bandId, "bandId");
        var composition = compositionRepository.findByIdAndBandId(compositionId, bandId)
                .orElseThrow(() -> new CompositionNotFoundException(compositionId));
        var parts = compositionInstrumentRepository.findAllByComposition(composition);

        // Resolve the ScoreFile name per id in the SAME open session — part.getScoreFile() is a
        // lazy proxy; dereferencing it here (while attached) is what makes it safe for rendering.
        java.util.Map<Long, String> fileNamesById = new java.util.HashMap<>();
        for (ScoreFile file : listByComposition(compositionId, bandId)) {
            if (file.getId() != null && file.getOriginalName() != null) {
                fileNamesById.put(file.getId(), file.getOriginalName());
            }
        }

        return parts.stream()
                .map(part -> toPartDto(part, fileNamesById))
                .toList();
    }

    private CompositionInstrumentDto toPartDto(CompositionInstrument part,
                                                java.util.Map<Long, String> fileNamesById) {
        String instrumentName = part.getInstrument() != null ? part.getInstrument().getName() : null;
        Long scoreFileId = part.getScoreFile() == null ? null : part.getScoreFile().getId();
        String scoreFileName = (scoreFileId == null) ? null : fileNamesById.get(scoreFileId);
        return new CompositionInstrumentDto(
                part.getId(),
                part.getComposition() != null ? part.getComposition().getId() : null,
                part.getInstrumentRole(),
                instrumentName,
                part.getPageFrom(),
                part.getPageTo(),
                part.getFileRef(),
                scoreFileId,
                scoreFileName,
                part.getSource(),
                part.getConfidenceScore(),
                part.getVerifiedBy(),
                part.getVerifiedAt());
    }

    /** 404 mirror of the repository contract (a file that is not on the disk or does not belong to the comp). */
    public static class CompositionNotFoundException extends RuntimeException {
        private final long compositionId;
        public CompositionNotFoundException(long compositionId) {
            super("composition " + compositionId + " not found");
            this.compositionId = compositionId;
        }
        public long compositionId() { return compositionId; }
    }
}
