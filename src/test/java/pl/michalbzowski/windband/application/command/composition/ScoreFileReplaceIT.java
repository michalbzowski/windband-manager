package pl.michalbzowski.windband.application.command.composition;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.application.command.composition.UploadValidator.UploadRejectedException;
import pl.michalbzowski.windband.application.dto.composition.ScoreFileDto;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.CompositionStatus;
import pl.michalbzowski.windband.domain.composition.PartSource;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * US-7.15 — end-to-end (Testcontainers PostgreSQL, real storage) proof of the feature's core
 * promise: after a content swap the SAME score_files row serves the NEW pages, every voice
 * mapping keeps pointing at it untouched (id + FK + page range), and exactly the stale claims
 * fall — the part's verification audit and the composition's READY ("Gotowy") status, which
 * returns to DRAFT ("Szkic"). Also pins the range-shrink guard against the real DB rows.
 */
class ScoreFileReplaceIT extends BaseIntegrationTest {

    @Autowired private BandRepository bandRepository;
    @Autowired private CompositionRepository compositionRepository;
    @Autowired private ScoreFileRepository scoreFileRepository;
    @Autowired private CompositionInstrumentRepository instrumentPartRepository;
    @Autowired private InstrumentRepository instrumentRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ScoreFileCommandService uploadService;
    @Autowired private ScoreFileReplaceCommandService replaceService;

    private Band band;
    private Composition composition;
    private Instrument trumpet;

    @BeforeEach
    void setUp() {
        band = bandRepository.findById(1L)
                .orElseGet(() -> bandRepository.save(Band.create("Replace IT Band", "replace-it-" + System.nanoTime())));
        composition = Composition.create("Replace IT " + System.nanoTime(), "opis", "autor", null, band);
        composition = compositionRepository.save(composition);
        trumpet = instrumentRepository.save(Instrument.create("Trąbka Bb " + System.nanoTime(), band));
    }

    /**
     * This class COMMITs (like the other non-transactional composition ITs) — and it is the first
     * to leave {@code composition_instruments.score_file_id} FKs pointing at committed
     * {@code score_files} rows. The shared-container cleanup in
     * {@code CompositionCommandServiceTest} / {@code CompositionRepositoryIT} /
     * {@code CompositionQueryServiceIT} runs a bare {@code DELETE FROM score_files}, which the FK
     * then rejects. Delete our own children first, then files, then this test's rows.
     */
    @AfterEach
    void cleanUpOwnRows() {
        if (composition.getId() != null) {
            jdbcTemplate.update("DELETE FROM composition_instruments WHERE composition_id = ?", composition.getId());
            jdbcTemplate.update("DELETE FROM score_files WHERE composition_id = ?", composition.getId());
            jdbcTemplate.update("DELETE FROM compositions WHERE id = ?", composition.getId());
        }
        if (trumpet.getId() != null) {
            jdbcTemplate.update("DELETE FROM instruments WHERE id = ?", trumpet.getId());
        }
    }

    private ScoreFileDto uploadPdf(String name, int pages) {
        return uploadService.upload(new ScoreFileUploadRequest(name, "application/pdf",
                TestPdfBuilder.generate(pages)), composition.getId(), band.getId());
    }

    private CompositionInstrument bindVerifiedPart(String role, int from, int to, Long fileId) {
        ScoreFile file = scoreFileRepository.findById(fileId).orElseThrow();
        CompositionInstrument part = CompositionInstrument.forComposition(
                composition, trumpet, role, from, to, null, file, PartSource.MANUAL, 1.0);
        part.verify("librarian@example.com", Instant.now());
        return instrumentPartRepository.save(part);
    }

