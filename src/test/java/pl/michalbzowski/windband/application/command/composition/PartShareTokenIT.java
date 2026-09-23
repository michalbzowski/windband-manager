package pl.michalbzowski.windband.application.command.composition;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.application.query.composition.PartLinkQueryService;
import pl.michalbzowski.windband.application.query.composition.PublicPartLinkQueryService;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.PartShareTokenRepository;
import pl.michalbzowski.windband.domain.composition.PartSource;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * US-7.11 — the public voice link over the REAL Spring context (shared Testcontainers PG):
 * token mint is lazy + idempotent, the public read returns a PHYSICALLY SLICED PDF (page count
 * asserted on the served bytes — this is the AC the old whole-PDF link failed), rotation
 * revokes the old credential, and part deletion cascades the token away.
 */
class PartShareTokenIT extends BaseIntegrationTest {

    @Autowired private BandRepository bandRepository;
    @Autowired private InstrumentRepository instrumentRepository;
    @Autowired private CompositionRepository compositionRepository;
    @Autowired private CompositionInstrumentRepository partRepository;
    @Autowired private ScoreFileRepository scoreFileRepository;
    @Autowired private PartShareTokenRepository tokenRepository;
    @Autowired private PartShareTokenCommandService tokenService;
    @Autowired private PublicPartLinkQueryService publicService;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private Path tempDir;
    private Long myCompositionId;

    @BeforeEach
    void makeTempDir() throws Exception {
        tempDir = Files.createTempDirectory("part-share-it-");
    }

    @AfterEach
    void cleanup() {
        // Children → parent. Tokens ride composition_instruments' ON DELETE CASCADE, but be
        // explicit so the wipe passes even if the cascade annotation drifts from the DDL.
        if (myCompositionId != null) {
            jdbcTemplate.update(
                    "DELETE FROM part_share_tokens WHERE part_id IN (SELECT id FROM composition_instruments WHERE composition_id = ?)",
                    myCompositionId);
            jdbcTemplate.update("DELETE FROM composition_instruments WHERE composition_id = ?", myCompositionId);
            jdbcTemplate.update("DELETE FROM score_files WHERE composition_id = ?", myCompositionId);
            jdbcTemplate.update("DELETE FROM compositions WHERE id = ?", myCompositionId);
            myCompositionId = null;
        }
    }

    /** Band-1 seed fixture "Flet"; reused across classes (shared DB), never deleted here. */
    private Instrument flute() {
        return instrumentRepository.findByNameAndBandId("Flet", 1L)
                .orElseGet(() -> instrumentRepository.save(
                        Instrument.create("Flet", bandRepository.findById(1L).orElseThrow())));
    }

    /** composition + a 5-page PDF row + a part covering pages 2–3 of it. @return the part. */
    private CompositionInstrument seedPart() throws Exception {
        var band = bandRepository.findById(1L).orElseThrow();
        Composition c = compositionRepository.save(Composition.create("US-7.11 IT", null, null, null, band));
        myCompositionId = c.getId();

        byte[] fivePagePdf = TestPdfBuilder.generate(5);
        Path file = tempDir.resolve("score.pdf");
        Files.write(file, fivePagePdf);
        ScoreFile sf = scoreFileRepository.save(ScoreFile.forComposition(
                c, "application/pdf", "score.pdf", (long) fivePagePdf.length,
                "sha-" + System.nanoTime(), file.toAbsolutePath().toString(), 5));

        return partRepository.save(CompositionInstrument.forComposition(
                c, flute(), "Flet 1", 2, 3, null, sf, PartSource.MANUAL, 1.0));
    }

