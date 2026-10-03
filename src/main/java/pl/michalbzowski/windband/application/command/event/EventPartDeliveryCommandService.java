package pl.michalbzowski.windband.application.command.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import pl.michalbzowski.windband.application.command.composition.PartShareTokenCommandService;
import pl.michalbzowski.windband.application.dto.event.EventPartDistributionDto.Distribution;
import pl.michalbzowski.windband.application.dto.event.EventPartDistributionDto.PartAssignment;

import static pl.michalbzowski.windband.domain.event.EventPartDelivery.OUTCOME_DELIVERED;
import static pl.michalbzowski.windband.domain.event.EventPartDelivery.OUTCOME_REFUSED_NO_SCORE_FILE;
import static pl.michalbzowski.windband.domain.event.EventPartDelivery.OUTCOME_SEND_FAILED;
import static pl.michalbzowski.windband.domain.event.EventPartDelivery.OUTCOME_SKIPPED_NO_CONSENT;
import static pl.michalbzowski.windband.domain.event.EventPartDelivery.OUTCOME_SKIPPED_NO_EMAIL;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.composition.PartLinkQueryService;
import pl.michalbzowski.windband.application.query.composition.PartLinkQueryService.NoCoveringFileException;
import pl.michalbzowski.windband.application.query.composition.PartLinkQueryService.PartLink;
import pl.michalbzowski.windband.application.query.event.EventCompositionPartsQueryService;
import pl.michalbzowski.windband.application.service.EmailSender;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.EventPartDelivery;
import pl.michalbzowski.windband.domain.event.EventPartDeliveryRepository;
import pl.michalbzowski.windband.domain.event.EventRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * US-6.3 — delivers an event's parts to its musicians by e-mail.
 *
 * <p>The read model comes from US-6.2 ({@link EventCompositionPartsQueryService#forEvent}) — this
 * service only adds the delivery policy on top: the e-mail-consent gate (V25 legacy column,
 * surfaced by the DTO), the no-mailbox skip, the covering-file gate (US-7.10's
 * {@code NoCoveringFileException} — a public link that cannot serve its pages is refused, not
 * mailed), and one envelope per musician containing every part they play, each carrying its own
 * US-7.11 public token link ({@code /public/parts/{token}}) so no band/composition/part ids ever
 * leave the system.</p>
 *
 * <p>Best-effort per envelope: one failed send does not cancel the rest; only when <em>every</em>
 * envelope fails is the last error rethrown as {@link IllegalStateException} so the UI can surface
 * "nie udało się" (same contract as US-7.10's {@code PartShareByEmailCommandService}). Members who
 * are skipped for a reason land in an honest result bucket, never in the sent list.</p>
 */
@Service
@Slf4j
public class EventPartDeliveryCommandService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final EventCompositionPartsQueryService partsQueryService;
    private final PartLinkQueryService partLinkQueryService;
    private final PartShareTokenCommandService tokenService;
    private final EmailSender emailSender;
    private final SpringTemplateEngine templateEngine;
    private final BandQueryService bandQueryService;
    private final MemberRepository memberRepository;
    private final EventRepository eventRepository;
    private final EventPartDeliveryRepository auditRepository;
    private final String baseUrl;

    public EventPartDeliveryCommandService(EventCompositionPartsQueryService partsQueryService,
                                           PartLinkQueryService partLinkQueryService,
                                           PartShareTokenCommandService tokenService,
                                           EmailSender emailSender,
                                           SpringTemplateEngine templateEngine,
                                           BandQueryService bandQueryService,
                                           MemberRepository memberRepository,
                                           EventRepository eventRepository,
                                           EventPartDeliveryRepository auditRepository,
                                           @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.partsQueryService = partsQueryService;
        this.partLinkQueryService = partLinkQueryService;
        this.tokenService = tokenService;
        this.emailSender = emailSender;
        this.templateEngine = templateEngine;
        this.bandQueryService = bandQueryService;
        this.memberRepository = memberRepository;
        this.eventRepository = eventRepository;
        this.auditRepository = auditRepository;
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "" : baseUrl.replaceAll("/+$", "");
    }

    /**
     * Delivers every routed part of the event to the musicians playing it, band-isolated.
     *
     * <p>Fail-closed contract (inherited from US-6.2): unknown band → {@link IllegalArgumentException}
     * (400); foreign event → {@link IllegalStateException} (409); unknown event → 404 — all before
     * any mailbox is touched.</p>
     *
     * @param actor the logged-in member who triggered the delivery (audit log, US-6.6 seam)
     * @return an honest per-category breakdown of what was sent / skipped / refused / failed
     */
    public PartDeliveryResult deliverParts(Long eventId, Long bandId, String actor) {
        // US-6.2 gate: band isolation + the routed read model (all band checks live in there).
        Distribution distribution = partsQueryService.forEvent(eventId, bandId);
        List<PartAssignment> assignments = distribution.assignments();
        if (assignments.isEmpty()) {
            return PartDeliveryResult.of(0, List.of(), List.of(), List.of(), List.of(), List.of(), null);
        }

        // One envelope per musician, in setlist-encounter order.
        Map<Long, List<PartAssignment>> rowsByMember = new LinkedHashMap<>();
        for (PartAssignment row : assignments) {
            rowsByMember.computeIfAbsent(row.memberId(), k -> new ArrayList<>()).add(row);
        }

        List<PartDeliveryResult.Delivered> delivered = new ArrayList<>();
        List<String> skippedNoConsent = new ArrayList<>();
        List<String> skippedNoEmail = new ArrayList<>();
        List<String> noScoreFile = new ArrayList<>();
        List<String> failedSend = new ArrayList<>();
        RuntimeException lastSendError = null;

        // US-6.6 — one timestamp per RUN: every audit row of this send shares sent_at, so the
        // history view can group "who got what in which attempt" without any extra column.
        Instant runAt = Instant.now();

        // Pass 1 — consent + mailbox gates. Nothing external happens for a gated member: no event
        // lookup, no covering-file probe, no token mint (the V25 contract says "refuse silently").
        Map<Long, Member> eligibleMembers = new HashMap<>();
        List<List<PartAssignment>> eligibleParts = new ArrayList<>();
        for (Map.Entry<Long, List<PartAssignment>> entry : rowsByMember.entrySet()) {
            PartAssignment sample = entry.getValue().get(0);
            if (!sample.emailConsentGiven()) {
                skippedNoConsent.add(sample.memberName());
                log.info("US-6.3: event {} — member {} (id={}) skipped, no e-mail consent (silent refusal)",
                        eventId, sample.memberName(), sample.memberId());
                // US-6.6 — the consent gate IS part of the history: prove non-consenting parts never left.
                for (PartAssignment row : entry.getValue()) {
                    recordAudit(eventId, sample.memberName(), null, row,
                            OUTCOME_SKIPPED_NO_CONSENT, null, actor, runAt);
                }
                continue;
            }
            Member member = memberRepository.findById(sample.memberId()).orElse(null);
            if (member == null || member.getEmail() == null || member.getEmail().isBlank()) {
                skippedNoEmail.add(sample.memberName());
                log.info("US-6.3: event {} — member {} (id={}) skipped, no e-mail address on file",
                        eventId, sample.memberName(), sample.memberId());
                // US-6.6 — audited like every other refusal; recipient_email stays null by definition.
                for (PartAssignment row : entry.getValue()) {
                    recordAudit(eventId, sample.memberName(), null, row,
                            OUTCOME_SKIPPED_NO_EMAIL, "brak adresu e-mail w systemie", actor, runAt);
                }
                continue;
            }
            eligibleMembers.put(sample.memberId(), member);
            eligibleParts.add(entry.getValue());
        }
        if (eligibleParts.isEmpty()) {
            return PartDeliveryResult.of(0, List.of(), skippedNoConsent, skippedNoEmail,
                    List.of(), List.of(), null);
        }

        // Pass 2 — per-part covering-file gate + public link minting (US-7.11: stable token per part).
        BandEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        // Band name resolved inside BandQueryService's own read-only transaction — never via the
        // LAZY BandEvent.band proxy: this service runs outside any session (open-in-view is off),
        // and a lazy association read there blows up in production/UI contexts.
        String bandName = bandQueryService.getRequiredBand(bandId).getName();
        String eventDate = event.getDate() == null ? "" : event.getDate().format(DATE_FORMAT);
        String eventTime = event.getStartTime() == null ? "" : event.getStartTime().toString();

        int sent = 0;
        for (int i = 0; i < eligibleParts.size(); i++) {
            List<PartAssignment> rows = eligibleParts.get(i);
            Member member = eligibleMembers.get(rows.get(0).memberId());

            // US-6.6 — reverse lookup so DELIVERED/SEND_FAILED audits can quote each exact assignment row.
            Map<Long, PartAssignment> rowByPartId = new HashMap<>();
            for (PartAssignment r : rows) { rowByPartId.put(r.partId(), r); }

            List<PartDeliveryResult.PartDeliveryRow> partRows = new ArrayList<>();
            for (PartAssignment row : rows) {
                PartLink link;
                try {
                    link = partLinkQueryService.open(row.partId(), row.compositionId(), bandId);
                } catch (NoCoveringFileException ex) {
                    noScoreFile.add(fullName(member) + " — „" + row.role()
                            + "” (strony " + row.pageFrom() + "–" + row.pageTo() + ")");
                    log.warn("US-6.3: event {} — part {} refused, {}", eventId, row.partId(), ex.getMessage());
                    // US-6.6 — the covering-file refusal is history too (token never minted, mail never sent).
                    recordAudit(eventId, fullName(member), member.getEmail(), row,
                            OUTCOME_REFUSED_NO_SCORE_FILE, ex.getMessage(), actor, runAt);
                    continue;
                }
                UUID token = tokenService.tokenFor(row.partId(), "band:" + bandId);
                partRows.add(new PartDeliveryResult.PartDeliveryRow(
                        row.partId(),
                        link.compositionTitle(),
                        link.roleText(),
                        link.pageFrom(),
                        link.pageTo(),
                        baseUrl + "/public/parts/" + token));
            }
            if (partRows.isEmpty()) {
                continue; // every one of this member's parts was refused — no empty e-mail
            }

            Context ctx = new Context(Locale.forLanguageTag("pl"));
            ctx.setVariable("eventName", event.getName());
            ctx.setVariable("eventDate", eventDate);
            ctx.setVariable("eventTime", eventTime);
            ctx.setVariable("eventLocation", event.getLocation() == null ? "" : event.getLocation());
            ctx.setVariable("bandName", bandName);
            ctx.setVariable("memberName", fullName(member));
            ctx.setVariable("parts", partRows);
            String htmlBody = templateEngine.process("email/event-part", ctx);
            String subject = "Głosy do „" + event.getName() + "” — " + eventDate;

            String who = fullName(member);
            try {
                emailSender.sendHtmlEmail(member.getEmail(), who, subject, htmlBody);
                delivered.add(new PartDeliveryResult.Delivered(
                        member.getId(), who, member.getEmail(), partRows));
                sent++;
                log.info("US-6.3: event {} — {} part(s) delivered to {} <{}> (by {})",
                        eventId, partRows.size(), who, member.getEmail(), actor);
                // US-6.6 — success is history too: every part of this envelope, with its exact pages.
                for (PartDeliveryResult.PartDeliveryRow partRow : partRows) {
                    PartAssignment src = rowByPartId.get(partRow.partId());
                    recordAudit(eventId, who, member.getEmail(), src,
                            OUTCOME_DELIVERED, null, actor, runAt);
                }
            } catch (RuntimeException ex) {
                failedSend.add(who);
                lastSendError = ex;
                log.warn("US-6.3: event {} — e-mail to {} failed: {}", eventId, who, ex.getMessage(), ex);
                // US-6.6 — the whole envelope failed; every part inside is audited as SEND_FAILED.
                for (PartAssignment src : rows) {
                    recordAudit(eventId, who, member.getEmail(), src,
                            OUTCOME_SEND_FAILED, ex.getMessage(), actor, runAt);
                }
            }
        }

        if (sent == 0 && lastSendError != null) {
            throw new IllegalStateException(
                    "Nie udało się wysłać żadnego z " + failedSend.size()
                            + " e-maili: " + lastSendError.getMessage(), lastSendError);
        }
        return PartDeliveryResult.of(sent, delivered, skippedNoConsent, skippedNoEmail,
                noScoreFile, failedSend, lastSendError);
    }

    /**
     * US-6.6 — append one delivery-decision audit row (US-7.11-style domain port, append-only).
     * An audit failure is logged and swallowed: the send has already succeeded/failed on its own
     * merits, and a history write must never flip an otherwise honest result.
     */
    private void recordAudit(Long eventId, String deliveredTo, String recipientEmail, PartAssignment row,
                             String outcome, String reason, String actor, Instant runAt) {
        try {
            auditRepository.save(EventPartDelivery.record(
                    eventId, deliveredTo, recipientEmail,
                    row.compositionTitle(), row.role(), row.pageFrom(), row.pageTo(),
                    outcome, reason, actor, runAt));
        } catch (RuntimeException ex) {
            log.warn("US-6.6: audit row not recorded for event {} ({} — {}): {}",
                    eventId, deliveredTo, outcome, ex.getMessage(), ex);
        }
    }

    /** "Jan Kowalski" — the display name used in e-mails and result buckets. */
    private static String fullName(Member member) {
        String first = member.getFirstName() == null ? "" : member.getFirstName().trim();
        String last = member.getLastName() == null ? "" : member.getLastName().trim();
        String name = (first + " " + last).trim();
        return name.isEmpty() ? "Częonek #" + member.getId() : name;
    }
}
