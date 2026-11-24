package privatbank.task.exception;

public class FundingRequestFullyPaidException extends ConflictException {

    public FundingRequestFullyPaidException(Long requestId) {
        super("FUNDING_REQUEST_FULLY_PAID",
                "Funding request %d is fully paid and cannot be suspended".formatted(requestId));
    }
}
