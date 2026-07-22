package pl.michalbzowski.windband.application.command.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.EventInvitation;
import pl.michalbzowski.windband.domain.event.EventInvitationRepository;
import pl.michalbzowski.windband.domain.event.EventRepository;
import pl.michalbzowski.windband.domain.event.NotificationStatus;
import pl.michalbzowski.windband.domain.member.Consent;
import pl.michalbzowski.windband.domain.member.ConsentRepository;
import pl.michalbzowski.windband.domain.member.ConsentType;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end regression for the "consent gates event-comm emails" contract.
 * Spins up the full Spring context (real DB, real ConsentService, real
 * NotificationSender) and replaces the email channel with a controllable
 * in-test Channel bean so we can assert exactly WHO was handed to the
 * channel and WHO was filtered out by the consent check.
 *
 * <p>Companion tests:
 * <ul>
 *   <li>{@code NotificationSenderConsentTest} — unit-level, isolated.</li>
 *   <li>{@code EventConsentBadgeUiTest} — UI-level, verifies the rendered
 *       "Zgoda na informacje" column.</li>
 * </ul>
 *
 * <p><b>Why a real bean and not {@code @MockBean Channel}:</b>
 * {@link ChannelResolver} builds its {@code Map<String,Channel>} in the
 * constructor by calling {@code Channel.getName()} on every {@code Channel}
 * bean. A {@code @MockBean} returns {@code null} from
 * {@code getName()} <em>before</em> {@code @BeforeEach} stubs it, so the
 * resolver would throw {@code IllegalStateException("No suitable channel")}
 * for every send. A real anonymous {@code Channel} returning
 * {@code "EMAIL"} from its constructor is safe.
 */
@Import(EventConsentIntegrationTest.FakeChannelConfig.class)
class EventConsentIntegrationTest extends BaseIntegrationTest {

    @Autowired private EventCommandService eventCommandService;
    @Autowired private NotificationSender notificationSender;
    @Autowired private EventRepository eventRepository;
    @Autowired private EventInvitationRepository invitationRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private BandRepository bandRepository;
    @Autowired private ConsentRepository consentRepository;
    /** Tracked across the whole test class — captures every send() invocation. */
    @Autowired private CapturingChannel capturingChannel;

    private Band band;
    private Member alfa;
    private Member beta;

    @BeforeEach
    void setUp() {
        band = bandRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Default band not found"));

        // Two fresh members — both with email, both active. We control consent
        // explicitly via the consentRepository (not via Member.emailConsentGiven,
        // which is a different flag not consulted by NotificationSender).
        alfa = createAndPersistMember("Alfa", "Consenting", "alfa@consent.test");
        beta = createAndPersistMember("Beta", "Refusing", "beta@consent.test");
        capturingChannel.reset();
    }

