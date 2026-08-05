package pl.michalbzowski.windband.application.query.attention;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.michalbzowski.windband.application.dto.UpcomingItemDto;
import pl.michalbzowski.windband.application.query.event.EventQueryService;
import pl.michalbzowski.windband.application.query.rehearsal.RehearsalQueryService;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects attention items from domain entities using pluggable conditions.
 * Easily extensible: add new conditions without modifying this class.
 *
 * Usage:
 *   List<UpcomingItemDto> attentionItems = attentionCollector.collect(activeTeamId);
 */
@Service
@RequiredArgsConstructor
public class AttentionItemCollector {

    private final EventQueryService eventQueryService;
    private final RehearsalQueryService rehearsalQueryService;

    /**
     * Collect all attention items for a given team.
     * @param teamId the team to collect items for
     * @return list of attention items (empty if none match)
     */
    public List<UpcomingItemDto> collect(Long teamId) {
        List<UpcomingItemDto> items = new ArrayList<>();

        // Collect attention items from past events
        List<BandEvent> pastEvents = eventQueryService.getPastEvents(teamId);
        for (BandEvent event : pastEvents) {
            // Condition 1: past paid-split event with unpaid confirmed participants
            AttentionCondition condition = new PastPaidSplitUnpaidCondition(event);
            UpcomingItemDto item = condition.evaluate();
            if (item != null) {
                items.add(item);
            }
        }

        // Collect attention items from past rehearsals
        List<Rehearsal> pastRehearsals = rehearsalQueryService.getPastRehearsals(teamId);
        for (Rehearsal rehearsal : pastRehearsals) {
            // Condition: past rehearsal with unconfirmed attendance (NO_RESPONSE)
            AttentionCondition condition = new PastRehearsalUnconfirmedAttendanceCondition(rehearsal);
            UpcomingItemDto item = condition.evaluate();
            if (item != null) {
                items.add(item);
            }

            // Condition: past rehearsal with unconfirmed location
            AttentionCondition locationCondition = new PastRehearsalLocationUnconfirmedCondition(rehearsal);
            UpcomingItemDto locationItem = locationCondition.evaluate();
            if (locationItem != null) {
                items.add(locationItem);
            }
        }

        // TODO: Add more conditions here in the future
        // Examples:
        //   - new MemberWithoutDocumentCondition(member)
        //   - etc.

        return items;
    }
}
