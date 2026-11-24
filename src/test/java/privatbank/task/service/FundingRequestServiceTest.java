package privatbank.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static privatbank.task.model.ScheduleItemStatus.PAID;
import static privatbank.task.model.ScheduleItemStatus.PAYMENT_CREATED;
import static privatbank.task.model.ScheduleItemStatus.SCHEDULED;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import privatbank.task.IntegrationTest;
import privatbank.task.dto.response.PaymentResponse;
import privatbank.task.dto.response.ScheduleItemResponse;
import privatbank.task.exception.FundingRequestFullyPaidException;
import privatbank.task.exception.FundingRequestNotFoundException;
import privatbank.task.exception.FundingRequestSuspendedException;
import privatbank.task.exception.InsufficientFundsException;
import privatbank.task.exception.ScheduleItemAlreadyHasPaymentException;
import privatbank.task.exception.ScheduleItemNotFoundException;
import privatbank.task.model.FundingRequestStatus;
import privatbank.task.model.PaymentStatus;
import privatbank.task.model.PaymentType;
import privatbank.task.support.Concurrently;
import privatbank.task.support.Concurrently.Outcome;

@WithMockUser(IntegrationTest.TEST_USER)
class FundingRequestServiceTest extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2030, 1, 15);

    @Autowired
    private FundingRequestService fundingRequestService;

    @Autowired
    private AutoPaymentService autoPaymentService;

    private long source;
    private long target;

    @BeforeEach
    void openWallets() {
        source = newWallet("1000.00");
        target = newWallet("0");
    }

    @Test
    void manualPaymentIsAnOrdinaryTransferAndMayPrecedeTheDueDate() {
        FundingRequestFixture request = approvedFundingRequest(source, target, TODAY.plusDays(30));

        PaymentResponse payment = fundingRequestService.createItemPayment(request.id(), request.itemId(0));

        assertThat(payment.type()).isEqualTo(PaymentType.TRANSFER);
        assertThat(payment.status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(payment.sourceWalletId()).isEqualTo(source);
        assertThat(payment.targetWalletId()).isEqualTo(target);
        assertThat(payment.amount()).isEqualByComparingTo("100.00");
        assertThat(item(request, 0).status()).isEqualTo(PAYMENT_CREATED);
        assertThat(item(request, 0).paymentId()).isEqualTo(payment.id());
    }

    @Test
    void schedulerPaysOverdueAndTodaysItemsButNotFutureOnes() {
        FundingRequestFixture request =
                approvedFundingRequest(source, target, TODAY.minusDays(3), TODAY, TODAY.plusDays(1));

        actingAsSystem(() -> autoPaymentService.createDuePayments(TODAY));

        assertThat(item(request, 0).status()).isEqualTo(PAYMENT_CREATED);
        assertThat(item(request, 1).status()).isEqualTo(PAYMENT_CREATED);
        assertThat(item(request, 2).status()).isEqualTo(SCHEDULED);
    }

    @Test
    void itemGetsAtMostOnePaymentWhoeverCreatesIt() {
        FundingRequestFixture paidManually = approvedFundingRequest(source, target, TODAY);
        PaymentResponse manual = fundingRequestService.createItemPayment(paidManually.id(), paidManually.itemId(0));
        actingAsSystem(() -> autoPaymentService.createDuePayments(TODAY));

        assertThat(item(paidManually, 0).paymentId()).isEqualTo(manual.id());
        assertThatThrownBy(() -> fundingRequestService.createItemPayment(paidManually.id(), paidManually.itemId(0)))
                .isInstanceOf(ScheduleItemAlreadyHasPaymentException.class);

        FundingRequestFixture paidAutomatically = approvedFundingRequest(source, target, TODAY);
        actingAsSystem(() -> autoPaymentService.createDuePayments(TODAY));

        assertThat(item(paidAutomatically, 0).paymentId()).isNotNull();
        assertThatThrownBy(() -> fundingRequestService.createItemPayment(
                paidAutomatically.id(), paidAutomatically.itemId(0)))
                .isInstanceOf(ScheduleItemAlreadyHasPaymentException.class);
    }

    @Test
    void suspendedRequestGetsNoPaymentsAndItsPaymentsWaitForResumption() {
        FundingRequestFixture request = approvedFundingRequest(source, target, TODAY, TODAY.plusDays(30));
        PaymentResponse created = fundingRequestService.createItemPayment(request.id(), request.itemId(1));

        fundingRequestService.suspend(request.id());

        assertThatThrownBy(() -> fundingRequestService.createItemPayment(request.id(), request.itemId(0)))
                .isInstanceOf(FundingRequestSuspendedException.class);
        actingAsSystem(() -> autoPaymentService.createDuePayments(TODAY));
        assertThat(item(request, 0).status()).isEqualTo(SCHEDULED);
        assertThatThrownBy(() -> paymentService.confirmPayment(created.id()))
                .isInstanceOf(FundingRequestSuspendedException.class);
        assertThat(balanceOf(source)).isEqualByComparingTo("1000.00");

        fundingRequestService.resume(request.id());
        paymentService.confirmPayment(created.id());

        assertThat(balanceOf(source)).isEqualByComparingTo("900.00");
        assertThat(item(request, 1).status()).isEqualTo(PAID);
    }

    @Test
    void requestStatusFollowsPaidItems() {
        FundingRequestFixture request = approvedFundingRequest(source, target, TODAY, TODAY.plusDays(30));
        PaymentResponse first = fundingRequestService.createItemPayment(request.id(), request.itemId(0));
        PaymentResponse second = fundingRequestService.createItemPayment(request.id(), request.itemId(1));
        assertThat(status(request)).isEqualTo(FundingRequestStatus.APPROVED);

        paymentService.confirmPayment(first.id());
        assertThat(status(request)).isEqualTo(FundingRequestStatus.PARTIALLY_PAID);
        assertThat(item(request, 0).status()).isEqualTo(PAID);
        assertThat(item(request, 0).paidAt()).isNotNull();

        paymentService.confirmPayment(second.id());
        assertThat(status(request)).isEqualTo(FundingRequestStatus.FULLY_PAID);
        assertThat(balanceOf(target)).isEqualByComparingTo("200.00");
        assertThatThrownBy(() -> fundingRequestService.suspend(request.id()))
                .isInstanceOf(FundingRequestFullyPaidException.class);
        assertReconciled(source);
        assertReconciled(target);
    }

    @Test
    void failedConfirmationLeavesTheItemUnpaid() {
        long poorSource = newWallet("50.00");
        FundingRequestFixture request = approvedFundingRequest(poorSource, target, TODAY);
        PaymentResponse payment = fundingRequestService.createItemPayment(request.id(), request.itemId(0));

        assertThatThrownBy(() -> paymentService.confirmPayment(payment.id()))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(item(request, 0).status()).isEqualTo(PAYMENT_CREATED);
        assertThat(status(request)).isEqualTo(FundingRequestStatus.APPROVED);
    }

    @Test
    void manualAndAutomaticCreationRacingForOneItemProduceOnePayment() throws Exception {
        FundingRequestFixture request = approvedFundingRequest(source, target, TODAY);
        List<Callable<Object>> racers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            racers.add(() -> fundingRequestService.createItemPayment(request.id(), request.itemId(0)));
            racers.add(() -> actingAsSystem(() -> autoPaymentService.createDuePayments(TODAY)));
        }

        List<Outcome<Object>> outcomes = Concurrently.run(racers);

        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .allSatisfy(outcome -> assertThat(outcome.error())
                        .isInstanceOf(ScheduleItemAlreadyHasPaymentException.class));
        assertThat(item(request, 0).paymentId()).isNotNull();
        assertThat(jdbc.sql("SELECT count(*) FROM payment WHERE idempotency_key = ?")
                .param("funding-schedule-item-" + request.itemId(0))
                .query(Long.class)
                .single()).isEqualTo(1L);
    }

    @Test
    void unknownRequestsAndItemsAreReported() {
        FundingRequestFixture request = approvedFundingRequest(source, target, TODAY);

        assertThatThrownBy(() -> fundingRequestService.getFundingRequest(Long.MAX_VALUE))
                .isInstanceOf(FundingRequestNotFoundException.class);
        assertThatThrownBy(() -> fundingRequestService.createItemPayment(request.id(), Long.MAX_VALUE))
                .isInstanceOf(ScheduleItemNotFoundException.class);
    }

    private FundingRequestStatus status(FundingRequestFixture request) {
        return fundingRequestService.getFundingRequest(request.id()).status();
    }

    private ScheduleItemResponse item(FundingRequestFixture request, int index) {
        long itemId = request.itemId(index);
        return fundingRequestService.getFundingRequest(request.id()).schedule().stream()
                .filter(item -> item.id() == itemId)
                .findFirst()
                .orElseThrow();
    }
}
