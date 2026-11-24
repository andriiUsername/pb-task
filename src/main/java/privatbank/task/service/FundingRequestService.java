package privatbank.task.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import privatbank.task.dto.DueScheduleItem;
import privatbank.task.dto.response.FundingRequestResponse;
import privatbank.task.dto.response.PaymentResponse;
import privatbank.task.exception.FundingRequestNotFoundException;
import privatbank.task.mapper.FundingRequestMapper;
import privatbank.task.model.FundingRequestEntity;
import privatbank.task.model.FundingRequestStatus;
import privatbank.task.model.ScheduleItemEntity;
import privatbank.task.repository.FundingRequestRepository;
import privatbank.task.security.CurrentActor;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundingRequestService {

    private static final String SCHEDULE_ITEM_IDEMPOTENCY_KEY_PREFIX = "funding-schedule-item-";

    private final FundingRequestRepository fundingRequestRepository;
    private final PaymentService paymentService;
    private final WalletAccessPolicy walletAccessPolicy;
    private final FundingRequestMapper fundingRequestMapper;
    private final CurrentActor currentActor;

    @Transactional(readOnly = true)
    public FundingRequestResponse getFundingRequest(long requestId) {
        FundingRequestEntity request = fundingRequestRepository.findById(requestId)
                .orElseThrow(() -> new FundingRequestNotFoundException(requestId));
        walletAccessPolicy.requireOwnerOrOperator(List.of(request.getSourceWalletId(), request.getTargetWalletId()));
        return fundingRequestMapper.toResponse(request);
    }

    @Transactional
    public FundingRequestResponse suspend(long requestId) {
        FundingRequestEntity request = lockFundingRequest(requestId);
        request.suspend();
        log.info("Funding request {} suspended by {}", requestId, currentActor.name());
        return fundingRequestMapper.toResponse(request);
    }

    @Transactional
    public FundingRequestResponse resume(long requestId) {
        FundingRequestEntity request = lockFundingRequest(requestId);
        request.resume();
        log.info("Funding request {} resumed by {}", requestId, currentActor.name());
        return fundingRequestMapper.toResponse(request);
    }

    @Transactional
    public PaymentResponse createItemPayment(long requestId, long itemId) {
        FundingRequestEntity request = lockFundingRequest(requestId);
        walletAccessPolicy.requireOwner(request.getSourceWalletId());
        ScheduleItemEntity item = request.item(itemId);
        request.ensurePaymentCanBeCreated(item);
        return createPayment(request, item);
    }

    @Transactional
    public Optional<PaymentResponse> createItemPaymentIfDue(long requestId, long itemId, LocalDate today) {
        FundingRequestEntity request = lockFundingRequest(requestId);
        ScheduleItemEntity item = request.item(itemId);
        if (!request.isAutomaticPaymentDue(item, today)) {
            return Optional.empty();
        }
        return Optional.of(createPayment(request, item));
    }

    @Transactional(readOnly = true)
    public List<DueScheduleItem> findDueItems(LocalDate today, long afterItemId, int limit) {
        return fundingRequestRepository.findDueItemsWithoutPayment(
                today, FundingRequestStatus.ACTIVE, afterItemId, PageRequest.ofSize(limit));
    }

    private PaymentResponse createPayment(FundingRequestEntity request, ScheduleItemEntity item) {
        PaymentResponse payment = paymentService.createTransfer(request.getSourceWalletId(),
                request.getTargetWalletId(), item.getAmount(), SCHEDULE_ITEM_IDEMPOTENCY_KEY_PREFIX + item.getId());
        request.attachPayment(item, payment.id());
        log.info("Payment {} created for schedule item {} of funding request {}",
                payment.id(), item.getId(), request.getId());
        return payment;
    }

    private FundingRequestEntity lockFundingRequest(long requestId) {
        return fundingRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new FundingRequestNotFoundException(requestId));
    }
}
