package pl.michalbzowski.windband.application.command.event;

import lombok.Data;

@Data
public class UpdatePaymentStatusCommand {
    private Long eventId;
    private Long memberId;
    private String status;
}
