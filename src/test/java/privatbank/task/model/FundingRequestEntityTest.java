package privatbank.task.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static privatbank.task.model.FundingRequestStatus.APPROVED;
import static privatbank.task.model.FundingRequestStatus.FULLY_PAID;
import static privatbank.task.model.FundingRequestStatus.PARTIALLY_PAID;
import static privatbank.task.model.FundingRequestStatus.SUSPENDED;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;
import privatbank.task.exception.FundingRequestFullyPaidException;
import privatbank.task.exception.FundingRequestSuspendedException;
import privatbank.task.exception.ScheduleItemAlreadyHasPaymentException;

class FundingRequestEntityTest {

    private static final LocalDate FIRST_DUE = LocalDate.of(2026, 12, 1);
    private static final LocalDate SECOND_DUE = LocalDate.of(2027, 1, 1);
    private static final Instant NOW = Instant.parse("2026-12-01T09:00:00Z");

    private final ScheduleItemEntity first = new ScheduleItemEntity(FIRST_DUE, new BigDecimal("100.00"));
    private final ScheduleItemEntity second = new ScheduleItemEntity(SECOND_DUE, new BigDecimal("200.00"));
    private final FundingRequestEntity request =
            FundingRequestEntity.approved(1, 2, Currency.getInstance("UAH"), List.of(first, second));

    @Test
    void createdPaymentsDoNotChangeTheStatus() {
        request.attachPayment(first, 10);
        request.attachPayment(second, 11);

        assertThat(request.getStatus()).isEqualTo(APPROVED);
    }

    @Test
    void statusFollowsPaidItems() {
        request.attachPayment(first, 10);
        request.attachPayment(second, 11);

        request.registerPaidPayment(10, NOW);
        assertThat(request.getStatus()).isEqualTo(PARTIALLY_PAID);
        assertThat(first.getStatus()).isEqualTo(ScheduleItemStatus.PAID);
        assertThat(second.getStatus()).isEqualTo(ScheduleItemStatus.PAYMENT_CREATED);

        request.registerPaidPayment(11, NOW);
        assertThat(request.getStatus()).isEqualTo(FULLY_PAID);
    }

    @Test
    void itemGetsAtMostOnePayment() {
        request.attachPayment(first, 10);

        assertThatThrownBy(() -> request.attachPayment(first, 11))
                .isInstanceOf(ScheduleItemAlreadyHasPaymentException.class);
        assertThat(first.getPaymentId()).isEqualTo(10L);
    }

    @Test
    void manualPaymentIsAllowedBeforeTheDueDate() {
        request.attachPayment(second, 10);

        assertThat(second.getPaymentId()).isEqualTo(10L);
    }

    @Test
    void automaticPaymentIsDueFromTheItemDateUntilTheItemHasAPayment() {
        assertThat(request.isAutomaticPaymentDue(first, FIRST_DUE.minusDays(1))).isFalse();
        assertThat(request.isAutomaticPaymentDue(first, FIRST_DUE)).isTrue();
        assertThat(request.isAutomaticPaymentDue(first, FIRST_DUE.plusDays(3))).isTrue();

        request.attachPayment(first, 10);

        assertThat(request.isAutomaticPaymentDue(first, FIRST_DUE)).isFalse();
    }

    @Test
    void suspendedRequestAcceptsNeitherNewPaymentsNorConfirmations() {
        request.attachPayment(first, 10);

        request.suspend();

        assertThat(request.getStatus()).isEqualTo(SUSPENDED);
        assertThatThrownBy(() -> request.ensurePaymentCanBeCreated(second))
                .isInstanceOf(FundingRequestSuspendedException.class);
        assertThat(request.isAutomaticPaymentDue(second, SECOND_DUE)).isFalse();
        assertThatThrownBy(() -> request.registerPaidPayment(10, NOW))
                .isInstanceOf(FundingRequestSuspendedException.class);
    }

    @Test
    void resumeRestoresTheStatusMatchingThePaidItems() {
        request.suspend();
        request.resume();
        assertThat(request.getStatus()).isEqualTo(APPROVED);

        request.attachPayment(first, 10);
        request.registerPaidPayment(10, NOW);
        request.suspend();
        request.resume();
        assertThat(request.getStatus()).isEqualTo(PARTIALLY_PAID);
    }

    @Test
    void fullyPaidRequestCannotBeSuspended() {
        request.attachPayment(first, 10);
        request.attachPayment(second, 11);
        request.registerPaidPayment(10, NOW);
        request.registerPaidPayment(11, NOW);

        assertThatThrownBy(request::suspend).isInstanceOf(FundingRequestFullyPaidException.class);
        assertThat(request.getStatus()).isEqualTo(FULLY_PAID);
    }
}
