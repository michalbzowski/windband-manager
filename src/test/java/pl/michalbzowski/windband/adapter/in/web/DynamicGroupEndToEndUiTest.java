package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;
import pl.michalbzowski.windband.application.command.band.MemberAttributeCommandService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.band.MemberAttributeDefRepository;
import pl.michalbzowski.windband.domain.member.Group;
import pl.michalbzowski.windband.domain.member.GroupRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicGroupEndToEndUiTest extends UiTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private MemberAttributeCommandService attrCmd;
    @org.springframework.beans.factory.annotation.Autowired
    private MemberRepository memberRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private MemberAttributeDefRepository attrDefRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private GroupRepository groupRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private BandRepository bandRepository;

    private void login() {
        loginViaUi();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/login")));
    }

    private void navigateAndWaitFor(String path, org.openqa.selenium.By selector) {
        driver.get(baseUrl() + path);
        new WebDriverWait(driver, Duration.ofSeconds(15))
                .until(ExpectedConditions.presenceOfElementLocated(selector));
    }

    @Test
    void createBooleanAttribute_dynamicGroupAppearsOnListWithBadge() {
        String attrName = "E2EDyn" + UUID.randomUUID().toString().substring(0, 6);
        Band band = ensureBand();
        MemberAttributeDef def = attrCmd.createAttributeDef(band, attrName, "BOOLEAN", false, false, 0, null);
        try {
            // Navigate to group list
            login();
            navigateAndWaitFor("/groups", By.cssSelector("#groups-content"));
            // Verify the dynamic group appears in the list with the badge
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.textToBePresentInElementLocated(
                            By.cssSelector("#groups-content"), attrName));
            String listText = driver.findElement(By.cssSelector("#groups-content")).getText();
            assertThat(listText)
                    .as("Group list should contain the dynamic group name")
                    .contains(attrName);
            assertThat(listText)
                    .as("Group list should contain the dynamic badge")
                    .contains("dynamiczna");
        } finally {
            safeDelete(() -> attrDefRepository.findById(def.getId()).ifPresent(d -> attrDefRepository.delete(d)));
        }
    }

    @Test
    void setAttributeValueTrue_memberAppearsInDynamicGroup() {
        String attrName = "E2EAdd" + UUID.randomUUID().toString().substring(0, 6);
        Band band = ensureBand();
        MemberAttributeDef def = attrCmd.createAttributeDef(band, attrName, "BOOLEAN", false, false, 0, null);
        Member m = memberRepository.save(Member.create("E2E", "Test", LocalDate.of(1990, 1, 1), band));
        try {
            // Set the attribute value via service (already wired to dynamic group sync)
            attrCmd.setAttributeValue(m.getId(), def.getId(), "true");
            Group g = groupRepository.findByDynamicSource(def).orElseThrow();
            // Navigate to the dynamic group's detail page
            login();
            navigateAndWaitFor("/groups/" + g.getId(), By.cssSelector("#groups-content"));
            String pageText = driver.findElement(By.cssSelector("#groups-content")).getText();
            assertThat(pageText)
                    .as("Dynamic group detail should show the member")
                    .contains("E2E Test");
            assertThat(pageText)
                    .as("Dynamic group detail should show the info banner")
                    .contains("Grupa dynamiczna");
            // Verify no manual "Dodaj" button is visible (it's th:unless-hidden)
            assertThat(driver.findElements(By.id("open-add-members-modal-btn")))
                    .as("Manual add button should be hidden for dynamic groups")
                    .isEmpty();
        } finally {
            safeDelete(() -> memberRepository.findById(m.getId()).ifPresent(memberRepository::delete));
            safeDelete(() -> attrDefRepository.findById(def.getId()).ifPresent(d -> attrDefRepository.delete(d)));
        }
    }

    @Test
    void setAttributeValueFalse_memberRemovedFromDynamicGroup() {
        String attrName = "E2ERem" + UUID.randomUUID().toString().substring(0, 6);
        Band band = ensureBand();
        MemberAttributeDef def = attrCmd.createAttributeDef(band, attrName, "BOOLEAN", false, false, 0, null);
        Member m = memberRepository.save(Member.create("E2E", "Remove", LocalDate.of(1990, 1, 1), band));
        try {
            // Set true first
            attrCmd.setAttributeValue(m.getId(), def.getId(), "true");
            Group g = groupRepository.findByDynamicSource(def).orElseThrow();
            // Then set false
            attrCmd.setAttributeValue(m.getId(), def.getId(), "false");
            // Navigate to the dynamic group's detail page
            login();
            navigateAndWaitFor("/groups/" + g.getId(), By.cssSelector("#groups-content"));
            String pageText = driver.findElement(By.cssSelector("#groups-content")).getText();
            assertThat(pageText)
                    .as("After setting value to false, member should NOT be in the group")
                    .doesNotContain("E2E Remove");
        } finally {
            safeDelete(() -> memberRepository.findById(m.getId()).ifPresent(memberRepository::delete));
            safeDelete(() -> attrDefRepository.findById(def.getId()).ifPresent(d -> attrDefRepository.delete(d)));
        }
    }

    @Test
    void renameAttribute_groupRenamesToo() {
        String origName = "E2EOld" + UUID.randomUUID().toString().substring(0, 6);
        String newName = "E2ENew" + UUID.randomUUID().toString().substring(0, 6);
        Band band = ensureBand();
        MemberAttributeDef def = attrCmd.createAttributeDef(band, origName, "BOOLEAN", false, false, 0, null);
        try {
            // Rename via service
            attrCmd.updateAttributeDef(def.getId(), newName, "BOOLEAN", false, false, 0, null);
            // Navigate to group list
            login();
            navigateAndWaitFor("/groups", By.cssSelector("#groups-content"));
            String listText = driver.findElement(By.cssSelector("#groups-content")).getText();
            assertThat(listText)
                    .as("After rename, group list should show new name")
                    .contains(newName);
            assertThat(listText)
                    .as("After rename, group list should NOT show old name")
                    .doesNotContain(origName);
        } finally {
            safeDelete(() -> attrDefRepository.findById(def.getId()).ifPresent(d -> attrDefRepository.delete(d)));
        }
    }

    private void safeDelete(Runnable r) {
        try { r.run(); } catch (Exception ignored) { /* intentionally ignored */ }
    }

    private Band ensureBand() {
        return bandRepository.findById(1L).orElseGet(() -> bandRepository.save(Band.create("E2E Test Band", "e2e-test")));
    }
}
