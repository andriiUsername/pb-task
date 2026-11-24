package privatbank.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import privatbank.task.model.Money;
import privatbank.task.security.CurrentActor;
import privatbank.task.security.Roles;
import privatbank.task.service.PaymentService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    public static final String TEST_USER = "test-user";
    protected static final String UAH = "UAH";
    protected static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected PaymentService paymentService;

    @Autowired
    protected CurrentActor currentActor;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected JwtAuthenticationConverter jwtAuthenticationConverter;

    protected long newWallet(String balance) {
        return insertWallet(TEST_USER, balance, UAH);
    }

    protected long newWallet(String balance, String currency) {
        return insertWallet(TEST_USER, balance, currency);
    }

    protected long newWalletOwnedBy(String owner, String balance) {
        return insertWallet(owner, balance, UAH);
    }

    protected BigDecimal balanceOf(long walletId) {
        return jdbc.sql("SELECT balance FROM wallet WHERE id = ?")
                .param(walletId)
                .query(BigDecimal.class)
                .single();
    }

    protected void assertReconciled(long walletId) {
        BigDecimal journalBalance = jdbc.sql("""
                        SELECT COALESCE(SUM(CASE WHEN target_wallet_id = :id THEN amount ELSE -amount END), 0)
                        FROM payment
                        WHERE status = 'COMPLETED' AND (source_wallet_id = :id OR target_wallet_id = :id)
                        """)
                .param("id", walletId)
                .query(BigDecimal.class)
                .single();
        assertThat(balanceOf(walletId)).isEqualByComparingTo(journalBalance);
    }

    protected FundingRequestFixture approvedFundingRequest(long sourceWalletId, long targetWalletId,
                                                           LocalDate... dueDates) {
        long requestId = jdbc.sql("""
                        INSERT INTO funding_request (source_wallet_id, target_wallet_id, currency, status)
                        VALUES (?, ?, 'UAH', 'APPROVED')
                        RETURNING id
                        """)
                .params(sourceWalletId, targetWalletId)
                .query(Long.class)
                .single();
        List<Long> itemIds = Arrays.stream(dueDates)
                .map(dueDate -> jdbc.sql("""
                                INSERT INTO funding_schedule_item (funding_request_id, due_date, amount)
                                VALUES (?, ?, 100.00)
                                RETURNING id
                                """)
                        .params(requestId, dueDate)
                        .query(Long.class)
                        .single())
                .toList();
        return new FundingRequestFixture(requestId, itemIds);
    }

    protected RequestPostProcessor asUser(String username) {
        return withToken(username, Roles.USER);
    }

    protected RequestPostProcessor asOperator() {
        return withToken("operator", Roles.OPERATOR);
    }

    protected <T> T actingAs(String username, String role, Supplier<T> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(
                new SecurityContextImpl(jwtAuthenticationConverter.convert(token(username, role))));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    protected <T> T actingAsOperator(Supplier<T> action) {
        return actingAs("operator", Roles.OPERATOR, action);
    }

    protected <T> T actingAsSystem(Supplier<T> action) {
        return currentActor.runAsSystem(action);
    }

    protected static Jwt token(String username, String role) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .claim("preferred_username", username)
                .claim("roles", List.of(role))
                .build();
    }

    protected static Money uah(String amount) {
        return Money.of(new BigDecimal(amount), UAH);
    }

    protected static String newKey() {
        return UUID.randomUUID().toString();
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private RequestPostProcessor withToken(String username, String role) {
        return authentication(jwtAuthenticationConverter.convert(token(username, role)));
    }

    private long insertWallet(String owner, String balance, String currency) {
        long walletId = jdbc.sql("INSERT INTO wallet (owner_id, currency) VALUES (?, ?) RETURNING id")
                .params(owner, currency)
                .query(Long.class)
                .single();
        Money initialBalance = Money.of(new BigDecimal(balance), currency);
        if (initialBalance.isPositive()) {
            long topUpId = actingAs(owner, Roles.USER,
                    () -> paymentService.createTopUp(walletId, initialBalance, newKey()).id());
            actingAsOperator(() -> paymentService.confirmPayment(topUpId));
        }
        return walletId;
    }

    protected record FundingRequestFixture(long id, List<Long> itemIds) {

        public long itemId(int index) {
            return itemIds.get(index);
        }
    }
}
