package privatbank.task.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentActorTest {

    private final CurrentActor currentActor = new CurrentActor();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void withoutAuthenticationThereIsNoActor() {
        assertThatThrownBy(currentActor::name).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(currentActor::isOperator).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(currentActor::isSystem).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void anonymousUserIsNotAnActor() {
        authenticate(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThatThrownBy(currentActor::name).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void systemRightsExistOnlyInsideRunAsSystem() {
        assertThat(currentActor.runAsSystem(currentActor::name)).isEqualTo(CurrentActor.SYSTEM);
        assertThat(currentActor.runAsSystem(currentActor::isSystem)).isTrue();
        assertThat(currentActor.runAsSystem(currentActor::isOperator)).isFalse();
        assertThatThrownBy(currentActor::isSystem).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void roleFromATokenCannotGrantSystemRights() {
        authenticate(UsernamePasswordAuthenticationToken.authenticated("mallory", null,
                AuthorityUtils.createAuthorityList("ROLE_SYSTEM")));

        assertThat(currentActor.isSystem()).isFalse();
        assertThat(currentActor.isOperator()).isFalse();
    }

    private static void authenticate(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
