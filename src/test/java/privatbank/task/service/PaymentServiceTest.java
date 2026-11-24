package privatbank.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import privatbank.task.IntegrationTest;
import privatbank.task.dto.response.PaymentResponse;
import privatbank.task.exception.CurrencyMismatchException;
import privatbank.task.exception.IdempotencyKeyReusedException;
import privatbank.task.exception.InsufficientFundsException;
import privatbank.task.exception.PaymentNotFoundException;
import privatbank.task.exception.WalletNotFoundException;
import privatbank.task.model.Money;
import privatbank.task.model.PaymentStatus;
import privatbank.task.security.CurrentActor;

@WithMockUser(IntegrationTest.TEST_USER)
class PaymentServiceTest extends IntegrationTest {

    @Test
    void createdPaymentMovesNoMoneyUntilConfirmed() {
        long source = newWallet("100.00");
        long target = newWallet("0");

        PaymentResponse transfer = paymentService.createTransfer(source, target, uah("40.00"), newKey());

        assertThat(transfer.status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(balanceOf(source)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(target)).isEqualByComparingTo("0.00");

        PaymentResponse confirmed = paymentService.confirmPayment(transfer.id());

        assertThat(confirmed.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(confirmed.completedAt()).isNotNull();
        assertThat(balanceOf(source)).isEqualByComparingTo("60.00");
        assertThat(balanceOf(target)).isEqualByComparingTo("40.00");
        assertReconciled(source);
        assertReconciled(target);
    }

    @Test
    void topUpAndWithdrawalChangeOneWallet() {
        long wallet = newWallet("0");
        long topUpId = paymentService.createTopUp(wallet, uah("250.00"), newKey()).id();

        actingAsOperator(() -> paymentService.confirmPayment(topUpId));
        paymentService.confirmPayment(paymentService.createWithdrawal(wallet, uah("100.50"), newKey()).id());

        assertThat(balanceOf(wallet)).isEqualByComparingTo("149.50");
        assertReconciled(wallet);
    }

    @Test
    void withdrawalBeyondTheBalanceIsRejectedAndCanBeConfirmedAfterATopUp() {
        long wallet = newWallet("50.00");
        PaymentResponse withdrawal = paymentService.createWithdrawal(wallet, uah("80.00"), newKey());

        assertThatThrownBy(() -> paymentService.confirmPayment(withdrawal.id()))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(paymentService.getPayment(withdrawal.id()).status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(balanceOf(wallet)).isEqualByComparingTo("50.00");

        long topUpId = paymentService.createTopUp(wallet, uah("30.00"), newKey()).id();
        actingAsOperator(() -> paymentService.confirmPayment(topUpId));
        paymentService.confirmPayment(withdrawal.id());

        assertThat(balanceOf(wallet)).isEqualByComparingTo("0.00");
        assertReconciled(wallet);
    }

    @Test
    void repeatedConfirmationMovesMoneyOnce() {
        long wallet = newWallet("100.00");
        PaymentResponse withdrawal = paymentService.createWithdrawal(wallet, uah("30.00"), newKey());

        PaymentResponse first = paymentService.confirmPayment(withdrawal.id());
        PaymentResponse repeated = paymentService.confirmPayment(withdrawal.id());

        assertThat(repeated).isEqualTo(first);
        assertThat(balanceOf(wallet)).isEqualByComparingTo("70.00");
    }

    @Test
    void retriedCreationReturnsThePaymentOfTheFirstAttempt() {
        long wallet = newWallet("0");
        String key = newKey();

        PaymentResponse first = paymentService.createTopUp(wallet, uah("10.00"), key);
        PaymentResponse retry = paymentService.createTopUp(wallet, uah("10"), key);

        assertThat(retry).isEqualTo(first);
    }

    @Test
    void idempotencyKeyCannotBeReusedForAnotherPayment() {
        long wallet = newWallet("0");
        String key = newKey();
        paymentService.createTopUp(wallet, uah("10.00"), key);

        assertThatThrownBy(() -> paymentService.createTopUp(wallet, uah("11.00"), key))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThatThrownBy(() -> paymentService.createWithdrawal(wallet, uah("10.00"), key))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void currenciesAreNeverMixed() {
        long hryvniaWallet = newWallet("100.00");
        long dollarWallet = newWallet("100.00", "USD");
        Money dollars = Money.of(new BigDecimal("10.00"), "USD");

        assertThatThrownBy(() -> paymentService.createTransfer(hryvniaWallet, dollarWallet, uah("10.00"), newKey()))
                .isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> paymentService.createTopUp(hryvniaWallet, dollars, newKey()))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void unknownWalletsAndPaymentsAreReported() {
        assertThatThrownBy(() -> paymentService.createTopUp(Long.MAX_VALUE, uah("1.00"), newKey()))
                .isInstanceOf(WalletNotFoundException.class);
        assertThatThrownBy(() -> paymentService.confirmPayment(Long.MAX_VALUE))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void paymentsCreatedByTheSchedulerAreRecordedAsSystem() {
        long source = newWallet("10.00");
        long target = newWallet("0");

        PaymentResponse payment = actingAsSystem(
                () -> paymentService.createTransfer(source, target, uah("5.00"), newKey()));

        assertThat(payment.createdBy()).isEqualTo(CurrentActor.SYSTEM);
    }

    @Test
    void nothingHappensWithoutAnActor() {
        long wallet = newWallet("10.00");
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> paymentService.createWithdrawal(wallet, uah("1.00"), newKey()))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
