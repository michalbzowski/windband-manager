package pl.michalbzowski.windband.application.command.composition;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * US-2.5 — delete a score file: removes the DB row, cascade-deletes any child
 * rows it spawned via ZIP extraction (US-2.3), and best-effort deletes the
 * physical bytes on disk.
 *
 * <p>Band isolation follows the same two-layer contract as US-2.4:
 * <ul>
 *   <li>layer 1 — band exists ({@link BandQueryService#getRequiredBand(Long)}) → 400 on failure;</li>
 *   <li>layer 2 — file's row exists, its composition is the one in the URL, and that
 *       composition belongs to the requested band → otherwise 409 (cross-band).
 * </ul>
 *
 * <p>This class lives in the application layer and references no Spring Web type
 * (ArchUnit rule, {@code GlobalExceptionHandler} owns HTTP mapping).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreFileDeleteCommandService {

    private final BandQueryService bandQueryService;
    private final CompositionRepository compositionRepository;
    private final ScoreFileRepository scoreFileRepository;

    /**
     * Deletes the file referenced by {@code fileId} within {@code (bandId, compositionId)}.
     *
     * @throws IllegalArgumentException if {@code bandId} does not exist (→ HTTP 400).
     * @throws IllegalStateException if {@code fileId} is unknown (→ HTTP 409) — treating an
     *         unknown id as a conflict-on-purpose keeps the contract identical to US-2.4's
     *         ownership checks and makes the URL-vs-database relationship explicit.
     * @throws IllegalStateException if the file belongs to another band or its composition
     *         does not match the URL (→ HTTP 409).
     */
    @Transactional
    public void delete(Long fileId, Long compositionId, Long bandId) {
        // Layer 1 — band must exist.
        bandQueryService.getRequiredBand(bandId);

        // Layer 2.1 — row exists in this system at all.
        ScoreFile file = scoreFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalStateException(
                        "Score file " + fileId + " does not exist."));

        // Layer 2.2 — composition URL must own this file.
        Long owningCompositionId = file.getComposition().getId();
        if (!owningCompositionId.equals(compositionId)) {
            throw new IllegalStateException(
                    "Score file " + fileId + " is not part of composition " + compositionId);
        }

        // Layer 2.3 — the composition must belong to the requested band (the actual multi-tenant gate).
        Long owningBand = file.getComposition().getBand().getId();
        if (!owningBand.equals(bandId)) {
            throw new IllegalStateException(
                    "Score file " + fileId + " does not belong to band " + bandId);
        }

        // Cascade: delete any child rows this ZIP parent spawned (US-2.3), then the row itself.
        List<ScoreFile> children = scoreFileRepository.findByParentFileId(fileId);
        for (ScoreFile child : children) {
            physicallyDeleteQuietly(child.getStoragePath());
            scoreFileRepository.delete(child);
        }

        // The file's own physical bytes, then its DB row.
        physicallyDeleteQuietly(file.getStoragePath());
        scoreFileRepository.delete(file);

        log.info("Deleted score file id={} (compositionId={}, bandId={}) and {} extracted child row(s)",
                fileId, compositionId, bandId, children.size());
    }

    /**
     * Best-effort on-disk deletion. A missing file is not an error — the DB row was the contract;
     * the bytes may already have been removed by a prior manual sweep (or never flushed). This keeps
     * the HTTP response 204 even in that edge case, and lets the caller's transaction roll back the
     * DB delete if a later failure forces it.
     */
    private void physicallyDeleteQuietly(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return;
        }
        Path path = Path.of(storagePath);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // Intentionally non-fatal: log-and-continue. The row will still be removed so the UI
            // state is accurate — an orphaned on-disk file is a far milder failure mode than a
            // dangling DB row. Ops can reconcile later if this logs repeatedly for one path.
            log.warn("Failed to delete on-disk score file at {} — continuing with DB deletion", storagePath, e);
        }
    }
}
