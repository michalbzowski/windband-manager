package pl.michalbzowski.windband.application.command.composition;

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
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * US-2.5 — delete side of a score file: band isolation (layer 1 → 400, layer 2 → 409),
 * cascade deletion of ZIP-extracted child rows, and best-effort on-disk cleanup.
 * Mirrors the test style of {@code ScoreFileDownloadQueryServiceTest} (US-2.4) — pure
 * Mockito, no Spring context, filesystem exercised via a real temp directory.
 */
@ExtendWith(MockitoExtension.class)
class ScoreFileDeleteCommandServiceTest {

    @Mock private BandQueryService bandQueryService;
    @Mock private CompositionRepository compositionRepository;
    @Mock private ScoreFileRepository scoreFileRepository;

    private ScoreFileDeleteCommandService service;

    private Long bandId;
    private Long otherBandId;
    private String ownOnDisk;
    private String childOnDisk;

    private final Path tmpRoot = Path.of(System.getProperty("java.io.tmpdir"), "sf-del-unit-" + System.nanoTime());

    @BeforeEach
    void setUp() throws Exception {
        service = new ScoreFileDeleteCommandService(bandQueryService, compositionRepository, scoreFileRepository);
        bandId = 1L;
        otherBandId = 2L;
        Files.createDirectories(tmpRoot);
        Path own = tmpRoot.resolve("score.pdf");
        Files.writeString(own, "PDF-BYTES");
        ownOnDisk = own.toAbsolutePath().toString();

        Path child = tmpRoot.resolve("trumpet-page-1.pdf");
        Files.writeString(child, "CHILD-PDF");
        childOnDisk = child.toAbsolutePath().toString();
    }

    private Band newBand(Long id) {
        Band b = mock(Band.class);
        lenient().when(b.getId()).thenReturn(id);
        return b;
    }

    private Composition newComposition(Long id, Long owningBandId) {
        Band owner = newBand(owningBandId);
        Composition c = mock(Composition.class);
        lenient().when(c.getId()).thenReturn(id);
        lenient().when(c.getBand()).thenReturn(owner);
        return c;
    }

    private ScoreFile newScoreFileRow(Long fileId, Composition composition, String storagePath) {
        ScoreFile row = mock(ScoreFile.class);
        lenient().when(row.getId()).thenReturn(fileId);
        lenient().when(row.getComposition()).thenReturn(composition);
        lenient().when(row.getStoragePath()).thenReturn(storagePath);
        return row;
    }

    @Test
    @DisplayName("delete() removes the DB row and the physical file for an owned score file")
    void deleteOwnFile_success() throws Exception {
        Band ownBand = newBand(bandId);
        Composition own = newComposition(100L, bandId);
        ScoreFile row = newScoreFileRow(42L, own, ownOnDisk);
        when(bandQueryService.getRequiredBand(bandId)).thenReturn(ownBand);
        when(scoreFileRepository.findById(42L)).thenReturn(Optional.of(row));
        when(scoreFileRepository.findByParentFileId(42L)).thenReturn(List.of());

        service.delete(42L, 100L, bandId);

        verify(scoreFileRepository).delete(row);
        assertThat(Path.of(ownOnDisk)).doesNotExist().as("physical file must be removed from disk");
    }

    @Test
    @DisplayName("delete() cascades: children extracted from the ZIP are deleted (rows + files) before the parent")
    void deleteZipParent_cascadesChildren() throws Exception {
        Band ownBand = newBand(bandId);
        Composition own = newComposition(100L, bandId);
        ScoreFile zipRow = newScoreFileRow(43L, own, ownOnDisk);
        ScoreFile childRow = newScoreFileRow(4301L, own, childOnDisk);

        when(bandQueryService.getRequiredBand(bandId)).thenReturn(ownBand);
        when(scoreFileRepository.findById(43L)).thenReturn(Optional.of(zipRow));
        when(scoreFileRepository.findByParentFileId(43L)).thenReturn(List.of(childRow));

        service.delete(43L, 100L, bandId);

        var orderCap = inOrder(scoreFileRepository);
        orderCap.verify(scoreFileRepository).delete(childRow);
        orderCap.verify(scoreFileRepository).delete(zipRow);
        assertThat(Path.of(childOnDisk)).doesNotExist();
        assertThat(Path.of(ownOnDisk)).doesNotExist();
    }

