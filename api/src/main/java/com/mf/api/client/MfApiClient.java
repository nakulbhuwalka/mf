package com.mf.api.client;

import com.mf.api.dto.UpstreamNavHistoryDto;
import com.mf.api.dto.UpstreamSchemeItemDto;
import com.mf.api.exception.SchemeNotFoundException;
import com.mf.api.exception.UpstreamGatewayException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class MfApiClient {

    private final RestClient restClient;

    @Value("${mfapi.schemes-path:/mf/latest}")
    private String schemesPath = "/mf/latest";

    @Autowired
    public MfApiClient(final RestClient restClient) {
        this.restClient = restClient;
    }

    public MfApiClient(final RestClient restClient, final String schemesPath) {
        this.restClient = restClient;
        this.schemesPath = schemesPath;
    }

    public List<UpstreamSchemeItemDto> fetchLatestSchemes() {
        try {
            return doFetchSchemes(schemesPath);
        } catch (final HttpClientErrorException.NotFound notFoundEx) {
            return tryFetchFallback(notFoundEx);
        } catch (final RestClientException ex) {
            throw new UpstreamGatewayException("Failed to download master scheme directory from mfapi.in", ex);
        }
    }

    private List<UpstreamSchemeItemDto> tryFetchFallback(final HttpClientErrorException.NotFound notFoundEx) {
        try {
            return doFetchSchemes("/mf");
        } catch (final RestClientException fallbackEx) {
            final UpstreamGatewayException error = new UpstreamGatewayException(
                    "Failed to download master scheme directory from mfapi.in",
                    fallbackEx
            );
            error.addSuppressed(notFoundEx);
            throw error;
        }
    }

    private List<UpstreamSchemeItemDto> doFetchSchemes(final String path) {
        final UpstreamSchemeItemDto[] response = restClient.get()
                .uri(path)
                .retrieve()
                .body(UpstreamSchemeItemDto[].class);

        if (response == null || response.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.asList(response);
    }

    public UpstreamNavHistoryDto fetchSchemeNav(final int schemeCode) {
        try {
            final UpstreamNavHistoryDto history = restClient.get()
                    .uri("/mf/{code}", schemeCode)
                    .retrieve()
                    .body(UpstreamNavHistoryDto.class);

            if (history == null || history.meta() == null) {
                throw new SchemeNotFoundException(schemeCode);
            }
            return history;
        } catch (final HttpClientErrorException.NotFound ex) {
            throw new SchemeNotFoundException(schemeCode, ex);
        } catch (final RestClientException ex) {
            throw new UpstreamGatewayException("Failed to retrieve NAV data from upstream provider mfapi.in", ex);
        }
    }
}
