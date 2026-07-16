package pl.michalbzowski.windband.application.service;

import pl.michalbzowski.windband.domain.member.ConsentType;

import java.util.Map;
import java.util.UUID;

/**
 * Aggregated view data for the public consent page.
 *
 * <p>All member/band associations are resolved inside the service transaction so the
 * controller does not touch lazy proxies outside of a Hibernate session.
 */
public record ConsentPageData(
        String memberName,
        String teamName,
        UUID token,
        Map<ConsentType, Boolean> consentMap
) {
}
