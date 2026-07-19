package pl.michalbzowski.windband.domain.member;

import pl.michalbzowski.windband.domain.band.MemberAttributeDef;
import pl.michalbzowski.windband.domain.band.MemberAttributeValue;
import pl.michalbzowski.windband.domain.band.MemberAttributeValueRepository;

import java.util.Optional;

/**
 * Polymorphic source of truth for a dynamic {@link Group}'s membership.
 *
 * <p>Each implementation knows (a) the human-readable group name it produces and
 * (b) whether a given {@link Member} currently belongs to the group. The sync
 * orchestration in {@code GroupCommandService} is written against this interface
 * only — it never branches on the concrete source type, which keeps the design
 * open for extension (OCP).</p>
 *
 * <p>Implementations live in the domain layer and depend only on other domain
 * types (including the {@code MemberAttributeValueRepository} interface), so they
 * stay free of persistence/Spring coupling and pass the ArchUnit rules.</p>
 */
public interface DynamicGroupSource {

    /** Display name for the dynamic group (e.g. attribute name, or "Aktywni"). */
    String getName();

    /** True iff the member should currently be a member of the dynamic group. */
    boolean memberMatches(Member member);
}