    @Test
    void sendToAll_deliversToConsentingAndSkipsRefusingMember() {
        // given — alfa grants EVENTS consent, beta explicitly denies
        Consent alfaConsent = Consent.create(alfa, ConsentType.EVENTS);
        alfaConsent.grant();
        consentRepository.save(alfaConsent);
        Consent betaConsent = Consent.create(beta, ConsentType.EVENTS);
        betaConsent.deny();
        consentRepository.save(betaConsent);

        BandEvent event = createEvent("Koncert z consentem");
        inviteToEvent(event, alfa);
        inviteToEvent(event, beta);

        // when — operator clicks "Wyślij do wszystkich"
        int sent = notificationSender.sendToAll(event.getId());

        // then — only alfa was actually delivered
        assertThat(sent).as("Only the consenting member's invitation should succeed")
                .isEqualTo(1);

        assertThat(capturingChannel.sentToMembers())
                .as("Only alfa should have been handed to the channel")
                .containsExactly(alfa.getId());

        // Alfa's invitation is SENT, beta's is FAILED — and the reason is consent,
        // not "no email" (both have emails).
        var alfaInvitation = invitationRepository
                .findByEventIdAndMemberId(event.getId(), alfa.getId()).get(0);
        var betaInvitation = invitationRepository
                .findByEventIdAndMemberId(event.getId(), beta.getId()).get(0);
        assertThat(alfaInvitation.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(betaInvitation.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void sendToAll_treatsMissingConsentAsDenied() {
        // given — NEITHER member has a Consent row → consentService returns false
        // for both. (This is the "default" state right after a member is created
        // — the consent record only appears once they click a link in welcome
        // email.)
        BandEvent event = createEvent("Koncert bez zgody");
        inviteToEvent(event, alfa);
        inviteToEvent(event, beta);

        // when
        int sent = notificationSender.sendToAll(event.getId());

        // then — neither goes out, channel is never called
        assertThat(sent).isZero();
        assertThat(capturingChannel.sentToMembers())
                .as("Channel must not be touched for either member")
                .isEmpty();

        var alfaInvitation = invitationRepository
                .findByEventIdAndMemberId(event.getId(), alfa.getId()).get(0);
        var betaInvitation = invitationRepository
                .findByEventIdAndMemberId(event.getId(), beta.getId()).get(0);
        assertThat(alfaInvitation.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(betaInvitation.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void sendToMember_alsoChecksConsentForSingleSend() {
        // given — beta has no consent
        Consent alfaConsent = Consent.create(alfa, ConsentType.EVENTS);
        alfaConsent.grant();
        consentRepository.save(alfaConsent);
        BandEvent event = createEvent("Koncert single-send");
        inviteToEvent(event, beta);

        // when
        notificationSender.sendToMember(event.getId(), beta.getId());

        // then — channel never called, invitation FAILED
        assertThat(capturingChannel.sentToMembers())
                .as("Channel must not be touched for the refusing member")
                .isEmpty();
        var betaInvitation = invitationRepository
                .findByEventIdAndMemberId(event.getId(), beta.getId()).get(0);
        assertThat(betaInvitation.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    // --- helpers ---

    private BandEvent createEvent(String name) {
        var cmd = new CreateEventCommand();
        cmd.setName(name);
        cmd.setDate(LocalDate.now().plusDays(10));
        cmd.setStartTime(LocalTime.of(18, 0));
        cmd.setLocation("Sala testowa");
        cmd.setEventType("CONCERT");
        cmd.setPaymentType("FREE");
        return eventCommandService.createEvent(cmd, band.getId());
    }

    private void inviteToEvent(BandEvent event, Member member) {
        var cmd = new InviteMemberCommand();
        cmd.setEventId(event.getId());
        cmd.setMemberId(member.getId());
        eventCommandService.inviteMember(cmd);
    }

    private Member createAndPersistMember(String firstName, String lastName, String email) {
        Member m = Member.create(firstName, lastName, LocalDate.of(1990, 1, 1), band);
        m.updateContact(email, "500500500", false);
        return memberRepository.save(m);
    }

    /**
     * Stand-in for the email channel that records every {@code send()} call.
     * Registered as {@code smtpEmailChannel} so {@code SendGridApiChannel}
     * is excluded by its {@code @ConditionalOnMissingBean(name =
     * "smtpEmailChannel")}.
     */
    static class CapturingChannel implements Channel {
        private final AtomicInteger callCount = new AtomicInteger(0);
        private final List<Long> sentToMembers = new ArrayList<>();

        @Override
        public String getName() {
            return "EMAIL";
        }

        @Override
        public void send(EventInvitation invitation,
                         pl.michalbzowski.windband.domain.event.BandEvent event,
                         Member member, String baseUrl) {
            callCount.incrementAndGet();
            sentToMembers.add(member.getId());
        }

        List<Long> sentToMembers() {
            return List.copyOf(sentToMembers);
        }

        void reset() {
            callCount.set(0);
            sentToMembers.clear();
        }
    }

    @TestConfiguration
    static class FakeChannelConfig {
        @Bean(name = "smtpEmailChannel")
        CapturingChannel capturingChannel() {
            return new CapturingChannel();
        }
    }
}
