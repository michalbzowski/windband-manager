package pl.michalbzowski.windband.application.command.member;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateMemberCommand {
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private LocalDate joinedDate;
    private String email;
    private String phone;
    private Long instrumentId;
}
