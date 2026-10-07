package co.com.compira.api.config;

import co.com.compira.model.auth.UserStatus;
import co.com.compira.api.auth.AuthenticationRoute;
import co.com.compira.api.task.TaskRoute;
import co.com.compira.api.team.TeamHandler;
import co.com.compira.api.notification.NotificationHandler;
import co.com.compira.api.organization.OrganizationSettingsHandler;
import co.com.compira.api.user.UserAdminHandler;
import co.com.compira.model.auth.gateways.ApplicationUserRepositoryGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class ResourceServerConfig {
    private static final String AUTH = AuthenticationRoute.API_V1 + AuthenticationRoute.AUTH_BASE;
    private static final String TASKS = TaskRoute.API_V1 + TaskRoute.TASKS_BASE;
    private static final String DESCENDANTS = "/**";
    private static final String HEALTH = "/actuator/health/**";
    private static final String OPEN_API = "/v3/**";
    private static final String WEBJARS = "/webjars/**";
    private static final String ADMINISTRATOR = "ADMINISTRATOR";
    private static final String COORDINATOR = "COORDINATOR";
    private static final String COLLABORATOR = "COLLABORATOR";
    private static final String INVALID_ACCOUNT = "La cuenta no está activa";

    @Bean
    ReactiveJwtDecoder jwtDecoder(@Value("${adapters.cognito.region}") String region,
                                  @Value("${adapters.cognito.user-pool-id}") String pool,
                                  @Value("${adapters.cognito.client-id}") String clientId) {
        String issuer = "https://cognito-idp." + region + ".amazonaws.com/" + pool;
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json").build();
        decoder.setJwtValidator(cognitoTokenValidator(issuer, clientId));
        return decoder;
    }

    org.springframework.security.oauth2.core.OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> cognitoTokenValidator(String issuer, String clientId) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<String>("token_use", "access"::equals),
                new JwtClaimValidator<String>("client_id", clientId::equals),
                new JwtClaimValidator<String>("sub", subject -> subject != null && !subject.isBlank()),
                new JwtClaimValidator<Object>("exp", expiration -> expiration != null));
    }

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder decoder,
                                                   ApplicationUserRepositoryGateway users) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, DESCENDANTS).permitAll()
                        .pathMatchers(HttpMethod.GET, TeamHandler.BASE).hasAnyAuthority(ADMINISTRATOR, COORDINATOR)
                        .pathMatchers(HttpMethod.POST, TeamHandler.MEMBERS, TeamHandler.TASKS).hasAnyAuthority(ADMINISTRATOR, COORDINATOR)
                        .pathMatchers(TeamHandler.BASE, TeamHandler.BASE + DESCENDANTS).hasAuthority(ADMINISTRATOR)
                        .pathMatchers(AUTH + AuthenticationRoute.REGISTER, OrganizationSettingsHandler.BASE)
                        .hasAuthority(ADMINISTRATOR)
                        .pathMatchers(UserAdminHandler.BASE, UserAdminHandler.BASE + DESCENDANTS).hasAuthority(ADMINISTRATOR)
                        .pathMatchers(AUTH + AuthenticationRoute.LOGIN, AUTH + AuthenticationRoute.LOGIN + DESCENDANTS, AUTH + AuthenticationRoute.PASSWORD_RECOVERY,
                                AUTH + AuthenticationRoute.PASSWORD_RECOVERY_CONFIRMATION, AUTH + AuthenticationRoute.LOGOUT,
                                HEALTH, OPEN_API, WEBJARS).permitAll()
                        .pathMatchers(NotificationHandler.BASE + DESCENDANTS, NotificationHandler.BASE)
                        .hasAnyAuthority(COORDINATOR, COLLABORATOR)
                        .pathMatchers(HttpMethod.POST, TASKS, TASKS + TaskRoute.ASSIGN, TASKS + TaskRoute.REASSIGN,
                                TASKS + TaskRoute.CANCEL, TASKS + TaskRoute.APPROVE).hasAuthority(COORDINATOR)
                        .pathMatchers(HttpMethod.POST, TASKS + TaskRoute.STATUS, TASKS + TaskRoute.OBSERVATIONS)
                        .hasAuthority(COLLABORATOR)
                        .pathMatchers(TASKS + DESCENDANTS, TASKS).hasAnyAuthority(ADMINISTRATOR, COORDINATOR, COLLABORATOR)
                        .anyExchange().denyAll())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtDecoder(decoder)
                        .jwtAuthenticationConverter(token -> users.findByCognitoSub(token.getSubject())
                                .filter(user -> user.status() == UserStatus.ACTIVE)
                                .switchIfEmpty(Mono.error(new InvalidBearerTokenException(INVALID_ACCOUNT)))
                                .map(user -> new JwtAuthenticationToken(token,
                                        user.roles().stream().map(SimpleGrantedAuthority::new).toList(), user.user().email())))))
                .build();
    }
}
