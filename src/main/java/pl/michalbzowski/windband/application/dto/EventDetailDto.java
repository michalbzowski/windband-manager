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
        String notes,
        long confirmedCount,
        long declinedCount,
        long noResponseCount,
        List<ParticipationDto> participations
) {
    public record ParticipationDto(
            Long id,
            String memberName,
            String response,
            BigDecimal paymentAmount,
            String paymentStatus
    ) {}
}
