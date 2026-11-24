package privatbank.task.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import privatbank.task.exception.UnsupportedCurrencyException;

class MoneyTest {

    @Test
    void amountIsKeptWithTwoDecimalPlaces() {
        assertThat(Money.of(new BigDecimal("100"), "UAH").amount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(Money.of(new BigDecimal("1.500"), "UAH").amount()).isEqualTo(new BigDecimal("1.50"));
    }

    @Test
    void fractionsOfAKopiykaAreRejectedInsteadOfRounded() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("0.001"), "UAH"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sameAmountWrittenDifferentlyIsEqual() {
        assertThat(Money.of(new BigDecimal("100"), "UAH")).isEqualTo(Money.of(new BigDecimal("100.00"), "UAH"));
        assertThat(Money.of(new BigDecimal("100"), "UAH")).isNotEqualTo(Money.of(new BigDecimal("100"), "USD"));
    }

    @Test
    void onlyIsoCurrenciesWithTwoDecimalPlacesAreSupported() {
        assertThat(Money.of(BigDecimal.ONE, "UAH").currency()).isEqualTo(Currency.getInstance("UAH"));
        assertThatThrownBy(() -> Money.of(BigDecimal.ONE, "XYZ")).isInstanceOf(UnsupportedCurrencyException.class);
        assertThatThrownBy(() -> Money.of(BigDecimal.ONE, "JPY")).isInstanceOf(UnsupportedCurrencyException.class);
    }
}
