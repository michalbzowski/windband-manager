package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2 S3 — Selenium coverage of the unified inventory list (F2 S2 mobile surface):
 *
 * <ol>
 *   <li><b>Create</b>: two-step form (type → renders attribute input, submit,
 *       new card with the filled attribute value chip; JDBC rows verified.</li>
 *   <li><b>Assign/return</b>: the seeded band-1 instrument "Trąbka Test" is assigned to a
 *       seeded member (shared modal: pick Jan Kowalski + condition → confirm), then returned
 *       to stock — lifecycle + member verified via JDBC; seed restored to AVAILABLE.</li>
 *   <li><b>Filter</b>: type filter toggles UNIFORM/INSTRUMENT card sets;
 *       the Obój attribute filter narrows to items carrying a non-empty value for that def.</li>
 * </ol>
 *
 * <p>Surface under test: {@code templates/inventory/list.html} + {@code list-js.html}
 * ({@code window.inv.*}), REST {@code /api/inventory/items/*}, seeds in
 * {@code src/test/resources/data.sql} (band 1: Bluza Test UNIFORM ASSIGNED to Jan,
 * Trąbka Test INSTRUMENT AVAILABLE; defs: Rozmiar INTEGER required / UNIFORM, Obój SELECT
 * BB;A;ESB / INSTRUMENT). The S2 list filters client-side via {@code style.display}, so all
 * assertions target individual cards by exact {@code <h3>} name — card ids are resolved
 * dynamically (H2 identity values may differ across runs after TRUNCATE RESTART IDENTITY).</p>
 */
class InventoryUiTest extends UiTestBase {

    private WebDriverWait wait;

    @BeforeEach
    void initWait() {
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        loginAndNavigateTo("/inventory");
        // Seeded band-1 cards (data.sql) rendered before we drive the UI.
        wait.until(d -> cardByName("Bluza Test") != null && cardByName("Trąbka Test") != null);
    }

    // ==================== 1. CREATE (two-step form → submit → new card) ==================

    @Test
    @DisplayName("Create: two-step INSTRUMENT form — name + Obój value, submit, card with the attribute chip")
    void shouldCreateInstrumentThroughTwoStepFormAndSeeCard() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String name = "Tuba F" + unique;

        try {
            // — Step 1: open the create form and choose INSTRUMENT (drives Step-2 input rendering).
            ((JavascriptExecutor) driver).executeScript("inv.toggleForm(true);");
            wait.until(d -> !"true".equals(driver.findElement(By.id("inv-form")).getAttribute("hidden")));
            selectOptionByValue("#inv-f-type", "INSTRUMENT");
            // onTypeChange() renders the per-definition inputs (Obój SELECT) into #inv-attr-step.
            List<WebElement> attrInputs = wait.until(d -> {
                List<WebElement> els = d.findElements(
                        By.cssSelector("#inv-attr-step select, #inv-attr-step input, #inv-attr-step textarea"));
                return els.isEmpty() ? null : els;
            });
            assertThat(attrInputs).as("Step-2 rendered attribute input for INSTRUMENT (Obój def)").isNotEmpty();

            // — Step 2: name + the Obój attribute value.
            WebElement nameInput = driver.findElement(By.id("inv-f-name"));
            nameInput.clear();
            nameInput.sendKeys(name);
            selectOptionByValue("#inv-attr-step select", "BB");

            WebElement submitBtn = driver.findElement(By.id("inv-f-submit"));
            assertThat(submitBtn.isEnabled()).as("submit is enabled once the form is complete").isTrue();

            // — Submit: fetchWithToast() POSTs, shows the success toast, then reloads the page.
            //   We treat "new card rendered & visible" as the deterministic success signal.
            submitBtn.click();
            wait.until(d -> {
                WebElement c = cardByName(name);
                return c != null && c.isDisplayed();
            });

            // — UI assertions: type badge, filled attribute value chip, in-stock footer.
            WebElement card = cardByName(name);
            assertThat(card.findElement(By.cssSelector(".inv-badge-type")).getText())
                    .as("card type badge for %s", name).containsIgnoringCase("Instrument");
            assertThat(findAttrValueText(card, "Obój").orElseThrow())
                    .as("card %s shows the Obój=BB value chip", name).contains("BB");
            assertThat(card.getText()).as("freshly created item is in stock").contains("W magazynie");

            // — JDBC assertions: row persisted as INSTRUMENT with an Obój=BB value.
            List<String> types = jdbcTemplate.queryForList(
                    "SELECT item_type FROM inventory_items WHERE name = ?", String.class, name);
            assertThat(types).as("item %s persisted as INSTRUMENT", name).containsExactly("INSTRUMENT");
            String defName = jdbcTemplate.queryForObject(
                    "SELECT d.name FROM item_attribute_values v" +
                            " JOIN item_attribute_defs d ON d.id = v.attribute_def_id" +
                            " JOIN inventory_items i ON i.id = v.item_id" +
                            " WHERE i.name = ? AND v.value_text = 'BB'", String.class, name);
            assertThat(defName).isEqualTo("Obój");

        } finally {
            deleteItemByName(name); // best-effort: attribute values first (FK), then the item row
        }
    }

    // ==================== 2. ASSIGN / RETURN flow =========================================

    @Test
    @DisplayName("Assign to seeded member + return: modal → Jan Kowalski, JDBC lifecycle, seed back in AVAILABLE")
    void shouldAssignSeededInstrumentToMemberThenReturnIt() {
        String seedItem = "Trąbka Test"; // INSTRUMENT, seeded AVAILABLE in band 1 (data.sql)

        // — Assign: open the shared modal on the card, pick Jan Kowalski + condition, confirm.
        clickCardAction(seedItem, "assign");
        wait.until(d -> !"true".equals(driver.findElement(By.id("inv-assign-modal")).getAttribute("hidden")));
        assertThat(driver.findElement(By.id("inv-a-title")).getText()).isEqualTo("Przydziel do członka");
        selectOptionContainingText("#inv-a-member", "Kowalski");
        selectOptionByValue("#inv-a-condition", "GOOD");
        driver.findElement(By.id("inv-a-submit")).click();

        // fetchWithToast success → page reloads → card footer flips to the assigned member.
        wait.until(d -> {
            WebElement c = cardByName(seedItem);
            return c != null && c.getText().contains("Przydzielone:") && c.getText().contains("Jan Kowalski");
        });

        String lcAfterAssign = jdbcTemplate.queryForObject(
                "SELECT lifecycle_status FROM inventory_items WHERE name = ?", String.class, seedItem);
        Long memberAfterAssign = jdbcTemplate.queryForObject(
                "SELECT member_id FROM inventory_items WHERE name = ?", Long.class, seedItem);
        assertThat(lcAfterAssign).isEqualTo("ASSIGNED");
        Long janId = jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE first_name = 'Jan' AND last_name = 'Kowalski'", Long.class);
        assertThat(memberAfterAssign).as("item %s assigned to seeded Jan Kowalski (id=%d)", seedItem, janId)
                .isEqualTo(janId);

        // — Return: open the modal again (card now renders only the Zwrot action) and confirm.
        clickCardAction(seedItem, "return");
        wait.until(d -> !"true".equals(driver.findElement(By.id("inv-assign-modal")).getAttribute("hidden")));
        assertThat(driver.findElement(By.id("inv-a-title")).getText()).isEqualTo("Zwrot do magazynu");
        driver.findElement(By.id("inv-a-submit")).click();

        wait.ignoring(org.openqa.selenium.StaleElementReferenceException.class).until(d -> {
            WebElement c = cardByName(seedItem);
            if (c == null) { return false; }
            try {
                return c.getText().contains("W magazynie");
            } catch (org.openqa.selenium.WebDriverException e) {
                return false; // stale: the list re-rendered this very moment — retry with a fresh handle
            }
        });

        String lcAfterReturn = jdbcTemplate.queryForObject(
                "SELECT lifecycle_status FROM inventory_items WHERE name = ?", String.class, seedItem);
        Long memberAfterReturn = jdbcTemplate.queryForObject(
                "SELECT member_id FROM inventory_items WHERE name = ?", Long.class, seedItem);
        assertThat(lcAfterReturn).isEqualTo("AVAILABLE");
        assertThat(memberAfterReturn).as("item %s unassigned after return", seedItem).isNull();
    }

    // ==================== 3. FILTERS (type + attribute) ==================================

    @Test
    @DisplayName("Filter: INSTRUMENT type hides uniform cards; Obój attribute filter keeps only seeded instrument")
    void shouldFilterByTypeAndByAttribute() {
        // Type filter → INSTRUMENT hides the uniform card (client-side renderList).
        selectOptionByValue("#inv-type-filter", "INSTRUMENT");
        wait.until(d -> visible("Trąbka Test"));
        assertThat(visible("Bluza Test")).as("uniform hidden under INSTRUMENT type filter").isFalse();
        assertThat(cardByName("Saksofon Other")).as("band-2 instrument never renders in band-1 view")
                .isEqualTo(null);

        // Type filter → UNIFORM hides the seeded instrument.
        selectOptionByValue("#inv-type-filter", "UNIFORM");
        wait.until(d -> !visible("Trąbka Test"));
        assertThat(visible("Bluza Test")).as("uniform visible under UNIFORM type filter").isTrue();

        // Attribute filter (Obój): only items carrying a non-empty value stay visible.
        selectOptionByValue("#inv-type-filter", "");
        wait.until(d -> visible("Bluza Test") && visible("Trąbka Test"));
        selectOptionByValue("#inv-attr-filter", "Obój");
        wait.until(d -> {
            List<WebElement> cards = driver.findElements(By.cssSelector("#inv-list .inv-card"));
            long visibleCount = cards.stream().filter(WebElement::isDisplayed).count();
            return visibleCount == 1 && visible("Trąbka Test");
        });
        assertThat(visible("Bluza Test")).as("uniform without Obój value hidden by attribute filter").isFalse();

        // Clear the attribute filter — both seeded band-1 cards are visible again.
        selectOptionByValue("#inv-attr-filter", "");
        wait.until(d -> visible("Bluza Test") && visible("Trąbka Test"));
    }

    // ==================== helpers =========================================================

    private boolean visible(String exactCardName) {
        WebElement c = cardByName(exactCardName);
        return c != null && c.isDisplayed();
    }

    /** Returns the card whose <h3> text equals exactCardName, else null (never throws on missing). */
    private WebElement cardByName(String exactCardName) {
        try {
            for (WebElement card : driver.findElements(By.cssSelector("#inv-list .inv-card"))) {
                try {
                    String h3 = card.findElement(By.cssSelector("h3")).getText();
                    if (exactCardName.equals(h3.trim())) return card;
                } catch (org.openqa.selenium.WebDriverException ignored) { /* re-render in flight / no <h3> — try next card */ }
            }
        } catch (Exception sessionGone) { /* page navigating / session reset — re-check by caller */ }
        return null;
    }

    /** Clicks the named action button inside the card whose <h3> equals cardName. */
    private void clickCardAction(String cardName, String action) {
        wait.until(d -> cardByName(cardName) != null);
        // The dashboard re-renders the card list (e.g. after a server-side assign), so any cached
        // handle can go stale between lookup and click — retry with a fresh element each attempt.
        List<WebElement> fresh = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                WebElement card = cardByName(cardName);
                if (card == null) { continue; }
                fresh = card.findElements(By.cssSelector("button[data-action='" + action + "']"));
                assertThat(fresh).as("card %s exposes a %s action button", cardName, action).hasSize(1);
                // The sticky dashboard header can overlap a card's footer action button once the
                // page is scrolled; scroll in before any mouse event (Chromium ElementClickInterceptedException)).
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({ block:'center' });", fresh.get(0));
                fresh.get(0).click();
                return; // click landed — success breaks out of the retry loop
            } catch (org.openqa.selenium.WebDriverException staleOrIntercept) {
                if (attempt == 4) { throw staleOrIntercept; } // exhausted retries for a real failure
            }
        }
    }

    private Optional<String> findAttrValueText(WebElement card, String defName) {
        for (WebElement attr : card.findElements(By.cssSelector(".inv-attr"))) {
            if (defName.equals(String.valueOf(attr.getAttribute("data-def-name")))) {
                try {
                    return Optional.of(attr.findElement(By.cssSelector(".inv-attr-value")).getText());
                } catch (NoSuchElementException e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /** Picks the option whose value === given. NOTE: must hoist arguments into a local
     *  variable — inside the find() callback 'arguments' no longer refers to the script args. */
    private void selectOptionByValue(String cssSelector, String value) {
        ((JavascriptExecutor) driver).executeScript(
                "var s = document.querySelector(arguments[0]);" +
                " var v = arguments[1];" +
                " if (!s) throw new Error('selector not found: ' + s);" +
                " var o = Array.prototype.find.call(s.options, function (x) { return x.value === v; });" +
                " if (!o) throw new Error('option value missing in selector for: ' + v);" +
                " s.value = o.value;" +
                " s.dispatchEvent(new Event('change', {bubbles: true}));", cssSelector, value);
    }

    private void selectOptionContainingText(String cssSelector, String fragment) {
        ((JavascriptExecutor) driver).executeScript(
                "var s = document.querySelector(arguments[0]);" +
                " var frag = arguments[1];" +
                " if (!s) throw new Error('selector not found: ' + s);" +
                " var o = Array.prototype.find.call(s.options, function (x) {" +
                "   var sp = document.createElement('span'); sp.innerHTML = x.text;" +
                "   return sp.textContent.indexOf(frag) >= 0; });" +
                " if (!o) throw new Error('option containing text not found: ' + frag);" +
                " s.value = o.value;" +
                " s.dispatchEvent(new Event('change', {bubbles: true}));", cssSelector, fragment);
    }

    /** Best-effort JDBC cleanup for items created by this test (values first — FK). */
    private void deleteItemByName(String name) {
        try {
            jdbcTemplate.update(
                    "DELETE FROM item_attribute_values WHERE item_id IN (SELECT id FROM inventory_items WHERE name = ?)",
                    name);
            jdbcTemplate.update("DELETE FROM inventory_items WHERE name = ?", name);
        } catch (Exception e) {
            System.err.println("[InventoryUiTest] cleanup of '" + name + "' failed: " + e.getMessage());
        }
    }
}
