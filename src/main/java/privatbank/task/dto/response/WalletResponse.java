package privatbank.task.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletResponse(long id, String ownerId, BigDecimal balance, String currency, Instant createdAt) {
}
