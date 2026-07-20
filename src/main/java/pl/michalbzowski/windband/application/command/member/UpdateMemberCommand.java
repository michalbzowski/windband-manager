package pl.michalbzowski.windband.application.command.member;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateMemberCommand {
    private Long memberId;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private LocalDate joinedDate;
    private LocalDate resignedDate;
    private boolean active;
    private String email;
    private String phone;
    private Long instrumentId;
    private boolean emailConsentGiven;
}
