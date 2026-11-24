package privatbank.task.exception;

public class IdempotencyKeyReusedException extends BusinessRuleViolationException {

    public IdempotencyKeyReusedException(String idempotencyKey) {
        super("IDEMPOTENCY_KEY_REUSED",
                "Idempotency key '%s' was already used for a different payment".formatted(idempotencyKey));
    }
}
