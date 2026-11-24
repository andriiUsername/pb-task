package privatbank.task.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import privatbank.task.exception.SameWalletTransferException;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PaymentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(updatable = false)
    private Long sourceWalletId;

    @Column(updatable = false)
    private Long targetWalletId;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    @Getter(AccessLevel.NONE)
    private Currency currency;

    @Column(nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private String confirmedBy;

    private Instant completedAt;

    private PaymentEntity(PaymentType type, Long sourceWalletId, Long targetWalletId, Money amount,
                          String idempotencyKey, String createdBy, Instant createdAt) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Payment amount must be positive: " + amount);
        }
        this.type = type;
        this.sourceWalletId = sourceWalletId;
        this.targetWalletId = targetWalletId;
        this.amount = amount.amount();
        this.currency = amount.currency();
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = PaymentStatus.CREATED;
    }

    public static PaymentEntity topUp(long targetWalletId, Money amount, String idempotencyKey,
                                      String createdBy, Instant now) {
        return new PaymentEntity(PaymentType.TOP_UP, null, targetWalletId, amount, idempotencyKey, createdBy, now);
    }

    public static PaymentEntity withdrawal(long sourceWalletId, Money amount, String idempotencyKey,
                                           String createdBy, Instant now) {
        return new PaymentEntity(PaymentType.WITHDRAWAL, sourceWalletId, null, amount, idempotencyKey, createdBy, now);
    }

    public static PaymentEntity transfer(long sourceWalletId, long targetWalletId, Money amount,
                                         String idempotencyKey, String createdBy, Instant now) {
        if (sourceWalletId == targetWalletId) {
            throw new SameWalletTransferException(sourceWalletId);
        }
        return new PaymentEntity(PaymentType.TRANSFER, sourceWalletId, targetWalletId, amount,
                idempotencyKey, createdBy, now);
    }

    public void complete(String confirmedBy, Instant now) {
        if (isCompleted()) {
            throw new IllegalStateException("Payment %d is already completed".formatted(id));
        }
        this.status = PaymentStatus.COMPLETED;
        this.confirmedBy = Objects.requireNonNull(confirmedBy, "confirmedBy");
        this.completedAt = Objects.requireNonNull(now, "now");
    }

    public boolean isCompleted() {
        return status == PaymentStatus.COMPLETED;
    }

    public boolean hasSameDetailsAs(PaymentEntity other) {
        return type == other.type
                && Objects.equals(sourceWalletId, other.sourceWalletId)
                && Objects.equals(targetWalletId, other.targetWalletId)
                && getAmount().equals(other.getAmount());
    }

    public List<Long> walletIds() {
        return Stream.of(sourceWalletId, targetWalletId).filter(Objects::nonNull).toList();
    }

    public long initiatorWalletId() {
        return type == PaymentType.TOP_UP ? targetWalletId : sourceWalletId;
    }

    public Money getAmount() {
        return new Money(amount, currency);
    }
}
