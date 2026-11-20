package ke.sauti.hms.capture;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import ke.sauti.hms.audit.AuditActor;
import ke.sauti.hms.capture.dto.AudioPieceResponse;
import ke.sauti.hms.capture.dto.CaptureSessionResponse;
import ke.sauti.hms.capture.dto.StartCaptureRequest;
import ke.sauti.hms.capture.dto.StopCaptureRequest;
import ke.sauti.hms.capture.dto.TranscriptSegmentResponse;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.visit.VisitService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Starting and stopping recordings, for the people who ask consent and record. */
@RestController
@PreAuthorize("hasAnyRole('RECEPTIONIST', 'NURSE', 'DOCTOR')")
public class CaptureController {

    private final CaptureService service;
    private final PieceService pieces;
    private final TranscriptService transcripts;

    public CaptureController(CaptureService service, PieceService pieces, TranscriptService transcripts) {
        this.service = service;
        this.pieces = pieces;
        this.transcripts = transcripts;
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

    /**
     * One audio piece, as raw bytes with its audio content type. The checksum, length and
     * position travel in headers. 201 for a new piece; 200 for a repeat of one already received.
     */
    @PutMapping("/api/captures/{sessionId}/pieces/{sequenceNo}")
    public ResponseEntity<AudioPieceResponse> uploadPiece(
            @PathVariable UUID sessionId,
            @PathVariable int sequenceNo,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType,
            @RequestHeader("X-Piece-Sha256") String sha256,
            @RequestHeader("X-Piece-Duration-Ms") int durationMs,
            @RequestHeader("X-Piece-Offset-Ms") int offsetMs,
            @RequestBody byte[] audio,
            @AuthenticationPrincipal AuditActor staff) {
        PieceService.Received received = pieces.receive(
                sessionId,
                new PieceService.Upload(sequenceNo, contentType, sha256, durationMs, offsetMs),
                audio,
                actor(staff));
        return ResponseEntity.status(received.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(AudioPieceResponse.of(received.piece()));
    }

    /** Segments after a position (2-second polling); leave out after on first open. */
    @GetMapping("/api/captures/{sessionId}/segments")
    public List<TranscriptSegmentResponse> segments(
            @PathVariable UUID sessionId,
            @RequestParam(name = "after", required = false) Integer after,
            @AuthenticationPrincipal AuditActor staff) {
        return transcripts.segmentsAfter(sessionId, after, actor(staff)).stream()
                .map(TranscriptSegmentResponse::of)
                .toList();
    }

    /** The audio piece playing at a moment, decrypted; never cached by the browser. */
    @GetMapping("/api/captures/{sessionId}/audio")
    public ResponseEntity<byte[]> audio(
            @PathVariable UUID sessionId, @RequestParam("atMs") int atMs, @AuthenticationPrincipal AuditActor staff) {
        TranscriptService.Audio audio = transcripts.audioAt(sessionId, atMs, actor(staff));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(audio.mimeType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Piece-Offset-Ms", String.valueOf(audio.offsetMs()))
                .body(audio.bytes());
    }

    @PostMapping("/api/captures/{sessionId}/erase")
    public CaptureSessionResponse erase(@PathVariable UUID sessionId, @AuthenticationPrincipal AuditActor staff) {
        return CaptureSessionResponse.of(transcripts.erase(sessionId, actor(staff)));
    }

    @GetMapping("/api/captures/{sessionId}")
    public CaptureSessionResponse get(@PathVariable UUID sessionId, @AuthenticationPrincipal AuditActor staff) {
        return CaptureSessionResponse.of(service.get(sessionId, actor(staff)));
    }
}
