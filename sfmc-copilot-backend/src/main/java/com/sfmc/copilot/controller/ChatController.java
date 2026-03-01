package com.sfmc.copilot.controller;

import com.sfmc.copilot.model.ChatRequest;
import com.sfmc.copilot.model.ChatResponse;
import com.sfmc.copilot.service.AiGatewayService;
import com.sfmc.copilot.service.AiGatewayService.RoutingResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST controller for the chat endpoint.
 * 
 * Delegates to the AiGatewayService for intelligent routing
 * between GPT-4o and Ollama based on message complexity.
 * The LLMs have access to SFMC tools via function calling.
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final AiGatewayService aiGatewayService;

    public ChatController(AiGatewayService aiGatewayService) {
        this.aiGatewayService = aiGatewayService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Received chat request: model={}, message='{}'",
                request.getPreferredModel(),
                request.getMessage().substring(0, Math.min(100, request.getMessage().length())));

        try {
            RoutingResult result = aiGatewayService.route(
                    request.getMessage(),
                    request.getConversationId() != null ? request.getConversationId() : "default",
                    request.getPreferredModel());

            log.info("Response generated via {} ({} chars)", result.model(), result.text().length());
            return ResponseEntity.ok(new ChatResponse(result.text(), result.model()));

        } catch (Exception e) {
            log.error("Chat request failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new ChatResponse(
                            "❌ Error: " + e.getMessage() +
                                    "\n\nPlease check that the AI services are running and properly configured.",
                            "error"));
        }
    }

    /**
     * Health check endpoint to verify the backend is running.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\": \"UP\", \"service\": \"SFMC Copilot Backend\"}");
    }
}
