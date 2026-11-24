package privatbank.task.exception;

public class ScheduleItemNotFoundException extends NotFoundException {

    public ScheduleItemNotFoundException(Long requestId, long itemId) {
        super("SCHEDULE_ITEM_NOT_FOUND", "Funding request %d has no schedule item %d".formatted(requestId, itemId));
    }
}
