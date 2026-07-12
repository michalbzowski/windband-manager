package pl.michalbzowski.windband.application.command.rehearsal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RehearsalNotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MemberRepository memberRepository;

    private RehearsalNotificationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RehearsalNotificationService(mailSender, memberRepository);
    }

    @Test
    void shouldSendEmailToAllMembersWithEmail() throws InterruptedException {
        Band band = Band.create("Test Orchestra", "test-orchestra");
        ReflectionTestUtils.setField(band, "id", 1L);

        Member jan = Member.create("Jan", "Kowalski", LocalDate.of(1990, 5, 15), band);
        ReflectionTestUtils.setField(jan, "id", 1L);
        jan.updateContact("jan@test.com", "123456789", false, false);

        Member anna = Member.create("Anna", "Nowak", LocalDate.of(1985, 3, 20), band);
        ReflectionTestUtils.setField(anna, "id", 2L);
        anna.updateContact("anna@test.com", "987654321", false, false);

        when(memberRepository.findAllActiveByBandId(1L)).thenReturn(List.of(jan, anna));

        Rehearsal rehearsal = Rehearsal.schedule(
                LocalDate.of(2026, 7, 1), LocalTime.of(18, 0), "Sala prób", band);
        ReflectionTestUtils.setField(rehearsal, "id", 100L);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        service.notifyMembersAboutNewRehearsal(rehearsal);

        boolean completed = latch.await(3, TimeUnit.SECONDS);
        assertThat(completed).as("Async mail send should complete").isTrue();

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(2)).send(captor.capture());

        List<SimpleMailMessage> messages = captor.getAllValues();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getTo()[0]).isEqualTo("jan@test.com");
        assertThat(messages.get(1).getTo()[0]).isEqualTo("anna@test.com");
        assertThat(messages.get(0).getSubject()).contains("Test Orchestra");
        assertThat(messages.get(1).getSubject()).contains("Test Orchestra");
    }

    @Test
    void shouldNotSendEmailToMembersWithoutEmail() throws InterruptedException {
        Band band = Band.create("Test Orchestra", "test-orchestra");
        ReflectionTestUtils.setField(band, "id", 1L);

        Member noEmail = Member.create("Bez", "Emaila", LocalDate.of(1990, 1, 1), band);
        ReflectionTestUtils.setField(noEmail, "id", 1L);
        // no email set

        Member withEmail = Member.create("Z", "Emilem", LocalDate.of(1991, 1, 1), band);
        ReflectionTestUtils.setField(withEmail, "id", 2L);
        withEmail.updateContact("mailem@test.com", null, false, false);

        when(memberRepository.findAllActiveByBandId(1L)).thenReturn(List.of(noEmail, withEmail));

        Rehearsal rehearsal = Rehearsal.schedule(
                LocalDate.of(2026, 7, 1), LocalTime.of(18, 0), "Sala", band);
        ReflectionTestUtils.setField(rehearsal, "id", 200L);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> { latch.countDown(); return null; }).when(mailSender).send(any(SimpleMailMessage.class));

        service.notifyMembersAboutNewRehearsal(rehearsal);

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        assertThat(captor.getValue().getTo()[0]).isEqualTo("mailem@test.com");
    }

    @Test
    void shouldRecordStatsAfterSending() throws InterruptedException {
        Band band = Band.create("Test Orchestra", "test-orchestra");
        ReflectionTestUtils.setField(band, "id", 1L);

        Member m1 = Member.create("Jan", "Kowalski", LocalDate.of(1990, 5, 15), band);
        ReflectionTestUtils.setField(m1, "id", 1L);
        m1.updateContact("jan@test.com", null, false, false);

        Member m2 = Member.create("Anna", "Nowak", LocalDate.of(1985, 3, 20), band);
        ReflectionTestUtils.setField(m2, "id", 2L);
        m2.updateContact("anna@test.com", null, false, false);

        when(memberRepository.findAllActiveByBandId(1L)).thenReturn(List.of(m1, m2));

        Rehearsal rehearsal = Rehearsal.schedule(
                LocalDate.of(2026, 7, 1), LocalTime.of(18, 0), "Sala", band);
        ReflectionTestUtils.setField(rehearsal, "id", 300L);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> { latch.countDown(); return null; }).when(mailSender).send(any(SimpleMailMessage.class));

        service.notifyMembersAboutNewRehearsal(rehearsal);
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();

        RehearsalEmailStats stats = service.getStats(300L);
        assertThat(stats).isNotNull();
        assertThat(stats.totalMembers()).isEqualTo(2);
        assertThat(stats.successCount()).isEqualTo(2);
        assertThat(stats.failedCount()).isEqualTo(0);
        assertThat(stats.memberResults()).hasSize(2);
    }

    @Test
    void shouldRecordFailedStatsOnMailException() throws InterruptedException {
        Band band = Band.create("Test Orchestra", "test-orchestra");
        ReflectionTestUtils.setField(band, "id", 1L);

        Member m1 = Member.create("Jan", "Kowalski", LocalDate.of(1990, 5, 15), band);
        ReflectionTestUtils.setField(m1, "id", 1L);
        m1.updateContact("jan@test.com", null, false, false);

        when(memberRepository.findAllActiveByBandId(1L)).thenReturn(List.of(m1));

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
            latch.countDown();
            throw new org.springframework.mail.MailSendException("SMTP down");
        }).when(mailSender).send(any(SimpleMailMessage.class));

        Rehearsal rehearsal = Rehearsal.schedule(
                LocalDate.of(2026, 7, 1), LocalTime.of(18, 0), "Sala", band);
        ReflectionTestUtils.setField(rehearsal, "id", 400L);

        service.notifyMembersAboutNewRehearsal(rehearsal);
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();

        RehearsalEmailStats stats = service.getStats(400L);
        assertThat(stats).isNotNull();
        assertThat(stats.totalMembers()).isEqualTo(1);
        assertThat(stats.successCount()).isEqualTo(0);
        assertThat(stats.failedCount()).isEqualTo(1);
        assertThat(stats.memberResults()).hasSize(1);
        assertThat(stats.memberResults().get(0).success()).isFalse();
    }

    @Test
    void shouldRecordZeroStatsWhenNoMembersWithEmail() throws InterruptedException {
        Band band = Band.create("Test Orchestra", "test-orchestra");
        ReflectionTestUtils.setField(band, "id", 1L);

        when(memberRepository.findAllActiveByBandId(1L)).thenReturn(List.of());

        Rehearsal rehearsal = Rehearsal.schedule(
                LocalDate.of(2026, 7, 1), LocalTime.of(18, 0), "Sala", band);
        ReflectionTestUtils.setField(rehearsal, "id", 500L);

        service.notifyMembersAboutNewRehearsal(rehearsal);

        RehearsalEmailStats stats = service.getStats(500L);
        assertThat(stats).isNotNull();
        assertThat(stats.totalMembers()).isZero();
        assertThat(stats.successCount()).isZero();
        assertThat(stats.failedCount()).isZero();
        assertThat(stats.memberResults()).isEmpty();
        verifyNoInteractions(mailSender);
    }
}
