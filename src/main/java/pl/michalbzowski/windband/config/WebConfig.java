package pl.michalbzowski.windband.config;

import org.springframework.context.annotation.Configuration;
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
}