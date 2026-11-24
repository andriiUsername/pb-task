package privatbank.task.dto.response;

import java.util.List;
import privatbank.task.model.FundingRequestStatus;

public record FundingRequestResponse(
        long id,
        long sourceWalletId,
        long targetWalletId,
        String currency,
        FundingRequestStatus status,
        List<ScheduleItemResponse> schedule) {
}
