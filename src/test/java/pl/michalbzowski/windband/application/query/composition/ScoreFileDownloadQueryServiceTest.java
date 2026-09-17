package pl.michalbzowski.windband.application.query.composition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * US-2.4 — read side of file download. Pins the two-layer band-isolation contract,
 * the composition-ownership guard, and the "missing on disk" path without starting a
 * Spring context (unit, Mockito).
 */
@ExtendWith(MockitoExtension.class)
class ScoreFileDownloadQueryServiceTest {

    @Mock private ScoreFileRepository scoreFileRepository;
    @Mock private BandQueryService bandQueryService;

    private ScoreFileDownloadQueryService service;

    private Long bandId;
    private Long otherBandId;
    private String onDisk;

    private final Path tmpRoot = Path.of(System.getProperty("java.io.tmpdir"), "sf-dl-unit-" + System.nanoTime());

    @BeforeEach
    void setUp() throws Exception {
        service = new ScoreFileDownloadQueryService(bandQueryService, scoreFileRepository);
        bandId = 1L;
        otherBandId = 2L;
        java.nio.file.Files.createDirectories(tmpRoot);
        Path file = tmpRoot.resolve("sample.pdf");
        onDisk = file.toAbsolutePath().toString();
        Files.writeString(file, "PDF-BYTES");
    }

    /** Build a stubbed Band BEFORE any Mockito stubbing is in progress (no nesting). */
    private Band newBand(Long id) {
        Band b = Mockito.mock(Band.class);
        lenient().when(b.getId()).thenReturn(id);
        return b;
    }

    /** Build a stubbed Composition with its band pre-resolved. Must NOT be called inside another stub's thenReturn(). */
    private Composition newComposition(Long id, Long owningBandId) {
        Band owner = newBand(owningBandId);
        Composition c = Mockito.mock(Composition.class);
        lenient().when(c.getId()).thenReturn(id);
        lenient().when(c.getBand()).thenReturn(owner);
        return c;
    }

    private ScoreFile newScoreFileRow(Long fileId, Composition composition, String storagePath,
                                      String name, String mime, long size) {
        ScoreFile row = Mockito.mock(ScoreFile.class);
        lenient().when(row.getId()).thenReturn(fileId);
        lenient().when(row.getComposition()).thenReturn(composition);
        lenient().when(row.getStoragePath()).thenReturn(storagePath);
        lenient().when(row.getOriginalName()).thenReturn(name);
        lenient().when(row.getMimeType()).thenReturn(mime);
        lenient().when(row.getSizeBytes()).thenReturn(size);
        return row;
    }

    @Test
    @DisplayName("open() on a file owned by the requested band + composition returns a readable stream")
    void openOwnFile_success() throws Exception {
        Band ownBand = newBand(bandId);
        Composition own = newComposition(100L, bandId);
        ScoreFile row = newScoreFileRow(42L, own, onDisk, "score.pdf", "application/pdf", 9L);
        when(bandQueryService.getRequiredBand(bandId)).thenReturn(ownBand);
        when(scoreFileRepository.findById(42L)).thenReturn(Optional.of(row));

        try (var handle = service.open(42L, 100L, bandId); var in = handle.stream()) {
            byte[] buf = new byte[64];
            int n = in.read(buf);
            assertThat(n).isEqualTo("PDF-BYTES".length());
            assertThat(new String(buf, 0, n)).containsIgnoringCase("pdf");
            assertThat(handle.metadata().fileId()).isEqualTo(42L);
            assertThat(handle.metadata().originalName()).isEqualTo("score.pdf");
            assertThat(handle.metadata().mimeType()).isEqualTo("application/pdf");
            assertThat(handle.metadata().sizeBytes()).isEqualTo(9L);
            assertThat(handle.metadata().isZip()).isFalse();
        }
    }

