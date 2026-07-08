package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.adapter.out.persistence.member.ConsentSpringDataRepository;
import pl.michalbzowski.windband.adapter.out.persistence.member.ConsentTokenSpringDataRepository;
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

    private final ConsentTokenSpringDataRepository tokenRepository;
    private final ConsentSpringDataRepository consentRepository;

    @GetMapping
    public String showConsentPage(@RequestParam("token") UUID token, Model model) {
        // Find token
        var tokenOpt = tokenRepository.findByToken(token);
        if (tokenOpt.isEmpty()) {
            return "error/404"; // not found
        }
        var consentToken = tokenOpt.get();
        Member member = consentToken.getMember();
        model.addAttribute("memberName", member.getFirstName() + " " + member.getLastName());
        model.addAttribute("teamName", member.getBand() != null ? member.getBand().getName() : "Nieznany zespół");
        model.addAttribute("token", token);

        // Build consent map
        Map<ConsentType, Boolean> consentMap = new EnumMap<>(ConsentType.class);
        for (ConsentType type : ConsentType.values()) {
            consentRepository.findByMemberAndConsentType(member, type)
                    .ifPresentOrElse(
                            c -> consentMap.put(type, c.isGranted()),
                            () -> consentMap.put(type, false) // default false
                    );
        }
        model.addAttribute("consentMap", consentMap);
        model.addAttribute("consentTypes", ConsentType.values());

        // Provide display names (could use enum method)
        model.addAttribute("displayNames", Map.of(
                ConsentType.EVENTS, "Wydarzenia",
                ConsentType.MANAGER_MESSAGES, "Wiadomości od zarządzającego",
                ConsentType.INVENTORY_SUMMARY, "Podsumowania inwentaryzacji"
        ));

        return "consent";
    }

    @PostMapping
    public String updateConsent(@RequestParam("token") UUID token,
                                @RequestParam("type") ConsentType type,
                                @RequestParam("grant") boolean grant) {
        var tokenOpt = tokenRepository.findByToken(token);
        if (tokenOpt.isEmpty()) {
            return "error/404";
        }
        var consentToken = tokenOpt.get();
        Member member = consentToken.getMember();

        consentRepository.findByMemberAndConsentType(member, type)
                .ifPresentOrElse(
                        consent -> consent.setGranted(grant),
                        () -> consentRepository.save(new Consent(member, type, grant))
                );
        return "redirect:/consent?token=" + token + "&saved=true";
    }
}