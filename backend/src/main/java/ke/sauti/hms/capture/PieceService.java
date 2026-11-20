package ke.sauti.hms.capture;

import java.io.Serial;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import ke.sauti.hms.common.error.ApiException;
import ke.sauti.hms.common.error.BusinessRuleException;
import ke.sauti.hms.common.error.ConflictException;
import ke.sauti.hms.common.error.ErrorCode;
import ke.sauti.hms.common.error.ForbiddenException;
import ke.sauti.hms.common.error.NotFoundException;
import ke.sauti.hms.jobs.JobQueue;
import ke.sauti.hms.visit.VisitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receiving audio pieces from the recorder. A piece is checked (who, which session, consent,
 * size, type, checksum), encrypted into storage, recorded, and its transcription queued, all
 * or nothing. Sending the same piece twice is harmless, so the browser can simply retry.
 */
@Service
@Transactional
public class PieceService {

    /** About 30 seconds of compressed speech is far below this. */
    static final int MAX_BYTES = 2 * 1024 * 1024;

    static final Set<String> AUDIO_TYPES =
            Set.of("audio/webm", "audio/ogg", "audio/mp4", "audio/mpeg", "audio/wav", "audio/x-wav");

    private final CaptureSessionRepository sessions;
    private final AudioPieceRepository pieces;
    private final ConsentService consents;
    private final AudioStore store;
    private final JobQueue jobs;

    public PieceService(
            CaptureSessionRepository sessions,
            AudioPieceRepository pieces,
            ConsentService consents,
            AudioStore store,
            JobQueue jobs) {
        this.sessions = sessions;
        this.pieces = pieces;
        this.consents = consents;
        this.store = store;
        this.jobs = jobs;
    }

    /** What happened to an upload. */
    public record Received(AudioPiece piece, boolean created) {}

    /**
     * The description of one uploaded piece (the audio travels separately).
     *
     * @param sequenceNo its place in the recording, from 0
     * @param mimeType the audio content type
     * @param sha256 hex SHA-256 of the audio, as the browser computed it
     * @param durationMs how long the piece is
     * @param offsetMs where it starts in the recording
     */
    public record Upload(int sequenceNo, String mimeType, String sha256, int durationMs, int offsetMs) {}

    public Received receive(UUID sessionId, Upload upload, byte[] audio, VisitService.Actor actor) {
        CaptureSession session =
                sessions.findById(sessionId).orElseThrow(() -> new NotFoundException("No such recording"));
        if (!session.getStartedBy().equals(actor.staffId())) {
            throw new ForbiddenException("Only the person recording can send its audio");
        }
        if (session.getStatus() == CaptureStatus.ERASED) {
            throw new GoneException();
        }
        if (session.getStatus() == CaptureStatus.COMPLETE) {
            throw new ConflictException("This recording is already complete");
        }
        String sha256 = upload.sha256() == null ? "" : upload.sha256().trim().toLowerCase(Locale.ROOT);
        String mimeType = baseType(upload.mimeType());

        var existing = pieces.findBySessionIdAndSequenceNo(sessionId, upload.sequenceNo());
        if (existing.isPresent()) {
            if (existing.get().getSha256().equals(sha256)) {
                return new Received(existing.get(), false);
            }
            throw new ConflictException("A different piece " + upload.sequenceNo() + " was already received");
        }

        consents.requireAgreed(session.getVisitId(), session.getPurpose());
        check(upload, audio, mimeType, sha256);

        String key = "sessions/" + sessionId + "/" + String.format(Locale.ROOT, "%06d", upload.sequenceNo());
        store.put(key, audio);
        AudioPiece piece = pieces.saveAndFlush(new AudioPiece(
                sessionId, upload.sequenceNo(), key, upload.durationMs(), upload.offsetMs(), sha256, mimeType));
        jobs.enqueue(TranscribeHandler.TYPE, piece.getId());
        return new Received(piece, true);
    }

    private static String baseType(String mimeType) {
        if (mimeType == null) {
            return "";
        }
        int parameters = mimeType.indexOf(';');
        return (parameters < 0 ? mimeType : mimeType.substring(0, parameters))
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static void check(Upload upload, byte[] audio, String type, String sha256) {
        if (audio.length == 0 || audio.length > MAX_BYTES) {
            throw new BusinessRuleException("A piece must be between 1 byte and " + MAX_BYTES + " bytes");
        }
        if (!AUDIO_TYPES.contains(type)) {
            throw new BusinessRuleException("A piece must be audio (webm, ogg, mp4, mpeg or wav)");
        }
        if (upload.sequenceNo() < 0 || upload.durationMs() < 0 || upload.offsetMs() < 0) {
            throw new BusinessRuleException("Sequence, duration and offset cannot be negative");
        }
        if (!sha256(audio).equals(sha256)) {
            throw new BusinessRuleException("The checksum does not match the audio; send the piece again");
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /** 410: the recording was erased; the browser should stop sending its pieces. */
    static final class GoneException extends ApiException {

        @Serial
        private static final long serialVersionUID = 1L;

        GoneException() {
            super(ErrorCode.GONE, ErrorCode.GONE.defaultMessage());
        }
    }
}
