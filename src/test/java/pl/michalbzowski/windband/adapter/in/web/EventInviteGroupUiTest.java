package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

import pl.michalbzowski.windband.UiTestBase;

class EventInviteGroupUiTest extends UiTestBase {

    @Test
    void inviteGroupAddsAllGroupMembersToEvent() throws Exception {
        WebDriver driver = getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        loginAsAdmin(driver, wait);

        String uid = "grp" + System.nanoTime();
        String groupName = "GrupaTest" + uid;
        // Create two members via UI
        createMemberViaUi(driver, wait, "Alpha" + uid, "Kowalski" + uid);
        createMemberViaUi(driver, wait, "Beta" + uid, "Nowak" + uid);
        Long alphaId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM members WHERE first_name = ?", Long.class, "Alpha" + uid);
        Long betaId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM members WHERE first_name = ?", Long.class, "Beta" + uid);
        System.out.println("[TEST] alphaId=" + alphaId + " betaId=" + betaId);

        // Create a manual group via API and add both members
        // Selenium: synchroniczny XHR zwraca ID dopiero po zakończeniu zapisu grupy.
        String groupIdStr = (String) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "var xhr = new XMLHttpRequest();" +
                "xhr.open('POST', '/api/groups', false);" +
                "xhr.setRequestHeader('Content-Type', 'application/json');" +
                "xhr.send(JSON.stringify({name: 'GrupaTest" + uid + "', description: 'test'}));" +
                "return JSON.parse(xhr.responseText).id.toString();");
        Long groupId = groupIdStr != null ? Long.valueOf(groupIdStr) : null;
        System.out.println("[TEST] groupId=" + groupId);
        assertThat(groupId).isNotNull();

        addMemberToGroupViaApi(driver, groupId, alphaId);
        addMemberToGroupViaApi(driver, groupId, betaId);

        // Create an event via API
        // Selenium: synchroniczny XHR zwraca ID dopiero po zakończeniu zapisu wydarzenia.
        String eventIdStr = (String) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "var xhr = new XMLHttpRequest();" +
                "xhr.open('POST', '/api/events', false);" +
                "xhr.setRequestHeader('Content-Type', 'application/json');" +
                "xhr.send(JSON.stringify({name: 'Wydarzenie" + uid + "', date: '" + java.time.LocalDate.now() + "'," +
                "  startTime: '18:00', endTime: '20:00', paymentType: 'FREE', eventType: 'CONCERT', bandId: 1}));" +
                "return JSON.parse(xhr.responseText).id.toString();");
        Long eventId = eventIdStr != null ? Long.valueOf(eventIdStr) : null;
        System.out.println("[TEST] eventId=" + eventId);
        assertThat(eventId).isNotNull();

        // Open event detail
        driver.get(baseUrl() + "/events/" + eventId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("open-invite-group-modal-btn")));

        // Click the open-invite-group-modal-btn to open modal (JS click: button sits under sticky nav)
        jsClick(driver.findElement(By.id("open-invite-group-modal-btn")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("invite-group-modal")));
        wait.until(d -> (Boolean) ((org.openqa.selenium.JavascriptExecutor) d).executeScript(
                "return document.getElementById('invite-group-modal').open === true;"));

// Check the group in the modal and click checkbox
        driver.findElement(By.xpath(
                "//*[@id='invite-group-modal']//label[contains(., '" + groupName + "')]/preceding-sibling::input[@type='checkbox']"))
                .click();

        // Click invite selected
        jsClick(driver.findElement(By.id("invite-group-selected-btn")));

        // Wait for participants table to contain both members
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            driver.get(baseUrl() + "/events/" + eventId);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));
            var rows = driver.findElements(
                    By.cssSelector("#participants-table tbody tr[data-member-id]"));
            assertThat(rows)
                    .as("Both group members should appear as participants after inviting the group")
                    .hasSizeGreaterThanOrEqualTo(2);
            boolean hasAlpha = rows.stream().anyMatch(r -> r.getAttribute("data-member-id").equals(String.valueOf(alphaId)));
            boolean hasBeta = rows.stream().anyMatch(r -> r.getAttribute("data-member-id").equals(String.valueOf(betaId)));
            assertThat(hasAlpha).isTrue();
            assertThat(hasBeta).isTrue();
        });
    }

    private void createMemberViaUi(WebDriver driver, WebDriverWait wait, String first, String last) {
        loginAndNavigateTo("/members");
        driver.findElement(By.xpath("//button[contains(., 'Dodaj członka')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("member-form")));
        fillField("firstName", first);
        fillField("lastName", last);
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='dateOfBirth']\").value = '1990-05-15';");
        driver.findElement(By.cssSelector("#member-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#members-content table")));
    }

    private void addMemberToGroupViaApi(WebDriver driver, Long groupId, Long memberId) {
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "return fetch('/api/groups/' + arguments[0] + '/members/' + arguments[1], {" +
                "  method: 'POST'" +
                "});", groupId, String.valueOf(memberId));
    }

    private WebDriver getDriver() {
        return driver;
    }

    private void loginAsAdmin(WebDriver driver, WebDriverWait wait) {
        driver.get(baseUrl() + "/login");
        driver.findElement(org.openqa.selenium.By.name("username")).sendKeys("admin");
        driver.findElement(org.openqa.selenium.By.name("password")).sendKeys("admin");
        driver.findElement(org.openqa.selenium.By.cssSelector("button[type='submit']")).click();
        wait.until(org.openqa.selenium.support.ui.ExpectedConditions.not(
                org.openqa.selenium.support.ui.ExpectedConditions.urlContains("/login")));
    }

    private void fillField(String name, String value) {
        org.openqa.selenium.WebElement el = driver.findElement(org.openqa.selenium.By.name(name));
        el.clear();
        el.sendKeys(value);
    }

    private void jsClick(org.openqa.selenium.WebElement el) {
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
    }
}
