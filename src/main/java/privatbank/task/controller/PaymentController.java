package privatbank.task.controller;

import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import privatbank.task.dto.request.TransferRequest;
import privatbank.task.dto.request.WalletPaymentRequest;
import privatbank.task.dto.response.PaymentResponse;
import privatbank.task.model.Money;
import privatbank.task.service.PaymentService;
import privatbank.task.validation.IdempotencyKey;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final PaymentService paymentService;

    @PostMapping("/top-up")
    public ResponseEntity<PaymentResponse> createTopUp(
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) @IdempotencyKey String idempotencyKey,
            @Valid @RequestBody WalletPaymentRequest request) {
        Money amount = Money.of(request.amount(), request.currency());
        return created(paymentService.createTopUp(request.walletId(), amount, idempotencyKey));
    }

    @PostMapping("/withdrawal")
    public ResponseEntity<PaymentResponse> createWithdrawal(
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) @IdempotencyKey String idempotencyKey,
            @Valid @RequestBody WalletPaymentRequest request) {
        Money amount = Money.of(request.amount(), request.currency());
        return created(paymentService.createWithdrawal(request.walletId(), amount, idempotencyKey));
    }

    @PostMapping("/transfer")
    public ResponseEntity<PaymentResponse> createTransfer(
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) @IdempotencyKey String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        Money amount = Money.of(request.amount(), request.currency());
        return created(paymentService.createTransfer(
                request.sourceWalletId(), request.targetWalletId(), amount, idempotencyKey));
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse getPayment(@PathVariable long paymentId) {
        return paymentService.getPayment(paymentId);
    }

    @PostMapping("/{paymentId}/confirm")
    public PaymentResponse confirmPayment(@PathVariable long paymentId) {
        return paymentService.confirmPayment(paymentId);
    }

    private static ResponseEntity<PaymentResponse> created(PaymentResponse payment) {
        return ResponseEntity.created(URI.create("/api/v1/payments/" + payment.id())).body(payment);
    }
}
