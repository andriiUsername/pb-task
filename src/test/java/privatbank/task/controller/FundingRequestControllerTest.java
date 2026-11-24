package privatbank.task.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import privatbank.task.IntegrationTest;

class FundingRequestControllerTest extends IntegrationTest {

    private static final LocalDate DUE_DATE = LocalDate.of(2031, 1, 1);
    private static final String ITEM_PAYMENT_URL = "/api/v1/funding-requests/{id}/items/{itemId}/payment";

    @Test
    void manualItemPaymentIsCreatedOnlyOnce() throws Exception {
        FundingRequestFixture request = approvedFundingRequest(
                newWalletOwnedBy("alice", "500.00"), newWalletOwnedBy("bob", "0"), DUE_DATE);

        mvc.perform(post(ITEM_PAYMENT_URL, request.id(), request.itemId(0)).with(asUser("alice")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.amount").value(100.0));
        mvc.perform(post(ITEM_PAYMENT_URL, request.id(), request.itemId(0)).with(asUser("alice")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_ITEM_ALREADY_HAS_PAYMENT"));
        mvc.perform(get("/api/v1/funding-requests/{id}", request.id()).with(asUser("bob")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule[0].status").value("PAYMENT_CREATED"));
    }

    @Test
    void onlyOperatorsSuspendFundingRequests() throws Exception {
        FundingRequestFixture request = approvedFundingRequest(
                newWalletOwnedBy("alice", "500.00"), newWalletOwnedBy("bob", "0"), DUE_DATE);

        mvc.perform(post("/api/v1/funding-requests/{id}/suspend", request.id()).with(asUser("alice")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/v1/funding-requests/{id}/suspend", request.id()).with(asOperator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mvc.perform(post(ITEM_PAYMENT_URL, request.id(), request.itemId(0)).with(asUser("alice")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FUNDING_REQUEST_SUSPENDED"));
    }
}
