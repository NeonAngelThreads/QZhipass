package org.microsoft.qintelipass.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.dtos.request.CreateAgentRequest;
import org.microsoft.qintelipass.dtos.response.AgentResponse;
import org.microsoft.qintelipass.dtos.response.AgentStreamEvent;
import org.microsoft.qintelipass.dtos.response.ApiResponse;
import org.microsoft.qintelipass.dtos.response.ConversationTurnResponse;
import org.microsoft.qintelipass.security.SecurityUtil;
import org.microsoft.qintelipass.services.agent.AgentInvocationService;
import org.microsoft.qintelipass.services.agent.AgentService;
import org.microsoft.qintelipass.services.chat.ConversationTurnService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("api/v1/agents")
public class AgentController {
    private static final int STREAM_CHUNK_CODE_POINTS = 48;
    private final AgentService agentService;
    private final AgentInvocationService agentInvocationService;
    private final ConversationTurnService conversationTurnService;
    private final SecurityUtil currentUserService;
    public AgentController(
            AgentService agentService,
            AgentInvocationService agentInvocationService,
            ConversationTurnService conversationTurnService,
            SecurityUtil currentUserService
    ) {
        this.agentService = agentService;
        this.agentInvocationService = agentInvocationService;
        this.conversationTurnService = conversationTurnService;
        this.currentUserService = currentUserService;
    }
    @PostMapping
    public ResponseEntity<ApiResponse<AgentResponse>> createAgent(
            @RequestBody CreateAgentRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = currentUserService.getCurrentUserId();
        AgentResponse agent = agentService.createAgent(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Agent创建成功", agent));
    }
    @GetMapping
    public ApiResponse<List<AgentResponse>> listAgents(HttpServletRequest request) {
        Long userId = currentUserService.getCurrentUserId();
        return ApiResponse.ok(agentService.listAgents(userId));
    }
    @DeleteMapping("/{agentId}")
    public ApiResponse<Void> deleteAgent(
            @PathVariable Long agentId,
            HttpServletRequest request
    ) {
        Long userId = currentUserService.getCurrentUserId();
        agentService.deleteAgent(userId, agentId);
        return ApiResponse.ok("Agent已删除", null);
    }
    private Flux<ServerSentEvent<AgentStreamEvent>> responseEvents(
            ConversationTurnResponse turn,
            String agentName
    ) {
        List<ServerSentEvent<AgentStreamEvent>> events = new ArrayList<>();
        events.add(toSse(AgentStreamEvent.status("answer_start", "正在整理结果。", agentName)));
        for (String chunk : splitContent(turn.assistantMessage().content())) {
            events.add(toSse(AgentStreamEvent.content(chunk, agentName)));
        }
        events.add(toSse(AgentStreamEvent.complete(turn, agentName)));
        return Flux.fromIterable(events);
    }

    private List<String> splitContent(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int codePoints = content.codePointCount(0, content.length());
        for (int startPoint = 0; startPoint < codePoints; startPoint += STREAM_CHUNK_CODE_POINTS) {
            int endPoint = Math.min(codePoints, startPoint + STREAM_CHUNK_CODE_POINTS);
            int start = content.offsetByCodePoints(0, startPoint);
            int end = content.offsetByCodePoints(0, endPoint);
            chunks.add(content.substring(start, end));
        }
        return chunks;
    }

    private ServerSentEvent<AgentStreamEvent> toSse(AgentStreamEvent event) {
        return ServerSentEvent.<AgentStreamEvent>builder()
                .event(event.type())
                .data(event)
                .build();
    }
}