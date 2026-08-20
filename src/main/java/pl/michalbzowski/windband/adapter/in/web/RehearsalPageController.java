package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/rehearsals")
@RequiredArgsConstructor
public class RehearsalPageController {

    /**
     * Redirect handler for deprecated /rehearsals route.
     * All requests to /rehearsals now redirect to /meetings as the canonical location
     * for internal band gatherings and practice sessions.
     */
    @GetMapping
    public String listPage() {
        return "redirect:/meetings";
    }

    /**
     * Redirect handler for deprecated /rehearsals/list route.
     */
    @GetMapping("/list")
    public String listFragment() {
        return "redirect:/meetings";
    }

    /**
     * Redirect handler for deprecated /rehearsals/new route.
     */
    @GetMapping("/new")
    public String newRehearsalForm() {
        return "redirect:/meetings/new";
    }

    /**
     * Redirect handler for deprecated /rehearsals/{id} detail page.
     * Since rehearsals don't have a 1:1 mapping to specific meetings,
     * redirect to the general meetings list.
     */
    @GetMapping("/{id}")
    public String rehearsalDetail(@PathVariable Long id) {
        return "redirect:/meetings";
    }

    /**
     * Redirect handler for deprecated /rehearsals/{id}/edit route.
     */
    @GetMapping("/{id}/edit")
    public String editRehearsalForm(@PathVariable Long id) {
        return "redirect:/meetings/new";
    }
}
