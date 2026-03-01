package com.sfmc.copilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Direct Google Gemini API client using Google AI Studio API key.
 * 
 * Bypasses the VertexAI SDK (which requires GCP auth) and calls the
 * Gemini REST API directly at generativelanguage.googleapis.com.
 * This is the simplest way to use Gemini with just an API key.
 */
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Check if Gemini is configured (API key present).
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Send a prompt to Gemini and return the text response.
     * 
     * @param systemPrompt System instruction for the model
     * @param userPrompt   User message
     * @return The generated text response
     */
    public String chat(String systemPrompt, String userPrompt) {
        if (!isConfigured()) {
            throw new RuntimeException("Gemini API key not configured. Set GEMINI_API_KEY env variable.");
        }

        String url = String.format(GEMINI_API_URL, model, apiKey);
        log.debug("Calling Gemini API: model={}", model);

        try {
            // Build the request body
            ObjectNode requestBody = objectMapper.createObjectNode();

            // System instruction
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                ObjectNode systemInstruction = objectMapper.createObjectNode();
                ObjectNode systemPart = objectMapper.createObjectNode();
                systemPart.put("text", systemPrompt);
                ArrayNode systemParts = objectMapper.createArrayNode();
                systemParts.add(systemPart);
                systemInstruction.set("parts", systemParts);
                requestBody.set("systemInstruction", systemInstruction);
            }

            // User content
            ArrayNode contents = objectMapper.createArrayNode();
            ObjectNode userContent = objectMapper.createObjectNode();
            userContent.put("role", "user");
            ObjectNode userPart = objectMapper.createObjectNode();
            userPart.put("text", userPrompt);
            ArrayNode userParts = objectMapper.createArrayNode();
            userParts.add(userPart);
            userContent.set("parts", userParts);
            contents.add(userContent);
            requestBody.set("contents", contents);

            // Generation config
            ObjectNode generationConfig = objectMapper.createObjectNode();
            generationConfig.put("temperature", 0.7);
            generationConfig.put("maxOutputTokens", 2048);
            requestBody.set("generationConfig", generationConfig);

            // Make the HTTP request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return extractTextFromResponse(response.getBody());
            } else {
                throw new RuntimeException("Gemini API error: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage());
            throw new RuntimeException("Gemini API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extract the text content from Gemini's JSON response.
     */
    private String extractTextFromResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode content = candidates.get(0).path("content");
                JsonNode parts = content.path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    return parts.get(0).path("text").asText();
                }
            }
            log.warn("Unexpected Gemini response structure: {}", responseBody);
            return "I received a response from Gemini but couldn't parse it.";
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage());
            return "Error parsing Gemini response.";
        }
    }
}
