package privatbank.task.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import privatbank.task.model.PaymentStatus;
import privatbank.task.model.PaymentType;

public record PaymentResponse(
        long id,
        PaymentType type,
        PaymentStatus status,
        Long sourceWalletId,
        Long targetWalletId,
        BigDecimal amount,
        String currency,
        String createdBy,
        Instant createdAt,
        String confirmedBy,
        Instant completedAt) {
}
