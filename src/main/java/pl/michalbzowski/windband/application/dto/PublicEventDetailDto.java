package pl.michalbzowski.windband.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record PublicEventDetailDto(
        Long eventId,
        String eventName,
        LocalDate eventDate,
        LocalTime eventTime,
        String location,
        String eventType,
        String paymentType,
        String paymentTypeDisplay,
        BigDecimal paymentAmount,
        BigDecimal payoutPerMember,
        String notes,
        Long memberId,
        String memberName,
        String memberEmail,
        String instrumentName,
        String currentResponse,
        boolean alreadyResponded,
        String token
) {
    public static String formatPaymentType(String paymentType) {
        return switch (paymentType) {
            case "FREE" -> "💰 Granie bezpłatne";
            case "PAID_SPLIT" -> "💰 Płatne — podział między grających";
            case "PAID_TO_TEAM" -> "💰 Płatne — na konto zespołu";
            default -> "💰 " + paymentType;
        };
    }
}