package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #267 — after adding a member the app menu is gone until a hard reload.
 *
 * <p>Reproduced on {@code origin/main}: the "Dodaj członka" action in the members page header
 * is rendered with {@code hx-target="#content"} (the only page header in the app that aims at
 * the whole {@code <main>} — tags, events, rehearsals all aim at their own content div).
 * {@code GET /members/new} answers an HTMX request with the fragment
 * {@code members/form :: #members-content}, which does NOT contain
 * {@code <header id="dashboardHeader">} — that header lives inside {@code #content}. The
 * innerHTML swap therefore throws away the bar holding the ☰ menu button, and saving the
 * member swaps the list back into {@code #members-content} — a node that is now the whole
 * page — so the menu never comes back without a reload.
 *
 * <p>The row's "Edytuj" button already targets {@code #members-content}; the add button must
 * match it.
 */
class MemberMenuAfterSaveUiTest extends UiTestBase {

    @Test
    void topBarWithMenuButtonSurvivesAddingAMember() {
        String surname = "Kowalski-" + UUID.randomUUID().toString().substring(0, 8);

        loginAndNavigateTo("/members");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(25));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        wait.until(d -> d.findElement(By.id("add-member-btn")));
        assertThat(driver.findElements(By.id("dashboardHeader")))
                .as("baseline: the top bar with the ☰ menu is on the list page")
                .isNotEmpty();

        driver.findElement(By.id("add-member-btn")).click();
        WebElement firstName = wait.until(d -> d.findElement(By.cssSelector("#member-form input[name='firstName']")));
        firstName.clear();
        firstName.sendKeys("Anna");
        WebElement lastName = driver.findElement(By.cssSelector("#member-form input[name='lastName']"));
        lastName.clear();
        lastName.sendKeys(surname);

        js.executeScript("document.querySelector('#member-form button[type=submit]').click();");

        // The save returns the user to the member list — wait for the new row.
        wait.until(d -> d.findElements(By.cssSelector("#members-content tr"))
                .stream()
                .anyMatch(r -> r.getText().contains(surname)));

        assertThat(driver.findElements(By.id("dashboardHeader")))
                .as("the ☰ top bar must survive the add-member round trip (issue #267)")
                .isNotEmpty();

        // It must also still work, not merely exist in some detached subtree.
        WebElement menuButton = driver.findElement(By.cssSelector("#dashboardHeader .menu-btn"));
        assertThat(menuButton.isDisplayed()).as("menu button visible after saving").isTrue();
        menuButton.click();
        wait.until(d -> {
            WebElement modal = d.findElement(By.id("menu-modal"));
            return modal.isDisplayed() ? modal : null;
        });
    }
}
