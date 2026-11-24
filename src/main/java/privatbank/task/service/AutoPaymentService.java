package privatbank.task.service;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import privatbank.task.dto.DueScheduleItem;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoPaymentService {

    private static final int BATCH_SIZE = 100;

    private final FundingRequestService fundingRequestService;

    public int createDuePayments(LocalDate today) {
        int created = 0;
        long afterItemId = 0;
        List<DueScheduleItem> batch;
        do {
            batch = fundingRequestService.findDueItems(today, afterItemId, BATCH_SIZE);
            for (DueScheduleItem item : batch) {
                if (tryCreatePayment(item, today)) {
                    created++;
                }
                afterItemId = item.itemId();
            }
        } while (batch.size() == BATCH_SIZE);
        return created;
    }

    private boolean tryCreatePayment(DueScheduleItem item, LocalDate today) {
        try {
            return fundingRequestService
                    .createItemPaymentIfDue(item.fundingRequestId(), item.itemId(), today)
                    .isPresent();
        } catch (RuntimeException e) {
            log.error("Could not create the payment for schedule item {}; the next run retries it", item.itemId(), e);
            return false;
        }
    }
}
