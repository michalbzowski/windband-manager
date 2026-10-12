package pl.michalbzowski.windband.adapter.in.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the URLs of EXTERNAL services (separate deployments linked from the
 * WM UI) as global Thymeleaf model attributes.
 *
 * <p>The inventory link is environment-parameterized per the integration
 * contract with windband-inventory (decision B11: v1 navigation is
 * one-directional — WM links out, inventory never links back into WM pages):
 * <ul>
 *   <li>production: {@code INVENTORY_URL} env var (Railway service variable),
 *       defaulting to the public domain so a fresh environment works even
 *       before the variable is set;</li>
 *   <li>local dev: {@code application-local.yml} pins it to the local
 *       windband-inventory dev server ({@code http://localhost:5173}, matching
 *       the Keycloak client's valid redirect URIs);</li>
 *   <li>tests: {@code application-test.yml} uses a marker URL asserted by
 *       {@code InventoryExternalLinkUiTest}.</li>
 * </ul>
 *
 * <p>Access control needs no URL parameter here: the external service
 * authenticates against the same Keycloak realm as WM (SSO), so only users
 * known to WM can log in there, and per-user band data is served by the
 * Bearer-JWT v1 API.
 */
@ControllerAdvice
public class ExternalLinksModelAdvice {

    private final String inventoryExternalUrl;

    public ExternalLinksModelAdvice(
            @Value("${app.inventory-url:}") String inventoryExternalUrl) {
        this.inventoryExternalUrl = inventoryExternalUrl;
    }

    @ModelAttribute("inventoryExternalUrl")
    public String inventoryExternalUrl() {
        return inventoryExternalUrl;
    }
}
