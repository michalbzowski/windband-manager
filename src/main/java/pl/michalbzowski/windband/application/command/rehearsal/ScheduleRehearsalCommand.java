package pl.michalbzowski.windband.application.command.rehearsal;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class ScheduleRehearsalCommand {
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private String location;
    private String notes;
}
