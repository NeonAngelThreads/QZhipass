package org.microsoft.qintelipass.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.request.AgentInvokeRequest;
import org.microsoft.qintelipass.request.CreateAgentRequest;
import org.microsoft.qintelipass.response.AgentResponse;
import org.microsoft.qintelipass.response.AgentStreamEvent;
import org.microsoft.qintelipass.response.ApiResponse;
import org.microsoft.qintelipass.response.ConversationTurnResponse;
import org.microsoft.qintelipass.services.AgentInvocationService;
import org.microsoft.qintelipass.services.AgentService;
import org.microsoft.qintelipass.services.ConversationTurnService;
import org.microsoft.qintelipass.services.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

@Slf4j
@RestController
@RequestMapping("api/v1/agents")
public class AgentController {
    private static final int STREAM_CHUNK_CODE_POINTS = 48;

    private final AgentService agentService;
    private final AgentInvocationService agentInvocationService;
    private final ConversationTurnService conversationTurnService;
    private final CurrentUserService currentUserService;

    public AgentController(
            AgentService agentService,
            AgentInvocationService agentInvocationService,
            ConversationTurnService conversationTurnService,
            CurrentUserService currentUserService
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
        Long userId = currentUserService.requireUserId(httpRequest);
        AgentResponse agent = agentService.createAgent(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Agent创建成功", agent));
    }

    @GetMapping
    public ApiResponse<List<AgentResponse>> listAgents(HttpServletRequest request) {
        Long userId = currentUserService.requireUserId(request);
        return ApiResponse.ok(agentService.listAgents(userId));
    }

    @PostMapping(value = "/{agentId}/invoke", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentStreamEvent>> invokeAgent(
            @PathVariable Long agentId,
            @Valid @RequestBody AgentInvokeRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = currentUserService.requireUserId(httpRequest);
        return Flux.defer(() -> {
            String agentName = agentInvocationService.requireAgentName(userId, agentId);
            AgentStreamEvent start = AgentStreamEvent.status(
                    "agent_start",
                    "正在调用" + agentName + "来构思回答...",
                    agentName
            );
            Mono<ConversationTurnResponse> invocation = Mono.fromCallable(() ->
                            conversationTurnService.send(
                                    userId,
                                    request.getConversationId(),
                                    request.toConversationTurnRequest(agentId)
                            ))
                    .subscribeOn(Schedulers.boundedElastic());

            return Flux.concat(
                    Flux.just(toSse(start)),
                    invocation.flatMapMany(turn -> responseEvents(turn, agentName))
            );
        }).onErrorResume(exception -> {
            log.warn("Agent invocation failed for userId={}, agentId={}: {}",
                    userId, agentId, exception.getMessage());
            return Flux.just(toSse(AgentStreamEvent.error()));
        });
    }

    @DeleteMapping("/{agentId}")
    public ApiResponse<Void> deleteAgent(
            @PathVariable Long agentId,
            HttpServletRequest request
    ) {
        Long userId = currentUserService.requireUserId(request);
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
