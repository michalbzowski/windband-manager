package pl.michalbzowski.windband.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Exposes the {@code app.api.build-info-enabled} flag to Thymeleaf views (SpringEL
 * {@code ${@buildInfoStatus.isApiEnabled()}}) so pages can render or skip UI that talks
 * to /api/auth/build-info — a 404'd fetch would otherwise pollute the browser console.
 */
@Component
public class BuildInfoStatus {

    private final boolean apiEnabled;

    public BuildInfoStatus(@Value("${app.api.build-info-enabled:false}") boolean apiEnabled) {
        this.apiEnabled = apiEnabled;
    }

    public boolean isApiEnabled() {
        return apiEnabled;
    }
}
