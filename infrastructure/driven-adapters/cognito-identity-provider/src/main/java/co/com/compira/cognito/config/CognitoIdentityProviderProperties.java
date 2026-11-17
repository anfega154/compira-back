package co.com.compira.cognito.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "adapters.cognito")
public record CognitoIdentityProviderProperties(
        String region,
        String userPoolId,
        String clientId) {
}
