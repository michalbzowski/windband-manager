package pl.michalbzowski.windband.application.dto;

import java.time.LocalDate;
import java.util.List;

public record MemberDto(
        Long id,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        int age,
        boolean minor,
        boolean senior,
        String email,
        String phone,
        boolean active,
        String primaryInstrument,
        List<String> allInstruments,
        LocalDate joinedDate,
        LocalDate resignedDate,
        Long instrumentId,
        String instrumentName,
        boolean emailConsentGiven
) {
}
