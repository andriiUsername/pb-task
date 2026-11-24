package privatbank.task.exception;

public class UnsupportedCurrencyException extends BusinessRuleViolationException {

    public UnsupportedCurrencyException(String currencyCode) {
        super("UNSUPPORTED_CURRENCY",
                "Currency %s is not supported: expected an ISO 4217 code of a currency with two decimal places"
                        .formatted(currencyCode));
    }
}
