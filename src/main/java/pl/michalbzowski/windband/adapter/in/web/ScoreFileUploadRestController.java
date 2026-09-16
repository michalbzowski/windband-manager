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
import pl.michalbzowski.windband.application.command.composition.ScoreFileUploadRequest;
import pl.michalbzowski.windband.application.dto.composition.ScoreFileDto;

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
    private final UploadedFileAssembler fileAssembler;

    public ScoreFileUploadRestController(ScoreFileCommandService commandService,
                                          UploadedFileAssembler fileAssembler) {
        this.commandService = commandService;
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
}
