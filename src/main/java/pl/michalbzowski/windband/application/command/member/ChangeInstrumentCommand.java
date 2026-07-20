package pl.michalbzowski.windband.application.command.member;

import lombok.Data;

@Data
public class ChangeInstrumentCommand {
    private Long memberId;
    private Long instrumentId;
}
