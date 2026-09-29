package pl.michalbzowski.windband.adapter.in.web;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #241 — "Brak możliwości edycji dodanego głosu": a voice once saved through the
 * "Dodaj głos" modal was untouchable (the row carried only 📤 Udostępnij). The fix adds an
 * ✏️ Edytuj button per row: it reopens the SAME modal prefilled with the stored instrument,
 * role, file and page range, and submits to POST .../parts/{partId} (update).
 *
 * <p>This test drives the REAL flow: click Edytuj on the seeded row → assert the modal opens
 * prefilled → change role + page range → save → poll the DB that the SAME row id was updated
 * (never duplicated). The row id invariance is the load-bearing assertion: the user complaint
 * was about editing, and silently creating a second voice would be the worst possible fix.</p>
 */
class EditVoiceModalUiTest extends UiTestBase {

    private static final Duration WAIT = Duration.ofSeconds(15);

    private Long compositionId;
    private Long partId;

    @BeforeEach
    void seedCompositionWithEditablePart() throws Exception {
        // Unique title per run — H2's shared TRUNCATE ... CASCADE can silent-no-op between
        // tests in one JVM, so lookups by fixed title must stay collision-free.
        String title = "Edit Voice UI-241-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO compositions (title, description, composer, arranger, status, band_id, created_at, updated_at) " +
                        "VALUES (?, 'regresja #241 edycja glosu', null, null, 'DRAFT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                title);
        compositionId = jdbcTemplate.queryForObject(
                "SELECT id FROM compositions WHERE band_id = 1 AND title = ?", Long.class, title);

        // One uploaded score file (REAL 5-page PDF so the modal's /thumb preview works).
        Path scoresRoot = Files.createDirectories(
                Path.of(System.getProperty("java.io.tmpdir"), "windband-ui-scores"));
        Path pdfPath = scoresRoot.resolve(UUID.randomUUID() + "_Polonez_UI-241.pdf");
        byte[] pdf = build5PagePdf();
        Files.write(pdfPath, pdf);
        jdbcTemplate.update("""
                INSERT INTO score_files
                    (composition_id, mime_type, size_bytes, sha256, storage_path, original_name, page_count, created_at)
                VALUES (%s, 'application/pdf', %d,
                        'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
                        '%s', 'Polonez (glosy) 241.pdf', 5, CURRENT_TIMESTAMP)
                """.formatted(String.valueOf(compositionId), pdf.length, pdfPath.toString()));
        Long fileId = jdbcTemplate.queryForObject(
                "SELECT id FROM score_files WHERE original_name = 'Polonez (glosy) 241.pdf' " +
                        "AND composition_id = ?", Long.class, compositionId);

        Long instrumentId = jdbcTemplate.queryForObject(
                "SELECT id FROM instruments WHERE band_id = 1 AND name = 'Trąbka'", Long.class);

        jdbcTemplate.update(
                "INSERT INTO composition_instruments (composition_id, instrument_id, instrument_role," +
                        " page_from, page_to, score_file_id, source, confidence_score, created_at, updated_at) VALUES " +
                        "(?, ?, 'Partytura', 1, 5, ?, 'MANUAL', 1.0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                compositionId, instrumentId, fileId);
        partId = jdbcTemplate.queryForObject(
                "SELECT id FROM composition_instruments WHERE composition_id = ?", Long.class, compositionId);
    }

    private static byte[] build5PagePdf() throws Exception {
        try (PDDocument doc = new PDDocument();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            for (int i = 1; i <= 5; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 28);
                    cs.newLineAtOffset(72, 760);
                    cs.showText("Trabka / strona " + i);
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void editButton_reopensModalPrefilledAndSaveUpdatesTheSameRow() {
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        WebDriverWait wait = new WebDriverWait(driver, WAIT);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // The seeded row renders with the new ✏️ Edytuj button.
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#existing-parts tr[data-part-id='" + partId + "'] button.part-edit-btn")));

        // Click via JS — table buttons can sit under sticky overlays on CI runners.
        js.executeScript(
                "var b = document.querySelector(\"#existing-parts tr[data-part-id='\" + arguments[0] + \"'] button.part-edit-btn\");" +
                "b.scrollIntoView({block: 'center'}); b.click();", String.valueOf(partId));

        wait.until(d -> Boolean.TRUE.equals(js.executeScript(
                "var dlg = document.getElementById('add-part-dialog');" +
                "return dlg && (dlg.open === true || dlg.hasAttribute('open'));")));

        // ── prefilled state: title, action, role, range, instrument ────────────────
        String title = (String) js.executeScript(
                "return document.getElementById('add-part-dialog-title').textContent;");
        assertThat(title).as("modal switches to edit title").contains("Edytuj");

        String action = (String) js.executeScript(
                "return String(document.getElementById('add-part-form').action);");
        assertThat(action).as("form posts to the part-specific update endpoint")
                .endsWith("/bands/1/compositions/" + compositionId + "/parts/" + partId);

        String role = (String) js.executeScript(
                "return document.getElementById('part-role-input').value;");
        assertThat(role).as("role prefilled from the row").isEqualTo("Partytura");

        String from = (String) js.executeScript(
                "return document.getElementById('part-page-from').value;");
        String to = (String) js.executeScript(
                "return document.getElementById('part-page-to').value;");
        assertThat(from).as("page od prefilled").isEqualTo("1");
        assertThat(to).as("page do prefilled").isEqualTo("5");

        Long instrumentId = jdbcTemplate.queryForObject(
                "SELECT instrument_id FROM composition_instruments WHERE id = ?", Long.class, partId);
        String picked = (String) js.executeScript(
                "return document.getElementById('part-instrument-picker').value;");
        assertThat(picked).as("instrument select prefilled")
                .isEqualTo(String.valueOf(instrumentId));

        // ── apply the correction the user actually wants: role + narrower range ────
        js.executeScript("document.getElementById('part-role-input').value = 'Partytura (poprawiona)'");
        setHiddenPageField(js, "part-page-from", "2");
        setHiddenPageField(js, "part-page-to", "4");

        // Save via the modal's own submit button.
        js.executeScript("document.getElementById('save-part-btn').click();");

        // Wait for the round-trip on the ROW ITSELF: same id, new values.
        wait.until(d -> {
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM composition_instruments WHERE id = ? " +
                            "AND instrument_role = 'Partytura (poprawiona)' AND page_from = 2 AND page_to = 4",
                    Integer.class, partId);
            return n != null && n == 1;
        });

        // No duplicate voice was created by the save.
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM composition_instruments WHERE composition_id = ?",
                Integer.class, compositionId);
        assertThat(rows).as("edit updated the row in place — no second voice").isEqualTo(1);

        // The explicit score-file binding survives the edit.
        Integer stillBound = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM composition_instruments WHERE id = ? AND score_file_id IS NOT NULL",
                Integer.class, partId);
        assertThat(stillBound).as("file binding kept through the edit").isEqualTo(1);
    }

