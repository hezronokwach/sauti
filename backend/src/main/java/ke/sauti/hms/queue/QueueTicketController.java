package ke.sauti.hms.queue;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import ke.sauti.hms.audit.AuditActor;
import ke.sauti.hms.queue.dto.IssueTicketRequest;
import ke.sauti.hms.queue.dto.QueueBoardResponse;
import ke.sauti.hms.queue.dto.TicketResponse;
import ke.sauti.hms.staff.StaffRole;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Arrival tickets: reception issues and calls them; clinical staff attend emergencies; the waiting area sees codes. */
@RestController
public class QueueTicketController {

    private final QueueTicketService service;

    public QueueTicketController(QueueTicketService service) {
        this.service = service;
    }

    private static QueueTicketService.Actor actor(AuditActor staff) {
        return new QueueTicketService.Actor(staff.actorId(), StaffRole.valueOf(staff.actorRole()));
    }

    @PostMapping("/api/queue/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public TicketResponse issue(
            @Valid @RequestBody IssueTicketRequest request, @AuthenticationPrincipal AuditActor staff) {
        return TicketResponse.of(service.issue(request.lane(), actor(staff)));
    }

    /** The next ticket in fair order; 204 when nobody is waiting. */
    @PostMapping("/api/queue/tickets/call-next")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<TicketResponse> callNext(@AuthenticationPrincipal AuditActor staff) {
        return service.callNext(actor(staff))
                .map(ticket -> ResponseEntity.ok(TicketResponse.of(ticket)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/api/queue/tickets/{ticketId}/cancel")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public TicketResponse cancel(@PathVariable UUID ticketId, @AuthenticationPrincipal AuditActor staff) {
        return TicketResponse.of(service.cancel(ticketId, actor(staff)));
    }

    @PostMapping("/api/queue/tickets/{ticketId}/attend")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR')")
    public TicketResponse attend(@PathVariable UUID ticketId, @AuthenticationPrincipal AuditActor staff) {
        return TicketResponse.of(service.attendEmergency(ticketId, actor(staff)));
    }

    @GetMapping("/api/queue/tickets")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'NURSE', 'DOCTOR')")
    public List<TicketResponse> today() {
        return service.today().stream().map(TicketResponse::of).toList();
    }

    /** No login: the waiting-area screen. Codes only. */
    @GetMapping("/api/public/queue-board")
    public QueueBoardResponse board() {
        return QueueBoardResponse.of(service.board());
    }
}
