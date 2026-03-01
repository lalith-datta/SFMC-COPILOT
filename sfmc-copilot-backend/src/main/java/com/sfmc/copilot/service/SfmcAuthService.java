package com.sfmc.copilot.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

/**
 * SFMC OAuth2 Authentication Service.
 * 
 * Manages the OAuth2 client_credentials flow for SFMC REST API access.
 * Automatically refreshes tokens before they expire.
 * 
 * Falls back to DEMO mode when credentials aren't configured.
 */
@Service
public class SfmcAuthService {

    private static final Logger log = LoggerFactory.getLogger(SfmcAuthService.class);

    @Value("${sfmc.auth.client-id:}")
    private String clientId;

    @Value("${sfmc.auth.client-secret:}")
    private String clientSecret;

    @Value("${sfmc.auth.base-uri:}")
    private String authBaseUri;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String accessToken;
    private Instant tokenExpiry = Instant.EPOCH;

    /**
     * Check if SFMC credentials are configured (non-demo mode).
     */
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && authBaseUri != null && !authBaseUri.isBlank();
    }

    /**
     * Get a valid access token, refreshing if necessary.
     */
    public String getAccessToken() {
        if (!isConfigured()) {
            log.debug("SFMC credentials not configured — running in DEMO mode");
            return "DEMO_TOKEN";
        }

        // Refresh if token expires in less than 60 seconds
        if (accessToken == null || Instant.now().isAfter(tokenExpiry.minusSeconds(60))) {
            refreshToken();
        }

        return accessToken;
    }

    /**
     * Request a new access token from SFMC OAuth2 endpoint.
     */
    private void refreshToken() {
        log.info("Refreshing SFMC access token...");

        // Remove trailing slash if present to avoid double slash
        String baseUri = authBaseUri.endsWith("/") ? authBaseUri.substring(0, authBaseUri.length() - 1) : authBaseUri;
        String tokenUrl = baseUri + "/v2/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // SFMC v2/token expects JSON body, not form-urlencoded
        String jsonBody = String.format(
                "{\"grant_type\":\"client_credentials\",\"client_id\":\"%s\",\"client_secret\":\"%s\"}",
                clientId, clientSecret);

        try {
            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    tokenUrl, HttpMethod.POST, request, String.class);

            TokenResponse tokenResponse = objectMapper.readValue(
                    response.getBody(), TokenResponse.class);

            this.accessToken = tokenResponse.accessToken();
            this.tokenExpiry = Instant.now().plusSeconds(tokenResponse.expiresIn());

            log.info("SFMC access token refreshed, expires in {} seconds", tokenResponse.expiresIn());
        } catch (Exception e) {
            log.error("Failed to refresh SFMC access token: {}", e.getMessage());
            throw new RuntimeException("SFMC authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * OAuth2 token response from SFMC.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") int expiresIn,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("rest_instance_url") String restInstanceUrl) {
    }
}
