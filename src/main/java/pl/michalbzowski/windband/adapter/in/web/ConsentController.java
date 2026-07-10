package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.service.ConsentService;
import pl.michalbzowski.windband.domain.member.Consent;
import pl.michalbzowski.windband.domain.member.ConsentType;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.ConsentToken;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/consent")
public class ConsentController {

    private final ConsentService consentService;

    @GetMapping
    public String showConsentPage(@RequestParam("token") UUID token, Model model) {
        // Find token
        ConsentToken consentToken = consentService.getConsentTokenByToken(token);
        Member member = consentToken.getMember();
        model.addAttribute("memberName", member.getFirstName() + " " + member.getLastName());
        model.addAttribute("teamName", member.getBand() != null ? member.getBand().getName() : "Nieznany zespół");
        model.addAttribute("token", token);

        // Build consent map
        Map<ConsentType, Boolean> consentMap = new EnumMap<>(ConsentType.class);
        for (ConsentType type : ConsentType.values()) {
            consentMap.put(type, consentService.isConsentGranted(member, type));
        }
        model.addAttribute("consentMap", consentMap);
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
                                @RequestParam("grant") boolean grant) {
        consentService.updateConsents(token, type, grant);
        return "redirect:/consent?token=" + token + "&saved=true";
    }
}