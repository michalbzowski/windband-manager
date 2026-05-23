package pl.michalbzowski.windband.application.command.member;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateMemberCommand {
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String role;
    private boolean ospMember;
    private String email;
    private String phone;
}
