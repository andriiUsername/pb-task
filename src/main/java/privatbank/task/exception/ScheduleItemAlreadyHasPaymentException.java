package privatbank.task.exception;

public class ScheduleItemAlreadyHasPaymentException extends ConflictException {

    public ScheduleItemAlreadyHasPaymentException(Long itemId, Long paymentId) {
        super("SCHEDULE_ITEM_ALREADY_HAS_PAYMENT",
                "Schedule item %d already has payment %d".formatted(itemId, paymentId));
    }
}
