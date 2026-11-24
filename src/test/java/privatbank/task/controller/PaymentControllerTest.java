package privatbank.task.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import privatbank.task.IntegrationTest;
import privatbank.task.security.Roles;

class PaymentControllerTest extends IntegrationTest {

    @Test
    void paymentIsCreatedAndConfirmedOverHttp() throws Exception {
        long source = newWalletOwnedBy("alice", "100.00");
        long target = newWalletOwnedBy("bob", "0");
        String key = newKey();
        String transfer = """
                {"sourceWalletId": %d, "targetWalletId": %d, "amount": 25.50, "currency": "UAH"}
                """.formatted(source, target);

        String created = mvc.perform(json(post("/api/v1/payments/transfer"), transfer)
                        .with(asUser("alice"))
                        .header(IDEMPOTENCY_KEY_HEADER, key))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.createdBy").value("alice"))
                .andReturn().getResponse().getContentAsString();
        long paymentId = JsonPath.<Number>read(created, "$.id").longValue();

        mvc.perform(json(post("/api/v1/payments/transfer"), transfer)
                        .with(asUser("alice"))
                        .header(IDEMPOTENCY_KEY_HEADER, key))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(paymentId));

        mvc.perform(post("/api/v1/payments/{id}/confirm", paymentId).with(asUser("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.confirmedBy").value("alice"));

        mvc.perform(get("/api/v1/wallets/{id}", target).with(asUser("bob")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(25.5))
                .andExpect(jsonPath("$.currency").value("UAH"));
    }

    @Test
    void sameIdempotencyKeyOfDifferentUsersCreatesDifferentPayments() throws Exception {
        long alicesWallet = newWalletOwnedBy("alice", "0");
        long bobsWallet = newWalletOwnedBy("bob", "0");
        String sharedKey = newKey();

        long alicesPayment = createTopUp("alice", alicesWallet, sharedKey);
        long bobsPayment = createTopUp("bob", bobsWallet, sharedKey);

        assertThat(bobsPayment).isNotEqualTo(alicesPayment);
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        long wallet = newWalletOwnedBy("alice", "0");

        mvc.perform(json(post("/api/v1/payments/top-up"), topUp(wallet, "10.00", "UAH"))
                        .with(asUser("alice")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(json(post("/api/v1/payments/top-up"), topUp(wallet, "10.001", "UAH"))
                        .with(asUser("alice"))
                        .header(IDEMPOTENCY_KEY_HEADER, newKey()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(json(post("/api/v1/payments/top-up"), topUp(wallet, "-5.00", "UAH"))
                        .with(asUser("alice"))
                        .header(IDEMPOTENCY_KEY_HEADER, newKey()))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/payments/top-up"), topUp(wallet, "10.00", "JPY"))
                        .with(asUser("alice"))
                        .header(IDEMPOTENCY_KEY_HEADER, newKey()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));
    }

    @Test
    void businessErrorsAreProblemDetailsWithAStableCode() throws Exception {
        long wallet = newWalletOwnedBy("alice", "10.00");
        long withdrawalId = actingAs("alice", Roles.USER,
                () -> paymentService.createWithdrawal(wallet, uah("20.00"), newKey()).id());

        mvc.perform(post("/api/v1/payments/{id}/confirm", withdrawalId).with(asUser("alice")))
                .andExpect(status().is(422))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        mvc.perform(get("/api/v1/payments/{id}", Long.MAX_VALUE).with(asUser("alice")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    private long createTopUp(String username, long walletId, String idempotencyKey) throws Exception {
        String response = mvc.perform(json(post("/api/v1/payments/top-up"), topUp(walletId, "10.00", "UAH"))
                        .with(asUser(username))
                        .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    private static String topUp(long walletId, String amount, String currency) {
        return """
                {"walletId": %d, "amount": %s, "currency": "%s"}
                """.formatted(walletId, amount, currency);
    }
}
