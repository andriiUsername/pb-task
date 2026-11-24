package privatbank.task.security;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import privatbank.task.IntegrationTest;

class JwtAuthenticationTest extends IntegrationTest {

    @Test
    void requestWithoutTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/wallets/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void requestWithMalformedTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/wallets/1").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void unreachableIdentityProviderIsReportedAsTemporaryFailure() throws Exception {
        mvc.perform(get("/api/v1/wallets/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + wellFormedToken()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                .andExpect(jsonPath("$.code").value("TEMPORARILY_UNAVAILABLE"));
    }

    @Test
    void usernameAndRolesAreTakenFromTokenClaims() {
        AbstractAuthenticationToken authentication = jwtAuthenticationConverter.convert(token("alice", Roles.USER));

        assertThat(authentication.getName()).isEqualTo("alice");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_USER")
                .doesNotContain("ROLE_OPERATOR");
    }

    @Test
    void healthCheckDoesNotRequireToken() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    private static String wellFormedToken() {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"RS256\",\"kid\":\"unknown\"}".getBytes(UTF_8));
        String payload = encoder.encodeToString("{\"sub\":\"alice\",\"exp\":4102444800}".getBytes(UTF_8));
        return header + "." + payload + "." + encoder.encodeToString("signature".getBytes(UTF_8));
    }
}
