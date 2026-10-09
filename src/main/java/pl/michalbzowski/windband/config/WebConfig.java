package pl.michalbzowski.windband.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pl.michalbzowski.windband.adapter.in.web.HtmxRequestInterceptor;

/**
 * Web MVC configuration: registers the HTMX request interceptor.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HtmxRequestInterceptor());
    }

    /**
     * Defensive binding (Issue #117): client scripts that build URLs by string
     * concatenation (e.g. {@code '/members/list?focus=' + value}) can send the
     * literal string "null" when no value is set, and Spring's default Long
     * binding turns that into a 500 — which made the "Pokaż nieaktywnych"
     * toggle button appear completely dead. Treat blank / literal "null" as an
     * absent value so optional numeric request parameters degrade gracefully.
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, Long.class, source -> {
            if (source == null) {
                return null;
            }
            String trimmed = source.trim();
            if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
                return null;
            }
            return Long.parseLong(trimmed);
        });
    }
}