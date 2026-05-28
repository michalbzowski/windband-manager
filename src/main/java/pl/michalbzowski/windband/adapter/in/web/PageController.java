package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import pl.michalbzowski.windband.application.query.event.EventQueryService;
import pl.michalbzowski.windband.application.query.inventory.InventoryQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.application.query.rehearsal.RehearsalQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final MemberQueryService memberQueryService;
    private final RehearsalQueryService rehearsalQueryService;
    private final EventQueryService eventQueryService;
    private final InventoryQueryService inventoryQueryService;
    private final BandRepository bandRepository;
    private final MemberRepository memberRepository;

    private Band getDefaultBand() {
        return bandRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Default band (id=1) not found"));
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        var band = getDefaultBand();
        
        // Stats - proste pobieranie przez listy
        long activeMembers = memberQueryService.getActiveMemberCount();
        long totalMembers = memberRepository.findAllActive().size();
        
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