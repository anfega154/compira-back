package co.com.compira.cognito.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderAsyncClient;

@Configuration
public class CognitoIdentityProviderConfig {
    @Bean
    public CognitoIdentityProviderAsyncClient cognitoIdentityProviderAsyncClient(CognitoIdentityProviderProperties properties) {
        return CognitoIdentityProviderAsyncClient.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
