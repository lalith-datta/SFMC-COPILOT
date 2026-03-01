package com.sfmc.copilot.model;

/**
 * Chat response sent back to the frontend.
 */
public class ChatResponse {

    private String text;
    private String model; // Which LLM actually responded: "gpt-4o" or "ollama"

    public ChatResponse() {
    }

    public ChatResponse(String text, String model) {
        this.text = text;
        this.model = model;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
