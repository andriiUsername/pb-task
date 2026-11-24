package privatbank.task.exception;

public class TopUpConfirmationDeniedException extends ForbiddenException {

    public TopUpConfirmationDeniedException(long paymentId) {
        super("TOP_UP_CONFIRMATION_DENIED",
                "Top-up %d is confirmed by an operator once the money has been received".formatted(paymentId));
    }
}
