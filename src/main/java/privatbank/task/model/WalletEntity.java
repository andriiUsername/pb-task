package privatbank.task.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import privatbank.task.exception.CurrencyMismatchException;
import privatbank.task.exception.InsufficientFundsException;

@Entity
@Table(name = "wallet")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String ownerId;

    @Column(nullable = false, updatable = false)
    private Currency currency;

    @Column(nullable = false)
    private BigDecimal balance;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public WalletEntity(String ownerId, Currency currency, Instant createdAt) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.balance = new Money(BigDecimal.ZERO, currency).amount();
    }

    public void deposit(Money amount) {
        requireApplicable(amount);
        balance = balance.add(amount.amount());
    }

    public void withdraw(Money amount) {
        requireApplicable(amount);
        if (balance.compareTo(amount.amount()) < 0) {
            throw new InsufficientFundsException(id, amount.amount(), currency);
        }
        balance = balance.subtract(amount.amount());
    }

    public boolean holds(Currency currency) {
        return this.currency.equals(currency);
    }

    private void requireApplicable(Money amount) {
        if (!holds(amount.currency())) {
            throw new CurrencyMismatchException(id, currency, amount.currency());
        }
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Amount must be positive: " + amount);
        }
    }

    public Money getBalance() {
        return new Money(balance, currency);
    }
}
