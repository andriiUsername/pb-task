package privatbank.task.controller;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import privatbank.task.dto.response.FundingRequestResponse;
import privatbank.task.dto.response.PaymentResponse;
import privatbank.task.service.FundingRequestService;

@RestController
@RequestMapping("/api/v1/funding-requests")
@RequiredArgsConstructor
public class FundingRequestController {

    private final FundingRequestService fundingRequestService;

    @GetMapping("/{requestId}")
    public FundingRequestResponse getFundingRequest(@PathVariable long requestId) {
        return fundingRequestService.getFundingRequest(requestId);
    }

    @PostMapping("/{requestId}/suspend")
    public FundingRequestResponse suspend(@PathVariable long requestId) {
        return fundingRequestService.suspend(requestId);
    }

    @PostMapping("/{requestId}/resume")
    public FundingRequestResponse resume(@PathVariable long requestId) {
        return fundingRequestService.resume(requestId);
    }

    @PostMapping("/{requestId}/items/{itemId}/payment")
    public ResponseEntity<PaymentResponse> createItemPayment(@PathVariable long requestId,
                                                             @PathVariable long itemId) {
        PaymentResponse payment = fundingRequestService.createItemPayment(requestId, itemId);
        return ResponseEntity.created(URI.create("/api/v1/payments/" + payment.id())).body(payment);
    }
}
