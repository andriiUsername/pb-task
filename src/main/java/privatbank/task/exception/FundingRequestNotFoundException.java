package privatbank.task.exception;

public class FundingRequestNotFoundException extends NotFoundException {

    public FundingRequestNotFoundException(long requestId) {
        super("FUNDING_REQUEST_NOT_FOUND", "Funding request %d not found".formatted(requestId));
    }
}
