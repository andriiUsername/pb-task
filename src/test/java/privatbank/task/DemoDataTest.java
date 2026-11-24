package privatbank.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import privatbank.task.exception.InsufficientFundsException;
import privatbank.task.model.FundingRequestStatus;
import privatbank.task.security.CurrentActor;
import privatbank.task.service.AutoPaymentService;
import privatbank.task.service.FundingRequestService;
import privatbank.task.service.PaymentService;
import privatbank.task.service.WalletService;

@SpringBootTest(properties = "spring.liquibase.contexts=demo")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DemoDataTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private FundingRequestService fundingRequestService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AutoPaymentService autoPaymentService;

    @Autowired
    private CurrentActor currentActor;

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void walletsHaveBalancesAndFundingRequestsAreApproved() {
        assertThat(walletService.getWallet(1).ownerId()).isEqualTo("alice");
        assertThat(walletService.getWallet(1).balance()).isEqualByComparingTo("10000.00");
        assertThat(walletService.getWallet(4).balance()).isEqualByComparingTo("300.00");
        assertThat(walletService.getWallet(5).currency()).isEqualTo("USD");
        for (long requestId = 1; requestId <= 3; requestId++) {
            assertThat(fundingRequestService.getFundingRequest(requestId).status())
                    .isEqualTo(FundingRequestStatus.APPROVED);
        }
    }

    @Test
    @WithMockUser("dave")
    void schedulerPaysDueDemoItemsAndUnderfundedOneCannotBeConfirmed() {
        int created = currentActor.runAsSystem(() -> autoPaymentService.createDuePayments(LocalDate.now().plusDays(1)));

        assertThat(created).isEqualTo(2);
        Long underfundedPaymentId = fundingRequestService.getFundingRequest(3).schedule().get(0).paymentId();
        assertThatThrownBy(() -> paymentService.confirmPayment(underfundedPaymentId))
                .isInstanceOf(InsufficientFundsException.class);
    }
}
