package com.sfmc.copilot.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory conversation history store.
 * 
 * Maintains a list of messages per conversation ID so the LLM
 * can reference previous exchanges in the same chat session.
 * 
 * Note: This is an in-memory store — conversations are lost on restart.
 * For production, replace with a database-backed implementation.
 */
@Service
public class ConversationStore {

    // Max messages per conversation to avoid token limit issues
    private static final int MAX_HISTORY_SIZE = 20;

    private final Map<String, List<Message>> conversations = new ConcurrentHashMap<>();

    /**
     * Add a message to a conversation's history.
     */
    public void addMessage(String conversationId, String role, String content) {
        conversations.computeIfAbsent(conversationId, k -> new ArrayList<>())
                .add(new Message(role, content));

        // Trim to max size (keep most recent messages)
        List<Message> history = conversations.get(conversationId);
        if (history.size() > MAX_HISTORY_SIZE) {
            conversations.put(conversationId,
                    new ArrayList<>(history.subList(history.size() - MAX_HISTORY_SIZE, history.size())));
        }
    }

    /**
     * Get conversation history for a given conversation ID.
     */
    public List<Message> getHistory(String conversationId) {
        return conversations.getOrDefault(conversationId, List.of());
    }

    /**
     * Clear a conversation (for "New Chat" functionality).
     */
    public void clearConversation(String conversationId) {
        conversations.remove(conversationId);
    }

    /**
     * A single message in the conversation.
     */
    public record Message(String role, String content) {
    }
}
