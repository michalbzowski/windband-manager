package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.service.ConsentService;
import pl.michalbzowski.windband.application.service.ConsentPageData;
import pl.michalbzowski.windband.domain.member.ConsentType;

import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/consent")
public class ConsentController {

    private final ConsentService consentService;

    @GetMapping
    public String showConsentPage(@RequestParam("token") UUID token, Model model) {
        ConsentPageData data = consentService.getConsentPageData(token);
        model.addAttribute("memberName", data.memberName());
        model.addAttribute("teamName", data.teamName());
        model.addAttribute("token", data.token());
        model.addAttribute("consentMap", data.consentMap());
        model.addAttribute("consentTypes", ConsentType.values());

        // Provide display names (could use enum method)
        model.addAttribute("displayNames", Map.of(
                ConsentType.EVENTS, "Wydarzenia",
                ConsentType.MANAGER_MESSAGES, "Wiadomości od zarządzającego",
                ConsentType.INVENTORY_SUMMARY, "Podsumowania inwentaryzacji"
        ));

        return "consent/form";
    }

    @PostMapping
    public String updateConsent(@RequestParam("token") UUID token,
                                @RequestParam("type") ConsentType type,
                                @RequestParam(value = "grant", required = false, defaultValue = "false") boolean grant) {
        consentService.updateConsents(token, type, grant);
        return "redirect:/consent?token=" + token + "&saved=true";
    }
}
