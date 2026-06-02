package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.command.member.InstrumentCommandService;
import pl.michalbzowski.windband.domain.member.Instrument;

import java.util.List;

@RestController
@RequestMapping("/api/instruments")
@RequiredArgsConstructor
public class InstrumentController {

    private final InstrumentCommandService instrumentCommandService;

    @GetMapping
    public List<Instrument> getAllInstruments() {
        return instrumentCommandService.getAllInstruments();
    }

    @PostMapping
    public ResponseEntity<Instrument> createInstrument(@RequestBody InstrumentRequest request) {
        var instrument = instrumentCommandService.createInstrument(request.name(), request.description(), request.sortPriority());
        return ResponseEntity.status(HttpStatus.CREATED).body(instrument);
    }

    @PutMapping("/{id}")
    public Instrument updateInstrument(@PathVariable Long id, @RequestBody InstrumentRequest request) {
        return instrumentCommandService.updateInstrument(id, request.name(), request.description(), request.sortPriority());
    }

    @PutMapping("/{id}/priority")
    public Instrument updateSortPriority(@PathVariable Long id, @RequestBody PriorityRequest request) {
        return instrumentCommandService.updateSortPriority(id, request.priority());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInstrument(@PathVariable Long id) {
        instrumentCommandService.deleteInstrument(id);
        return ResponseEntity.noContent().build();
    }

    public record InstrumentRequest(String name, String description, Integer sortPriority) {}
    public record PriorityRequest(Integer priority) {}
}
