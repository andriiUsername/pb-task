package privatbank.task.scheduler;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import privatbank.task.security.CurrentActor;
import privatbank.task.service.AutoPaymentService;

@Component
@RequiredArgsConstructor
@Slf4j
public class AutoPaymentScheduler {

    private final AutoPaymentService autoPaymentService;
    private final CurrentActor currentActor;
    private final Clock clock;

    @Scheduled(cron = "${funding.auto-payments.cron}", zone = "${app.time-zone}")
    public void createDuePayments() {
        int created = currentActor.runAsSystem(() -> autoPaymentService.createDuePayments(LocalDate.now(clock)));
        if (created > 0) {
            log.info("Created {} scheduled payment(s)", created);
        }
    }
}