    @Test
    @DisplayName("delete() with an unknown band throws IllegalArgumentException (→ 400)")
    void deleteUnknownBand_badRequest() {
        when(bandQueryService.getRequiredBand(99L)).thenThrow(new IllegalArgumentException("Band not found: 99"));

        assertThatThrownBy(() -> service.delete(1L, 100L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Band not found");
        verify(scoreFileRepository, never()).delete(Mockito.any(ScoreFile.class));
    }

    @Test
    @DisplayName("delete() with an unknown file id throws IllegalStateException (→ 409)")
    void deleteUnknownFile_conflict() {
        Band ownBand = newBand(bandId);
        when(bandQueryService.getRequiredBand(bandId)).thenReturn(ownBand);
        when(scoreFileRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(404L, 100L, bandId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Score file 404 does not exist");
        verify(scoreFileRepository, never()).delete(Mockito.any(ScoreFile.class));
        assertThat(Path.of(ownOnDisk)).exists().as("on-disk file must be UNTOUCHED on a failed lookup");
    }

    @Test
    @DisplayName("delete() for a file in another band throws IllegalStateException (→ 409), row + file untouched")
    void deleteCrossBand_conflict() {
        Band ownBand = newBand(bandId);
        Composition foreignComp = newComposition(100L, otherBandId); // file's composition is in another band
        ScoreFile row = newScoreFileRow(7L, foreignComp, ownOnDisk);

        when(bandQueryService.getRequiredBand(bandId)).thenReturn(ownBand);
        when(scoreFileRepository.findById(7L)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.delete(7L, 100L, bandId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not belong to band " + bandId);

        verify(scoreFileRepository, never()).delete(Mockito.any(ScoreFile.class));
        assertThat(Path.of(ownOnDisk)).exists().as("on-disk file must be UNTOUCHED on a cross-band attempt");
    }

    @Test
    @DisplayName("delete() for a composition-id in URL that doesn't match the row throws IllegalStateException (→ 409)")
    void deleteWrongComposition_conflict() {
        Band ownBand = newBand(bandId);
        Composition own = newComposition(100L, bandId);
        ScoreFile row = newScoreFileRow(44L, own, ownOnDisk);

        when(bandQueryService.getRequiredBand(bandId)).thenReturn(ownBand);
        when(scoreFileRepository.findById(44L)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.delete(44L, 999L, bandId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not part of composition");
        verify(scoreFileRepository, never()).delete(Mockito.any(ScoreFile.class));
        assertThat(Path.of(ownOnDisk)).exists();
    }

    @Test
    @DisplayName("delete() tolerates a recorded storage path that is already gone (no IOException escapes to the caller)")
    void deleteMissingOnDisk_stillDeletesRow() {
        Band ownBand = newBand(bandId);
        Composition own = newComposition(100L, bandId);
        String ghost = tmpRoot.resolve("never-existed.pdf").toString();
        assertThat(Path.of(ghost)).doesNotExist();
        ScoreFile row = newScoreFileRow(45L, own, ghost);

        when(bandQueryService.getRequiredBand(bandId)).thenReturn(ownBand);
        when(scoreFileRepository.findById(45L)).thenReturn(Optional.of(row));
        when(scoreFileRepository.findByParentFileId(45L)).thenReturn(List.of());

        service.delete(45L, 100L, bandId); // must not throw

        verify(scoreFileRepository).delete(row);
    }
}
