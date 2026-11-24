package privatbank.task.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import privatbank.task.exception.FundingRequestFullyPaidException;
import privatbank.task.exception.FundingRequestSuspendedException;
import privatbank.task.exception.ScheduleItemAlreadyHasPaymentException;
import privatbank.task.exception.ScheduleItemNotFoundException;

@Entity
@Table(name = "funding_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FundingRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long sourceWalletId;

    @Column(nullable = false, updatable = false)
    private Long targetWalletId;

    @Column(nullable = false, updatable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FundingRequestStatus status;

    @OneToMany(mappedBy = "fundingRequest", cascade = CascadeType.PERSIST)
    @OrderBy("dueDate ASC, id ASC")
    private List<ScheduleItemEntity> schedule = new ArrayList<>();

    public static FundingRequestEntity approved(long sourceWalletId, long targetWalletId, Currency currency,
                                                List<ScheduleItemEntity> schedule) {
        if (schedule.isEmpty()) {
            throw new IllegalArgumentException("A funding request needs at least one schedule item");
        }
        FundingRequestEntity request = new FundingRequestEntity();
        request.sourceWalletId = sourceWalletId;
        request.targetWalletId = targetWalletId;
        request.currency = Objects.requireNonNull(currency, "currency");
        request.status = FundingRequestStatus.APPROVED;
        schedule.forEach(item -> item.attachTo(request));
        request.schedule.addAll(schedule);
        return request;
    }

    public ScheduleItemEntity item(long itemId) {
        return schedule.stream()
                .filter(item -> Objects.equals(item.getId(), itemId))
                .findFirst()
                .orElseThrow(() -> new ScheduleItemNotFoundException(id, itemId));
    }

    public void ensurePaymentCanBeCreated(ScheduleItemEntity item) {
        ensureNotSuspended();
        if (item.hasPayment()) {
            throw new ScheduleItemAlreadyHasPaymentException(item.getId(), item.getPaymentId());
        }
    }

    public boolean isAutomaticPaymentDue(ScheduleItemEntity item, LocalDate today) {
        return FundingRequestStatus.ACTIVE.contains(status) && !item.hasPayment() && item.isDue(today);
    }

    public void attachPayment(ScheduleItemEntity item, long paymentId) {
        ensurePaymentCanBeCreated(item);
        item.assignPayment(paymentId);
    }

    public void ensureNotSuspended() {
        if (status == FundingRequestStatus.SUSPENDED) {
            throw new FundingRequestSuspendedException(id);
        }
    }

    public void registerPaidPayment(long paymentId, Instant paidAt) {
        ensureNotSuspended();
        ScheduleItemEntity item = schedule.stream()
                .filter(candidate -> Objects.equals(candidate.getPaymentId(), paymentId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment %d does not belong to funding request %d".formatted(paymentId, id)));
        item.markPaid(paidAt);
        status = schedule.stream().allMatch(ScheduleItemEntity::isPaid)
                ? FundingRequestStatus.FULLY_PAID
                : FundingRequestStatus.PARTIALLY_PAID;
    }

    public void suspend() {
        if (status == FundingRequestStatus.FULLY_PAID) {
            throw new FundingRequestFullyPaidException(id);
        }
        status = FundingRequestStatus.SUSPENDED;
    }

    public void resume() {
        if (status != FundingRequestStatus.SUSPENDED) {
            return;
        }
        status = schedule.stream().anyMatch(ScheduleItemEntity::isPaid)
                ? FundingRequestStatus.PARTIALLY_PAID
                : FundingRequestStatus.APPROVED;
    }

    public List<ScheduleItemEntity> getSchedule() {
        return Collections.unmodifiableList(schedule);
    }
}
