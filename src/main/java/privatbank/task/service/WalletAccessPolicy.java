package privatbank.task.service;

import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import privatbank.task.exception.WalletAccessDeniedException;
import privatbank.task.exception.WalletNotFoundException;
import privatbank.task.repository.WalletRepository;
import privatbank.task.security.CurrentActor;

@Component
@RequiredArgsConstructor
public class WalletAccessPolicy {

    private final WalletRepository walletRepository;
    private final CurrentActor currentActor;

    public void requireOwner(long walletId) {
        if (!isOwnedByCurrentActor(walletId)) {
            throw new WalletAccessDeniedException(walletId);
        }
    }

    public void requireOwnerOrSystem(long walletId) {
        if (!currentActor.isSystem()) {
            requireOwner(walletId);
        }
    }

    public void requireOwnerOrOperator(Collection<Long> walletIds) {
        if (!currentActor.isOperator() && walletIds.stream().noneMatch(this::isOwnedByCurrentActor)) {
            throw new WalletAccessDeniedException(walletIds);
        }
    }

    private boolean isOwnedByCurrentActor(long walletId) {
        String actor = currentActor.name();
        return walletRepository.findOwnerIdById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId))
                .equals(actor);
    }
}
