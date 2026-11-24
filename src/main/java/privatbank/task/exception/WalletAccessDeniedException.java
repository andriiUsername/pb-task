package privatbank.task.exception;

import java.util.Collection;

public class WalletAccessDeniedException extends ForbiddenException {

    public WalletAccessDeniedException(long walletId) {
        super("WALLET_ACCESS_DENIED", "Wallet %d belongs to another user".formatted(walletId));
    }

    public WalletAccessDeniedException(Collection<Long> walletIds) {
        super("WALLET_ACCESS_DENIED", "None of the wallets %s belongs to the current user".formatted(walletIds));
    }
}
