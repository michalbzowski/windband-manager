package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.command.event.*;
import pl.michalbzowski.windband.application.query.event.EventQueryService;
import pl.michalbzowski.windband.domain.event.BandEvent;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventCommandService commandService;
    private final EventQueryService queryService;

    @GetMapping
    public List<BandEvent> getAllEvents() {
        return queryService.getAllEvents();
    }

    @GetMapping("/{id}")
    public BandEvent getEvent(@PathVariable Long id) {
        return queryService.getEventById(id);
    }

    @PostMapping
    public ResponseEntity<BandEvent> createEvent(@RequestBody CreateEventCommand cmd) {
        var event = commandService.createEvent(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }

    @PostMapping("/{id}/invite")
    public ResponseEntity<Void> inviteMember(@PathVariable Long id,
                                              @RequestBody InviteMemberCommand cmd) {
        cmd.setEventId(id);
        commandService.inviteMember(cmd);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/invite-group")
    public ResponseEntity<Void> inviteGroup(@PathVariable Long id,
                                             @RequestBody InviteGroupCommand cmd) {
        cmd.setEventId(id);
        commandService.inviteGroup(cmd);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/response")
    public ResponseEntity<Void> recordResponse(@PathVariable Long id,
                                                @RequestBody RecordResponseCommand cmd) {
        cmd.setEventId(id);
        commandService.recordResponse(cmd);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/payment")
    public ResponseEntity<Void> recordPayment(@PathVariable Long id,
                                               @RequestBody RecordPaymentCommand cmd) {
        cmd.setEventId(id);
        commandService.recordPayment(cmd);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{eventId}/payment/{memberId}/paid")
    public ResponseEntity<Void> markPaymentPaid(@PathVariable Long eventId,
                                                 @PathVariable Long memberId) {
        commandService.markPaymentPaid(eventId, memberId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/payment-status")
    public ResponseEntity<Void> updatePaymentStatus(@PathVariable Long id,
                                                     @RequestBody UpdatePaymentStatusCommand cmd) {
        cmd.setEventId(id);
        commandService.updatePaymentStatus(cmd.getEventId(), cmd.getMemberId(), cmd.getStatus());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id) {
        commandService.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }
}
