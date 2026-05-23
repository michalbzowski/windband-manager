package pl.michalbzowski.windband.application.command.event;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RecordPaymentCommand {
    private Long eventId;
    private Long memberId;
    private BigDecimal amount;
}
