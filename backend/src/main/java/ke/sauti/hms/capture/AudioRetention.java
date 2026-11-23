package ke.sauti.hms.capture;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import ke.sauti.hms.jobs.JobQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Removes old audio files. Transcripts, notes and every other record stay.
 *
 * <p>The database is changed and committed first, and the file is deleted afterwards. A failed
 * delete therefore leaves only a file no live row points to, which the orphan sweep removes on a
 * later night. The sweep looks only under {@value #RECORDINGS}, so cached spoken instructions in
 * the same bucket are never touched.
 */
@Service
public class AudioRetention {

    static final String RECORDINGS = "sessions/";

    private static final Logger log = LoggerFactory.getLogger(AudioRetention.class);
    private static final int BATCH = 200;
    private static final String ERASE_BATCH = """
            with due as (
                select p.id from audio_piece p
                join capture_session s on s.id = p.session_id
                join clinical_note n on n.visit_id = s.visit_id
                where n.status = 'SIGNED' and n.signed_at < ?
                  and p.status <> 'ERASED' and s.status <> 'RECORDING'
                order by p.id
                limit ?
                for update of p skip locked
            )
            update audio_piece a set status = 'ERASED', updated_at = ?, version = a.version + 1
            from due where a.id = due.id
            returning a.id, a.session_id, a.storage_key
            """;

    private record ErasedPiece(UUID id, UUID sessionId, String storageKey) {}

    private final JdbcTemplate jdbc;
    private final AudioStore store;
    private final CaptureSessionRepository sessions;
    private final JobQueue jobs;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public AudioRetention(
            JdbcTemplate jdbc,
            AudioStore store,
            CaptureSessionRepository sessions,
            JobQueue jobs,
            AuditService audit,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.store = store;
        this.sessions = sessions;
        this.jobs = jobs;
        this.audit = audit;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Erases the audio of visits whose note was signed before the cutoff; returns how many pieces. */
    public int eraseAudioOfNotesSignedBefore(Instant cutoff) {
        int total = 0;
        List<ErasedPiece> batch;
        do {
            batch = Objects.requireNonNull(transaction.execute(tx -> eraseBatch(cutoff)));
            batch.forEach(piece -> deleteFile(piece.storageKey()));
            total += batch.size();
        } while (batch.size() == BATCH);
        return total;
    }

    private List<ErasedPiece> eraseBatch(Instant cutoff) {
        List<ErasedPiece> erased = jdbc.query(
                ERASE_BATCH,
                (row, number) -> new ErasedPiece(
                        row.getObject("id", UUID.class),
                        row.getObject("session_id", UUID.class),
                        row.getString("storage_key")),
                Timestamp.from(cutoff),
                BATCH,
                Timestamp.from(clock.instant()));
        erased.forEach(piece -> jobs.cancel(TranscribeHandler.TYPE, piece.id()));
        erased.stream()
                .collect(Collectors.groupingBy(ErasedPiece::sessionId, Collectors.counting()))
                .forEach((sessionId, count) -> audit.record(
                        AuditAction.AUDIO_RETENTION_ERASED,
                        CaptureSession.class.getSimpleName(),
                        sessionId,
                        null,
                        sessions.findById(sessionId)
                                .map(CaptureSession::getVisitId)
                                .orElse(null),
                        Map.of("pieces", count)));
        return erased;
    }

    /**
     * Deletes recording files older than the given time that no live row points to. The age check
     * protects an upload whose file is stored but whose row is not committed yet.
     */
    public int deleteOrphansOlderThan(Instant before) {
        List<String> orphans = new ArrayList<>();
        store.forEachPageOlderThan(RECORDINGS, before, keys -> {
            Set<String> live = liveKeys(keys);
            keys.stream().filter(key -> !live.contains(key)).forEach(orphans::add);
        });
        int deleted = 0;
        for (String key : orphans) {
            if (deleteFile(key)) {
                deleted++;
            }
        }
        if (deleted > 0) {
            int count = deleted;
            transaction.executeWithoutResult(
                    tx -> audit.record(AuditAction.ORPHAN_AUDIO_DELETED, Map.of("files", count)));
        }
        return deleted;
    }

    private Set<String> liveKeys(List<String> keys) {
        return new HashSet<>(Objects.requireNonNull(jdbc.execute((ConnectionCallback<List<String>>) connection -> {
            Array array = connection.createArrayOf("varchar", keys.toArray());
            return jdbc.queryForList(
                    "select storage_key from audio_piece where storage_key = any(?) and status <> 'ERASED'",
                    String.class,
                    array);
        })));
    }

    private boolean deleteFile(String key) {
        try {
            store.delete(key);
            return true;
        } catch (RuntimeException e) {
            log.error("Could not delete audio object {}; a later clean-up will retry it", key, e);
            return false;
        }
    }
}
