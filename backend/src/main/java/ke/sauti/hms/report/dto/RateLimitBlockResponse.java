package ke.sauti.hms.report.dto;

/**
 * Requests one limit refused in the last 24 hours. It never names an address or account.
 *
 * @param limit the limit's name, for example LOGIN_ACCOUNT
 * @param count how many were refused
 */
public record RateLimitBlockResponse(String limit, long count) {}
