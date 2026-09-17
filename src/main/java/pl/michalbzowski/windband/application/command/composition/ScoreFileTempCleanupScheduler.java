package pl.michalbzowski.windband.application.command.composition;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import pl.michalbzowski.windband.application.config.ScoresConfig;

/**
 * US-2.5 — scheduled safety-net cleanup of leftover temp files under the scores root
 * (see {@link ScoreFileStorage} for who writes them during a two-phase upload).
 *
 * <p>If the application crashes <i>after</i> creating the temp file but <i>before</i> the
 * atomic rename into the final location, that temp file would otherwise linger forever —
 * the per-request {@code finally} block in {@link ScoreFileStorage#store} cleans up its own
 * temp file, but only for its in-flight request. This task is the backstop: anything still
 * matching {@code <root>/upload-*.tmp} older than {@code windband.scores.temp-cleanup-hours}
 * (default 24h, see {@link ScoresConfig#tempCleanupHoursOr()}) is a zombie and gets removed.</p>
 *
 * <p>Scheduling cadence: fixed 1-hour delay. The cleanup itself is cheap (a shallow walk of
 * one directory tree) even on Railway's small volumes; a full sweep every hour is comfortably
 * under the 24h threshold so no single stale file can outlive two ticks.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreFileTempCleanupScheduler {

    private final ScoreFileStorage storage;
    private final ScoresConfig config;

    /**
     * Invoked by Spring's task scheduler (see {@code @EnableScheduling} on the application
     * class) every hour. Threshold comes from {@link ScoresConfig}; no arguments — Spring's
     * scheduler only calls parameterless methods.
     */
    @Scheduled(fixedDelay = 60L * 60L * 1000L, initialDelay = 5L * 60L * 1000L)
    public CleanupReport runSweep() {
        return sweep(storage.effectiveRootPath(), Duration.ofHours(config.tempCleanupHoursOr()));
    }

    /**
     * Visible-for-test entry point: performs the actual filesystem sweep against an explicit
     * root + age threshold. The Spring-scheduled method above merely resolves the configured
     * values and delegates here — keeping this pure function makes it directly unit-testable
     * with a temp directory and no Spring context at all.
     *
     * <p>The walk is non-recursive-by-default for {@code <root>/*.tmp} files; it only descends
     * into subdirectories if they contain more temp-named files (defensive, matches the
     * two-stage layout US-2.1 could plausibly produce as it evolves).</p>
     */
    public CleanupReport sweep(Path root, Duration maxAge) {
        if (root == null || !Files.exists(root) || !Files.isDirectory(root)) {
            log.debug("Score-file temp cleanup skipped: root {} does not exist yet", root);
            return CleanupReport.empty();
        }

        Instant cutoff = Instant.now().minus(maxAge);
        List<Path> victims = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file == null || attrs == null) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path fileNameObj = file.getFileName();
                    String name = fileNameObj == null ? "" : fileNameObj.toString();
                    boolean isTemp = name.startsWith(ScoreFileStorage.TEMP_FILE_PREFIX)
                            && name.endsWith(ScoreFileStorage.TEMP_FILE_SUFFIX);
                    if (!isTemp) {
                        return FileVisitResult.CONTINUE;
                    }
                    java.nio.file.attribute.FileTime lastModified = attrs.lastModifiedTime();
                    if (lastModified != null && lastModified.toInstant().isBefore(cutoff)) {
                        victims.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // Non-fatal: a transient I/O error during a safety-net sweep must never crash the app.
            log.warn("Score-file temp cleanup walk failed under {}: {}", root, e.getMessage());
            return CleanupReport.empty();
        }

        int removed = 0;
        for (Path victim : victims) {
            try {
                Files.deleteIfExists(victim);
                removed++;
            } catch (IOException e) {
                log.warn("Could not delete stale temp score file {} — will retry next sweep", victim, e);
            }
        }

        if (removed > 0) {
            log.info("Score-file temp cleanup removed {} stale temp file(s) under {}", removed, root);
        }
        return new CleanupReport(removed, victims.size());
    }

    /** Result of a single sweep: how many files were actually deleted vs. how many were candidates. */
    public record CleanupReport(int removed, int candidatesFound) {
        public static CleanupReport empty() { return new CleanupReport(0, 0); }
    }
}
