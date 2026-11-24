package privatbank.task.service;

import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import privatbank.task.dto.response.PaymentResponse;
import privatbank.task.exception.IdempotencyKeyReusedException;
import privatbank.task.exception.PaymentNotFoundException;
import privatbank.task.exception.PaymentRequestInProgressException;
import privatbank.task.exception.TopUpConfirmationDeniedException;
import privatbank.task.mapper.PaymentMapper;
import privatbank.task.model.FundingRequestEntity;
import privatbank.task.model.Money;
import privatbank.task.model.PaymentEntity;
import privatbank.task.model.PaymentType;
import privatbank.task.repository.FundingRequestRepository;
import privatbank.task.repository.PaymentRepository;
import privatbank.task.security.CurrentActor;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final String IDEMPOTENCY_KEY_CONSTRAINT = "uk_payment_created_by_idempotency_key";

    private final PaymentRepository paymentRepository;
    private final FundingRequestRepository fundingRequestRepository;
    private final WalletService walletService;
    private final WalletAccessPolicy walletAccessPolicy;
    private final PaymentMapper paymentMapper;
    private final CurrentActor currentActor;
    private final Clock clock;

    @Transactional
    public PaymentResponse createTopUp(long targetWalletId, Money amount, String idempotencyKey) {
        return create(PaymentEntity.topUp(
                targetWalletId, amount, idempotencyKey, currentActor.name(), clock.instant()));
    }

    @Transactional
    public PaymentResponse createWithdrawal(long sourceWalletId, Money amount, String idempotencyKey) {
        return create(PaymentEntity.withdrawal(
                sourceWalletId, amount, idempotencyKey, currentActor.name(), clock.instant()));
    }

    @Transactional
    public PaymentResponse createTransfer(long sourceWalletId, long targetWalletId, Money amount,
                                          String idempotencyKey) {
        return create(PaymentEntity.transfer(sourceWalletId, targetWalletId, amount, idempotencyKey,
                currentActor.name(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(long paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        walletAccessPolicy.requireOwnerOrOperator(payment.walletIds());
        return paymentMapper.toResponse(payment);
    }

    @Transactional
    public PaymentResponse confirmPayment(long paymentId) {
        PaymentEntity payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        requireConfirmationRight(payment);
        if (payment.isCompleted()) {
            return paymentMapper.toResponse(payment);
        }
        Optional<FundingRequestEntity> fundingRequest = lockFundingRequestOf(payment);
        fundingRequest.ifPresent(FundingRequestEntity::ensureNotSuspended);
        moveFunds(payment);
        payment.complete(currentActor.name(), clock.instant());
        fundingRequest.ifPresent(request -> request.registerPaidPayment(paymentId, payment.getCompletedAt()));
        log.info("Payment {} ({} of {}) confirmed by {}",
                paymentId, payment.getType(), payment.getAmount(), payment.getConfirmedBy());
        return paymentMapper.toResponse(payment);
    }

    private Optional<FundingRequestEntity> lockFundingRequestOf(PaymentEntity payment) {
        return fundingRequestRepository.findIdByPaymentId(payment.getId())
                .flatMap(fundingRequestRepository::findByIdForUpdate);
    }

    private void requireConfirmationRight(PaymentEntity payment) {
        if (payment.getType() != PaymentType.TOP_UP) {
            walletAccessPolicy.requireOwner(payment.initiatorWalletId());
        } else if (!currentActor.isOperator()) {
            throw new TopUpConfirmationDeniedException(payment.getId());
        }
    }

    private void moveFunds(PaymentEntity payment) {
        Money amount = payment.getAmount();
        switch (payment.getType()) {
            case TOP_UP -> walletService.deposit(payment.getTargetWalletId(), amount);
            case WITHDRAWAL -> walletService.withdraw(payment.getSourceWalletId(), amount);
            case TRANSFER -> walletService.transfer(payment.getSourceWalletId(), payment.getTargetWalletId(), amount);
        }
    }

    private PaymentResponse create(PaymentEntity requested) {
        walletAccessPolicy.requireOwnerOrSystem(requested.initiatorWalletId());
        Optional<PaymentEntity> previous = paymentRepository.findByCreatedByAndIdempotencyKey(
                requested.getCreatedBy(), requested.getIdempotencyKey());
        if (previous.isPresent()) {
            return replay(previous.get(), requested);
        }
        requested.walletIds()
                .forEach(walletId -> walletService.requireCurrency(walletId, requested.getAmount().currency()));
        PaymentEntity created = insert(requested);
        log.info("Payment {} ({} of {}) created by {}",
                created.getId(), created.getType(), created.getAmount(), created.getCreatedBy());
        return paymentMapper.toResponse(created);
    }

    private PaymentResponse replay(PaymentEntity previous, PaymentEntity requested) {
        if (!previous.hasSameDetailsAs(requested)) {
            throw new IdempotencyKeyReusedException(requested.getIdempotencyKey());
        }
        return paymentMapper.toResponse(previous);
    }

    private PaymentEntity insert(PaymentEntity payment) {
        try {
            return paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            if (violatesIdempotencyKey(e)) {
                throw new PaymentRequestInProgressException(payment.getIdempotencyKey());
            }
            throw e;
        }
    }

    private static boolean violatesIdempotencyKey(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return IDEMPOTENCY_KEY_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }
}
