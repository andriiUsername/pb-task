package privatbank.task.exception;

import java.math.BigDecimal;
import java.util.Currency;

public class InsufficientFundsException extends BusinessRuleViolationException {

    public InsufficientFundsException(Long walletId, BigDecimal amount, Currency currency) {
        super("INSUFFICIENT_FUNDS", "Wallet %d does not have enough funds to debit %s %s"
                .formatted(walletId, amount.toPlainString(), currency.getCurrencyCode()));
    }
}
