package privatbank.task.model;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;
import privatbank.task.exception.UnsupportedCurrencyException;

public record Money(BigDecimal amount, Currency currency) {

    public static final int SCALE = 2;

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (currency.getDefaultFractionDigits() != SCALE) {
            throw new UnsupportedCurrencyException(currency.getCurrencyCode());
        }
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw new IllegalArgumentException("Amount has more than " + SCALE + " decimal places: " + amount);
        }
        amount = amount.setScale(SCALE);
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, currencyOf(currencyCode));
    }

    private static Currency currencyOf(String currencyCode) {
        try {
            return Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedCurrencyException(currencyCode);
        }
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
