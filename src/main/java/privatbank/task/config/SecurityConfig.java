package privatbank.task.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;
import privatbank.task.security.Roles;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        AuthenticationEntryPoint authenticationEntryPoint =
                (request, response, e) -> exceptionResolver.resolveException(request, response, null, e);
        AccessDeniedHandler accessDeniedHandler =
                (request, response, e) -> exceptionResolver.resolveException(request, response, null, e);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .logout(AbstractHttpConfigurer::disable)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(withDefaults())
                        .withObjectPostProcessor(useFailureHandler(authenticationEntryPoint::commence)))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/funding-requests/*/suspend",
                                "/api/v1/funding-requests/*/resume").hasRole(Roles.OPERATOR)
                        .requestMatchers("/api/**").hasAnyRole(Roles.USER, Roles.OPERATOR)
                        .anyRequest().denyAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    private static ObjectPostProcessor<BearerTokenAuthenticationFilter> useFailureHandler(
            AuthenticationFailureHandler failureHandler) {
        return new ObjectPostProcessor<>() {
            @Override
            public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                filter.setAuthenticationFailureHandler(failureHandler);
                return filter;
            }
        };
    }
}
