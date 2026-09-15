package pl.michalbzowski.windband.application.command.composition;

import java.io.InputStream;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;

/**
 * Port for the score-file write path (upload): persisting the binary to disk and
 * the {@link ScoreFile} metadata row.
 *
 * <p><b>Status: Task 1.06 = empty implementation.</b> The plan reserves this file
 * as the stable application-layer contract that Task 1.07 builds behind
 * (validation: magic-bytes MIME detection, size caps {@code windband.scores.max*},
 * sanitised root-dir layout) and the future UI (US-2.01). Introducing the port
 * now keeps later tasks to a pure implementation swap — no call-site churn.
 *
 * <p><b>Band isolation:</b> {@code bandId} is bound before the composition can be
 * loaded, so an upload can never attach a file to a foreign-band composition:
 * the pair simply does not resolve (same 409 contract as
 * {@link CompositionCommandService#requireOwned}).
 *
 * <p><b>ArchUnit gate:</b> no Spring Web types here — Task 1.07 implements via
 * the same discipline (domain ports only; any HTTP concern stays in the adapter).
 */
@Service
public class ScoreFileUploadPort {

    private static final Logger log = LoggerFactory.getLogger(ScoreFileUploadPort.class);

    private final CompositionRepository compositionRepository;
    private final BandRepository bandRepository;

    public ScoreFileUploadPort(CompositionRepository compositionRepository,
                               BandRepository bandRepository) {
        this.compositionRepository = compositionRepository;
        this.bandRepository = bandRepository;
    }

    /**
     * Persist an uploaded score file for the given composition.
     *
     * @param id            composition to attach to (must resolve inside {@code bandId})
     * @param originalName  user-submitted file name (display only)
     * @param stream        binary payload (PDF or ZIP — validated internally in Task 1.07)
     * @param bandId        the calling band; enforces isolation before any work happens
     * @return the stored record with id, SHA-256 and storage path populated
     * @throws UnsupportedOperationException always, until Task 1.07 is merged
     *         (the endpoint is not exposed yet, so no request can reach here)
     */
    public ScoreFile upload(Long id, String originalName, InputStream stream, Long bandId) {
        if (bandRepository.findById(bandId).isEmpty()) {
            throw new IllegalArgumentException("Band not found: " + bandId);
        }
        var composition = compositionRepository.findByIdAndBandId(id, bandId)
                .orElseThrow(() -> new IllegalStateException(
                        "Composition " + id + " does not belong to band " + bandId));

        // Intentionally close the stream before signalling "not implemented" so we
        // never leak the caller's resource even while this throws.
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception ignored) {
                // best-effort cleanup; the UnsupportedOperationException below is the real signal
            }
        }
        log.warn("ScoreFileUploadPort.upload invoked before Task 1.07; compositionId={}", composition.getId());
        throw new UnsupportedOperationException(
                "Score file upload is not implemented yet — see plan Task 1.07 " +
                "(adapter persist: magic-bytes validation, size limits, disk layout)");
    }

    /**
     * Resolve the storage path of a stored score file without reading its bytes.
     * Reserved for Task 1.08's download endpoint (which also adds streaming + ACL).
     */
    public Path resolvePath(Long id) {
        throw new UnsupportedOperationException(
                "Score file retrieval is not implemented yet — see plan Task 1.08");
    }
}
