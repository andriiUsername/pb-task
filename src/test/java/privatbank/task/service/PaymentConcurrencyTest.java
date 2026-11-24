package privatbank.task.service;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import privatbank.task.IntegrationTest;
import privatbank.task.dto.response.PaymentResponse;
import privatbank.task.exception.InsufficientFundsException;
import privatbank.task.exception.PaymentRequestInProgressException;
import privatbank.task.support.Concurrently;
import privatbank.task.support.Concurrently.Outcome;

@WithMockUser(IntegrationTest.TEST_USER)
class PaymentConcurrencyTest extends IntegrationTest {

    @Test
    void concurrentConfirmationsOfOnePaymentMoveMoneyOnce() throws Exception {
        long wallet = newWallet("100.00");
        PaymentResponse withdrawal = paymentService.createWithdrawal(wallet, uah("30.00"), newKey());

        List<Outcome<PaymentResponse>> outcomes =
                Concurrently.run(10, () -> paymentService.confirmPayment(withdrawal.id()));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(balanceOf(wallet)).isEqualByComparingTo("70.00");
        assertReconciled(wallet);
    }

    @Test
    void concurrentWithdrawalsNeverOverdrawTheWallet() throws Exception {
        long wallet = newWallet("100.00");
        List<Callable<PaymentResponse>> confirmations = IntStream.range(0, 20)
                .mapToObj(i -> paymentService.createWithdrawal(wallet, uah("10.00"), newKey()))
                .<Callable<PaymentResponse>>map(withdrawal -> () -> paymentService.confirmPayment(withdrawal.id()))
                .toList();

        List<Outcome<PaymentResponse>> outcomes = Concurrently.run(confirmations);

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(10);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .allSatisfy(outcome -> assertThat(outcome.error()).isInstanceOf(InsufficientFundsException.class));
        assertThat(balanceOf(wallet)).isEqualByComparingTo("0.00");
        assertReconciled(wallet);
    }

    @Test
    void oppositeTransfersDoNotDeadlockAndKeepTheTotal() throws Exception {
        long first = newWallet("1000.00");
        long second = newWallet("1000.00");
        List<Callable<PaymentResponse>> confirmations = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            PaymentResponse forward = paymentService.createTransfer(first, second, uah("5.00"), newKey());
            PaymentResponse backward = paymentService.createTransfer(second, first, uah("3.00"), newKey());
            confirmations.add(() -> paymentService.confirmPayment(forward.id()));
            confirmations.add(() -> paymentService.confirmPayment(backward.id()));
        }

        List<Outcome<PaymentResponse>> outcomes = Concurrently.run(confirmations);

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(balanceOf(first)).isEqualByComparingTo("960.00");
        assertThat(balanceOf(second)).isEqualByComparingTo("1040.00");
        assertReconciled(first);
        assertReconciled(second);
    }

    @Test
    void concurrentRequestsWithOneIdempotencyKeyCreateOnePayment() throws Exception {
        long wallet = newWallet("0");
        String key = newKey();

        List<Outcome<PaymentResponse>> outcomes =
                Concurrently.run(10, () -> paymentService.createTopUp(wallet, uah("10.00"), key));

        Set<Long> paymentIds = outcomes.stream()
                .filter(Outcome::succeeded)
                .map(outcome -> outcome.value().id())
                .collect(toSet());
        assertThat(paymentIds).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .allSatisfy(outcome -> assertThat(outcome.error())
                        .isInstanceOf(PaymentRequestInProgressException.class));
        assertThat(jdbc.sql("SELECT count(*) FROM payment WHERE idempotency_key = ?")
                .param(key)
                .query(Long.class)
                .single()).isEqualTo(1L);
    }
}
