package pl.michalbzowski.windband.adapter.in.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpSession;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.attention.AttentionItemCollector;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.band.MemberAttributeQueryService;
import pl.michalbzowski.windband.application.dto.UpcomingItemDto;
import pl.michalbzowski.windband.application.query.event.EventQueryService;
import pl.michalbzowski.windband.application.query.inventory.InventoryQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.application.query.rehearsal.RehearsalQueryService;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final MemberQueryService memberQueryService;
    private final RehearsalQueryService rehearsalQueryService;
    private final EventQueryService eventQueryService;
    private final InventoryQueryService inventoryQueryService;
    private final BandQueryService bandQueryService;
    private final TeamQueryService teamQueryService;
    private final MemberAttributeQueryService attributeQueryService;
    private final AttentionItemCollector attentionItemCollector;

    private Band getDefaultBand() {
        return bandQueryService.getDefaultBand();
    }

    @Value("${app.keycloak.registration-redirect-url:}")
    private String keycloakRegistrationUrl;

    /**
     * Registration page. In OIDC mode, unauthenticated users are redirected
     * to Keycloak registration. Authenticated users (with or without a team)
     * see the team creation form to create additional teams.
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

        if (oidcUser instanceof WindbandOidcUser wu) {
            model.addAttribute("email", wu.getWbEmail());
            model.addAttribute("username", wu.getWbUsername());
        }
        return "register";
    }

@GetMapping("/")
    public String dashboard(@AuthenticationPrincipal OidcUser oidcUser, HttpSession session, Model model) {
        Long activeTeamId = null;
        if (oidcUser instanceof WindbandOidcUser wu) {
            Long sessionTeamId = (Long) session.getAttribute("activeTeamId");
            if (sessionTeamId != null) {
                boolean stillBelongs = teamQueryService.getUserTeam(wu.getUserId(), sessionTeamId).isPresent();
                if (stillBelongs) {
                    activeTeamId = sessionTeamId;
                } else {
                    activeTeamId = wu.getActiveTeamId();
                }
            } else {
                activeTeamId = wu.getActiveTeamId();
            }
        }

        // If no team is set, don't show any data
        if (activeTeamId == null) {
            model.addAttribute("totalMembers", 0L);
            model.addAttribute("activeMembers", 0L);
            model.addAttribute("rehearsalsThisWeek", 0L);
            model.addAttribute("upcomingEvents", 0L);
            model.addAttribute("activeOrders", 0L);
            model.addAttribute("totalUniforms", 0L);
            model.addAttribute("totalInstruments", 0L);
            return "dashboard";
        }

        var band = bandQueryService.getBandById(activeTeamId);

        // Stats - z filtracją po zespole
        long activeMembers = memberQueryService.getActiveMemberCount(activeTeamId);
        long totalMembers = memberQueryService.findAllActiveMembers(activeTeamId).size();

        LocalDate today = LocalDate.now();
        LocalDate weekEnd = today.plusDays(7);
        long rehearsalsThisWeek = rehearsalQueryService.getRehearsalCountBetween(today, weekEnd, activeTeamId);

        // Events - używamy countBetween
        long upcomingEvents = eventQueryService.getEventCountBetween(today, LocalDate.of(2099, 12, 31), activeTeamId);

        // Inventory - filtered by activeTeamId
        var orders = inventoryQueryService.getAllOrders(activeTeamId);
        long activeOrders = orders.stream()
                .filter(o -> "SUBMITTED".equals(o.status()) ||
                            "PENDING_APPROVAL".equals(o.status()) ||
                            "IN_PRODUCTION".equals(o.status()) ||
                            "SHIPPED".equals(o.status()))
                .count();

        long totalUniforms = inventoryQueryService.getAllUniformItems(activeTeamId).size();
        long totalInstruments = inventoryQueryService.getAllInstrumentItems(activeTeamId).size();

        model.addAttribute("totalMembers", totalMembers);
        model.addAttribute("activeMembers", activeMembers);
        model.addAttribute("rehearsalsThisWeek", rehearsalsThisWeek);
        model.addAttribute("upcomingEvents", upcomingEvents);
        model.addAttribute("activeOrders", activeOrders);
        model.addAttribute("totalUniforms", totalUniforms);
        model.addAttribute("totalInstruments", totalInstruments);

        // BOOLEAN member attributes -> count of members with value "true" per attribute
        Map<String, Long> booleanAttributeCounts = attributeQueryService.getBooleanAttributeCounts(band);
        model.addAttribute("booleanAttributeCounts", booleanAttributeCounts);

        List<UpcomingItemDto> upcoming = collectUpcomingItems(activeTeamId, today, 5);
        model.addAttribute("upcoming", upcoming);

        // Calculate stats for mini-stats bar
        int attendancePercent = 0;
        int totalCount = upcoming.size();
        long activeMembersCount = activeMembers;

        if (!upcoming.isEmpty()) {
            int sumAttendance = 0;
            int countWithAttendance = 0;
            for (UpcomingItemDto item : upcoming) {
                if (item.attendancePercentage() != null) {
                    sumAttendance += item.attendancePercentage();
                    countWithAttendance++;
                }
            }
            if (countWithAttendance > 0) {
                attendancePercent = sumAttendance / countWithAttendance;
            }
        }

        model.addAttribute("upcomingStats", Map.of(
            "attendancePercent", attendancePercent,
            "totalCount", totalCount,
            "activeMembers", activeMembersCount
        ));

        // Attention items: collect using pluggable conditions
        List<UpcomingItemDto> attentionItems = attentionItemCollector.collect(activeTeamId);
        model.addAttribute("attentionItems", attentionItems);

        return "dashboard";
    }

    private List<UpcomingItemDto> collectUpcomingItems(Long activeTeamId, LocalDate from, int limit) {
        LocalDate to = from.plusYears(1);

        List<UpcomingItemDto> items = new ArrayList<>();

        for (Rehearsal rehearsal : rehearsalQueryService.getRehearsalsBetween(from, to, activeTeamId)) {
            long present = rehearsal.getPresentCount();
            long total = rehearsal.getAttendances().size();
            Integer attendancePercentage = total > 0 ? (int) Math.round((present * 100.0) / total) : 0;
            items.add(new UpcomingItemDto(
                    "REHEARSAL",
                    rehearsal.getId(),
                    "Próba",
                    "Obecność " + present + "/" + total,
                    rehearsal.getDate(),
                    rehearsal.getStartTime(),
                    rehearsal.getDate().isEqual(from) ? "Dziś" : formatRelativeLabel(rehearsal.getDate(), from),
                    "/rehearsals/" + rehearsal.getId(),
                    "🎵",
                    attendancePercentage
            ));
        }

        for (BandEvent event : eventQueryService.getEventsBetween(from, to, activeTeamId)) {
            items.add(new UpcomingItemDto(
                    "EVENT",
                    event.getId(),
                    event.getName(),
                    event.getLocation() != null && !event.getLocation().isBlank() ? event.getLocation() : event.getEventType().name(),
                    event.getDate(),
                    event.getStartTime(),
                    event.getDate().isEqual(from) ? "Dziś" : formatRelativeLabel(event.getDate(), from),
                    "/events/" + event.getId(),
                    "🎪",
                    null
            ));
        }

        return items.stream()
                .sorted(Comparator
                        .comparing(UpcomingItemDto::date)
                        .thenComparing(item -> item.startTime() != null ? item.startTime() : java.time.LocalTime.MAX)
                        .thenComparing(UpcomingItemDto::title))
                .limit(limit)
                .toList();
    }

    private String formatRelativeLabel(LocalDate date, LocalDate from) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, date);
        if (days <= 0) return "Dziś";
        if (days == 1) return "Jutro";
        if (days < 7) return "Za " + days + " dni";
        return date.toString();
    }
}
