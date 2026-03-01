package com.sfmc.copilot.model;

/**
 * Incoming chat request from the frontend.
 */
public class ChatRequest {

    private String message;
    private String conversationId;
    private String preferredModel; // "auto", "gpt-4o", or "ollama"

    public ChatRequest() {
    }

    public ChatRequest(String message, String conversationId, String preferredModel) {
        this.message = message;
        this.conversationId = conversationId;
        this.preferredModel = preferredModel;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getPreferredModel() {
        return preferredModel;
    }

    public void setPreferredModel(String preferredModel) {
        this.preferredModel = preferredModel;
    }
}
