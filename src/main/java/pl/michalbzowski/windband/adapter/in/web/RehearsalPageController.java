package pl.michalbzowski.windband.adapter.in.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Deprecated /rehearsals routes — all redirect to the unified /meetings views.
 * Kept to preserve bookmarks and external links after the events/rehearsals unify.
 */
@Controller
@RequestMapping("/rehearsals")
public class RehearsalPageController {

    @GetMapping
    public RedirectView listPage() {
        return new RedirectView("/meetings", true);
    }

    @GetMapping("/list")
    public RedirectView listFragment() {
        return new RedirectView("/meetings", true);
    }

    @GetMapping("/new")
    public RedirectView newRehearsalForm() {
        return new RedirectView("/meetings/new", true);
    }

    @GetMapping("/{id}")
    public RedirectView rehearsalDetail(@PathVariable Long id) {
        return new RedirectView("/meetings/" + id, true);
    }

    @GetMapping("/{id}/edit")
    public RedirectView editRehearsalForm(@PathVariable Long id) {
        return new RedirectView("/meetings/new", true);
    }
}
