package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.UiTestBase;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.band.MemberAttributeDefRepository;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI test reproducing issue #91: clicking "Edytuj" on a MEMBER attribute
 * throws "Unknown type: MEMBER" error.
 *
 * <p>Scenario: create a MEMBER attribute directly in DB, then use the browser
 * to navigate to the edit URL and verify the form opens without the
 * "Unknown type: MEMBER" error.</p>
 */
class MemberAttributeEditUiTest extends UiTestBase {

    @Autowired
    private MemberAttributeDefRepository memberAttrDefRepository;

    @Autowired
    private BandRepository bandRepository;

    @Test
    void shouldOpenEditFormForMemberAttribute_withoutError() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String attrName = "MemAttr" + unique;
        Long attrId;

        // Create a MEMBER attribute directly in the database
        Band band = bandRepository.findById(1L).orElseThrow();
        MemberAttributeDef def = MemberAttributeDef.create(band, attrName, "TEXT", false, false, 0, null);
        def = memberAttrDefRepository.save(def);
        attrId = def.getId();

        try {
            // === STEP 1: Login (using members page which we know works) ===
            loginAndNavigateTo("/members");

            // === STEP 2: Navigate to the edit URL directly via the browser ===
            String editUrl = "/band/inventory-attributes/" + attrId + "/edit?type=MEMBER";
            System.out.println("[TEST] Navigating to edit URL: " + editUrl);
            driver.get(baseUrl() + editUrl);

            // Selenium: czekamy na załadowanie formularza edycji atrybutu.
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("form input[name='name']")));

            String pageSource = driver.getPageSource();
            System.out.println("[TEST] Page title: " + driver.getTitle());
            System.out.println("[TEST] Page contains 'Unknown type': " + pageSource.contains("Unknown type"));
            System.out.println("[TEST] Page contains form: " + pageSource.contains("<form"));

            // === STEP 3: Verify no "Unknown type" error ===
            assertThat(pageSource)
                    .as("Edit form should not contain 'Unknown type' error")
                    .doesNotContain("Unknown type");

            // The form should contain the attribute name
            assertThat(pageSource)
                    .as("Edit form should contain the attribute name")
                    .contains(attrName);

        } finally {
            // Cleanup
            memberAttrDefRepository.findById(attrId).ifPresent(memberAttrDefRepository::delete);
        }
    }
}
