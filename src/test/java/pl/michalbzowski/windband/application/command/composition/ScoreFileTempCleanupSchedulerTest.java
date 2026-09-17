package pl.michalbzowski.windband.application.command.composition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.michalbzowski.windband.application.config.ScoresConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-2.5 — scheduled temp-file sweep (pure-JVM test, no Spring context): exercises
 * {@link ScoreFileTempCleanupScheduler#sweep(Path, Duration)} directly against a real
 * temp directory so filesystem behaviour is asserted, not mocked away.
 */
@ExtendWith(MockitoExtension.class)
class ScoreFileTempCleanupSchedulerTest {

    @TempDir
    Path dir;

    private ScoreFileTempCleanupScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        ScoresConfig config = new ScoresConfig(dir.toString(), 50L, 200L, null);
        ScoreFileStorage storage = new ScoreFileStorage(config);
        scheduler = new ScoreFileTempCleanupScheduler(storage, config);
    }

    private static long hoursAgoMillis(long hours) {
        return System.currentTimeMillis() - (hours * 60L * 60L * 1000L);
    }

    private static void createAt(Path p, long timestampMillis) throws IOException {
        Files.writeString(p, "x");
        java.nio.file.attribute.FileTime t = java.nio.file.attribute.FileTime.fromMillis(timestampMillis);
        Files.setLastModifiedTime(p, t);
    }

    @Test
    @DisplayName("sweep() deletes temp files older than the threshold and keeps fresh ones")
    void sweep_deletesOnlyStaleTempFiles() throws Exception {
        Path stale = dir.resolve(ScoreFileStorage.TEMP_FILE_PREFIX + "zzz" + ScoreFileStorage.TEMP_FILE_SUFFIX);
        Path fresh = dir.resolve(ScoreFileStorage.TEMP_FILE_PREFIX + "fresh" + ScoreFileStorage.TEMP_FILE_SUFFIX);
        createAt(stale, hoursAgoMillis(48)); // 2x the 24h threshold → must be removed
        createAt(fresh, System.currentTimeMillis()); // fresh → must survive

        var report = scheduler.sweep(dir, Duration.ofHours(24));

        assertThat(stale).doesNotExist().as("stale temp file must be removed");
        assertThat(fresh).exists().as("recently created temp file must be kept");
        assertThat(report.removed()).isEqualTo(1);
        assertThat(report.candidatesFound()).isEqualTo(1);
    }

    @Test
    @DisplayName("sweep() ignores non-temp files even if they are very old")
    void sweep_ignoresNonTempFiles() throws Exception {
        Path oldPdf = dir.resolve("1738000000000_score.pdf");
        createAt(oldPdf, hoursAgoMillis(24 * 20)); // far older than any threshold

        var report = scheduler.sweep(dir, Duration.ofHours(24));

        assertThat(oldPdf).exists().as("regular (already-committed) score files are never touched by the sweep");
        assertThat(report.removed()).isEqualTo(0);
    }

    @Test
    @DisplayName("sweep() on a missing root is a no-op, not an error")
    void sweep_missingRoot_isNoOp() {
        var report = scheduler.sweep(dir.resolve("does-not-exist"), Duration.ofHours(24));
        assertThat(report.removed()).isEqualTo(0);
        assertThat(report.candidatesFound()).isEqualTo(0);
    }

    @Test
    @DisplayName("sweep() with a very short threshold removes an old-but-not-super-old temp file")
    void sweep_shortThreshold_applies() throws Exception {
        Path stale = dir.resolve(ScoreFileStorage.TEMP_FILE_PREFIX + "y" + ScoreFileStorage.TEMP_FILE_SUFFIX);
        createAt(stale, hoursAgoMillis(3));

        var report = scheduler.sweep(dir, Duration.ofHours(2)); // threshold 2h < 3h age → stale

        assertThat(stale).doesNotExist();
        assertThat(report.removed()).isEqualTo(1);
    }
}
