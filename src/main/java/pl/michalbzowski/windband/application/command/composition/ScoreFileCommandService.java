package pl.michalbzowski.windband.application.command.composition;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.dto.composition.ScoreFileDto;
import pl.michalbzowski.windband.application.dto.composition.ZipEntryDto;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;

/**
 * Command side of US-2.1 and US-2.3: accept an uploaded score file (PDF or ZIP),
 * validate it, store the bytes, persist the resulting {@code score_files} row,
 * and — for ZIP uploads — expand individual entries into their own rows.
 *
 * <p>This class is in the application layer and does NOT reference any Spring Web
 * type — that keeps the ArchitectureTest rule satisfied. The adapter (REST
 * controller) owns conversion from {@code MultipartFile} to the web-agnostic
 * {@link ScoreFileUploadRequest}.</p>
 */
@Service
@RequiredArgsConstructor
public class ScoreFileCommandService {

    private final CompositionRepository compositionRepository;
    private final ScoreFileRepository scoreFileRepository;
    private final UploadValidator uploadValidator;
    private final ScoreFileStorage storage;
    private final PdfPageCounter pdfPageCounter;
    private final ZipEntryExtractor zipEntryExtractor;

    /**
     * Upload + persist one score file.
     */
    @Transactional
    public ScoreFileDto upload(ScoreFileUploadRequest request, Long compositionId, Long bandId) {
        if (request == null || request.bytes() == null || request.bytes().length == 0) {
            throw new UploadValidator.UploadRejectedException(400, "Brak treści pliku do zapisu.");
        }

        Composition composition = requireOwned(compositionId, bandId);

        String contentType = request.contentType();
        long size = request.size();
        uploadValidator.requireAllowedMime(contentType);
        uploadValidator.requireAllowedSize(size, contentType);
        if ("application/zip".equalsIgnoreCase(contentType)) {
            uploadValidator.requireZipContentSafe(request.bytes());
        }

        ScoreFileStorage.StoredFile stored;
        try {
            stored = storage.store(new ByteArrayInputStream(request.bytes()), size, request.originalFileName());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Nie udało się zapisać pliku na dysku.", e);
        }

        Integer pageCount = "application/pdf".equalsIgnoreCase(contentType)
                ? pdfPageCounter.extract(request.bytes())
                : null;

        ScoreFile record = ScoreFile.forComposition(
                composition, contentType, request.originalFileName(),
                size, stored.sha256(), stored.storagePath(), pageCount);
        scoreFileRepository.save(record);

        return new ScoreFileDto(
                record.getId(),
                composition.getId(),
                request.originalFileName(),
                size,
                contentType,
                pageCount,
                "application/zip".equalsIgnoreCase(contentType),
                stored.sha256(),
                null,   // standalone upload — no parent ZIP row
                Instant.now());
    }

    /**
     * Expand all entries of a previously uploaded ZIP into individual
     * {@code score_files} rows (US-2.3). Each entry gets its own disk write
     * and SHA-256; the original ZIP row remains untouched as the parent.
     *
     * <p>Zip-slip protection is re-applied here at extraction time — not just at
     * upload time — because the spec requires it in both phases.</p>
     *
     * @return list of {@link ZipEntryDto}s (one per file entry in the ZIP)
     */
    @Transactional
    public java.util.List<ZipEntryDto> expandZip(Long fileId, Long compositionId, Long bandId) {
        var maybeParent = scoreFileRepository.findById(fileId);
        if (maybeParent.isEmpty()) {
            throw new IllegalStateException("ScoreFile " + fileId + " not found");
        }
        final ScoreFile parent = maybeParent.get();

        // Band isolation: the composition must resolve in this band.
        requireOwned(compositionId, bandId);

        // The parent row must be a ZIP (or at least hold ZIP-format bytes).
        if (!"application/zip".equalsIgnoreCase(parent.getMimeType())) {
            throw new UploadValidator.UploadRejectedException(422,
                    "Plik o id " + fileId + " nie jest archiwum ZIP.");
        }

        byte[] zipBytes;
        try (var in = java.nio.file.Files.newInputStream(java.nio.file.Path.of(parent.getStoragePath()))) {
            zipBytes = in.readAllBytes();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Nie udało się odczytać pliku ZIP z dysku.", e);
        }

        ZipEntryExtractor.ExtractionResult result = zipEntryExtractor.extract(zipBytes, uploadValidator);

        // Persist each entry as its own score_files row.
        ScoreFile parentEntity = parent; // already loaded in this transaction
        for (ZipEntryExtractor.ExtractionResult.ZipEntryBytes entry : result.bytes()) {
            String mimeType = result.dtos().stream()
                    .filter(d -> d.entryName().equals(entry.entryName()))
                    .findFirst()
                    .map(ZipEntryDto::mimeType)
                    .orElse("application/octet-stream");

            boolean isPdf = "application/pdf".equalsIgnoreCase(mimeType);
            Integer pageCount = isPdf ? pdfPageCounter.extract(entry.content()) : null;

            ScoreFileStorage.StoredFile stored;
            String fileName = entryNameLeaf(entry.entryName());
            try {
                stored = storage.store(
                        new ByteArrayInputStream(entry.content()),
                        entry.sizeBytes(),
                        fileName);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException("Nie udało się zapisać pliku " + entry.entryName() + ".", e);
            }

            ScoreFile record = ScoreFile.forComposition(
                    parentEntity.getComposition(),
                    mimeType,
                    fileName,
                    entry.sizeBytes(),
                    stored.sha256(),
                    stored.storagePath(),
                    pageCount,
                    parentEntity.getId()); // parentFileId links back to the ZIP row

            scoreFileRepository.save(record);
        }

        return result.dtos();
    }

    private Composition requireOwned(Long id, Long bandId) {
        return compositionRepository.findByIdAndBandId(id, bandId)
                .orElseThrow(() -> new IllegalStateException(
                        "Composition " + id + " does not belong to band " + bandId));
    }

    private static String entryNameLeaf(String entryName) {
        if (entryName == null || entryName.isBlank()) return "file";
        java.nio.file.Path p = java.nio.file.Path.of(entryName);
        var fileName = p.getFileName();
        return fileName != null ? fileName.toString() : entryName;
    }
}
