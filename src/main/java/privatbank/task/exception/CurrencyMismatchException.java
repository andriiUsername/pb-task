package privatbank.task.exception;

import java.util.Currency;

public class CurrencyMismatchException extends BusinessRuleViolationException {

    public CurrencyMismatchException(Long walletId, Currency walletCurrency, Currency requestedCurrency) {
        super("CURRENCY_MISMATCH", "Wallet %d holds %s, not %s".formatted(walletId, walletCurrency, requestedCurrency));
    }
}
