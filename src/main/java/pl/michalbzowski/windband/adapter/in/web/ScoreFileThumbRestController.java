package pl.michalbzowski.windband.adapter.in.web;

import java.util.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** US-7.9 – thin REST endpoint for rendering a single PDF page thumbnail as JPEG. */
@RestController
public class ScoreFileThumbRestController {

  private final pl.michalbzowski.windband.application.query.composition.ScoreFileThumbQueryService
      thumbService;

  public ScoreFileThumbRestController(
      pl.michalbzowski.windband.application.query.composition.ScoreFileThumbQueryService thumbService) {
    this.thumbService = thumbService;
  }

  @GetMapping("/bands/{bandId}/compositions/{compositionId}/files/{fileId}/thumb")
  public ResponseEntity<byte[]> thumb(
      @PathVariable Long bandId,
      @PathVariable Long compositionId,
      @PathVariable Long fileId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "400") int width) {
    try {
      byte[] jpeg = thumbService.renderThumbnail(fileId, compositionId, bandId, page, width);
      HttpHeaders h = new HttpHeaders();
      h.setContentType(MediaType.IMAGE_JPEG);
      h.setCacheControl("max-age=86400");
      return ResponseEntity.ok().headers(h).body(jpeg);
    } catch (IllegalArgumentException iae) {
      if ("pageNumber must be >= 1".equals(iae.getMessage())
          || iae.getMessage().contains("out of range")) {
        return ResponseEntity.badRequest().build();
      }
      throw iae;
    } catch (Exception e) {
      return ResponseEntity.status(422).build();
    }
  }
}
