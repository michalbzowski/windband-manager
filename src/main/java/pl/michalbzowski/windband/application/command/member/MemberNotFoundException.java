package pl.michalbzowski.windband.application.command.member;

public class MemberNotFoundException extends RuntimeException {
    public MemberNotFoundException(Long memberId) {
        super("Member not found: " + memberId);
    }
}
