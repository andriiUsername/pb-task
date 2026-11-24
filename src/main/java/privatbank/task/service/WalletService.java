package privatbank.task.service;

import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import privatbank.task.dto.response.WalletResponse;
import privatbank.task.exception.CurrencyMismatchException;
import privatbank.task.exception.WalletNotFoundException;
import privatbank.task.mapper.WalletMapper;
import privatbank.task.model.Money;
import privatbank.task.model.WalletEntity;
import privatbank.task.repository.WalletRepository;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletMapper walletMapper;
    private final WalletAccessPolicy walletAccessPolicy;

    @Transactional(readOnly = true)
    public WalletResponse getWallet(long walletId) {
        walletAccessPolicy.requireOwnerOrOperator(List.of(walletId));
        return walletMapper.toResponse(findWallet(walletId));
    }

    @Transactional(readOnly = true)
    public void requireCurrency(long walletId, Currency currency) {
        WalletEntity wallet = findWallet(walletId);
        if (!wallet.holds(currency)) {
            throw new CurrencyMismatchException(walletId, wallet.getCurrency(), currency);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deposit(long walletId, Money amount) {
        lockWallet(walletId).deposit(amount);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void withdraw(long walletId, Money amount) {
        lockWallet(walletId).withdraw(amount);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void transfer(long sourceWalletId, long targetWalletId, Money amount) {
        Map<Long, WalletEntity> wallets = lockInAscendingIdOrder(sourceWalletId, targetWalletId);
        wallets.get(sourceWalletId).withdraw(amount);
        wallets.get(targetWalletId).deposit(amount);
    }

    private Map<Long, WalletEntity> lockInAscendingIdOrder(long... walletIds) {
        Map<Long, WalletEntity> lockedWallets = new LinkedHashMap<>();
        LongStream.of(walletIds).sorted().forEach(walletId -> lockedWallets.put(walletId, lockWallet(walletId)));
        return lockedWallets;
    }

    private WalletEntity findWallet(long walletId) {
        return walletRepository.findById(walletId).orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    private WalletEntity lockWallet(long walletId) {
        return walletRepository.findByIdForUpdate(walletId).orElseThrow(() -> new WalletNotFoundException(walletId));
    }
}
