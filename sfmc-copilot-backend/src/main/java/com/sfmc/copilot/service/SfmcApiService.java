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

import java.util.List;
import java.util.Map;

/**
 * SFMC REST API Client Service.
 * 
 * Handles actual API calls to Salesforce Marketing Cloud for:
 * - Data Extensions (CRUD)
 * - Email Definitions
 * - Automations
 * - Subscriber queries
 * 
 * When SFMC credentials are not configured, returns realistic demo responses.
 */
@Service
public class SfmcApiService {

    private static final Logger log = LoggerFactory.getLogger(SfmcApiService.class);

    private final SfmcAuthService authService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${sfmc.api.rest-base-uri:}")
    private String restBaseUri;

    public SfmcApiService(SfmcAuthService authService) {
        this.authService = authService;
    }

    private boolean isDemoMode() {
        return !authService.isConfigured() || restBaseUri == null || restBaseUri.isBlank();
    }

    /**
     * Get the clean base URI without trailing slash.
     */
    private String getBaseUri() {
        return restBaseUri.endsWith("/") ? restBaseUri.substring(0, restBaseUri.length() - 1) : restBaseUri;
    }

    // ==================== DATA EXTENSIONS ====================

    /**
     * Discover a valid categoryId from any existing DE in the account.
     * Used as a fallback when the user doesn't provide one.
     *
     * @return A valid categoryId, or -1 if none found
     */
    private long discoverCategoryId() {
        try {
            String url = getBaseUri() + "/data/v1/customobjects?$pageSize=1";
            log.info("Auto-discovering categoryId from existing DEs...");
            String response = callSfmcApi(url, HttpMethod.GET, null);

            JsonNode root = objectMapper.readTree(response);
            JsonNode items = root.path("items");
            if (items.isArray() && !items.isEmpty()) {
                long categoryId = items.get(0).path("categoryId").asLong(-1);
                if (categoryId > 0) {
                    log.info("Discovered categoryId: {} from existing DE '{}'",
                            categoryId, items.get(0).path("name").asText());
                    return categoryId;
                }
            }
            log.warn("No existing DEs found to discover categoryId");
            return -1;
        } catch (Exception e) {
            log.warn("Failed to auto-discover categoryId: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * Create a Data Extension with the given name, fields, categoryId, and sendable
     * settings.
     *
     * @param name              Name of the Data Extension
     * @param fields            List of field definitions
     * @param categoryId        Folder category ID (0 = auto-discover from existing
     *                          DEs)
     * @param isSendable        Whether the DE should be sendable
     * @param sendableFieldName The DE field name to use for the send relationship
     *                          (e.g. "SubscriberKey")
     * @return JSON string result of the operation
     */
    public String createDataExtension(String name, List<Map<String, Object>> fields, long categoryId,
            boolean isSendable, String sendableFieldName) {
        if (isDemoMode()) {
            return demoCreateDataExtension(name, fields);
        }

        try {
            String url = getBaseUri() + "/data/v1/customobjects";

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("name", name);
            payload.put("customerKey", name.replaceAll("\\s+", "_"));

            // Use provided categoryId, or auto-discover from existing DEs
            long effectiveCategoryId = categoryId > 0 ? categoryId : discoverCategoryId();
            if (effectiveCategoryId > 0) {
                payload.put("categoryId", effectiveCategoryId);
                log.info("Using categoryId: {} for DE '{}'", effectiveCategoryId, name);
            }

            // Sendable DE configuration
            if (isSendable) {
                payload.put("isSendable", true);
                payload.put("isTestable", true);

                // Determine which DE field to use for the send relationship
                String deField = sendableFieldName;
                if (deField == null || deField.isBlank()) {
                    // Default: use the first primary key field
                    for (Map<String, Object> field : fields) {
                        if (Boolean.TRUE.equals(field.get("isPrimaryKey"))) {
                            deField = (String) field.get("name");
                            break;
                        }
                    }
                    if (deField == null && !fields.isEmpty()) {
                        deField = (String) fields.get(0).get("name");
                    }
                }

                if (deField != null) {
                    // sendableDataExtensionField — the field in this DE for the relationship
                    // ObjectNode sendableDeField = objectMapper.createObjectNode();
                    // sendableDeField.put("name", deField);
                    // sendableDeField.put("fieldType", "Text");
                    payload.put("sendableCustomObjectField", deField);

                    // sendableSubscriberField — the subscriber attribute to relate to
                    // ObjectNode sendableSubField = objectMapper.createObjectNode();
                    // sendableSubField.put("name", "_SubscriberKey");
                    // sendableSubField.put("referenceType", "CustomerKey");
                    payload.put("sendableSubscriberField", "_SubscriberKey");

                    log.info("DE '{}' set as sendable with field '{}' → _SubscriberKey", name, deField);
                }
            }

            ArrayNode columns = payload.putArray("fields");
            int ordinal = 0;
            for (Map<String, Object> field : fields) {
                ObjectNode col = columns.addObject();
                col.put("name", (String) field.get("name"));

                // Validate and normalize field type — default to Text
                String fieldType = (String) field.getOrDefault("type", "Text");
                fieldType = normalizeFieldType(fieldType);
                col.put("type", fieldType);

                // Required SFMC field properties
                col.put("maskType", "None");
                col.put("storageType", "Plain");
                col.put("description", "");
                col.put("ordinal", ordinal++);

                boolean isPrimaryKey = (Boolean) field.getOrDefault("isPrimaryKey", false);
                boolean isRequired = (Boolean) field.getOrDefault("isRequired", false);
                col.put("isNullable", !(isPrimaryKey || isRequired));
                col.put("isPrimaryKey", isPrimaryKey);
                col.put("isTemplateField", false);
                col.put("isInheritable", true);
                col.put("isOverridable", true);
                col.put("isHidden", false);
                col.put("isReadOnly", false);
                col.put("mustOverride", false);

                // Set length for Text fields
                if (field.containsKey("maxLength")) {
                    col.put("length", ((Number) field.get("maxLength")).intValue());
                } else if ("Text".equals(fieldType)) {
                    col.put("length", 254);
                }
            }

            log.info("Creating DE payload: {}", payload);
            String response = callSfmcApi(url, HttpMethod.POST, payload.toString());
            log.info("Created Data Extension '{}' successfully", name);
            return "✅ Data Extension '" + name + "' created successfully in SFMC!\n\nDetails:\n" + response;

        } catch (Exception e) {
            log.error("Failed to create Data Extension '{}': {}", name, e.getMessage());
            return "❌ Failed to create Data Extension '" + name + "': " + e.getMessage();
        }
    }

    /**
     * Normalize field type to a valid SFMC fieldType.
     * Maps common aliases from LLM output to SFMC-recognized types.
     */
    private String normalizeFieldType(String type) {
        if (type == null || type.isBlank())
            return "Text";
        return switch (type.toLowerCase().trim()) {
            case "text", "string", "varchar", "char" -> "Text";
            case "number", "integer", "int", "long" -> "Number";
            case "decimal", "float", "double" -> "Decimal";
            case "date", "datetime", "timestamp" -> "Date";
            case "boolean", "bool", "bit" -> "Boolean";
            case "emailaddress", "email", "email address" -> "EmailAddress";
            case "phone", "telephone", "tel" -> "Phone";
            case "locale" -> "Locale";
            default -> {
                log.warn("Unknown field type '{}', defaulting to Text", type);
                yield "Text";
            }
        };
    }

    /**
     * Backward-compatible overload without categoryId or sendable settings.
     */
    public String createDataExtension(String name, List<Map<String, Object>> fields) {
        return createDataExtension(name, fields, 0, false, null);
    }

    /**
     * List all Data Extensions in the SFMC account.
     */
    public String listDataExtensions() {
        if (isDemoMode()) {
            return demoListDataExtensions();
        }

        try {
            String url = getBaseUri() + "/data/v1/customobjects?$search=Test123_Lalith123";
            log.info("Calling SFMC list DEs API: {}", url);
            String response = callSfmcApi(url, HttpMethod.GET, null);
            return "📊 **Your Data Extensions:**\n\n" + response;
        } catch (Exception e) {
            log.error("Failed to list Data Extensions: {}", e.getMessage());
            return "❌ Failed to list Data Extensions: " + e.getMessage();
        }
    }

    // ==================== EMAIL ====================

    /**
     * Create an email definition in SFMC.
     */
    public String createEmailDefinition(String name, String subject, String htmlContent) {
        if (isDemoMode()) {
            return demoCreateEmail(name, subject);
        }

        try {
            String url = getBaseUri() + "/messaging/v1/email/definitions";

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("name", name);
            payload.put("definitionKey", name.replaceAll("\\s+", "_"));

            ObjectNode content = payload.putObject("content");
            content.put("customerKey", name.replaceAll("\\s+", "_") + "_content");

            ObjectNode subscriptions = payload.putObject("subscriptions");
            subscriptions.put("dataExtension", "All Subscribers");

            String response = callSfmcApi(url, HttpMethod.POST, payload.toString());
            log.info("Created Email Definition '{}' successfully", name);
            return "✅ Email Definition '" + name + "' created successfully!\n\n" +
                    "**Subject:** " + subject + "\n" + response;

        } catch (Exception e) {
            log.error("Failed to create Email Definition '{}': {}", name, e.getMessage());
            return "❌ Failed to create Email Definition '" + name + "': " + e.getMessage();
        }
    }

    // ==================== AUTOMATIONS ====================

    /**
     * Create an automation in SFMC.
     */
    public String createAutomation(String name, String description, String scheduleFrequency) {
        if (isDemoMode()) {
            return demoCreateAutomation(name, description, scheduleFrequency);
        }

        try {
            String url = getBaseUri() + "/automation/v1/automations";

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("name", name);
            payload.put("description", description);
            payload.put("type", "scheduled");

            ObjectNode schedule = payload.putObject("schedule");
            schedule.put("scheduleType", scheduleFrequency);

            String response = callSfmcApi(url, HttpMethod.POST, payload.toString());
            log.info("Created Automation '{}' successfully", name);
            return "✅ Automation '" + name + "' created successfully!\n\n" + response;

        } catch (Exception e) {
            log.error("Failed to create Automation '{}': {}", name, e.getMessage());
            return "❌ Failed to create Automation '" + name + "': " + e.getMessage();
        }
    }

    // ==================== SUBSCRIBERS ====================

    /**
     * Get subscriber count from the All Subscribers list.
     */
    public String getSubscriberCount() {
        if (isDemoMode()) {
            return demoGetSubscriberCount();
        }

        try {
            // POST /contacts/v1/addresses/count with queryFilter
            String url = getBaseUri() + "/contacts/v1/addresses/count";
            String body = "{\"queryFilter\":{\"hasCriteria\":false}}";
            log.info("Calling SFMC subscriber count API: {}", url);
            String response = callSfmcApi(url, HttpMethod.POST, body);
            return "👥 **Subscriber Data from your SFMC Account:**\n\n" + response;
        } catch (Exception e) {
            log.error("Failed to get subscriber count: {}", e.getMessage());
            return "❌ Failed to get subscriber count: " + e.getMessage();
        }
    }

    // ==================== HTTP HELPER ====================

    private String callSfmcApi(String url, HttpMethod method, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authService.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = body != null
                ? new HttpEntity<>(body, headers)
                : new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("SFMC API returned " + response.getStatusCode() +
                    ": " + response.getBody());
        }

        return response.getBody();
    }

    // ==================== DEMO MODE RESPONSES ====================

    private String demoCreateDataExtension(String name, List<Map<String, Object>> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ **Data Extension '").append(name).append("' created successfully!** (Demo Mode)\n\n");
        sb.append("| Field Name | Data Type | Primary Key | Required |\n");
        sb.append("|------------|-----------|------------|----------|\n");
        for (Map<String, Object> f : fields) {
            sb.append("| ").append(f.get("name"))
                    .append(" | ").append(f.getOrDefault("type", "Text"))
                    .append(" | ").append(Boolean.TRUE.equals(f.get("isPrimaryKey")) ? "✅" : "❌")
                    .append(" | ").append(Boolean.TRUE.equals(f.get("isRequired")) ? "✅" : "❌")
                    .append(" |\n");
        }
        sb.append("\n📎 *Customer Key:* `").append(name.replaceAll("\\s+", "_")).append("`");
        return sb.toString();
    }

    private String demoListDataExtensions() {
        return """
                📊 **Your Data Extensions** (Demo Mode)

                | # | Name | Fields | Records | Created |
                |---|------|--------|---------|---------|
                | 1 | All_Subscribers | 8 | 156,432 | 2024-01-15 |
                | 2 | Holiday_Promo | 4 | 12,845 | 2024-11-20 |
                | 3 | Campaign_Tracking | 6 | 89,200 | 2024-03-08 |
                | 4 | Email_Preferences | 5 | 145,670 | 2024-02-01 |
                | 5 | Purchase_History | 10 | 234,100 | 2024-06-15 |

                **Total:** 5 Data Extensions | 638,247 total records
                """;
    }

    private String demoCreateEmail(String name, String subject) {
        return "✅ **Email Definition '" + name + "' created successfully!** (Demo Mode)\n\n" +
                "**Subject:** " + subject + "\n" +
                "**Definition Key:** `" + name.replaceAll("\\s+", "_") + "`\n" +
                "**Status:** Draft\n" +
                "**Content Type:** HTML + Text\n\n" +
                "The email is now available in Content Builder for editing.";
    }

    private String demoCreateAutomation(String name, String description, String scheduleFrequency) {
        return "✅ **Automation '" + name + "' created successfully!** (Demo Mode)\n\n" +
                "**Description:** " + description + "\n" +
                "**Schedule:** " + scheduleFrequency + "\n" +
                "**Status:** Paused (ready to activate)\n\n" +
                "To activate, go to Automation Studio or ask me to start it.";
    }

    private String demoGetSubscriberCount() {
        return """
                👥 **Subscriber Metrics** (Demo Mode)

                - **Total Subscribers:** 156,432
                - **Active:** 142,890 (91.3%)
                - **Unsubscribed:** 8,542 (5.5%)
                - **Bounced:** 3,200 (2.0%)
                - **Held:** 1,800 (1.2%)

                📈 Growth: +2,340 new subscribers this month
                """;
    }
}