    private static int pageCount(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    @Test
    @DisplayName("tokenFor mints lazily and is idempotent; openByToken serves a 2-PAGE slice of the 5-page score")
    void lazyMint_publicReadIsPhysicallySliced() throws Exception {
        CompositionInstrument part = seedPart();

        UUID first = tokenService.tokenFor(part.getId(), "it@test");
        UUID again = tokenService.tokenFor(part.getId(), "it@test");
        assertThat(again).isEqualTo(first);   // stable link, not per-read re-mint

        PublicPartLinkQueryService.PublicPart served = publicService.openByToken(first);
        assertThat(served.pdfBytes()).isNotEmpty();
        // THE US-7.11 acceptance: the old link streamed the whole 5-page book; this must be 2.
        assertThat(pageCount(served.pdfBytes())).isEqualTo(2);
        assertThat(served.mimeType()).isEqualTo("application/pdf");
        assertThat(served.compositionTitle()).isEqualTo("US-7.11 IT");
        assertThat(served.roleText()).isEqualTo("Flet 1");
        assertThat(served.pageFrom()).isEqualTo(2);
        assertThat(served.pageTo()).isEqualTo(3);
    }

    @Test
    @DisplayName("rotate swaps the credential: the old token stops resolving, the new one serves the slice")
    void rotate_revokesTheOldLink() throws Exception {
        CompositionInstrument part = seedPart();
        UUID oldToken = tokenService.tokenFor(part.getId(), "it@test");

        UUID fresh = tokenService.rotate(part.getId(), "rot@test");
        assertThat(fresh).isNotEqualTo(oldToken);
        assertThat(tokenRepository.findByToken(oldToken)).isEmpty();

        assertThat(pageCount(publicService.openByToken(fresh).pdfBytes())).isEqualTo(2);
        assertThatThrownBy(() -> publicService.openByToken(oldToken))
                .isInstanceOf(PublicPartLinkQueryService.TokenNotFoundException.class);
    }

    @Test
    @DisplayName("unknown token → TokenNotFoundException (uniform 404 — no existence oracle)")
    void unknownToken_failsClosed() {
        assertThatThrownBy(() -> publicService.openByToken(UUID.randomUUID()))
                .isInstanceOf(PublicPartLinkQueryService.TokenNotFoundException.class);
    }

    @Test
    @DisplayName("file passes the covering gate by pageCount but bytes cannot be sliced → 409 (NoCoveringFile), not a misleading 404")
    void slicesToNull_isConflictNotNotFound() throws Exception {
        // US-7.11 regression guard: metadata says the file covers pages 2–3 (pageCount=5), so it
        // clears the covering gate — but the on-disk bytes are not a PDF, so the slice comes back
        // null. The recipient should learn "not covered right now" (409), not a false "link
        // expired" (404) that would suggest re-sharing fixes it, when nothing they can resend helps.
        CompositionInstrument part = seedPart();
        UUID token = tokenService.tokenFor(part.getId(), "it@test");

        // Overwrite the served file in place; the DB pageCount=5 is untouched, so the gate passes.
        Files.writeString(tempDir.resolve("score.pdf"), "garbage — not a PDF");

        assertThatThrownBy(() -> publicService.openByToken(token))
                .isInstanceOf(PartLinkQueryService.NoCoveringFileException.class);
    }

    @Test
    @DisplayName("deleting the part cascades its token away (DB ON DELETE CASCADE)")
    void partDelete_cascadesToken() throws Exception {
        CompositionInstrument part = seedPart();
        UUID token = tokenService.tokenFor(part.getId(), "it@test");

        partRepository.delete(part);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM part_share_tokens WHERE token = ?", Integer.class, token))
                .isZero();
    }

    @Test
    @DisplayName("a part whose range no uploaded file covers → 409 semantics (NoCoveringFileException)")
    void noCoveringFile_isConflictNotNotFound() throws Exception {
        // Legacy (unbound) part asking for pages 4–5 of a 2-page PDF: the domain factory's
        // scoreFile guard does not apply to unbound rows, so the SERVICE's covering-file gate
        // is the one that rejects — proving 409 semantics, not a silent 404.
        var band = bandRepository.findById(1L).orElseThrow();
        Composition c = compositionRepository.save(Composition.create("US-7.11 NoCover", null, null, null, band));
        myCompositionId = c.getId();
        byte[] twoPage = TestPdfBuilder.generate(2);
        Path file = tempDir.resolve("two.pdf");
        Files.write(file, twoPage);
        scoreFileRepository.save(ScoreFile.forComposition(
                c, "application/pdf", "two.pdf", (long) twoPage.length,
                "sha-" + System.nanoTime(), file.toAbsolutePath().toString(), 2));
        CompositionInstrument part = partRepository.save(CompositionInstrument.forComposition(
                c, flute(), "Flet 2", 4, 5, null, PartSource.MANUAL, 1.0));

        UUID token = tokenService.tokenFor(part.getId(), "it@test");
        assertThatThrownBy(() -> publicService.openByToken(token))
                .isInstanceOf(PartLinkQueryService.NoCoveringFileException.class);
    }
}
