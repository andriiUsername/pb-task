package privatbank.task.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import privatbank.task.dto.DueScheduleItem;
import privatbank.task.model.FundingRequestEntity;
import privatbank.task.model.FundingRequestStatus;

public interface FundingRequestRepository extends JpaRepository<FundingRequestEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FundingRequestEntity r where r.id = :id")
    Optional<FundingRequestEntity> findByIdForUpdate(long id);

    @Query("select i.fundingRequest.id from ScheduleItemEntity i where i.paymentId = :paymentId")
    Optional<Long> findIdByPaymentId(long paymentId);

    @Query("""
            select new privatbank.task.dto.DueScheduleItem(i.fundingRequest.id, i.id)
            from ScheduleItemEntity i
            where i.dueDate <= :today
              and i.paymentId is null
              and i.fundingRequest.status in :statuses
              and i.id > :afterItemId
            order by i.id
            """)
    List<DueScheduleItem> findDueItemsWithoutPayment(LocalDate today, Collection<FundingRequestStatus> statuses,
                                                     long afterItemId, Pageable page);
}