    @Test
    void closingTheModalAfterEdytuj_restoresAddModeForTheNextOpen() {
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        WebDriverWait wait = new WebDriverWait(driver, WAIT);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#existing-parts tr[data-part-id='" + partId + "'] button.part-edit-btn")));
        js.executeScript(
                "document.querySelector(\"#existing-parts tr[data-part-id='\" + arguments[0] + \"'] button.part-edit-btn\").click();",
                String.valueOf(partId));
        wait.until(d -> Boolean.TRUE.equals(js.executeScript(
                "var dlg = document.getElementById('add-part-dialog'); return dlg && dlg.open === true;")));

        // Close via the dialog's own close event (what "Zamknij"/× fire).
        js.executeScript("document.getElementById('add-part-dialog').close();");

        // Reopen through the plain "+ Dodaj głos" button: must be ADD mode again.
        wait.until(d -> Boolean.TRUE.equals(js.executeScript(
                "return !!document.getElementById('open-add-part-modal-btn');")));
        js.executeScript("document.getElementById('open-add-part-modal-btn').click();");
        wait.until(d -> Boolean.TRUE.equals(js.executeScript(
                "var dlg = document.getElementById('add-part-dialog'); return dlg && dlg.open === true;")));

        String action = (String) js.executeScript(
                "return String(document.getElementById('add-part-form').action);");
        assertThat(action).as("action reset to the create endpoint")
                .endsWith("/bands/1/compositions/" + compositionId + "/parts");

        String title = (String) js.executeScript(
                "return document.getElementById('add-part-dialog-title').textContent;");
        assertThat(title).as("title reset to add mode").contains("Dodaj głos");
    }

    /**
     * "Strona od/do" are visually hidden inputs — Selenium cannot type into them. Set the
     * value and dispatch the same 'input' event a real keystroke fires (same helper shape as
     * CompositionDetailPartsUiTest, kept private here on purpose: subclasses must not widen
     * UiTestBase helpers that clash with existing private ones).
     */
    private static void setHiddenPageField(JavascriptExecutor js, String id, String value) {
        js.executeScript(
                "var i = document.getElementById(arguments[0]);" +
                "i.value = arguments[1];" +
                "i.dispatchEvent(new Event('input', { bubbles: true }));", id, value);
    }
}
