package pl.michalbzowski.windband.application.command.member;

import lombok.Data;

@Data
public class UpdateMemberCommand {
    private Long memberId;
    private String email;
    private String phone;
}
