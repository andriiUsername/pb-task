package privatbank.task.exception;

public class SameWalletTransferException extends BusinessRuleViolationException {

    public SameWalletTransferException(long walletId) {
        super("SAME_WALLET_TRANSFER", "Cannot transfer from wallet %d to itself".formatted(walletId));
    }
}
