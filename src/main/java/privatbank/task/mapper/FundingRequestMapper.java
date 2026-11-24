package privatbank.task.mapper;

import org.springframework.stereotype.Component;
import privatbank.task.dto.response.FundingRequestResponse;
import privatbank.task.dto.response.ScheduleItemResponse;
import privatbank.task.model.FundingRequestEntity;
import privatbank.task.model.ScheduleItemEntity;

@Component
public class FundingRequestMapper {

    public FundingRequestResponse toResponse(FundingRequestEntity request) {
        return new FundingRequestResponse(
                request.getId(),
                request.getSourceWalletId(),
                request.getTargetWalletId(),
                request.getCurrency().getCurrencyCode(),
                request.getStatus(),
                request.getSchedule().stream().map(this::toResponse).toList());
    }

    private ScheduleItemResponse toResponse(ScheduleItemEntity item) {
        return new ScheduleItemResponse(
                item.getId(),
                item.getDueDate(),
                item.getAmount().amount(),
                item.getStatus(),
                item.getPaymentId(),
                item.getPaidAt());
    }
}
