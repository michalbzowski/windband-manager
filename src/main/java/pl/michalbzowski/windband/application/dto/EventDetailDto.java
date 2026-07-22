package pl.michalbzowski.windband.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record EventDetailDto(
        Long id,
        String name,
        LocalDate date,
        LocalTime startTime,
        String location,
        String eventType,
        String paymentType,
        BigDecimal paymentAmount,
        BigDecimal payoutPerMember,
        String notes,
        long confirmedCount,
        long declinedCount,
        long noResponseCount,
        List<ParticipationDto> participations,
        List<GroupSummaryDto> allGroups,
        List<InstrumentCountDto> instrumentSummary
) {
    public record ParticipationDto(
            Long id,
            Long memberId,
            String memberName,
            String memberEmail,
            String instrumentName,
            String response,
            BigDecimal paymentAmount,
            String paymentStatus,
            String invitationStatus,
            boolean emailConsentGiven
    ) {}

    public record InstrumentCountDto(
            String instrumentName,
            long count
    ) {}
}
