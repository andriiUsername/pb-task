package privatbank.task.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import privatbank.task.model.ScheduleItemStatus;

public record ScheduleItemResponse(
        long id,
        LocalDate dueDate,
        BigDecimal amount,
        ScheduleItemStatus status,
        Long paymentId,
        Instant paidAt) {
}
