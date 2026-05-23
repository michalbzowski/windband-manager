package pl.michalbzowski.windband.application.command.member;

import lombok.Data;

@Data
public class AssignInstrumentCommand {
    private Long memberId;
    private String instrumentName;
    private boolean primary;
}
