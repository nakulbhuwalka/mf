package com.mf.api.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    @Value("${mfapi.base-url:https://api.mfapi.in}")
    private String baseUrl;

    @Value("${mfapi.timeout-seconds:15}")
    private int timeoutSeconds;

    @Bean
    public RestClient.Builder restClientBuilder() {
        final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        return RestClient.builder()
                .requestFactory(requestFactory);
    }

    @Bean
    public RestClient mfApiRestClient(final RestClient.Builder restClientBuilder) {
        return restClientBuilder
                .baseUrl(baseUrl)
                .build();
    }
}
