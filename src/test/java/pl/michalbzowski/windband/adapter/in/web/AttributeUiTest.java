package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI tests for inventory attribute definitions.
 *
 * <p>Historically these were blocked by issue #167: a form login left a plain {@code User} in the
 * security context, so {@code @AuthenticationPrincipal OidcUser} injection failed. That part is
 * fixed ({@code DashboardHomeUiTest} proves it), but the screens themselves are gone from this
 * application, so the class stays disabled — see the {@code @Disabled} reason on the class.
 *
 * <p>They verify:
 * 1. Navigating to the attribute list for each type (UNIFORM, INSTRUMENT, ORDER, AWARD, MEMBER)
 * 2. Opening the new-attribute form for each type
 * 3. Creating attributes through the UI form and seeing them on the list
 * 4. Created attributes appearing on inventory forms
 */
@Disabled("Ekran atrybutów inventory wyszedł z tej aplikacji (patrz windband-inventory) — ścieżki /inventory/attributes* nie istnieją w src/main, test nie ma czego klikać. Auth z #167 jest już naprawiony: DashboardHomeUiTest przechodzi.")
class AttributeUiTest extends UiTestBase {

    @Test
    void shouldNavigateToAllAttributeTypeTabs() {
        loginAndNavigateTo("/band/inventory-attributes?type=UNIFORM");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content h2")));

        for (String type : new String[]{"UNIFORM", "INSTRUMENT", "ORDER", "AWARD", "MEMBER"}) {
            driver.get(baseUrl() + "/band/inventory-attributes?type=" + type);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content h2")));

            var heading = driver.findElement(By.cssSelector("#content h2"));
            assertThat(heading.getText()).as("Heading for type " + type).contains("Atrybuty");

            var addButton = driver.findElement(By.xpath("//button[contains(., 'Dodaj atrybut')]"));
            assertThat(addButton).as("Add button for type " + type).isNotNull();
        }
    }

    @Test
    void shouldOpenNewAttributeFormForAllTypes() {
        loginAndNavigateTo("/band/inventory-attributes?type=UNIFORM");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content h2")));

        for (String type : new String[]{"UNIFORM", "INSTRUMENT", "ORDER", "AWARD", "MEMBER"}) {
            driver.get(baseUrl() + "/band/inventory-attributes?type=" + type);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content")));

            var addBtn = driver.findElement(By.xpath("//button[contains(., 'Dodaj atrybut')]"));
            addBtn.click();

            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("form input[name='name']")));
            assertThat(driver.findElement(By.cssSelector("form input[name='name']"))).isNotNull();
        }
    }

    @Test
    void shouldCreateUniformAttributeAndShowOnList() {
        loginAndNavigateTo("/band/inventory-attributes?type=UNIFORM");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content h2")));

        createAttributeViaUI("UNIFORM", "Rozmiar stroju", "TEXT", wait);

        driver.get(baseUrl() + "/band/inventory-attributes?type=UNIFORM");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content")));
        assertThat(driver.getPageSource()).contains("Rozmiar stroju");
    }

    @Test
    void shouldCreateInstrumentAttributeAndShowOnList() {
        loginAndNavigateTo("/band/inventory-attributes?type=INSTRUMENT");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content h2")));

        createAttributeViaUI("INSTRUMENT", "Marka instrumentu", "TEXT", wait);

        driver.get(baseUrl() + "/band/inventory-attributes?type=INSTRUMENT");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content")));
        assertThat(driver.getPageSource()).contains("Marka instrumentu");
    }

    @Test
    void shouldCreateOrderAttributeAndShowOnList() {
        loginAndNavigateTo("/band/inventory-attributes?type=ORDER");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content h2")));

        createAttributeViaUI("ORDER", "Kolor", "TEXT", wait);

        driver.get(baseUrl() + "/band/inventory-attributes?type=ORDER");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content")));
        assertThat(driver.getPageSource()).contains("Kolor");
    }

    @Test
    void shouldCreateUniformAttributeAndShowOnInventoryForm() {
        loginAndNavigateTo("/band/inventory-attributes?type=UNIFORM");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content h2")));

        createAttributeViaUI("UNIFORM", "Rozmiar czapki", "TEXT", wait);

        driver.get(baseUrl() + "/inventory");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content")));

        var addUniformBtn = driver.findElement(By.xpath("//button[contains(., 'Dodaj ekwipunek')]"));
        addUniformBtn.click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("uniform-form")));
        assertThat(driver.getPageSource()).contains("Rozmiar czapki");
    }

    @Test
    void shouldCreateInstrumentAttributeAndShowOnInventoryForm() {
        loginAndNavigateTo("/band/inventory-attributes?type=INSTRUMENT");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content h2")));

        createAttributeViaUI("INSTRUMENT", "Liczba wentyli", "NUMBER", wait);

        driver.get(baseUrl() + "/inventory");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content")));

        driver.findElement(By.xpath("//button[contains(., 'Instrumenty')]")).click();
        var addInstBtn = driver.findElement(By.xpath("//button[contains(., 'Dodaj instrument')]"));
        addInstBtn.click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("instrument-form")));
        assertThat(driver.getPageSource()).contains("Liczba wentyli");
    }

    @Test
    void shouldShowOnlyCurrentTypeAttributesOnList() {
        loginAndNavigateTo("/band/inventory-attributes?type=UNIFORM");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content h2")));

        createAttributeViaUI("UNIFORM", "TylkoUniformowy", "TEXT", wait);
        createAttributeViaUI("INSTRUMENT", "TylkoInstrumentalny", "TEXT", wait);

        driver.get(baseUrl() + "/band/inventory-attributes?type=UNIFORM");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content")));
        assertThat(driver.getPageSource()).contains("TylkoUniformowy");
        assertThat(driver.getPageSource()).doesNotContain("TylkoInstrumentalny");

        driver.get(baseUrl() + "/band/inventory-attributes?type=INSTRUMENT");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content")));
        assertThat(driver.getPageSource()).contains("TylkoInstrumentalny");
        assertThat(driver.getPageSource()).doesNotContain("TylkoUniformowy");
    }

    private void createAttributeViaUI(String type, String name, String attrType, WebDriverWait wait) {
        driver.get(baseUrl() + "/band/inventory-attributes/new?type=" + type);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("form input[name='name']")));

        driver.findElement(By.cssSelector("input[name='name']")).sendKeys(name);
        driver.findElement(By.cssSelector("select[name='attributeType']")).click();
        driver.findElement(By.cssSelector("select[name='attributeType'] option[value='" + attrType + "']")).click();
        driver.findElement(By.cssSelector("input[name='displayOrder']")).clear();
        driver.findElement(By.cssSelector("input[name='displayOrder']")).sendKeys("1");
        driver.findElement(By.cssSelector("input[name='displayInList']")).click();

        // Wait for submit button to be clickable (visible + enabled)
        // Fallback to JavaScript click if Selenium considers it non-interactable
        // (e.g. PicoCSS may hide the button behind its own styling)
        try {
            var submitBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("form button[type='submit']")));
            submitBtn.click();
        } catch (org.openqa.selenium.TimeoutException e) {
            // Element exists but not clickable via Selenium — use JS click
            var btn = driver.findElement(By.cssSelector("form button[type='submit']"));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
        }
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content h2")));
    }
}
