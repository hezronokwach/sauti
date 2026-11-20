package ke.sauti.hms.capture;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import ke.sauti.hms.common.error.NotFoundException;
import ke.sauti.hms.jobs.JobQueue;
import ke.sauti.hms.visit.AccessPolicy;
import ke.sauti.hms.visit.ClinicalData;
import ke.sauti.hms.visit.VisitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Reading a recording's transcript, playing its audio, and erasing it. Every read goes
 * through the access policy; opening a transcript and playing audio are access-logged.
 */
@Service
@Transactional
public class TranscriptService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptService.class);
    private static final String SESSION = CaptureSession.class.getSimpleName();

    private final CaptureSessionRepository sessions;
    private final AudioPieceRepository pieces;
    private final TranscriptSegmentRepository segments;
    private final AudioStore store;
    private final JobQueue jobs;
    private final AccessPolicy accessPolicy;
    private final AuditService audit;
    private final Clock clock;

    public TranscriptService(
            CaptureSessionRepository sessions,
            AudioPieceRepository pieces,
            TranscriptSegmentRepository segments,
            AudioStore store,
            JobQueue jobs,
            AccessPolicy accessPolicy,
            AuditService audit,
            Clock clock) {
        this.sessions = sessions;
        this.pieces = pieces;
        this.segments = segments;
        this.store = store;
        this.jobs = jobs;
        this.accessPolicy = accessPolicy;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Segments after a position, for polling. The first fetch (no position) counts as
     * opening the transcript and is access-logged; the polls that follow are not, so the
     * log records who opened it rather than every two seconds of watching.
     */
    public List<TranscriptSegment> segmentsAfter(UUID sessionId, Integer afterOrderNo, VisitService.Actor actor) {
        CaptureSession session = load(sessionId);
        accessPolicy.requireVisitAccess(actor, session.getVisitId(), ClinicalData.TRANSCRIPT, afterOrderNo == null);
        if (session.getStatus() == CaptureStatus.ERASED) {
            throw new PieceService.GoneException();
        }
        return segments.findBySessionIdAndOrderNoGreaterThanOrderByOrderNoAsc(
                sessionId, afterOrderNo == null ? -1 : afterOrderNo);
    }

    /** The piece playing at a moment of the recording, decrypted. Every play is access-logged. */
    public Audio audioAt(UUID sessionId, int atMs, VisitService.Actor actor) {
        CaptureSession session = load(sessionId);
        accessPolicy.requireVisitAccess(actor, session.getVisitId(), ClinicalData.AUDIO, true);
        if (session.getStatus() == CaptureStatus.ERASED) {
            throw new PieceService.GoneException();
        }
        AudioPiece piece = pieces.findBySessionIdOrderBySequenceNoAsc(sessionId).stream()
                .filter(p -> p.getStatus() != AudioPieceStatus.ERASED)
                .filter(p -> atMs >= p.getOffsetMs() && atMs < p.getOffsetMs() + p.getDurationMs())
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No audio at that time"));
        return new Audio(piece.getMimeType(), piece.getOffsetMs(), store.get(piece.getStorageKey()));
    }

    /**
     * Erases a recording: the session is marked ERASED, its segments deleted, its queued
     * transcriptions cancelled, and once that is saved its encrypted audio files are deleted
     * from storage. The audit row says how many pieces, never what was said.
     */
    public CaptureSession erase(UUID sessionId, VisitService.Actor actor) {
        CaptureSession session = load(sessionId);
        accessPolicy.requireVisitAccess(actor, session.getVisitId(), ClinicalData.CONSENT, false);
        if (session.getStatus() == CaptureStatus.ERASED) {
            return session;
        }
        List<AudioPiece> all = pieces.findBySessionIdOrderBySequenceNoAsc(sessionId);
        int segmentCount = segments.deleteBySessionId(sessionId);
        List<String> keys = all.stream().map(AudioPiece::getStorageKey).toList();
        for (AudioPiece piece : all) {
            jobs.cancel(TranscribeHandler.TYPE, piece.getId());
            piece.markStatus(AudioPieceStatus.ERASED);
        }
        session.erase(clock.instant());
        audit.record(
                AuditAction.CAPTURE_ERASED,
                SESSION,
                sessionId,
                null,
                session.getVisitId(),
                Map.of("pieces", all.size(), "segments", segmentCount));
        deleteAfterCommit(keys);
        return session;
    }

    /** Storage deletes wait for the commit, so a rolled-back erase never loses audio. */
    private void deleteAfterCommit(List<String> keys) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String key : keys) {
                    try {
                        store.delete(key);
                    } catch (RuntimeException e) {
                        log.error("Could not delete erased audio object {}; it remains encrypted in storage", key, e);
                    }
                }
            }
        });
    }

    private CaptureSession load(UUID sessionId) {
        return sessions.findById(sessionId).orElseThrow(() -> new NotFoundException("No such recording"));
    }

    /** Decrypted audio for playback: its content type, where it starts, and the bytes. */
    public static final class Audio {

        private final String mimeType;
        private final int offsetMs;
        private final byte[] bytes;

        Audio(String mimeType, int offsetMs, byte[] bytes) {
            this.mimeType = mimeType;
            this.offsetMs = offsetMs;
            this.bytes = bytes.clone();
        }

        public String mimeType() {
            return mimeType;
        }

        public int offsetMs() {
            return offsetMs;
        }

        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
