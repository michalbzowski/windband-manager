package pl.michalbzowski.windband.application.command.composition;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.dto.composition.ScoreFileDto;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;

/**
 * Command side of US-2.1: accept an uploaded score file (already in-memory as
 * {@link ScoreFileUploadRequest}), validate it through {@link UploadValidator},
 * store the bytes with {@link ScoreFileStorage}, and persist the resulting
 * {@code score_files} row.
 *
 * <p>This class is in the application layer and does NOT reference any Spring Web
 * type — that keeps the ArchitectureTest rule {@code ..application.. must not
 * depend on org.springframework.web..} satisfied. The adapter (REST controller)
 * owns the conversion from {@code MultipartFile} to {@link ScoreFileUploadRequest}.</p>
 */
@Service
@RequiredArgsConstructor
public class ScoreFileCommandService {

    private final CompositionRepository compositionRepository;
    private final ScoreFileRepository scoreFileRepository;
    private final UploadValidator uploadValidator;
    private final ScoreFileStorage storage;

    /**
     * Upload + persist one score file.
     *
     * <p>Validation failures throw {@link UploadValidator.UploadRejectedException}
     * (carry the HTTP status: 415/413/422). Unexpected I/O failures surface as
     * {@link java.io.UncheckedIOException}. Callers (the REST controller) translate
     * both into the correct HTTP response.</p>
     */
    @Transactional
    public ScoreFileDto upload(ScoreFileUploadRequest request, Long compositionId, Long bandId) {
        if (request == null || request.bytes() == null || request.bytes().length == 0) {
            throw new UploadValidator.UploadRejectedException(400, "Brak treści pliku do zapisu.");
        }

        // 1. Band isolation: the composition must resolve in this band.
        Composition composition = requireOwned(compositionId, bandId);

        // 2. Validate (cheap).
        String contentType = request.contentType();
        long size = request.size();
        uploadValidator.requireAllowedMime(contentType);
        uploadValidator.requireAllowedSize(size, contentType);
        if ("application/zip".equalsIgnoreCase(contentType)) {
            uploadValidator.requireZipContentSafe(request.bytes());
        }

        // 3. Store (expensive — writes to disk). SHA-256 is computed here.
        ScoreFileStorage.StoredFile stored;
        try {
            stored = storage.store(new ByteArrayInputStream(request.bytes()), size, request.originalFileName());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Nie udało się zapisać pliku na dysku.", e);
        }

        // 4. Persist the row.
        ScoreFile record = ScoreFile.forComposition(
                composition, contentType, request.originalFileName(),
                size, stored.sha256(), stored.storagePath());
        scoreFileRepository.save(record);

        return new ScoreFileDto(
                record.getId(),
                composition.getId(),
                request.originalFileName(),
                size,
                contentType,
                null,                          // page count is Epic 2 US-2.2's job
                "application/zip".equalsIgnoreCase(contentType),
                stored.sha256(),
                Instant.now());
    }

    private Composition requireOwned(Long id, Long bandId) {
        return compositionRepository.findByIdAndBandId(id, bandId)
                .orElseThrow(() -> new IllegalStateException(
                        "Composition " + id + " does not belong to band " + bandId));
    }

}
