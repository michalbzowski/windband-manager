package pl.michalbzowski.windband.adapter.in.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.config.JwtConfig;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtConfig jwtConfig;

    @PostMapping("/login")
    public ResponseEntity<Void> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        if (auth.isAuthenticated()) {
            String token = jwtConfig.generateToken(request.getUsername());
            Cookie cookie = new Cookie("JWT", token);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(86400);
            cookie.setAttribute("SameSite", "Lax");
            cookie.setDomain(null);
            response.addCookie(cookie);
            response.setStatus(200);
            return ResponseEntity.ok().build();
        }

        return ResponseEntity.status(401).build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("JWT", null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.ok().build();
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
