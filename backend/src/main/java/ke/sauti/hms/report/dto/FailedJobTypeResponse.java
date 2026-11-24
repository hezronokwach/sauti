package ke.sauti.hms.report.dto;

/**
 * How many jobs of one type failed in the last 24 hours.
 *
 * @param type the job type, for example TRANSCRIBE
 * @param count how many failed
 */
public record FailedJobTypeResponse(String type, long count) {}
