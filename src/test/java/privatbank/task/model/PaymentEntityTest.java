package privatbank.task.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import privatbank.task.exception.SameWalletTransferException;

class PaymentEntityTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

    @Test
    void newPaymentMovesNoMoneyUntilConfirmed() {
        PaymentEntity payment = transfer(1, 2, "100.00");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(payment.getConfirmedBy()).isNull();
        assertThat(payment.getCompletedAt()).isNull();
    }

    @Test
    void paymentTouchesTheWalletsOfItsType() {
        assertThat(PaymentEntity.topUp(2, uah("1.00"), "k1", "client", NOW).walletIds()).containsExactly(2L);
        assertThat(PaymentEntity.withdrawal(1, uah("1.00"), "k2", "client", NOW).walletIds()).containsExactly(1L);
        assertThat(transfer(1, 2, "1.00").walletIds()).containsExactly(1L, 2L);
    }

    @Test
    void transferToTheSameWalletIsRejected() {
        assertThatThrownBy(() -> transfer(1, 1, "10.00")).isInstanceOf(SameWalletTransferException.class);
    }

    @Test
    void amountMustBePositive() {
        assertThatThrownBy(() -> transfer(1, 2, "0.00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> transfer(1, 2, "-5.00")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void completionRecordsWhoConfirmedAndWhen() {
        PaymentEntity payment = transfer(1, 2, "100.00");

        payment.complete("back-office", NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getConfirmedBy()).isEqualTo("back-office");
        assertThat(payment.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void completedPaymentCannotBeCompletedAgain() {
        PaymentEntity payment = transfer(1, 2, "100.00");
        payment.complete("client", NOW);

        assertThatThrownBy(() -> payment.complete("client", NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void retryMatchesOnlyAnIdenticalPayment() {
        PaymentEntity original = transfer(1, 2, "100.00");

        assertThat(original.hasSameDetailsAs(transfer(1, 2, "100"))).isTrue();
        assertThat(original.hasSameDetailsAs(transfer(1, 2, "100.01"))).isFalse();
        assertThat(original.hasSameDetailsAs(transfer(1, 3, "100.00"))).isFalse();
        assertThat(original.hasSameDetailsAs(PaymentEntity.withdrawal(1, uah("100.00"), "key", "client", NOW)))
                .isFalse();
        assertThat(original.hasSameDetailsAs(PaymentEntity.transfer(1, 2, Money.of(new BigDecimal("100.00"), "USD"),
                "key", "client", NOW))).isFalse();
    }

    private static PaymentEntity transfer(long source, long target, String amount) {
        return PaymentEntity.transfer(source, target, uah(amount), "key", "client", NOW);
    }

    private static Money uah(String amount) {
        return Money.of(new BigDecimal(amount), "UAH");
    }
}
