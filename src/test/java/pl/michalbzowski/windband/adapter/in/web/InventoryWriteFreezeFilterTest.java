package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cutover B2 §6.2: writes to the legacy WM inventory surface must answer 410 Gone while the
 * freeze is on; reads and everything outside the module must pass through untouched.
 */
class InventoryWriteFreezeFilterTest {

    private static final String INVENTORY_URL = "https://inventory.bandmanager.pl";

    private final InventoryWriteFreezeFilter filter =
            new InventoryWriteFreezeFilter(true, INVENTORY_URL);
    private final InventoryWriteFreezeFilter unfrozen =
            new InventoryWriteFreezeFilter(false, INVENTORY_URL);

    @Test
    void rejectsApiWritesWith410GoneAndRedirect() throws Exception {
        for (var method : new String[]{"POST", "PUT", "PATCH", "DELETE"}) {
            var request = new MockHttpServletRequest(method, "/api/inventory/items");
            request.setRequestURI(method.equals("DELETE") ? "/api/inventory/items/3" : "/api/inventory/items");
            var response = new MockHttpServletResponse();
            var passed = new AtomicBoolean();

            filter.doFilter(request, response, chainRecording(passed));

            assertThat(passed).as(method + " must not reach the controller").isFalse();
            assertThat(response.getStatus()).isEqualTo(410);
            assertThat(response.getContentAsString()).contains("inventory_frozen", INVENTORY_URL);
        }
    }

    @Test
    void rejectsPageWritesOnAttributeDefinitions() throws Exception {
        var request = new MockHttpServletRequest("POST", "/band/inventory-attributes");
        request.setRequestURI("/band/inventory-attributes");
        var response = new MockHttpServletResponse();
        var passed = new AtomicBoolean();

        filter.doFilter(request, response, chainRecording(passed));

        assertThat(passed).isFalse();
        assertThat(response.getStatus()).isEqualTo(410);
    }

    @Test
    void letsReadsThrough() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/inventory/items");
        var response = new MockHttpServletResponse();
        var passed = new AtomicBoolean();

        filter.doFilter(request, response, chainRecording(passed));

        assertThat(passed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void leavesTheRestOfWindbandManagerUntouched() throws Exception {
        var request = new MockHttpServletRequest("POST", "/bands/1/events");
        request.setRequestURI("/bands/1/events");
        var response = new MockHttpServletResponse();
        var passed = new AtomicBoolean();

        filter.doFilter(request, response, chainRecording(passed));

        assertThat(passed).as("non-inventory writes must not be frozen").isTrue();
    }

    @Test
    void freezeSwitchIsTheRollbackLever() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/inventory/items");
        var response = new MockHttpServletResponse();
        var passed = new AtomicBoolean();

        unfrozen.doFilter(request, response, chainRecording(passed));

        assertThat(passed).as("windband.inventory.freeze=false must let writes through again").isTrue();
    }

    private static FilterChain chainRecording(final AtomicBoolean passed) {
        return (req, res) -> passed.set(true);
    }
}
