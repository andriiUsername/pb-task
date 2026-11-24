package privatbank.task.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "funding_schedule_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "funding_request_id", nullable = false, updatable = false)
    @Getter(AccessLevel.NONE)
    private FundingRequestEntity fundingRequest;

    @Column(nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    private Long paymentId;

    private Instant paidAt;

    public ScheduleItemEntity(LocalDate dueDate, BigDecimal amount) {
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate");
        this.amount = Objects.requireNonNull(amount, "amount");
    }

    void attachTo(FundingRequestEntity fundingRequest) {
        this.fundingRequest = fundingRequest;
    }

    boolean hasPayment() {
        return paymentId != null;
    }

    boolean isPaid() {
        return paidAt != null;
    }

    boolean isDue(LocalDate today) {
        return !dueDate.isAfter(today);
    }

    void assignPayment(long paymentId) {
        if (hasPayment()) {
            throw new IllegalStateException("Schedule item %d already has payment %d".formatted(id, this.paymentId));
        }
        this.paymentId = paymentId;
    }

    void markPaid(Instant paidAt) {
        this.paidAt = Objects.requireNonNull(paidAt, "paidAt");
    }

    public Money getAmount() {
        return new Money(amount, fundingRequest.getCurrency());
    }

    public ScheduleItemStatus getStatus() {
        if (isPaid()) {
            return ScheduleItemStatus.PAID;
        }
        return hasPayment() ? ScheduleItemStatus.PAYMENT_CREATED : ScheduleItemStatus.SCHEDULED;
    }
}
