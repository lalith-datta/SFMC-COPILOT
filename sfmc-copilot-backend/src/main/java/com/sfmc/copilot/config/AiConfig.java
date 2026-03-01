package com.sfmc.copilot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI Configuration for dual LLM support.
 * 
 * - Gemini: Handled directly via GeminiService (REST API with API key)
 * - Ollama: Configured via Spring AI ChatClient for local LLM
 * 
 * Gemini is called directly via HTTP because the Vertex AI SDK in Spring AI
 * 1.0.0-M5
 * requires GCP service account auth, while we want to use a simple Google AI
 * Studio API key.
 */
@Configuration
public class AiConfig {

    /**
     * System prompt for Ollama — no mention of functions since Llama 3.2
     * doesn't support function calling properly.
     */
    private static final String OLLAMA_SYSTEM_PROMPT = """
            You are SFMC Copilot, an expert AI assistant for Salesforce Marketing Cloud (SFMC).

            Your capabilities:
            - Advise on creating Data Extensions with custom fields
            - Help design email templates for Content Builder
            - Guide users on setting up automations with schedules
            - Provide subscriber data insights and campaign performance analysis
            - Share best practices for email marketing and SFMC usage

            Guidelines:
            1. Always be clear and specific about what actions you'd recommend
            2. When a user asks you to create something, describe what you'll set up in detail and ask for confirmation
            3. Format responses with markdown for readability (tables, bold, lists)
            4. If a request is ambiguous, ask clarifying questions
            5. Provide field-level details when discussing Data Extensions (name, type, required, primary key)
            6. For automations, specify the schedule and steps clearly
            7. Always be helpful, professional, and concise
            8. Do NOT output raw JSON. Always respond in natural language with markdown formatting.
            """;

    /**
     * ChatClient for Ollama (local LLM) — conversational only.
     * NO function calling registered — Llama 3.2 outputs raw JSON
     * instead of properly executing tool calls.
     */
    @Bean("ollamaChatClient")
    public ChatClient ollamaChatClient(OllamaChatModel ollamaChatModel) {
        return ChatClient.builder(ollamaChatModel)
                .defaultSystem(OLLAMA_SYSTEM_PROMPT)
                .build();
    }
}
