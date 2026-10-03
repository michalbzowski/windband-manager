package pl.michalbzowski.windband.application.command.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Objects;

/**
 * US-6.3 — outcome of delivering an event's parts to its musicians, by category.
 *
 * <p>Every category is rendered separately in the post-send banner so the band leader always
 * knows what happened with each musician — a silent partial send is a privacy bug waiting to
 * happen (a member who revoked consent must NOT be reported as "delivered"). All names are
 * display strings, never ids — the result is safe to hand to Thymeleaf after the transaction
 * closes.</p>
 *
 * <p>A {@link record} so the US-6.4 REST endpoint can serialize it directly: Jackson discovers
 * every component automatically (a plain class with {@code sent()}-style accessors would fail as
 * an "empty bean" and return {@code {}} to the client). {@code sendError} is an internal transport
 * handle — never serialized, since a raw {@link RuntimeException} in a REST body would leak a stack
 * trace; its message is already folded into {@link #failedSend()} for the honest UI banner.</p>
 *
 * <p>Record accessor names ({@code sent()}, {@code delivered()}, …) match the Thymeleaf
 * expressions in {@code events/detail.html} exactly, so the existing "Rozdanie głosów" banner reads
 * this object unchanged.</p>
 */
public record PartDeliveryResult(
        int sent,
        List<Delivered> delivered,
        List<String> skippedNoConsent,
        List<String> skippedNoEmail,
        List<String> noScoreFile,
        List<String> failedSend,
        @JsonIgnore RuntimeException sendError) {

    /** One e-mail actually handed to the transport, with every part it contained. */
    public record Delivered(long memberId, String memberName, String email, List<PartDeliveryRow> parts) {
        public Delivered {
            Objects.requireNonNull(memberName, "memberName required");
            Objects.requireNonNull(email, "email required");
            parts = List.copyOf(parts);
        }
    }

    /** One part inside a delivered e-mail: the public token link (US-7.11) + the page range. */
    public record PartDeliveryRow(long partId, String pieceTitle, String role, int pageFrom, int pageTo,
                                  String publicPartLink) {
        public PartDeliveryRow {
            Objects.requireNonNull(role, "role required");
            Objects.requireNonNull(publicPartLink, "publicPartLink required");
        }
    }

    /** Canonical form: immutable copies of every list — the delivery result never mutates after the fact. */
    public PartDeliveryResult {
        delivered = List.copyOf(delivered);
        skippedNoConsent = List.copyOf(skippedNoConsent);
        skippedNoEmail   = List.copyOf(skippedNoEmail);
        noScoreFile      = List.copyOf(noScoreFile);
        failedSend       = List.copyOf(failedSend);
    }

    /** Factory preserving the US-6.3 call sites and their argument order. */
    public static PartDeliveryResult of(int sent, List<Delivered> delivered, List<String> skippedNoConsent,
                                        List<String> skippedNoEmail, List<String> noScoreFile,
                                        List<String> failedSend, RuntimeException sendError) {
        return new PartDeliveryResult(sent, delivered, skippedNoConsent, skippedNoEmail, noScoreFile, failedSend, sendError);
    }
}
