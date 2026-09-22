package pl.michalbzowski.windband.adapter.in.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pl.michalbzowski.windband.application.query.composition.ScoreFileThumbQueryService;

/**
 * US-7.9 - thin REST endpoint rendering one page of a PDF score file to a JPEG header
 * preview: GET /bands/{bandId}/compositions/{compositionId}/files/{fileId}/thumb.
 *
 * <p>Band isolation is enforced inside the query service (via
 * {@code ScoreFileDownloadQueryService.open}); the controller itself stays stateless.
 *
 * <p>Error handling is delegated to {@link GlobalExceptionHandler} and the documented exception
 * contract of the query service: 400 non-positive page, 404 unknown page, 409 cross-band,
 * 410 unreadable on disk, 422 not a parseable PDF (e.g. the ZIP parent). There is intentionally
 * NO blanket catch here - swallowing exceptions would collapse these distinct failures into one
 * ambiguous status and hide misconfiguration from tests.
 *
 * <p>The rendered JPEG is deterministic for a given (fileId, page, width) triple, since a file
 * only changes when a fresh upload gets a new id. The response therefore carries a weak ETag
 * keyed on exactly that triple plus a bounded {@code Cache-Control max-age} so browsers can hold
 * the bitstream locally instead of re-hitting PDFBox on every repeat load.
 */
@RestController
public class ScoreFileThumbRestController {

    private final ScoreFileThumbQueryService thumbService;

    public ScoreFileThumbRestController(ScoreFileThumbQueryService thumbService) {
        this.thumbService = thumbService;
    }

    @GetMapping("/bands/{bandId}/compositions/{compositionId}/files/{fileId}/thumb")
    public ResponseEntity<byte[]> thumb(
            @PathVariable Long bandId,
            @PathVariable Long compositionId,
            @PathVariable Long fileId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "400") int width) {
        byte[] jpeg = thumbService.renderThumbnail(fileId, compositionId, bandId, page, width);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_JPEG);
        headers.setContentLength(jpeg.length);
        // Revalidate with the server on each load; browser may keep the bitstream for a day.
        headers.setCacheControl("public, max-age=86400");
        // Weak ETag: token must be quoted (W/"<token>") or Spring rejects the header.
        String etag = "W/\"thumb-" + fileId + "-p" + page + "-w" + width + "\"";
        headers.setETag(etag);
        return ResponseEntity.ok().headers(headers).body(jpeg);
    }
}
