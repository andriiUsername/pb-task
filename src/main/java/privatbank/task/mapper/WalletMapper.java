package privatbank.task.mapper;

import org.springframework.stereotype.Component;
import privatbank.task.dto.response.WalletResponse;
import privatbank.task.model.WalletEntity;

@Component
public class WalletMapper {

    public WalletResponse toResponse(WalletEntity wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getOwnerId(),
                wallet.getBalance().amount(),
                wallet.getCurrency().getCurrencyCode(),
                wallet.getCreatedAt());
    }
}
