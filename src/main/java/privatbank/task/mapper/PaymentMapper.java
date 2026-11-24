package privatbank.task.mapper;

import org.springframework.stereotype.Component;
import privatbank.task.dto.response.PaymentResponse;
import privatbank.task.model.PaymentEntity;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(PaymentEntity payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getType(),
                payment.getStatus(),
                payment.getSourceWalletId(),
                payment.getTargetWalletId(),
                payment.getAmount().amount(),
                payment.getAmount().currency().getCurrencyCode(),
                payment.getCreatedBy(),
                payment.getCreatedAt(),
                payment.getConfirmedBy(),
                payment.getCompletedAt());
    }
}
