package ke.sauti.hms.common.error;

/** The request is well formed but breaks a business rule (422). */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATED, message);
    }
}
