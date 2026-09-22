package pl.michalbzowski.windband.application.query.composition;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import pl.michalbzowski.windband.application.query.composition.ScoreFileDownloadQueryService.Download;
import java.util.LinkedHashMap;

/**
 * US-7.9 — header-preview render path (design option B): renders a single page of a PDF score
 * file to JPEG at a clamped viewport width and serves it from an in-memory LRU cache keyed on
 * (band, composition, file, page, width), so repeated thumbnail requests do not re-parse the
 * document — the spec's "cache so the endpoint isn't abused by looping requests" guard.
 *
 * <p>Status contract of {@link #renderThumbnail}, mapped by the adapter layer's global exception
 * handler (see {@code GlobalExceptionHandler}):
 * <ul>
 *   <li>{@link IllegalArgumentException} — non-positive {@code page} parameter (→ HTTP 400)</li>
 *   <li>{@code IllegalStateException} from the band-ownership gate in
 *       {@link ScoreFileDownloadQueryService#open} (→ HTTP 409)</li>
 *   <li>{@code ScoreFileMissingException} — recorded path unreadable on disk (→ HTTP 410)</li>
 *   <li>{@link NotAPdfException} — stored file is not a parseable PDF, e.g. the ZIP parent (→ HTTP 422)</li>
 *   <li>{@link UnknownPageException} — requested page does not exist in the document (→ HTTP 404)</li>
 * </ul>
 */
@Service
public class ScoreFileThumbQueryService {

    /** Upper bound of kept rendered thumbnails; oldest entry is evicted first (access-order LRU). */
    private static final int MAX_CACHED_THUMBNAILS = 64;
    /** Width clamp — a malicious ?width=100000 must not spike render memory (spec step 2). */
    private static final int MIN_WIDTH_PX = 64;
    private static final int MAX_WIDTH_PX = 2000;

    private final ScoreFileDownloadQueryService downloadService;
    // Access-order LRU; every cache mutation happens inside the synchronized blocks below.
    private final LinkedHashMap<ThumbKey, byte[]> cache = new LinkedHashMap<>(16, 0.75f, true);

    public ScoreFileThumbQueryService(ScoreFileDownloadQueryService downloadService) {
        this.downloadService = downloadService;
    }

    /**
     * Render page {@code pageNumber} (1-based) of the given file to a JPEG at ~{@code widthPx} wide.
     *
     * @throws IllegalArgumentException if {@code pageNumber < 1}
     * @throws IllegalStateException on cross-band / ownership violations (→ HTTP 409)
     * @throws ScoreFileMissingException when the recorded file is unreadable on disk (→ HTTP 410)
     * @throws NotAPdfException when the stored bytes are not a parseable PDF (→ HTTP 422)
     * @throws UnknownPageException when the page does not exist in the document (→ HTTP 404)
     */
    public byte[] renderThumbnail(Long fileId, Long compositionId, Long bandId, int pageNumber, int widthPx) {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be >= 1");
        }
        int width = Math.max(MIN_WIDTH_PX, Math.min(MAX_WIDTH_PX, widthPx));
        ThumbKey key = new ThumbKey(bandId, compositionId, fileId, pageNumber, width);
        byte[] cached = cacheGet(key);
        if (cached != null) {
            return cached;
        }

        // Band/composition ownership is enforced inside open() and must run on EVERY call —
        // a cache key alone would let a foreign-band request replay another band's thumbnail.
        Download handle = downloadService.open(fileId, compositionId, bandId);
        String mimeType = handle.metadata().mimeType();
        String originalName = handle.metadata().originalName();
        byte[] pdfBytes;
        try (handle) {
            try (InputStream in = handle.stream()) {
                pdfBytes = in.readAllBytes();
            }
        } catch (IOException e) {
            throw new ScoreFileDownloadQueryService.ScoreFileMissingException(
                    "Nie udało się odczytać pliku " + fileId + " do podglądu.", e);
        }

        if (mimeType != null && !"application/pdf".equalsIgnoreCase(mimeType)) {
            throw new NotAPdfException("Plik \"" + originalName + "\" nie jest PDF-em (typ: "
                    + mimeType + ") — miniatura niedostępna.");
        }

        byte[] jpeg;
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int totalPages = doc.getNumberOfPages();
            if (pageNumber > totalPages) {
                throw new UnknownPageException("Strona " + pageNumber + " nie istnieje — ten plik ma tylko "
                        + totalPages + " stron(y).");
            }
            PDFRenderer renderer = new PDFRenderer(doc);
            double scale = Math.max(1.0, width / 595.0);
            BufferedImage img = renderer.renderImageWithDPI(pageNumber - 1, 150f * (float) (scale / 2.0));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "jpeg", baos);
            jpeg = baos.toByteArray();
        } catch (IOException e) {
            throw new NotAPdfException("Nie można odczytać pliku jako PDF — miniatura niedostępna.", e);
        }

        cachePut(key, jpeg);
        return jpeg;
    }

    private byte[] cacheGet(ThumbKey key) {
        synchronized (cache) {
            return cache.get(key);
        }
    }

    private void cachePut(ThumbKey key, byte[] value) {
        synchronized (cache) {
            cache.put(key, value);
            while (cache.size() > MAX_CACHED_THUMBNAILS) {
                cache.remove(cache.keySet().iterator().next());
            }
        }
    }

    /** Cache key — deliberately scoped to the caller's band + composition (no cross-band replay). */
    private record ThumbKey(Long bandId, Long compositionId, Long fileId, int page, int width) {
    }

    /** The requested page does not exist in the document (→ HTTP 404 via the global handler). */
    public static class UnknownPageException extends RuntimeException {
        public UnknownPageException(String message) {
            super(message);
        }
    }

    /** The stored file cannot be rendered as a PDF — ZIP parent, corrupt bytes (→ HTTP 422). */
    public static class NotAPdfException extends RuntimeException {
        public NotAPdfException(String message) {
            super(message);
        }

        public NotAPdfException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
