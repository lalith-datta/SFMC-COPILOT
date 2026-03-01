package com.sfmc.copilot.tools;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.sfmc.copilot.service.SfmcApiService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.*;
import java.util.function.Function;

/**
 * Spring AI Function Calling definitions for SFMC operations.
 * 
 * Each @Bean returns a java.util.function.Function that the LLM can invoke.
 * The @Description annotation tells the AI what each function does.
 * Record classes define the input schema the LLM must provide.
 */
@Configuration
public class SfmcTools {

    private final SfmcApiService sfmcApiService;

    public SfmcTools(SfmcApiService sfmcApiService) {
        this.sfmcApiService = sfmcApiService;
    }

    // ==================== INPUT RECORDS ====================

    @JsonClassDescription("Request to create a new Data Extension in SFMC")
    public record CreateDataExtensionRequest(
            @JsonProperty(required = true) @JsonPropertyDescription("Name of the Data Extension") String name,

            @JsonProperty(required = true) @JsonPropertyDescription("Comma-separated field definitions in format: fieldName:fieldType:isPrimaryKey:isRequired. "
                    +
                    "Example: SubscriberKey:Text:true:true,FirstName:Text:false:false,Email:EmailAddress:false:true") String fieldDefinitions) {
    }

    @JsonClassDescription("Request to create an email definition in SFMC")
    public record CreateEmailRequest(
            @JsonProperty(required = true) @JsonPropertyDescription("Name of the email definition") String name,

            @JsonProperty(required = true) @JsonPropertyDescription("Email subject line") String subject,

            @JsonPropertyDescription("HTML content for the email body") String htmlContent) {
    }

    @JsonClassDescription("Request to create an automation in SFMC")
    public record CreateAutomationRequest(
            @JsonProperty(required = true) @JsonPropertyDescription("Name of the automation") String name,

            @JsonPropertyDescription("Description of what the automation does") String description,

            @JsonProperty(required = true) @JsonPropertyDescription("Schedule frequency: Daily, Weekly, Monthly, or Hourly") String scheduleFrequency) {
    }

    // No input needed for list/count operations
    public record EmptyRequest() {
    }

    // ==================== FUNCTION BEANS ====================

    @Bean
    @Description("Create a new Data Extension in Salesforce Marketing Cloud with specified fields. " +
            "Each field needs a name, data type (Text, Number, Date, Boolean, EmailAddress), " +
            "and optionally isPrimaryKey and isRequired flags.")
    public Function<CreateDataExtensionRequest, String> createDataExtension() {
        return request -> {
            List<Map<String, Object>> fields = parseFieldDefinitions(request.fieldDefinitions());
            return sfmcApiService.createDataExtension(request.name(), fields);
        };
    }

    @Bean
    @Description("List all Data Extensions in the SFMC account with names, field counts, and record counts.")
    public Function<EmptyRequest, String> listDataExtensions() {
        return request -> sfmcApiService.listDataExtensions();
    }

    @Bean
    @Description("Create a new email definition/template in SFMC with a name, subject line, and optional HTML content.")
    public Function<CreateEmailRequest, String> createEmailDefinition() {
        return request -> sfmcApiService.createEmailDefinition(request.name(), request.subject(),
                request.htmlContent());
    }

    @Bean
    @Description("Create a new automation in SFMC Automation Studio with a name, description, and schedule (Daily, Weekly, Monthly, Hourly).")
    public Function<CreateAutomationRequest, String> createAutomation() {
        return request -> sfmcApiService.createAutomation(request.name(), request.description(),
                request.scheduleFrequency());
    }

    @Bean
    @Description("Get total subscriber count and metrics from the SFMC account including active, unsubscribed, bounced, and held counts.")
    public Function<EmptyRequest, String> getSubscriberCount() {
        return request -> sfmcApiService.getSubscriberCount();
    }

    // ==================== FIELD PARSER ====================

    private List<Map<String, Object>> parseFieldDefinitions(String fieldDefinitions) {
        List<Map<String, Object>> fields = new ArrayList<>();
        if (fieldDefinitions == null || fieldDefinitions.isBlank())
            return fields;

        for (String fieldDef : fieldDefinitions.split(",")) {
            String[] parts = fieldDef.trim().split(":");
            Map<String, Object> field = new HashMap<>();
            field.put("name", parts[0].trim());
            field.put("type", parts.length > 1 ? parts[1].trim() : "Text");
            field.put("isPrimaryKey", parts.length > 2 && Boolean.parseBoolean(parts[2].trim()));
            field.put("isRequired", parts.length > 3 && Boolean.parseBoolean(parts[3].trim()));
            if ("Text".equalsIgnoreCase((String) field.get("type"))) {
                field.put("maxLength", 254);
            }
            fields.add(field);
        }
        return fields;
    }
}