    @Test
    @DisplayName("replace() keeps the row id + mapping FK, serves new bytes, clears the audit pair and demotes READY→DRAFT")
    void replace_preservesMapping_demotesDraft_invalidatesAudit() throws Exception {
        ScoreFileDto uploaded = uploadPdf("marsz_v1.pdf", 2);
        String oldPath = uploadedFile(uploaded.fileId()).getStoragePath();
        CompositionInstrument part = bindVerifiedPart("Trąbka 1", 1, 2, uploaded.fileId());
        composition.markReady();
        compositionRepository.save(composition);

        byte[] v2 = TestPdfBuilder.generate(3);
        ScoreFileDto replaced = replaceService.replace(
                new ScoreFileUploadRequest("marsz_v2.pdf", "application/pdf", v2),
                uploaded.fileId(), composition.getId(), band.getId());

        // Same row — every FK in composition_instruments still resolves through it.
        assertThat(replaced.fileId()).isEqualTo(uploaded.fileId());
        ScoreFile row = uploadedFile(uploaded.fileId());
        assertThat(row.getOriginalName()).isEqualTo("marsz_v2.pdf");
        assertThat(row.getPageCount()).isEqualTo(3);
        assertThat(row.getMimeType()).isEqualTo("application/pdf");
        assertThat(row.getReplacedAt()).isNotNull();

        // Download path: new bytes on disk at the new location; the superseded copy is gone.
        assertThat(Path.of(row.getStoragePath())).exists();
        assertThat(Files.readAllBytes(Path.of(row.getStoragePath()))).isEqualTo(v2);
        assertThat(Path.of(oldPath)).doesNotExist();

        // Mapping preserved, verification stale → cleared (re-verify gate re-arms).
        CompositionInstrument after = instrumentPartRepository.findById(part.getId()).orElseThrow();
        assertThat(after.getScoreFile()).isNotNull();
        assertThat(after.getScoreFile().getId()).isEqualTo(uploaded.fileId());
        assertThat(after.getPageFrom()).isEqualTo(1);
        assertThat(after.getPageTo()).isEqualTo(2);
        assertThat(after.getVerifiedAt()).isNull();
        assertThat(after.getVerifiedBy()).isNull();

        // "Gotowy" was a claim about the old notation → back to "Szkic".
        assertThat(compositionRepository.findByIdAndBandId(composition.getId(), band.getId())
                .orElseThrow().getStatus()).isEqualTo(CompositionStatus.DRAFT);

        // No ghost rows: replacing did NOT create a second file.
        List<ScoreFile> rows = scoreFileRepository.findAllByComposition(composition);
        assertThat(rows).hasSize(1);
    }

    @Test
    @DisplayName("replace() refuses a smaller PDF when a bound voice range points past its last page — mapping and status untouched")
    void replace_shrinkBelowBoundRange_rejectedBeforeAnyMutation() {
        ScoreFileDto uploaded = uploadPdf("marsz_v1.pdf", 5);
        CompositionInstrument part = bindVerifiedPart("Trąbka 1", 3, 5, uploaded.fileId());
        composition.markReady();
        compositionRepository.save(composition);

        assertThatThrownBy(() -> replaceService.replace(
                new ScoreFileUploadRequest("marsz_skrocony.pdf", "application/pdf",
                        TestPdfBuilder.generate(2)),
                uploaded.fileId(), composition.getId(), band.getId()))
                .isInstanceOf(UploadRejectedException.class)
                .hasMessageContaining("Trąbka 1");

        ScoreFile row = uploadedFile(uploaded.fileId());
        assertThat(row.getPageCount()).isEqualTo(5);
        assertThat(row.getReplacedAt()).isNull();
        assertThat(instrumentPartRepository.findById(part.getId()).orElseThrow().getVerifiedAt()).isNotNull();
        assertThat(compositionRepository.findByIdAndBandId(composition.getId(), band.getId())
                .orElseThrow().getStatus()).isEqualTo(CompositionStatus.READY);
    }

    private ScoreFile uploadedFile(Long fileId) {
        return scoreFileRepository.findById(fileId).orElseThrow();
    }
}
