package pl.michalbzowski.windband.application.command.member;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateMemberCommand {
    private Long memberId;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String role;
    private boolean ospMember;
    private boolean active;
    private String email;
    private String phone;
}
