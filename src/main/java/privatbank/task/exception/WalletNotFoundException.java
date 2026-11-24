package privatbank.task.exception;

public class WalletNotFoundException extends NotFoundException {

    public WalletNotFoundException(long walletId) {
        super("WALLET_NOT_FOUND", "Wallet %d not found".formatted(walletId));
    }
}
