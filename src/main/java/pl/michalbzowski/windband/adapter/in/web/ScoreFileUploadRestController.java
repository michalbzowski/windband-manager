package pl.michalbzowski.windband.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.michalbzowski.windband.application.command.composition.ScoreFileCommandService;
import pl.michalbzowski.windband.application.command.composition.ScoreFileReplaceCommandService;
import pl.michalbzowski.windband.application.command.composition.ScoreFileUploadRequest;
import pl.michalbzowski.windband.application.dto.composition.ScoreFileDto;
import pl.michalbzowski.windband.application.dto.composition.ZipEntryDto;

/**
 * US-2.1 REST endpoint — multipart POST for one score file attached to a
 * specific composition, band-scoped so a member of band B cannot upload for a
 * composition owned by band A.
 *
 * <p>This class lives in the adapter layer and is allowed to reference
 * {@code org.springframework.web..} types (the ArchitectureTest rule only
 * forbids such dependencies from the application layer). It converts the
 * multipart part into a web-agnostic {@link ScoreFileUploadRequest} before
 * delegating to the application service.</p>
 *
 * <p>Rejection mapping (via {@code GlobalExceptionHandler}):</p>
 * <ul>
 *   <li>MIME not in whitelist → 415</li>
 *   <li>Size exceeds per-type cap → 413</li>
 *   <li>ZIP-slip / unsafe entry → 422</li>
 *   <li>I/O failure → 500 (global handler wraps UncheckedIOException)</li>
 * </ul>
 */
@RestController
@RequestMapping("/bands/{bandId}/compositions/{compositionId}/files")
public class ScoreFileUploadRestController {

    private final ScoreFileCommandService commandService;
    private final ScoreFileReplaceCommandService replaceCommandService;
    private final UploadedFileAssembler fileAssembler;

    public ScoreFileUploadRestController(ScoreFileCommandService commandService,
                                          ScoreFileReplaceCommandService replaceCommandService,
                                          UploadedFileAssembler fileAssembler) {
        this.commandService = commandService;
        this.replaceCommandService = replaceCommandService;
        this.fileAssembler = fileAssembler;
    }

    @PostMapping
    public ResponseEntity<ScoreFileDto> upload(
            @PathVariable("bandId") Long bandId,
            @PathVariable("compositionId") Long compositionId,
            @RequestPart(name = "file", required = true) MultipartFile file) {
        ScoreFileUploadRequest request = fileAssembler.toRequest(file);
        ScoreFileDto dto = commandService.upload(request, compositionId, bandId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * US-7.15 — replace the CONTENT of an existing score file in place ("Wymień plik"): the
     * librarian edited the piece and re-exported the PDF. The row keeps its id, so every part
     * mapping stays intact; the command service clears the stale verification audit, demotes
     * a READY composition to DRAFT and evicts cached thumbnails. Returns the updated row.
     *
     * <p>Extra rejection mapping on top of the upload one: ZIP parent → 422, MIME different
     * from the row's type → 422, new page count breaking a bound mapping → 422.</p>
     */
    @PostMapping("/{fileId}/replace")
    public ResponseEntity<ScoreFileDto> replace(
            @PathVariable("bandId") Long bandId,
            @PathVariable("compositionId") Long compositionId,
            @PathVariable("fileId") Long fileId,
            @RequestPart(name = "file", required = true) MultipartFile file) {
        ScoreFileUploadRequest request = fileAssembler.toRequest(file);
        ScoreFileDto dto = replaceCommandService.replace(request, fileId, compositionId, bandId);
        return ResponseEntity.ok(dto);
    }

    /**
     * US-2.3 — expand a previously uploaded ZIP into individual score-file rows.
     * Returns the list of extracted entries (file name, MIME, size) so the UI can
     * display them for instrument mapping in Epic 4.
     */
    @PostMapping("/{fileId}/expand")
    public ResponseEntity<java.util.List<ZipEntryDto>> expandZip(
            @PathVariable("bandId") Long bandId,
            @PathVariable("compositionId") Long compositionId,
            @PathVariable("fileId") Long fileId) {
        java.util.List<ZipEntryDto> entries = commandService.expandZip(fileId, compositionId, bandId);
        return ResponseEntity.ok(entries);
    }
}
