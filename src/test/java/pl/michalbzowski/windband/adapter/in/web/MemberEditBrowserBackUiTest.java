package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** UI test for issue #95: browser back from member edit should return to members list. */
class MemberEditBrowserBackUiTest extends UiTestBase {

    @Test
    void shouldNavigateBackToMembersListAfterEdit() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Long memberId = createTestDataMember(unique);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        try {
            loginAndNavigateTo("/members");
            assertThat(driver.getCurrentUrl()).as("Should be on /members list page").contains("/members");
            wait.until(ExpectedConditions.textToBePresentInElementLocated(By.cssSelector("#members-content"), "TestMember" + unique));

            clickEditForMember(wait, "TestMember" + unique);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

            driver.findElement(By.cssSelector("input[name='lastName']")).clear();
            driver.findElement(By.cssSelector("input[name='lastName']")).sendKeys("Edited");
            submitPrimaryFormButton();

            // After fix: HTMX ajax loads #members-content with member list
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("members-table")));

            String urlAfterSave = driver.getCurrentUrl();
            assertThat(urlAfterSave).as("URL should still be /members").contains("/members");
            wait.until(ExpectedConditions.textToBePresentInElementLocated(By.cssSelector("#members-content"), "Edited"));
        } finally {
            if (memberId != null) {
                deleteMemberViaApi(memberId);
            }
        }
    }

    @Test
    void shouldNavigateBackToMembersListAfterAddNew() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        try {
            loginAndNavigateTo("/members");
            assertThat(driver.getCurrentUrl()).as("Should be on /members before").contains("/members");

            WebElement addButton = driver.findElement(By.cssSelector("#add-member-btn"));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", addButton);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

            fillField("firstName", "TestMember" + unique);
            fillField("lastName", "New");
            submitPrimaryFormButton();

            // After fix: HTMX ajax loads #members-content with member list
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("members-table")));

            String urlAfterAdd = driver.getCurrentUrl();
            assertThat(urlAfterAdd).contains("/members");

            wait.until(ExpectedConditions.textToBePresentInElementLocated(By.cssSelector("#members-content"), "TestMember" + unique));
        } finally {
            cleanupMembers(unique);
        }
    }

    private Long createTestDataMember(String suffix) {
        org.springframework.jdbc.support.GeneratedKeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO members (first_name, last_name, active, band_id, email_consent_given, joined_date) VALUES (?, ?, true, 1, false, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, "TestMember" + suffix);
            ps.setString(2, "Original");
            ps.setDate(3, new java.sql.Date(LocalDate.now().toEpochDay()));
            return ps;
        }, kh);

        Number key = kh.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to create test member: TestMember" + suffix);
        }
        return key.longValue();
    }

    private void fillField(String name, String value) {
        driver.findElement(By.cssSelector("input[name='" + name + "']")).clear();
        driver.findElement(By.cssSelector("input[name='" + name + "']")).sendKeys(value);
    }

    private void submitPrimaryFormButton() {
        driver.findElement(By.cssSelector("#member-form button.primary[type='submit']")).click();
    }

    private void clickEditForMember(WebDriverWait wait, String firstName) {
        String xpath = "//tr[td[contains(., '" + firstName + "')]]//button[contains(., 'Edytuj')]";
        WebElement btn = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
    }

    private void deleteMemberViaApi(Long id) {
        String script = "" + "var done = arguments[0];" +
                "fetch('/api/members/" + id + "', {method: 'DELETE', credentials: 'same-origin'})" +
                "  .then(function(r) { done(true); })" +
                "  .catch(function(e) { done(false); });";
        ((JavascriptExecutor) driver).executeAsyncScript(script);
    }

    private void cleanupMembers(String unique) {
        // Cleanup via jdbcTemplate - delete test members by prefix (simpler than fetch via Selenium)
        try {
            String sql = "DELETE FROM member_attribute_values WHERE member_id IN (" +
                         "SELECT id FROM members WHERE first_name LIKE ? AND band_id = 1);";
            jdbcTemplate.update(sql, "TestMember" + unique + "%");

            String deleteSql = "DELETE FROM members WHERE first_name LIKE ? AND band_id = 1;";
            jdbcTemplate.update(deleteSql, "TestMember" + unique + "%");
        } catch (Exception e) {
            // Ignore cleanup failures - database is cleaned between tests by UiTestBase @BeforeEach
        }
    }

}