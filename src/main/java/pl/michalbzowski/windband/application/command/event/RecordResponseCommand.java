package pl.michalbzowski.windband.application.command.event;

import lombok.Data;

@Data
public class RecordResponseCommand {
    private Long eventId;
    private Long memberId;
    private String response;
}
