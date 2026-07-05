package com.example.springai.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.springai.tools.TravelTools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final ChatClient chatClient;

    private final TravelTools travelTools;

    public AgentController(ChatClient.Builder chatClientBuilder, TravelTools travelTools) {
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        You are a concise travel assistant. Use tools for real-time or booking facts.
                        When booking, summarize the confirmation code and total price.
                        """)
                .build();
        this.travelTools = travelTools;
    }

    @GetMapping("/tools")
    public Map<String, Object> tools() {
        return Map.of(
                "toolCount", travelTools.toolNames().size(),
                "tools", travelTools.toolNames(),
                "notes", List.of(
                        "POST /api/agent/basic registers the tool catalog directly for one request.",
                        "POST /api/agent/search passes a session id for ToolSearchToolCallingAdvisor."));
    }

    @PostMapping("/basic")
    public ResponseEntity<AgentResponse> basic(@RequestBody AgentRequest request) {
        String answer = chatClient.prompt()
                .user(request.message())
                .tools(travelTools)
                .call()
                .content();

        return ResponseEntity.ok(new AgentResponse(sessionIdOrNew(request.sessionId()), answer));
    }

    @PostMapping("/search")
    public ResponseEntity<AgentResponse> search(@RequestBody AgentRequest request) {
        String sessionId = sessionIdOrNew(request.sessionId());
        String answer = chatClient.prompt()
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, sessionId))
                .user(request.message())
                .tools(travelTools)
                .call()
                .content();

        return ResponseEntity.ok(new AgentResponse(sessionId, answer));
    }

    private static String sessionIdOrNew(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? UUID.randomUUID().toString() : sessionId;
    }

    public record AgentRequest(String message, String sessionId) {
    }

    public record AgentResponse(String sessionId, String answer) {
    }
}
