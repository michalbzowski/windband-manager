package pl.michalbzowski.windband.application.command.rehearsal;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class RehearsalNotificationService {

    private final JavaMailSender mailSender;
    private final MemberRepository memberRepository;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Key = rehearsal ID, Value = latest email stats for that rehearsal.
     * Stats are replaced each time a notification is sent (create or update).
     */
    private final Map<Long, RehearsalEmailStats> statsMap = new ConcurrentHashMap<>();

    public RehearsalEmailStats getStats(Long rehearsalId) {
        return statsMap.get(rehearsalId);
    }

    @Async
    public void notifyMembersAboutNewRehearsal(Rehearsal rehearsal) {
        sendNotifications(rehearsal);
    }

    @Async
    public void notifyMembersAboutUpdatedRehearsal(Rehearsal rehearsal) {
        sendNotifications(rehearsal);
    }

    private void sendNotifications(Rehearsal rehearsal) {
        Band band = rehearsal.getBand();
        System.out.println("[NOTIFY] sendNotifications for rehearsal id=" + rehearsal.getId() + " band=" + band.getId());
        List<Member> membersWithEmail = memberRepository.findAllActiveByBandId(band.getId()).stream()
                .filter(m -> m.getEmail() != null && !m.getEmail().isBlank())
                .toList();
        System.out.println("[NOTIFY] membersWithEmail count=" + membersWithEmail.size());

        if (membersWithEmail.isEmpty()) {
            statsMap.put(rehearsal.getId(), new RehearsalEmailStats(0, 0, 0, Collections.emptyList()));
            return;
        }

        String subject = "Nowa próba: %s — %s".formatted(
                band.getName(),
                rehearsal.getDate().format(DATE_FORMAT)
        );

        String body = buildEmailBody(rehearsal, band);

        List<RehearsalEmailStats.MemberEmailResult> results = new ArrayList<>();
        int success = 0;
        int failed = 0;

        for (Member member : membersWithEmail) {
            boolean sent = false;
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(member.getEmail());
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);
                sent = true;
                success++;
            } catch (MailException e) {
                System.err.println("[NOTIFY] Failed to send email to " + member.getEmail() + ": " + e.getMessage());
                failed++;
            }
            results.add(new RehearsalEmailStats.MemberEmailResult(
                    member.getId(),
                    member.getFirstName(),
                    member.getLastName(),
                    member.getEmail(),
                    sent
            ));
        }

        statsMap.put(rehearsal.getId(), new RehearsalEmailStats(
                membersWithEmail.size(),
                success,
                failed,
                results
        ));
    }

    private String buildEmailBody(Rehearsal rehearsal, Band band) {
        StringBuilder sb = new StringBuilder();
        sb.append("Cześć,\n\n");
        sb.append("Została zaplanowana nowa próba zespołu \"").append(band.getName()).append("\".\n\n");
        sb.append("📅 Data: ").append(rehearsal.getDate().format(DATE_FORMAT)).append("\n");
        sb.append("⏰ Godzina: ").append(rehearsal.getStartTime().format(TIME_FORMAT));
        if (rehearsal.getEndTime() != null) {
            sb.append(" – ").append(rehearsal.getEndTime().format(TIME_FORMAT));
        }
        sb.append("\n");
        if (rehearsal.getLocation() != null && !rehearsal.getLocation().isBlank()) {
            sb.append("📍 Miejsce: ").append(rehearsal.getLocation()).append("\n");
        }
        if (rehearsal.getNotes() != null && !rehearsal.getNotes().isBlank()) {
            sb.append("📝 Uwagi: ").append(rehearsal.getNotes()).append("\n");
        }
        sb.append("\nDo zobaczenia!\n");
        return sb.toString();
    }
}
