package pl.michalbzowski.windband.webui;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.UiTestBase;
import pl.michalbzowski.windband.application.command.composition.CompositionCommandService;
import pl.michalbzowski.windband.application.command.composition.CreateCompositionCommand;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;

import java.time.Duration;

/**
 * US-4.3 — composition detail page renders the "🎼 Analizuj utwór" modal (file picker + progress/result panels).
 *
 * <p>Browser-based (Selenium via {@link UiTestBase}) because the full auth flow on CI
 * requires a WindbandOidcUser that belongs to the band, which is what requireBandAccess checks.
 *
 * <p>What this pins down:
 * <ol>
 *   <li>The detail page renders successfully (no 500, no blank body after navigation).</li>
 *   <li>The analyze modal's structural contract is in the DOM: file picker, start button,
 *       progress panel, result panel, cancel button.</li>
 *   <li>The lifecycle modal (US-3.5) ids are unchanged (sibling flow — stable shell).</li>
 * </ol>
 *
 * <p>Limits: the JS auto-poll loop and fetch chain are runtime-only; other suites cover them.
 */
class CompositionDetailPageUiTest extends UiTestBase {

    @Autowired private BandRepository bandRepository;
    @Autowired private CompositionCommandService compositionCommandService;
    @Autowired private ScoreFileRepository scoreFileRepository;

    private static final long BAND_A = 1L; // seed "Test Band" (see src/test/resources/data.sql)

    @Test
    @Disabled("See GitHub issue: test security context returns plain User instead of WindbandOidcUser on CI")
    void detailPage_rendersAnalyzeModalStructure() {
        // Seed a DRAFT composition with one score file (so the analyze button is visible).
        var band = bandRepository.findById(BAND_A).orElseThrow();
        CreateCompositionCommand cmd = new CreateCompositionCommand();
        cmd.setTitle("Detail page UI");
        cmd.setComposer("UI-tester");
        cmd.setArranger("Arranger UI");
        var composition = compositionCommandService.create(cmd, BAND_A);

        ScoreFile file = ScoreFile.forComposition(
                composition, "application/pdf", "sheet.pdf", 128L,
                "abc-def-ghij", "classpath:fake.pdf", null);
        scoreFileRepository.save(file);

        // Navigate to the detail page; wait for the analyze modal's file picker to be in the DOM.
        driver.get("http://localhost:" + port + "/bands/" + BAND_A + "/compositions/" + composition.getId());
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.presenceOfElementLocated(By.id("score-file-picker")));

        String html = driver.findElement(By.tagName("body")).getAttribute("innerHTML");
        Assertions.assertThat(html)
                // Structural contract of the analyze modal (user-facing DOM shell):
                .contains("id=\"score-file-picker\"")
                .contains("id=\"analyze-start-panel\"")
                .contains("id=\"analyze-progress-panel\"")
                .contains("id=\"analyze-result-panel\"")
                .contains("id=\"analyze-cancel-btn\"")
                // + lifecycle modal (US-3.5) is a sibling flow — pin its stable ids too:
                .contains("id=\"lifecycle-dialog\"");
    }
}
