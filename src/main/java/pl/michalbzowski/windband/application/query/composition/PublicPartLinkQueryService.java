package pl.michalbzowski.windband.application.query.composition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.composition.PdfPageExtractor;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.PartShareToken;
import pl.michalbzowski.windband.domain.composition.PartShareTokenRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;

/**
 * US-7.11 — read path behind the PUBLIC voice link {@code GET /public/parts/{token}}.
 *
 * <p>Resolves an opaque UUIDv4 token → the part's live page range → a real, physical
 * PDF slice containing ONLY {@code pageFrom..pageTo}. The whole thing happens inside one
 * {@code REQUIRES_NEW} transaction: the PDF is read from disk and sliced eagerly, so the
 * adapter layer receives finished bytes plus scalar metadata and never touches a lazy
 * association after the session closes (the {@code partsFor} EL1008E lesson — projections
 * over entities).</p>
 *
 * <p><b>No band-id input at all.</b> The token IS the authorization; the part/band chain is
 * traversed here only to locate the file, never validated against a caller-supplied id.
 * Unknown, garbage or revoked token → {@link TokenNotFoundException} (HTTP 404). A part whose
 * range matches no uploaded PDF → {@link NoCoveringFileException} (HTTP 409, same contract as
 * the authenticated US-7.10 path).</p>
 */
@Service
public class PublicPartLinkQueryService {

    private final PartShareTokenRepository tokenRepository;
    private final ScoreFileRepository scoreFileRepository;
    private final PdfPageExtractor pageExtractor;

    public PublicPartLinkQueryService(PartShareTokenRepository tokenRepository,
                                      ScoreFileRepository scoreFileRepository,
                                      PdfPageExtractor pageExtractor) {
        this.tokenRepository     = Objects.requireNonNull(tokenRepository);
        this.scoreFileRepository = Objects.requireNonNull(scoreFileRepository);
        this.pageExtractor       = Objects.requireNonNull(pageExtractor);
    }

    /** Flat result for the adapter: sliced bytes + scalar metadata. No entities escape. */
    public record PublicPart(String compositionTitle, String roleText, int pageFrom, int pageTo,
                             String mimeType, byte[] pdfBytes) {}

    /** Raised for unknown/garbage/rotated tokens — mapped to HTTP 404 (fail closed, uniform). */
    public static class TokenNotFoundException extends RuntimeException {
        public TokenNotFoundException() { super("Ten link do głosu nie istnieje lub wygasł."); }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public PublicPart openByToken(UUID token) {
        if (token == null) {
            throw new TokenNotFoundException();
        }
        PartShareToken share = tokenRepository.findByToken(token)
                .orElseThrow(TokenNotFoundException::new);

        CompositionInstrument part = share.getPart();
        int from = part.getPageFrom();
        int to   = part.getPageTo();

        // US-7.14: an explicit part→file binding wins; legacy rows fall back to the same
        // "largest PDF whose pageCount covers the range" resolution as PartLinkQueryService.
        ScoreFile bound = part.getScoreFile();
        List<ScoreFile> candidates = scoreFileRepository.findAllByComposition(part.getComposition());
        ScoreFile best;
        if (bound != null && bound.getPageCount() != null && bound.getPageCount() >= to) {
            best = bound;
        } else {
            java.util.Comparator<ScoreFile> byIdDesc = (a, b) -> Long.compare(b.getId(), a.getId());
            best = candidates.stream()
                    .filter(f -> f.getId() != null)
                    .sorted(byIdDesc)
                    .filter(f -> f.getPageCount() != null && f.getPageCount() >= to)
                    .findFirst()
                    .orElseThrow(() -> new PartLinkQueryService.NoCoveringFileException(
                            "Żaden wgrany plik nut nie obejmuje stron " + from + "–" + to
                                    + " utworu o id=" + part.getComposition().getId()));
        }

        byte[] full;
        Path path = Path.of(best.getStoragePath());
        if (!Files.exists(path) || !Files.isReadable(path)) {
            // A stale DB row pointing at vanished bytes is indistinguishable from a revoked
            // link from the recipient's point of view — same 404, nothing leaks about band state.
            throw new TokenNotFoundException();
        }
        try (InputStream in = Files.newInputStream(path)) {
            full = in.readAllBytes();
        } catch (IOException e) {
            throw new TokenNotFoundException();
        }

        byte[] slice = pageExtractor.extractPages(full, from, to);
        if (slice == null) {
            // Corrupt PDF / range beyond real page count — fail closed the same way.
            throw new TokenNotFoundException();
        }

        String role = (part.getInstrumentRole() == null || part.getInstrumentRole().isBlank())
                ? "głos" : part.getInstrumentRole();
        return new PublicPart(part.getComposition().getTitle(), role, from, to,
                Objects.requireNonNullElse(best.getMimeType(), "application/pdf"),
                slice);
    }
}
