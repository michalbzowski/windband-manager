package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import pl.michalbzowski.windband.UiTestBase;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryAttributesUiTest extends UiTestBase {

    @Test
    void shouldDisplayTabsWithConsistentStyling() {
        loginAndNavigateTo("/band/inventory-attributes?type=UNIFORM");

        assertThat(driver.getTitle()).contains("Atrybuty");

        // Verify tabs container exists with border-bottom styling (matching inventory page)
        var tabsContainer = driver.findElement(By.id("attribute-tabs"));
        assertThat(tabsContainer).isNotNull();

        // Verify all tab links are present
        var uniformTab = driver.findElement(By.xpath("//a[contains(@href, 'type=UNIFORM')]"));
        var instrumentTab = driver.findElement(By.xpath("//a[contains(@href, 'type=INSTRUMENT')]"));
        var orderTab = driver.findElement(By.xpath("//a[contains(@href, 'type=ORDER')]"));
        var awardTab = driver.findElement(By.xpath("//a[contains(@href, 'type=AWARD')]"));
        var memberTab = driver.findElement(By.xpath("//a[contains(@href, 'type=MEMBER')]"));

        assertThat(uniformTab).isNotNull();
        assertThat(instrumentTab).isNotNull();
        assertThat(orderTab).isNotNull();
        assertThat(awardTab).isNotNull();
        assertThat(memberTab).isNotNull();

        // Verify selected tab has primary class
        assertThat(uniformTab.getAttribute("class")).contains("primary");

        // Verify non-selected tabs have secondary class
        assertThat(instrumentTab.getAttribute("class")).contains("secondary");
        assertThat(orderTab.getAttribute("class")).contains("secondary");

        // Test tab switching by clicking on Instrumenty tab
        instrumentTab.click();

        // Verify URL changed
        assertThat(driver.getCurrentUrl()).contains("type=INSTRUMENT");

        // Verify Instrumenty tab now has primary class
        var instrumentTabAfterClick = driver.findElement(By.xpath("//a[contains(@href, 'type=INSTRUMENT')]"));
        assertThat(instrumentTabAfterClick.getAttribute("class")).contains("primary");

        // Verify UNIFORM tab now has secondary class
        var uniformTabAfterClick = driver.findElement(By.xpath("//a[contains(@href, 'type=UNIFORM')]"));
        assertThat(uniformTabAfterClick.getAttribute("class")).contains("secondary");
    }
}