package privatbank.task.exception;

public class PaymentNotFoundException extends NotFoundException {

    public PaymentNotFoundException(long paymentId) {
        super("PAYMENT_NOT_FOUND", "Payment %d not found".formatted(paymentId));
    }
}
