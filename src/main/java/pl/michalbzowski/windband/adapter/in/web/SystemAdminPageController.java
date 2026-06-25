package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import pl.michalbzowski.windband.domain.user.AppUserRepository;

/**
 * Serves the system admin management HTML page.
 * REST API is handled by {@link SystemAdminController}.
 */
@Controller
@RequestMapping("/admin/system-admins")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
@RequiredArgsConstructor
public class SystemAdminPageController {

    private final AppUserRepository appUserRepository;

    @GetMapping
    public String systemAdminsPage(Model model) {
        model.addAttribute("users", appUserRepository.findAll());
        return "admin/system-admins";
    }
}
