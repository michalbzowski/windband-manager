package pl.michalbzowski.windband.application.command.rehearsal;

import lombok.Data;

@Data
public class RecordAttendanceCommand {
    private Long rehearsalId;
    private Long memberId;
    private String status;
}
