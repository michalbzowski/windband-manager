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

        // Verify all tab buttons are present
        var uniformsTab = driver.findElement(By.xpath("//button[@data-tab='uniforms']"));
        var instrumentsTab = driver.findElement(By.xpath("//button[@data-tab='instruments']"));
        var ordersTab = driver.findElement(By.xpath("//button[@data-tab='orders']"));
        var awardsTab = driver.findElement(By.xpath("//button[@data-tab='awards']"));
        var membersTab = driver.findElement(By.xpath("//button[@data-tab='members']"));

        assertThat(uniformsTab).isNotNull();
        assertThat(instrumentsTab).isNotNull();
        assertThat(ordersTab).isNotNull();
        assertThat(awardsTab).isNotNull();
        assertThat(membersTab).isNotNull();

        // Verify selected tab has primary class
        assertThat(uniformsTab.getAttribute("class")).contains("primary");

        // Verify non-selected tabs have secondary class
        assertThat(instrumentsTab.getAttribute("class")).contains("secondary");
        assertThat(ordersTab.getAttribute("class")).contains("secondary");

        // Test tab switching by clicking on Instrumenty tab
        instrumentsTab.click();

        // Verify Instrumenty tab now has primary class
        var instrumentsTabAfterClick = driver.findElement(By.xpath("//button[@data-tab='instruments']"));
        assertThat(instrumentsTabAfterClick.getAttribute("class")).contains("primary");

        // Verify UNIFORM tab now has secondary class
        var uniformsTabAfterClick = driver.findElement(By.xpath("//button[@data-tab='uniforms']"));
        assertThat(uniformsTabAfterClick.getAttribute("class")).contains("secondary");
    }
}