    @Test
    @DisplayName("open() with a ZIP file flags isZip=true (Content-Disposition will be 'attachment')")
    void openZip_isZipTrue() throws Exception {
        Band ownBand = newBand(bandId);
        Composition own = newComposition(100L, bandId);

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (var zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("x.pdf"));
            zos.write("hi".getBytes());
            zos.closeEntry();
        }
        byte[] zipBytes = baos.toByteArray();

        Path p = tmpRoot.resolve("parts.zip");
        java.nio.file.Files.write(p, zipBytes);
        ScoreFile row = newScoreFileRow(43L, own, p.toString(), "parts.zip", "application/zip", zipBytes.length);
        when(bandQueryService.getRequiredBand(bandId)).thenReturn(ownBand);
        when(scoreFileRepository.findById(43L)).thenReturn(Optional.of(row));

        try (var handle = service.open(43L, 100L, bandId)) {
            assertThat(handle.metadata().isZip()).isTrue();
        }
    }

    @Test
    @DisplayName("open() with an unknown band throws IllegalArgumentException (→ 400)")
    void openUnknownBand_badRequest() {
        when(bandQueryService.getRequiredBand(99L)).thenThrow(new IllegalArgumentException("Band not found: 99"));
        assertThatThrownBy(() -> service.open(1L, 100L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Band not found");
    }

    @Test
    @DisplayName("open() on a file whose composition belongs to another band throws IllegalStateException (→ 409)")
    void openCrossBand_conflict() {
        Band ownBand = newBand(bandId);
        Composition sameComposition = newComposition(100L, otherBandId); // file's composition is in another band
        String never = tmpRoot.resolve("cross-" + 7L + ".pdf").toString();
        ScoreFile row = newScoreFileRow(7L, sameComposition, never, "score.pdf", "application/pdf", 9L);
        when(bandQueryService.getRequiredBand(bandId)).thenReturn(ownBand);
        when(scoreFileRepository.findById(7L)).thenReturn(Optional.of(row));

        // Caller from band 1, file's composition in band 2, URL composition matches file → layer 2.3 fires.
        assertThatThrownBy(() -> service.open(7L, 100L, bandId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not belong to band " + bandId);
    }

    @Test
    @DisplayName("open() on a file that doesn't exist throws IllegalStateException (→ 409)")
    void openMissingFile_conflict() {
        Band ownBand = newBand(bandId);
        when(bandQueryService.getRequiredBand(bandId)).thenReturn(ownBand);
        when(scoreFileRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.open(404L, 100L, bandId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Score file 404 was not found");
    }

    @Test
    @DisplayName("open() on a file whose composition-id in URL doesn't match throws IllegalStateException (→ 409)")
    void openWrongComposition_conflict() {
        Band ownBand = newBand(bandId);
        Composition own = newComposition(100L, bandId);
        ScoreFile row = newScoreFileRow(44L, own, onDisk, "score.pdf", "application/pdf", 9L);
        when(bandQueryService.getRequiredBand(bandId)).thenReturn(ownBand);
        when(scoreFileRepository.findById(44L)).thenReturn(Optional.of(row));

        // URL carries a different composition id — must be rejected.
        assertThatThrownBy(() -> service.open(44L, 999L, bandId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not part of composition");
    }

    @Test
    @DisplayName("open() on a file whose recorded path is missing on disk throws ScoreFileMissingException (→ 410)")
    void openPathMissingOnDisk_gone() {
        Band ownBand = newBand(bandId);
        Composition own = newComposition(100L, bandId);
        String never = tmpRoot.resolve("never-" + 45L + ".pdf").toString();
        assertThat(Path.of(never)).doesNotExist();
        ScoreFile row = newScoreFileRow(45L, own, never, "score.pdf", "application/pdf", 9L);
        when(bandQueryService.getRequiredBand(bandId)).thenReturn(ownBand);
        when(scoreFileRepository.findById(45L)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.open(45L, 100L, bandId))
                .isInstanceOf(ScoreFileDownloadQueryService.ScoreFileMissingException.class)
                .hasMessageContaining("Nie udało się odczytać");
    }
}
