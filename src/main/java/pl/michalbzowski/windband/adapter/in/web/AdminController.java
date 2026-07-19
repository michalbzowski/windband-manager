package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.michalbzowski.windband.application.command.member.DynamicGroupService;

/**
 * System-admin only endpoints (guarded by SecurityConfig: {@code /admin/**}
 * requires ROLE_ADMIN or ROLE_SYSTEM_ADMIN).
 *
 * <p>Exposes an on-demand trigger for the dynamic-group backfill so the
 * "Aktywni" group (and any attribute-backed groups) can be (re)created
 * without a full application restart — e.g. when a new band is created
 * after startup, or when the startup backfill was skipped.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final DynamicGroupService dynamicGroupService;

    @PostMapping("/groups/backfill")
    public String backfillDynamicGroups(RedirectAttributes redirectAttributes) {
        try {
            dynamicGroupService.ensureActiveGroupsForAllBands();
            redirectAttributes.addFlashAttribute("toastMessage", "Zaktualizowano grupy dynamiczne.");
        } catch (Exception e) {
            log.error("[admin] Manual dynamic-group backfill failed: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("toastMessage",
                    "Błąd podczas aktualizacji grup dynamicznych: " + e.getMessage());
        }
        return "redirect:/groups";
    }
}
