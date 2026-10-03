package pl.michalbzowski.windband.domain.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * US-6.6 — one audit row of "who got which part, when" inside ONE delivery run (US-6.3/6.4).
 *
 * <p>Append-only: rows are written once by the delivery command service and never updated or
 * deleted (no mutators on the entity). Each re-send appends a fresh block of rows — the
 * accumulation per {@code eventId} <em>is</em> the "Historia rozdań" the band leader reads.</p>
 *
 * <p>Member / piece / role are stored as display-value snapshots, not FKs: an audit must still
 * say exactly who received what at that moment even if the member is renamed or deleted later,
 * and a musician's departure must never cascade into the record of what they were (or were not)
 * sent. The single true relationship is the event — it is a primitive {@code eventId} column in
 * particular on purpose: this entity is read outside any session ({@code open-in-view: false}),
 * so it must carry no lazy associations at all (every other LAZY-read pitfall in this codebase
 * avoided by construction).</p>
 *
 * <p>{@code outcome} mirrors the honest US-6.3 policy buckets — {@code DELIVERED},
 * {@code SEND_FAILED}, {@code SKIPPED_NO_CONSENT} (the V25 hard gate), {@code SKIPPED_NO_EMAIL},
 * {@code REFUSED_NO_SCORE_FILE} — so the trail can prove both sides of the consent story.</p>
 */
@Entity
@Table(name = "event_part_deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventPartDelivery {

    /** The only channel in the product today; kept as a column for the spec's "via which channel". */
    public static final String CHANNEL_EMAIL = "EMAIL";

    // ── outcome vocabulary (the `outcome` column), one value per US-6.3/6.4 policy decision ──
    /** The e-mail containing this part left the transport successfully. */
    public static final String OUTCOME_DELIVERED = "DELIVERED";
    /** The transport rejected the envelope; {@code reason} carries the error text. */
    public static final String OUTCOME_SEND_FAILED = "SEND_FAILED";
    /** V25 {@code email_consent=false} — refused silently, never sent (hard gate, audited). */
    public static final String OUTCOME_SKIPPED_NO_CONSENT = "SKIPPED_NO_CONSENT";
    /** No mailbox on file for the member. */
    public static final String OUTCOME_SKIPPED_NO_EMAIL = "SKIPPED_NO_EMAIL";
    /** Covering-file gate refused the part before a token was minted / mail sent. */
    public static final String OUTCOME_REFUSED_NO_SCORE_FILE = "REFUSED_NO_SCORE_FILE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    /** Member display name at send time (audit snapshot). */
    @Column(name = "delivered_to", nullable = false, length = 255)
    private String deliveredTo;

    /** Recipient address; null for the SKIPPED_NO_EMAIL outcome by definition. */
    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Column(name = "piece_title", length = 255)
    private String pieceTitle;

    @Column(name = "part_role", length = 120)
    private String partRole;

    @Column(name = "page_from")
    private Integer pageFrom;

    @Column(name = "page_to")
    private Integer pageTo;

    @Column(nullable = false, length = 16)
    private String channel;

    /** DELIVERED / SEND_FAILED / SKIPPED_NO_CONSENT / SKIPPED_NO_EMAIL / REFUSED_NO_SCORE_FILE. */
    @Column(nullable = false, length = 32)
    private String outcome;

    /** SEND_FAILED error text or the gate reason — kept short (first line, truncated at 512). */
    @Column(length = 512)
    private String reason;

    /** E-mail ("system") of whoever triggered the run. */
    @Column(length = 255)
    private String actor;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    /**
     * Factory: one audit decision for one musician × part inside delivery run
     * {@code (eventId, actor, now)}. Outcome stays a plain string vocabulary documented above.
     */
    public static EventPartDelivery record(Long eventId, String deliveredTo, String recipientEmail,
                                           String pieceTitle, String partRole, Integer pageFrom,
                                           Integer pageTo, String outcome, String reason,
                                           String actor, Instant now) {
        if (eventId == null) { throw new IllegalArgumentException("eventId jest wymagane"); }
        if (deliveredTo == null || deliveredTo.isBlank()) {
            throw new IllegalArgumentException("deliveredTo jest wymagane");
        }
        if (outcome == null || outcome.isBlank()) { throw new IllegalArgumentException("outcome jest wymagane"); }
        EventPartDelivery row = new EventPartDelivery();
        row.eventId        = eventId;
        row.deliveredTo    = deliveredTo;
        row.recipientEmail = recipientEmail;
        row.pieceTitle     = pieceTitle;
        row.partRole       = partRole;
        row.pageFrom       = pageFrom;
        row.pageTo         = pageTo;
        row.channel        = CHANNEL_EMAIL;
        row.outcome        = outcome;
        row.reason         = truncate(reason);
        row.actor          = actor;
        row.sentAt         = now == null ? Instant.now() : now;
        return row;
    }

    /** 512-char column cap, no stack traces in the audit (first line wins). */
    private static String truncate(String text) {
        if (text == null) { return null; }
        int nl = text.indexOf('\n');
        String first = nl >= 0 ? text.substring(0, nl) : text;
        return first.length() <= 512 ? first : first.substring(0, 512);
    }
}
