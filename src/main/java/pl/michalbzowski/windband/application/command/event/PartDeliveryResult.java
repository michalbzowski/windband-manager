package pl.michalbzowski.windband.application.command.event;

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
 */
public final class PartDeliveryResult {

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

    private final int sent;
    private final List<Delivered> delivered;
    private final List<String> skippedNoConsent;
    private final List<String> skippedNoEmail;
    private final List<String> noScoreFile;
    private final List<String> failedSend;
    private final RuntimeException sendError;

    private PartDeliveryResult(int sent, List<Delivered> delivered, List<String> skippedNoConsent,
                               List<String> skippedNoEmail, List<String> noScoreFile,
                               List<String> failedSend, RuntimeException sendError) {
        this.sent = sent;
        this.delivered = List.copyOf(delivered);
        this.skippedNoConsent = List.copyOf(skippedNoConsent);
        this.skippedNoEmail = List.copyOf(skippedNoEmail);
        this.noScoreFile = List.copyOf(noScoreFile);
        this.failedSend = List.copyOf(failedSend);
        this.sendError = sendError;
    }

    public static PartDeliveryResult of(int sent, List<Delivered> delivered, List<String> skippedNoConsent,
                                        List<String> skippedNoEmail, List<String> noScoreFile,
                                        List<String> failedSend, RuntimeException sendError) {
        return new PartDeliveryResult(sent, delivered, skippedNoConsent, skippedNoEmail, noScoreFile, failedSend, sendError);
    }

    public int sent() {
        return sent;
    }

    public List<Delivered> delivered() {
        return delivered;
    }

    public List<String> skippedNoConsent() {
        return skippedNoConsent;
    }

    public List<String> skippedNoEmail() {
        return skippedNoEmail;
    }

    /**
     * Parts whose score file does not cover the page range (US-7.10's {@code NoCoveringFileException}).
     * Delivery is refused for them — better an honest "brak pliku" than a dead public link.
     */
    public List<String> noScoreFile() {
        return noScoreFile;
    }

    /** Members whose envelope the transport rejected (logged; the send itself did happen). */
    public List<String> failedSend() {
        return failedSend;
    }

    /** The last transport error, when every envelope failed and the caller must surface it. */
    public RuntimeException sendError() {
        return sendError;
    }
}
