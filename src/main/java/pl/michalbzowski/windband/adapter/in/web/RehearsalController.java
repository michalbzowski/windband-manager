package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.command.rehearsal.RecordAttendanceCommand;
import pl.michalbzowski.windband.application.command.rehearsal.ScheduleRehearsalCommand;
import pl.michalbzowski.windband.application.command.rehearsal.RehearsalCommandService;
import pl.michalbzowski.windband.application.query.rehearsal.RehearsalQueryService;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;

import java.util.List;

@RestController
@RequestMapping("/api/rehearsals")
@RequiredArgsConstructor
public class RehearsalController {

    private final RehearsalCommandService commandService;
    private final RehearsalQueryService queryService;

    @GetMapping
    public List<Rehearsal> getAllRehearsals() {
        return queryService.getAllRehearsals();
    }

    @GetMapping("/{id}")
    public Rehearsal getRehearsal(@PathVariable Long id) {
        return queryService.getRehearsalById(id);
    }

    @PostMapping
    public ResponseEntity<Rehearsal> scheduleRehearsal(@RequestBody ScheduleRehearsalCommand cmd) {
        var rehearsal = commandService.scheduleRehearsal(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(rehearsal);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Rehearsal> updateRehearsal(@PathVariable Long id,
                                                     @RequestBody ScheduleRehearsalCommand cmd) {
        var rehearsal = commandService.updateRehearsal(id, cmd);
        return ResponseEntity.ok(rehearsal);
    }

    @PostMapping("/{id}/attendance")
    public ResponseEntity<Void> recordAttendance(@PathVariable Long id,
                                                  @RequestBody RecordAttendanceCommand cmd) {
        cmd.setRehearsalId(id);
        commandService.recordAttendance(cmd);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRehearsal(@PathVariable Long id) {
        commandService.deleteRehearsal(id);
        return ResponseEntity.noContent().build();
    }
}
