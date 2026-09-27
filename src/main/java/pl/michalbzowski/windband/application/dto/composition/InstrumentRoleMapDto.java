package pl.michalbzowski.windband.application.dto.composition;

/**
 * View-side projection of an {@link pl.michalbzowski.windband.domain.composition.InstrumentRoleMap}
 * row (US-7.3 admin UI) — everything the Thymeleaf list page references, with no lazy
 * {@code Band} proxy leaked into the template.
 */
public record InstrumentRoleMapDto(
        Long id,
        String sourceTag,
        String targetRolePattern,
        String description) {
}
