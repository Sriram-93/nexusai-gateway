package com.llm.nexusai_gateway.Provider.Auth;

import com.llm.nexusai_gateway.Provider.ProviderConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class AwsSigV4AuthStrategy implements ProviderAuthStrategy {

    @Override
    public boolean supports(ProviderConfig.ProviderType type) {
        return type == ProviderConfig.ProviderType.BEDROCK;
    }

    @Override
    public Mono<ClientRequest> applyAuth(ClientRequest request, ProviderConfig config) {
        Map<String, String> creds = config.getCredentials();
        String accessKey = creds.get("access_key");
        String secretKey = creds.get("secret_key");
        String region = creds.get("region");

        if (accessKey == null || secretKey == null || region == null) {
            return Mono.error(new IllegalArgumentException("AWS credentials missing for Bedrock"));
        }

        try {
            software.amazon.awssdk.auth.credentials.AwsBasicCredentials credentials = 
                software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(accessKey, secretKey);
            
            software.amazon.awssdk.auth.signer.Aws4Signer signer = software.amazon.awssdk.auth.signer.Aws4Signer.create();
            
            software.amazon.awssdk.http.SdkHttpFullRequest.Builder sdkRequestBuilder = 
                software.amazon.awssdk.http.SdkHttpFullRequest.builder()
                .uri(request.url())
                .method(software.amazon.awssdk.http.SdkHttpMethod.valueOf(request.method().name()));

            // For reactive clients, we use UNSIGNED-PAYLOAD so we don't have to block the reactive stream to hash the body
            sdkRequestBuilder.putHeader("X-Amz-Content-Sha256", "UNSIGNED-PAYLOAD");
            
            request.headers().forEach((name, values) -> sdkRequestBuilder.putHeader(name, values));
            
            software.amazon.awssdk.auth.signer.params.Aws4SignerParams signerParams = 
                software.amazon.awssdk.auth.signer.params.Aws4SignerParams.builder()
                .awsCredentials(credentials)
                .signingRegion(software.amazon.awssdk.regions.Region.of(region))
                .signingName("bedrock")
                .build();
                
            software.amazon.awssdk.http.SdkHttpFullRequest signedRequest = signer.sign(sdkRequestBuilder.build(), signerParams);
            
            ClientRequest.Builder clientRequestBuilder = ClientRequest.from(request);
            clientRequestBuilder.headers(headers -> {
                headers.clear();
                signedRequest.headers().forEach(headers::addAll);
            });
            
            return Mono.just(clientRequestBuilder.build());
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Failed to mathematically sign AWS SigV4 Request", e));
        }
    }
}
