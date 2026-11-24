package privatbank.task.exception;

public class FundingRequestSuspendedException extends ConflictException {

    public FundingRequestSuspendedException(Long requestId) {
        super("FUNDING_REQUEST_SUSPENDED",
                "Funding request %d is suspended: its payments can be neither created nor confirmed"
                        .formatted(requestId));
    }
}
