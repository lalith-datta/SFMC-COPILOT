package com.sfmc.copilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

/**
 * AI Gateway Service — Intelligent routing between Gemini and Ollama
 * with conversation memory and SFMC tool augmentation.
 * 
 * Gemini is called directly via REST API (GeminiService) using a Google AI
 * Studio API key.
 * Ollama is called via Spring AI ChatClient.
 * 
 * For SFMC operations (create DE, etc.), this service:
 * 1. Detects the intent from the user's message
 * 2. Uses Gemini to parse the request into structured parameters
 * 3. Calls the SFMC API with those parameters
 * 4. Passes the real result to the LLM for a formatted response
 */
@Service
public class AiGatewayService {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayService.class);

    private final GeminiService geminiService;
    private final ChatClient ollamaChatClient;
    private final ConversationStore conversationStore;
    private final SfmcApiService sfmcApiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String GEMINI_SYSTEM_PROMPT = """
            You are SFMC Copilot, an expert AI assistant for Salesforce Marketing Cloud (SFMC).

            Your capabilities:
            - Create, modify, and list Data Extensions with custom fields
            - Build and manage email templates in Content Builder
            - Set up automations with schedules and multi-step workflows
            - Query subscriber data and campaign performance metrics
            - Provide best practices for email marketing and SFMC usage

            Guidelines:
            1. Format responses with markdown for readability (tables, bold, lists)
            2. If real SFMC data or action results are provided to you, the action has ALREADY BEEN EXECUTED.
               Do NOT ask for confirmation. Do NOT say "shall I create this?" — just report the result.
            3. Provide field-level details when describing Data Extensions (name, type, required, primary key)
            4. For automations, specify the schedule and steps clearly
            5. Always be helpful, professional, and concise
            """;

    /**
     * System prompt used to extract structured DE parameters from natural language.
     */
    private static final String DE_PARSER_PROMPT = """
            You are a JSON parser. Extract the Data Extension name, categoryId, and fields from the user's request.
            Respond ONLY with valid JSON, no markdown, no explanation.

            Format:
            {
              "name": "DataExtensionName",
              "categoryId": 0,
              "fields": [
                {"name": "FieldName", "type": "Text", "isPrimaryKey": true, "isRequired": true, "maxLength": 254},
                {"name": "AnotherField", "type": "EmailAddress", "isPrimaryKey": false, "isRequired": true}
              ]
            }

            Valid field types: Text, Number, Date, Boolean, EmailAddress, Phone, Decimal, Locale

            Rules:
            - Every DE must have at least one primary key field
            - If the user doesn't specify a primary key, make the first field the primary key
            - If the user doesn't specify a field type, default to Text
            - For Text fields, default maxLength to 254
            - If the user provides a numeric categoryId or folder ID, set it in categoryId
            - If no categoryId is mentioned, set categoryId to 0
            - Respond ONLY with the JSON object, nothing else
            """;

    // Keywords that indicate complex operations needing Gemini
    private static final Set<String> COMPLEX_KEYWORDS = Set.of(
            "create", "build", "set up", "setup", "configure", "automation",
            "schedule", "journey", "campaign", "template", "design",
            "integrate", "migrate", "transform", "optimize", "analyze",
            "data extension", "triggered send", "content block");

    // Keywords that indicate simple operations Ollama can handle
    private static final Set<String> SIMPLE_KEYWORDS = Set.of(
            "list", "show", "count", "how many", "what is", "explain",
            "help", "status", "check", "describe", "summary", "hello", "hi");

    public AiGatewayService(
            GeminiService geminiService,
            @Qualifier("ollamaChatClient") ChatClient ollamaChatClient,
            ConversationStore conversationStore,
            SfmcApiService sfmcApiService) {
        this.geminiService = geminiService;
        this.ollamaChatClient = ollamaChatClient;
        this.conversationStore = conversationStore;
        this.sfmcApiService = sfmcApiService;
    }

    /**
     * Route the message to the appropriate LLM based on preference and complexity.
     * Detects SFMC queries and pre-fetches real data to augment the prompt.
     */
    public RoutingResult route(String message, String conversationId, String preferredModel) {
        if (preferredModel == null || preferredModel.isBlank()) {
            preferredModel = "auto";
        }

        // Detect if this is an SFMC data query and fetch real data
        String sfmcData = detectAndFetchSfmcData(message);

        // Build the full prompt with conversation history + SFMC data
        String fullPrompt = buildPromptWithHistory(message, conversationId, sfmcData);

        // Store the user message
        conversationStore.addMessage(conversationId, "user", message);

        // Route to the appropriate LLM
        RoutingResult result = switch (preferredModel.toLowerCase()) {
            case "gemini" -> callWithFallback(fullPrompt, true);
            case "ollama" -> callOllama(fullPrompt);
            case "auto" -> routeAuto(message, fullPrompt);
            default -> routeAuto(message, fullPrompt);
        };

        // Store the assistant response
        conversationStore.addMessage(conversationId, "assistant", result.text());

        return result;
    }

    /**
     * Detect SFMC-related queries and EXECUTE the corresponding actions.
     * For read operations (list, count): calls the API and returns the data.
     * For write operations (create): parses the request, calls the API, returns the
     * result.
     */
    private String detectAndFetchSfmcData(String message) {
        String lower = message.toLowerCase();

        try {
            // ===== WRITE OPERATIONS — Check these FIRST (before read) =====
            // Must be checked before read operations because "create data extension"
            // also contains "data extension" which would match the list check.

            if (lower.contains("create") && lower.contains("data extension")) {
                log.info("SFMC Action detected: Creating data extension...");
                return executeCreateDataExtension(message);
            }

            if (lower.contains("create") && (lower.contains("email") || lower.contains("template"))) {
                log.info("SFMC Action detected: Creating email definition...");
                return executeCreateEmail(message);
            }

            if (lower.contains("create") && lower.contains("automation")) {
                log.info("SFMC Action detected: Creating automation...");
                return executeCreateAutomation(message);
            }

            // ===== READ OPERATIONS =====

            if (matchesAny(lower, "subscriber", "how many", "subscriber count", "total subscribers",
                    "active subscribers", "subscriber metrics", "subscriber stats")) {
                log.info("SFMC Query detected: Fetching subscriber data...");
                return "SFMC_SUBSCRIBER_DATA:\n" + sfmcApiService.getSubscriberCount();
            }

            if (matchesAny(lower, "list data extension", "show data extension", "my data extension",
                    "data extensions", "what data extension", "how many data extension")) {
                log.info("SFMC Query detected: Fetching data extensions...");
                return "SFMC_DATA_EXTENSIONS:\n" + sfmcApiService.listDataExtensions();
            }

        } catch (Exception e) {
            log.warn("SFMC data fetch failed: {}", e.getMessage());
            return "SFMC_ERROR: Could not fetch data from SFMC: " + e.getMessage();
        }

        return null;
    }

    /**
     * Use Gemini to parse the user's natural language request into structured
     * DE parameters, then call the SFMC API to create it.
     */
    private String executeCreateDataExtension(String userMessage) {
        try {
            // Step 1: Use Gemini to parse the request into JSON
            String parsedJson = null;
            if (geminiService.isConfigured()) {
                log.info("Using Gemini to parse DE creation request...");
                parsedJson = geminiService.chat(DE_PARSER_PROMPT, userMessage);
                log.debug("Gemini parsed DE request: {}", parsedJson);
            }

            // Step 2: Parse the JSON response
            String deName;
            long categoryId = 0;
            List<Map<String, Object>> fields;

            if (parsedJson != null) {
                // Clean up the response (remove markdown fences if present)
                parsedJson = parsedJson.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

                JsonNode root = objectMapper.readTree(parsedJson);
                deName = root.get("name").asText();

                // Extract categoryId if provided
                if (root.has("categoryId")) {
                    categoryId = root.get("categoryId").asLong(0);
                }

                fields = new ArrayList<>();

                JsonNode fieldsNode = root.get("fields");
                for (JsonNode fieldNode : fieldsNode) {
                    Map<String, Object> field = new HashMap<>();
                    field.put("name", fieldNode.get("name").asText());
                    field.put("type", fieldNode.has("type") ? fieldNode.get("type").asText() : "Text");
                    field.put("isPrimaryKey",
                            fieldNode.has("isPrimaryKey") && fieldNode.get("isPrimaryKey").asBoolean());
                    field.put("isRequired", fieldNode.has("isRequired") && fieldNode.get("isRequired").asBoolean());
                    if (fieldNode.has("maxLength")) {
                        field.put("maxLength", fieldNode.get("maxLength").asInt());
                    } else if ("Text".equalsIgnoreCase((String) field.get("type"))) {
                        field.put("maxLength", 254);
                    }
                    fields.add(field);
                }
            } else {
                // Fallback: basic regex parsing
                log.info("Gemini not available, using basic parsing...");
                deName = extractDeName(userMessage);
                fields = extractBasicFields(userMessage);
            }

            // Step 3: Actually create the Data Extension via SFMC API
            log.info("Creating Data Extension '{}' with {} fields, categoryId={}", deName, fields.size(), categoryId);
            String result = sfmcApiService.createDataExtension(deName, fields, categoryId);

            return "SFMC_CREATE_DE_RESULT:\n" + result;

        } catch (Exception e) {
            log.error("Failed to create Data Extension: {}", e.getMessage());
            return "SFMC_CREATE_DE_ERROR: Failed to create Data Extension: " + e.getMessage();
        }
    }

    /**
     * Parse a create email request and execute it.
     */
    private String executeCreateEmail(String userMessage) {
        try {
            String name = extractQuotedName(userMessage, "email");
            String subject = "Marketing Email"; // default

            if (geminiService.isConfigured()) {
                String parsed = geminiService.chat(
                        "Extract email name and subject from this request. Respond ONLY with JSON: {\"name\":\"...\",\"subject\":\"...\",\"htmlContent\":\"<html>...</html>\"}",
                        userMessage);
                parsed = parsed.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
                JsonNode root = objectMapper.readTree(parsed);
                name = root.get("name").asText();
                subject = root.has("subject") ? root.get("subject").asText() : subject;
                String html = root.has("htmlContent") ? root.get("htmlContent").asText() : null;
                String result = sfmcApiService.createEmailDefinition(name, subject, html);
                return "SFMC_CREATE_EMAIL_RESULT:\n" + result;
            }

            String result = sfmcApiService.createEmailDefinition(name, subject, null);
            return "SFMC_CREATE_EMAIL_RESULT:\n" + result;
        } catch (Exception e) {
            log.error("Failed to create email: {}", e.getMessage());
            return "SFMC_CREATE_EMAIL_ERROR: Failed to create email: " + e.getMessage();
        }
    }

    /**
     * Parse a create automation request and execute it.
     */
    private String executeCreateAutomation(String userMessage) {
        try {
            String name = extractQuotedName(userMessage, "automation");
            String description = "Automation created by SFMC Copilot";
            String frequency = "Daily";

            if (geminiService.isConfigured()) {
                String parsed = geminiService.chat(
                        "Extract automation details from this request. Respond ONLY with JSON: {\"name\":\"...\",\"description\":\"...\",\"scheduleFrequency\":\"Daily|Weekly|Monthly|Hourly\"}",
                        userMessage);
                parsed = parsed.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
                JsonNode root = objectMapper.readTree(parsed);
                name = root.get("name").asText();
                description = root.has("description") ? root.get("description").asText() : description;
                frequency = root.has("scheduleFrequency") ? root.get("scheduleFrequency").asText() : frequency;
            }

            String result = sfmcApiService.createAutomation(name, description, frequency);
            return "SFMC_CREATE_AUTOMATION_RESULT:\n" + result;
        } catch (Exception e) {
            log.error("Failed to create automation: {}", e.getMessage());
            return "SFMC_CREATE_AUTOMATION_ERROR: Failed to create automation: " + e.getMessage();
        }
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Extract a DE name from the user's message using simple heuristics.
     */
    private String extractDeName(String message) {
        // Try quoted names first: "MyDE" or 'MyDE'
        Pattern quoted = Pattern.compile("[\"']([^\"']+)[\"']");
        Matcher m = quoted.matcher(message);
        if (m.find())
            return m.group(1);

        // Try "called X" or "named X"
        Pattern named = Pattern.compile("(?:called|named)\\s+([A-Za-z_]\\w*)", Pattern.CASE_INSENSITIVE);
        m = named.matcher(message);
        if (m.find())
            return m.group(1);

        // Default
        return "NewDataExtension_" + System.currentTimeMillis();
    }

    /**
     * Extract a name from the user's message for email/automation.
     */
    private String extractQuotedName(String message, String entityType) {
        Pattern quoted = Pattern.compile("[\"']([^\"']+)[\"']");
        Matcher m = quoted.matcher(message);
        if (m.find())
            return m.group(1);

        Pattern named = Pattern.compile("(?:called|named)\\s+([A-Za-z_]\\w*)", Pattern.CASE_INSENSITIVE);
        m = named.matcher(message);
        if (m.find())
            return m.group(1);

        return entityType + "_" + System.currentTimeMillis();
    }

    /**
     * Extract basic field definitions with simple heuristics (fallback when Gemini
     * is unavailable).
     */
    private List<Map<String, Object>> extractBasicFields(String message) {
        List<Map<String, Object>> fields = new ArrayList<>();

        // Always add a SubscriberKey as primary key
        Map<String, Object> pk = new HashMap<>();
        pk.put("name", "SubscriberKey");
        pk.put("type", "Text");
        pk.put("isPrimaryKey", true);
        pk.put("isRequired", true);
        pk.put("maxLength", 254);
        fields.add(pk);

        // Look for field names mentioned in the message
        String[] commonFields = { "Email", "FirstName", "LastName", "Phone", "Name", "Address",
                "City", "State", "Country", "ZipCode", "Status", "DateOfBirth" };

        for (String fieldName : commonFields) {
            if (message.toLowerCase().contains(fieldName.toLowerCase())) {
                Map<String, Object> field = new HashMap<>();
                field.put("name", fieldName);
                field.put("isPrimaryKey", false);
                field.put("isRequired", false);

                // Infer type from name
                if (fieldName.equalsIgnoreCase("Email")) {
                    field.put("type", "EmailAddress");
                    field.put("isRequired", true);
                } else if (fieldName.equalsIgnoreCase("Phone")) {
                    field.put("type", "Phone");
                } else if (fieldName.equalsIgnoreCase("DateOfBirth")) {
                    field.put("type", "Date");
                } else {
                    field.put("type", "Text");
                    field.put("maxLength", 254);
                }
                fields.add(field);
            }
        }

        return fields;
    }

    private boolean matchesAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword))
                return true;
        }
        return false;
    }

    /**
     * Build a prompt that includes conversation history and SFMC data.
     */
    private String buildPromptWithHistory(String currentMessage, String conversationId, String sfmcData) {
        List<ConversationStore.Message> history = conversationStore.getHistory(conversationId);

        StringBuilder sb = new StringBuilder();

        if (!history.isEmpty()) {
            sb.append("Here is our conversation so far:\n\n");
            for (ConversationStore.Message msg : history) {
                String roleLabel = msg.role().equals("user") ? "User" : "You (SFMC Copilot)";
                sb.append(roleLabel).append(": ").append(msg.content()).append("\n\n");
            }
        }

        if (sfmcData != null) {
            sb.append("---\n");
            sb.append("IMPORTANT: The following is REAL DATA from the user's SFMC account. ");
            sb.append("This action has ALREADY BEEN EXECUTED. Report the result accurately. ");
            sb.append("Do NOT say you will do it — it is already done. Present the result clearly.\n\n");
            sb.append(sfmcData);
            sb.append("\n---\n\n");
        }

        sb.append("User: ").append(currentMessage);

        return sb.toString();
    }

    /**
     * Auto-routing: analyze complexity, choose model, with fallback.
     */
    private RoutingResult routeAuto(String originalMessage, String fullPrompt) {
        boolean isComplex = analyzeComplexity(originalMessage);

        if (isComplex) {
            log.info("Auto-route: Complex request detected → trying Gemini first");
            return callWithFallback(fullPrompt, true);
        } else {
            log.info("Auto-route: Simple request detected → using Ollama");
            return callOllama(fullPrompt);
        }
    }

    /**
     * Analyze message complexity using keyword heuristics.
     */
    private boolean analyzeComplexity(String message) {
        String lower = message.toLowerCase();

        int complexScore = 0;
        int simpleScore = 0;

        for (String keyword : COMPLEX_KEYWORDS) {
            if (lower.contains(keyword))
                complexScore++;
        }
        for (String keyword : SIMPLE_KEYWORDS) {
            if (lower.contains(keyword))
                simpleScore++;
        }

        if (message.length() > 50 && complexScore > 0)
            return true;
        return complexScore > simpleScore;
    }

    /**
     * Call Gemini with automatic fallback to Ollama on failure.
     */
    private RoutingResult callWithFallback(String prompt, boolean tryGeminiFirst) {
        if (tryGeminiFirst && geminiService.isConfigured()) {
            try {
                String response = geminiService.chat(GEMINI_SYSTEM_PROMPT, prompt);
                return new RoutingResult(response, "gemini");
            } catch (Exception e) {
                log.warn("Gemini call failed ({}), falling back to Ollama", e.getMessage());
            }
        }
        return callOllama(prompt);
    }

    /**
     * Call Ollama directly.
     */
    private RoutingResult callOllama(String prompt) {
        String response = ollamaChatClient.prompt()
                .user(prompt)
                .call()
                .content();
        return new RoutingResult(response, "ollama");
    }

    /**
     * Result of LLM routing — contains the response and which model was used.
     */
    public record RoutingResult(String text, String model) {
    }
}
