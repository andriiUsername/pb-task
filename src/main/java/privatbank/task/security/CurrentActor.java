package privatbank.task.security;

import java.util.function.Supplier;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentActor {

    public static final String SYSTEM = "system";

    private static final String OPERATOR_AUTHORITY = "ROLE_" + Roles.OPERATOR;
    private static final String SYSTEM_AUTHORITY = "SYSTEM";
    private static final Authentication SYSTEM_AUTHENTICATION = UsernamePasswordAuthenticationToken.authenticated(
            SYSTEM, null, AuthorityUtils.createAuthorityList(SYSTEM_AUTHORITY));
    private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

    public String name() {
        return authentication().getName();
    }

    public boolean isOperator() {
        return hasAuthority(OPERATOR_AUTHORITY);
    }

    public boolean isSystem() {
        return hasAuthority(SYSTEM_AUTHORITY);
    }

    public <T> T runAsSystem(Supplier<T> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext system = SecurityContextHolder.createEmptyContext();
        system.setAuthentication(SYSTEM_AUTHENTICATION);
        SecurityContextHolder.setContext(system);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private boolean hasAuthority(String authority) {
        return authentication().getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    private static Authentication authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!TRUST_RESOLVER.isAuthenticated(authentication)) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user or system actor");
        }
        return authentication;
    }
}
