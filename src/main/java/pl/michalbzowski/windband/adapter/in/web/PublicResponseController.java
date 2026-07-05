package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.michalbzowski.windband.application.command.event.PublicResponseService;
import pl.michalbzowski.windband.application.dto.PublicEventDetailDto;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/public/events")
@RequiredArgsConstructor
public class PublicResponseController {

    private final PublicResponseService publicResponseService;

    @GetMapping("/{token}")
    public String showEvent(@PathVariable String token, Model model,
                            @RequestParam(required = false) String responseSubmitted,
                            @RequestParam(required = false) String responseValue,
                            @RequestParam(required = false) String response) {
        try {
            // Handle email button click: record response from GET parameter
            if (response != null && !response.isBlank()) {
                publicResponseService.recordResponse(token, response);
                return "redirect:/public/events/" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                        + "?responseSubmitted=true&responseValue=" + response;
            }

            PublicEventDetailDto eventDetail = publicResponseService.getEventByToken(token);
            model.addAttribute("event", eventDetail);

            if ("true".equals(responseSubmitted)) {
                String responseLabel = switch (responseValue != null ? responseValue : "") {
                    case "CONFIRMED" -> "Będę! ✅";
                    case "DECLINED" -> "Nie będę";
                    case "LATER" -> "Dam znać później";
                    default -> "";
                };
                model.addAttribute("responseSubmitted", true);
                model.addAttribute("responseValue", responseValue);
                model.addAttribute("responseLabel", responseLabel);
            }

            return "public/event";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", "Nieprawidłowy lub nieaktualny link.");
            return "public/error";
        }
    }

    @PostMapping("/{token}/response")
    public String submitResponse(@PathVariable String token,
                                 @RequestParam String response,
                                 RedirectAttributes redirectAttributes) {
        try {
            publicResponseService.recordResponse(token, response);
            return "redirect:/public/events/" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                    + "?responseSubmitted=true&responseValue=" + response;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "Nieprawidłowy lub nieaktualny link.");
            return "redirect:/public/error";
        }
    }
}