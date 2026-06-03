package pl.michalbzowski.windband.adapter.in.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import lombok.RequiredArgsConstructor;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.event.EventQueryService;
import pl.michalbzowski.windband.application.query.inventory.InventoryQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.application.query.rehearsal.RehearsalQueryService;
import pl.michalbzowski.windband.domain.band.Band;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final MemberQueryService memberQueryService;
    private final RehearsalQueryService rehearsalQueryService;
    private final EventQueryService eventQueryService;
    private final InventoryQueryService inventoryQueryService;
    private final BandQueryService bandQueryService;

    private Band getDefaultBand() {
        return bandQueryService.getDefaultBand();
    }

    @Value("${app.keycloak.registration-redirect-url:}")
    private String keycloakRegistrationUrl;

    /**
     * Registration page. In OIDC mode, unauthenticated users are redirected
     * to Keycloak registration. Authenticated users without a team see the
     * team creation form.
     */
    @GetMapping("/register")
    public String register(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        if (oidcUser == null) {
            // Not authenticated — redirect to Keycloak registration page
            if (keycloakRegistrationUrl != null && !keycloakRegistrationUrl.isBlank()) {
                return "redirect:" + keycloakRegistrationUrl;
            }
            return "redirect:/";
        }

        if (oidcUser instanceof WindbandOidcUser wu && wu.getActiveTeamId() == null) {
            model.addAttribute("email", wu.getWbEmail());
            model.addAttribute("username", wu.getWbUsername());
            return "register";
        }

        return "redirect:/";
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        var band = getDefaultBand();
        
        // Stats - proste pobieranie przez listy
        long activeMembers = memberQueryService.getActiveMemberCount();
        long totalMembers = memberQueryService.findAllActiveMembers().size();
        
        LocalDate today = LocalDate.now();
        LocalDate weekEnd = today.plusDays(7);
        long rehearsalsThisWeek = rehearsalQueryService.getRehearsalCountBetween(today, weekEnd);
        
        // Events - używamy countBetween
        long upcomingEvents = eventQueryService.getEventCountBetween(today, LocalDate.of(2099, 12, 31));
        
        // Inventory - proste liczenie
        var orders = inventoryQueryService.getAllOrders();
        long activeOrders = orders.stream()
                .filter(o -> "SUBMITTED".equals(o.status()) || 
                            "PENDING_APPROVAL".equals(o.status()) ||
                            "IN_PRODUCTION".equals(o.status()) ||
                            "SHIPPED".equals(o.status()))
                .count();
        
        long totalUniforms = inventoryQueryService.getAllUniformItems().size();
        long totalInstruments = inventoryQueryService.getAllInstrumentItems().size();
        
        model.addAttribute("totalMembers", totalMembers);
        model.addAttribute("activeMembers", activeMembers);
        model.addAttribute("rehearsalsThisWeek", rehearsalsThisWeek);
        model.addAttribute("upcomingEvents", upcomingEvents);
        model.addAttribute("activeOrders", activeOrders);
        model.addAttribute("totalUniforms", totalUniforms);
        model.addAttribute("totalInstruments", totalInstruments);
        
        return "dashboard";
    }
}