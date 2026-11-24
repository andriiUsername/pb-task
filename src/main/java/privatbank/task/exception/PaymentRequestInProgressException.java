package privatbank.task.exception;

public class PaymentRequestInProgressException extends ConflictException {

    public PaymentRequestInProgressException(String idempotencyKey) {
        super("REQUEST_IN_PROGRESS",
                "A request with idempotency key '%s' is being processed, retry later".formatted(idempotencyKey));
    }
}
