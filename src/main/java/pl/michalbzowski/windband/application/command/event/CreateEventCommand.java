package pl.michalbzowski.windband.application.command.event;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class CreateEventCommand {
    private String name;
    private LocalDate date;
    private LocalTime startTime;
    private String location;
    private String eventType;
    private String notes;
}
