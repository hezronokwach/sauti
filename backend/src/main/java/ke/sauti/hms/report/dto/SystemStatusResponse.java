package ke.sauti.hms.report.dto;

import java.util.List;

/**
 * The administrator's view of system health. It holds no patient data.
 *
 * @param jobs background work
 * @param backup the nightly backup
 * @param rateLimitBlocks refused requests since this app instance started, up to 24 hours back
 */
public record SystemStatusResponse(
        JobStatusResponse jobs, BackupStatusResponse backup, List<RateLimitBlockResponse> rateLimitBlocks) {}
