package ke.sauti.hms.capture;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import ke.sauti.hms.audit.AuditActor;
import ke.sauti.hms.capture.dto.ConsentResponse;
import ke.sauti.hms.capture.dto.ConsentWordingResponse;
import ke.sauti.hms.capture.dto.RecordConsentRequest;
import ke.sauti.hms.patient.PreferredLanguage;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.visit.VisitService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consent to record, asked by reception (reception recordings), nurses and doctors
 * (consultations). Recording and reading decisions also needs the care team, checked by
 * the access policy.
 */
@RestController
@PreAuthorize("hasAnyRole('RECEPTIONIST', 'NURSE', 'DOCTOR')")
public class ConsentController {

    private final ConsentService service;
    private final ConsentWording wording;

    public ConsentController(ConsentService service, ConsentWording wording) {
        this.service = service;
        this.wording = wording;
    }

    private static VisitService.Actor actor(AuditActor staff) {
        return new VisitService.Actor(staff.actorId(), StaffRole.valueOf(staff.actorRole()));
    }

    @GetMapping("/api/consent/wording")
    public ConsentWordingResponse wording(@RequestParam("language") PreferredLanguage language) {
        return new ConsentWordingResponse(wording.versionLabel(language), language, wording.text(language), true);
    }

    @PostMapping("/api/visits/{visitId}/consents")
    @ResponseStatus(HttpStatus.CREATED)
    public ConsentResponse record(
            @PathVariable UUID visitId,
            @Valid @RequestBody RecordConsentRequest request,
            @AuthenticationPrincipal AuditActor staff) {
        return ConsentResponse.of(
                service.record(visitId, request.purpose(), request.decision(), request.givenBy(), actor(staff)));
    }

    @GetMapping("/api/visits/{visitId}/consents")
    public List<ConsentResponse> history(@PathVariable UUID visitId, @AuthenticationPrincipal AuditActor staff) {
        return service.history(visitId, actor(staff)).stream()
                .map(ConsentResponse::of)
                .toList();
    }
}
