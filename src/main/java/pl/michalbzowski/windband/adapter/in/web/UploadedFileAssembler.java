package pl.michalbzowski.windband.adapter.in.web;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import pl.michalbzowski.windband.application.command.composition.ScoreFileUploadRequest;

/**
 * Adapter-layer conversion from the Servlet/Spring Web multipart request to a
 * web-agnostic value object in the application layer. Keeps the application
 * layer free of Spring Web dependencies (ArchitectureTest enforces this rule).
 */
@Component
class UploadedFileAssembler {

    /**
     * Reads the multipart part into an immutable {@link ScoreFileUploadRequest}.
     * Throws an {@link IllegalArgumentException} if the file is null or cannot be read.
     */
    ScoreFileUploadRequest toRequest(MultipartFile file) {
        if (file == null) {
            throw new IllegalArgumentException("Brak pliku w żądaniu");
        }
        try {
            byte[] bytes = file.getBytes();
            String mime = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
            return new ScoreFileUploadRequest(file.getOriginalFilename(), mime, bytes);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Nie udało się odczytać pliku: " + e.getMessage(), e);
        }
    }
}
