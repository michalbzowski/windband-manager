package pl.michalbzowski.windband.application.dto.event;

import java.util.List;
import java.util.Objects;

/**
 * US-6.2 read model — the concrete part list an event distributes to its musicians.
 */
public final class EventPartDistributionDto {

    private EventPartDistributionDto() {
        // utility holder
    }

    /** One member getting one part — who, and whether US-6.3 may mail them. */
    public record PlayerForPart(long memberId, String memberName, boolean emailConsentGiven) {}

    /**
     * One composition part with everyone routed onto it. Empty {@code players} means "brak muzyka"
     * — the hole the band leader must fix before US-6.3 can send parts.
     */
    public record PartView(
            long partId,
            String role,
            int pageFrom,
            int pageTo,
            String fileRef,
            boolean verified,
            List<PlayerForPart> players
    ) {
        public PartView {
            Objects.requireNonNull(role, "role required");
            if (pageFrom < 1 || pageTo < pageFrom) {
                throw new IllegalArgumentException("illegal page range: " + pageFrom + ".." + pageTo);
            }
            players = List.copyOf(players);
        }

        public boolean isUncovered() {
            return players.isEmpty();
        }
    }

    /** A setlist piece with no parts defined yet (US-3.03 gate not passed) — surfaced, not hidden. */
    public record UnassignedComposition(long compositionId, String title, int setlistPosition) {
        public UnassignedComposition {
            Objects.requireNonNull(title, "title required");
            if (setlistPosition < 1) {
                throw new IllegalArgumentException("setlistPosition must be >= 1");
            }
        }
    }


    /** Flat one-member-one-part row, kept for API consumers that want the plain table. */
    public record PartAssignment(
            long memberId,
            String memberName,
            boolean emailConsentGiven,
            long compositionId,
            String compositionTitle,
            int setlistPosition,
            long partId,
            String role,
            int pageFrom,
            int pageTo,
            String fileRef,
            boolean verified,
            String matchedMemberTag
    ) {
        public PartAssignment {
            Objects.requireNonNull(memberName, "memberName required");
            if (pageFrom < 1 || pageTo < pageFrom) {
                throw new IllegalArgumentException("illegal page range: " + pageFrom + ".." + pageTo);
            }
            matchedMemberTag = matchedMemberTag == null || matchedMemberTag.isBlank() ? null : matchedMemberTag.trim();
        }
    }

    /** One setlist piece in positional order, every part row resolved against the active roster. */
    public record PieceDistribution(
            long compositionId,
            String title,
            int setlistPosition,
            List<PartView> parts
    ) {
        public PieceDistribution {
            Objects.requireNonNull(title, "title required");
            if (setlistPosition < 1) {
                throw new IllegalArgumentException("setlistPosition must be >= 1");
            }
            parts = List.copyOf(parts);
        }

        public boolean hasNoParts() {
            return parts.isEmpty();
        }

        public List<PartView> uncoveredParts() {
            return parts.stream().filter(PartView::isUncovered).toList();
        }
    }

    /**
     * Full event distribution: the piece-grouped view (primary, setlist order), the flat row list,
     * and the part-less pieces. All scalar — safe to render after the transaction closes ("Shape C").
     */
    public record Distribution(
            List<PieceDistribution> pieces,
            List<PartAssignment> assignments,
            List<UnassignedComposition> unassigned
    ) {
        public Distribution {
            pieces = List.copyOf(pieces);
            assignments = List.copyOf(assignments);
            unassigned = List.copyOf(unassigned);
        }
    }
}
