package pl.michalbzowski.windband.application.query.composition;

import org.springframework.stereotype.Service;
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

    public ScoreFileListQueryService(CompositionRepository compositionRepository,
                                     ScoreFileRepository scoreFileRepository) {
        this.compositionRepository = Objects.requireNonNull(compositionRepository, "compositionRepository");
        this.scoreFileRepository   = Objects.requireNonNull(scoreFileRepository, "scoreFileRepository");
    }

    /** All files of {@code compositionId} for {@code bandId}, newest first. Empty list if none. */
    public List<ScoreFile> listByComposition(Long compositionId, Long bandId) {
        Objects.requireNonNull(compositionId, "compositionId");
        Objects.requireNonNull(bandId, "bandId");
        var composition = compositionRepository.findByIdAndBandId(compositionId, bandId)
                .orElseThrow(() -> new CompositionNotFoundException(compositionId));
        return scoreFileRepository.findAllByComposition(composition);
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
