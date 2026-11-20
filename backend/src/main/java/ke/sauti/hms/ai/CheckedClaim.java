package ke.sauti.hms.ai;

/**
 * One AI claim with the server's verdict on it.
 *
 * @param claim what the model wrote
 * @param verdict what the checks found
 */
public record CheckedClaim(ClinicalAi.DraftClaim claim, ClaimChecker.Verdict verdict) {}
