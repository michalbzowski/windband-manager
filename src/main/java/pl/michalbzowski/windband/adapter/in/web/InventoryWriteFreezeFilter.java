package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Cutover B2 step 2 (analysis doc §6, windband-inventory): the WM inventory module is
 * frozen — reads stay (legacy data remains viewable), every write is rejected with 410 Gone.
 * Editing lives exclusively in the standalone Inventory app since v1.x is complete.
 *
 * <p>Freeze can be lifted per environment with {@code app.inventory-write-freeze=false}
 * (rollback lever without a redeploy of code, only a variable change + restart).
 */
@Component
public class InventoryWriteFreezeFilter extends OncePerRequestFilter {

    private static final List<String> FROZEN_PREFIXES = List.of(
            "/api/inventory",
            "/band/inventory-attributes"
    );

    private static final List<String> WRITE_METHODS = List.of("POST", "PUT", "PATCH", "DELETE");

    private final boolean freeze;
    private final String inventoryUrl;

    public InventoryWriteFreezeFilter(
            @Value("${app.inventory-write-freeze:true}") final boolean freeze,
            @Value("${app.inventory-url:https://inventory.bandmanager.pl}") final String inventoryUrl) {
        this.freeze = freeze;
        this.inventoryUrl = inventoryUrl;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        if (freeze && isWrite(request.getMethod()) && isFrozen(request.getRequestURI())) {
            response.setStatus(HttpServletResponse.SC_GONE);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"error\":\"inventory_frozen\","
                            + "\"message\":\"Moduł ekwipunku w WM jest zamrożony do odczytu — zapisów dokonaj w aplikacji Inventory.\","
                            + "\"redirect\":\"" + inventoryUrl + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isWrite(final String method) {
        return WRITE_METHODS.contains(method);
    }

    private static boolean isFrozen(final String uri) {
        return FROZEN_PREFIXES.stream().anyMatch(uri::startsWith);
    }
}
