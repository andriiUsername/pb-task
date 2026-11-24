package privatbank.task.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import privatbank.task.exception.CurrencyMismatchException;
import privatbank.task.exception.InsufficientFundsException;

class WalletEntityTest {

    private final WalletEntity wallet = new WalletEntity("user-1", Currency.getInstance("UAH"), Instant.now());

    @Test
    void newWalletIsEmpty() {
        assertThat(wallet.getBalance()).isEqualTo(uah("0"));
    }

    @Test
    void depositsAndWithdrawalsChangeTheBalance() {
        wallet.deposit(uah("100.00"));
        wallet.withdraw(uah("30.50"));

        assertThat(wallet.getBalance()).isEqualTo(uah("69.50"));
    }

    @Test
    void wholeBalanceCanBeWithdrawn() {
        wallet.deposit(uah("50.00"));
        wallet.withdraw(uah("50.00"));

        assertThat(wallet.getBalance()).isEqualTo(uah("0"));
    }

    @Test
    void overdraftIsRejectedAndLeavesTheBalanceUntouched() {
        wallet.deposit(uah("100.00"));

        assertThatThrownBy(() -> wallet.withdraw(uah("100.01"))).isInstanceOf(InsufficientFundsException.class);
        assertThat(wallet.getBalance()).isEqualTo(uah("100.00"));
    }

    @Test
    void moneyInAnotherCurrencyIsRejected() {
        Money dollars = Money.of(new BigDecimal("10.00"), "USD");

        assertThatThrownBy(() -> wallet.deposit(dollars)).isInstanceOf(CurrencyMismatchException.class);
        assertThat(wallet.getBalance()).isEqualTo(uah("0"));
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        assertThatThrownBy(() -> wallet.deposit(uah("0"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wallet.withdraw(uah("-1.00"))).isInstanceOf(IllegalArgumentException.class);
    }

    private static Money uah(String amount) {
        return Money.of(new BigDecimal(amount), "UAH");
    }
}
