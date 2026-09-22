package pl.michalbzowski.windband.application.query.composition;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import pl.michalbzowski.windband.application.query.composition.ScoreFileDownloadQueryService.Download;

/** Extracts the page count of a PDF document from in-memory bytes. */
@Service
public class ScoreFileThumbQueryService {

  private final ScoreFileDownloadQueryService downloadService;

  public ScoreFileThumbQueryService(ScoreFileDownloadQueryService downloadService) {
    this.downloadService = downloadService;
  }

  public byte[] renderThumbnail(Long fileId, Long compositionId, Long bandId, int pageNumber, int widthPx)
      throws Exception {
    if (pageNumber < 1) {
      throw new IllegalArgumentException("pageNumber must be >= 1");
    }
    Download handle = downloadService.open(fileId, compositionId, bandId);
    try (InputStream in = handle.stream();
         PDDocument doc = Loader.loadPDF(in.readAllBytes())) {
      int total = doc.getNumberOfPages();
      if (pageNumber > total) {
        throw new IllegalArgumentException("pageNumber out of range");
      }
      PDFRenderer renderer = new PDFRenderer(doc);
      double scale = Math.max(1.0, widthPx / 595.0);
      BufferedImage img = renderer.renderImageWithDPI(pageNumber - 1,
          150 * (float) (scale / 2.0));
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ImageIO.write(img, "jpeg", baos);
      return baos.toByteArray();
    }
  }
}
