package privatbank.task.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import privatbank.task.IntegrationTest;

class AccessControlTest extends IntegrationTest {

    @Test
    void userSeesOnlyOwnWallets() throws Exception {
        long bobsWallet = newWalletOwnedBy("bob", "10.00");

        mvc.perform(get("/api/v1/wallets/{id}", bobsWallet).with(asUser("bob")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/wallets/{id}", bobsWallet).with(asUser("alice")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WALLET_ACCESS_DENIED"));
        mvc.perform(get("/api/v1/wallets/{id}", bobsWallet).with(asOperator()))
                .andExpect(status().isOk());
    }

    @Test
    void userMovesMoneyOnlyFromOwnWallet() throws Exception {
        long alicesWallet = newWalletOwnedBy("alice", "100.00");
        long bobsWallet = newWalletOwnedBy("bob", "100.00");

        mvc.perform(json(post("/api/v1/payments/withdrawal"), walletPayment(bobsWallet))
                        .with(asUser("alice"))
                        .header(IDEMPOTENCY_KEY_HEADER, newKey()))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/v1/payments/transfer"), transfer(bobsWallet, alicesWallet))
                        .with(asUser("alice"))
                        .header(IDEMPOTENCY_KEY_HEADER, newKey()))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/v1/payments/transfer"), transfer(alicesWallet, bobsWallet))
                        .with(asUser("alice"))
                        .header(IDEMPOTENCY_KEY_HEADER, newKey()))
                .andExpect(status().isCreated());
    }

    @Test
    void onlyAnOperatorConfirmsATopUp() throws Exception {
        long alicesWallet = newWalletOwnedBy("alice", "0");
        String created = mvc.perform(json(post("/api/v1/payments/top-up"), walletPayment(alicesWallet))
                        .with(asUser("alice"))
                        .header(IDEMPOTENCY_KEY_HEADER, newKey()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long topUpId = JsonPath.<Number>read(created, "$.id").longValue();

        mvc.perform(post("/api/v1/payments/{id}/confirm", topUpId).with(asUser("alice")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TOP_UP_CONFIRMATION_DENIED"));
        mvc.perform(post("/api/v1/payments/{id}/confirm", topUpId).with(asOperator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void onlyTheInitiatorConfirmsAndOnlyParticipantsSeeThePayment() throws Exception {
        long alicesWallet = newWalletOwnedBy("alice", "100.00");
        long bobsWallet = newWalletOwnedBy("bob", "0");
        long paymentId = actingAs("alice", Roles.USER,
                () -> paymentService.createTransfer(alicesWallet, bobsWallet, uah("10.00"), newKey()).id());

        mvc.perform(post("/api/v1/payments/{id}/confirm", paymentId).with(asUser("bob")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payments/{id}", paymentId).with(asUser("carol")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payments/{id}", paymentId).with(asUser("bob")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/payments/{id}/confirm", paymentId).with(asUser("alice")))
                .andExpect(status().isOk());
    }

    @Test
    void operatorSeesClientPaymentsButCannotMoveClientMoney() throws Exception {
        long alicesWallet = newWalletOwnedBy("alice", "100.00");
        long withdrawalId = actingAs("alice", Roles.USER,
                () -> paymentService.createWithdrawal(alicesWallet, uah("10.00"), newKey()).id());

        mvc.perform(json(post("/api/v1/payments/withdrawal"), walletPayment(alicesWallet))
                        .with(asOperator())
                        .header(IDEMPOTENCY_KEY_HEADER, newKey()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/payments/{id}/confirm", withdrawalId).with(asOperator()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payments/{id}", withdrawalId).with(asOperator()))
                .andExpect(status().isOk());
        assertThat(balanceOf(alicesWallet)).isEqualByComparingTo("100.00");
    }

    @Test
    void onlyTheSourceWalletOwnerPaysAFundingRequestItem() throws Exception {
        FundingRequestFixture request = approvedFundingRequest(
                newWalletOwnedBy("alice", "500.00"), newWalletOwnedBy("bob", "0"), LocalDate.of(2031, 1, 1));

        mvc.perform(post("/api/v1/funding-requests/{id}/items/{itemId}/payment", request.id(), request.itemId(0))
                        .with(asUser("bob")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/funding-requests/{id}", request.id()).with(asUser("carol")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/funding-requests/{id}/items/{itemId}/payment", request.id(), request.itemId(0))
                        .with(asUser("alice")))
                .andExpect(status().isCreated());
    }

    private static String walletPayment(long walletId) {
        return """
                {"walletId": %d, "amount": 10.00, "currency": "UAH"}
                """.formatted(walletId);
    }

    private static String transfer(long sourceWalletId, long targetWalletId) {
        return """
                {"sourceWalletId": %d, "targetWalletId": %d, "amount": 10.00, "currency": "UAH"}
                """.formatted(sourceWalletId, targetWalletId);
    }
}
