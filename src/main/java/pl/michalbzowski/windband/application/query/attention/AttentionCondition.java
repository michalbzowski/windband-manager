package pl.michalbzowski.windband.application.query.attention;

import pl.michalbzowski.windband.application.dto.UpcomingItemDto;

/**
 * Pluggable condition for attention items.
 * Implementations check if a domain entity meets criteria for attention display.
 */
public interface AttentionCondition {
    /**
     * Convert matching entity to AttentionItemDto, or null if condition not met.
     * @return UpcomingItemDto if condition matches, null otherwise
     */
    UpcomingItemDto evaluate();
}
