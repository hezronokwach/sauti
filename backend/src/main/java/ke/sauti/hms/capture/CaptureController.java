package ke.sauti.hms.capture;

import jakarta.validation.Valid;
import java.util.UUID;
import ke.sauti.hms.audit.AuditActor;
import ke.sauti.hms.capture.dto.CaptureSessionResponse;
import ke.sauti.hms.capture.dto.StartCaptureRequest;
import ke.sauti.hms.capture.dto.StopCaptureRequest;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.visit.VisitService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Starting and stopping recordings, for the people who ask consent and record. */
@RestController
@PreAuthorize("hasAnyRole('RECEPTIONIST', 'NURSE', 'DOCTOR')")
public class CaptureController {

    private final CaptureService service;

    public CaptureController(CaptureService service) {
        this.service = service;
    }

    private static VisitService.Actor actor(AuditActor staff) {
        return new VisitService.Actor(staff.actorId(), StaffRole.valueOf(staff.actorRole()));
    }

    @PostMapping("/api/visits/{visitId}/captures")
    @ResponseStatus(HttpStatus.CREATED)
    public CaptureSessionResponse start(
            @PathVariable UUID visitId,
            @Valid @RequestBody StartCaptureRequest request,
            @AuthenticationPrincipal AuditActor staff) {
        return CaptureSessionResponse.of(
                service.start(visitId, request.purpose(), request.languageHint(), actor(staff)));
    }

    @PostMapping("/api/captures/{sessionId}/stop")
    public CaptureSessionResponse stop(
            @PathVariable UUID sessionId,
            @Valid @RequestBody StopCaptureRequest request,
            @AuthenticationPrincipal AuditActor staff) {
        return CaptureSessionResponse.of(service.stop(sessionId, request.expectedPieces(), actor(staff)));
    }

    @GetMapping("/api/captures/{sessionId}")
    public CaptureSessionResponse get(@PathVariable UUID sessionId, @AuthenticationPrincipal AuditActor staff) {
        return CaptureSessionResponse.of(service.get(sessionId, actor(staff)));
    }
}
