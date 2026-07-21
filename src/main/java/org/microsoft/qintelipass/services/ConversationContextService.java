package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.ai.AiChatClient;
import org.microsoft.qintelipass.ai.AiChatMessage;
import org.microsoft.qintelipass.entity.Conversation;
import org.microsoft.qintelipass.entity.ConversationMemory;
import org.microsoft.qintelipass.entity.ConversationMessage;
import org.microsoft.qintelipass.entity.ConversationMessageRole;
import org.microsoft.qintelipass.entity.ConversationMessageStatus;
import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.microsoft.qintelipass.repository.ConversationMemoryRepository;
import org.microsoft.qintelipass.repository.ConversationMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ConversationContextService {
    private static final int MESSAGE_OVERHEAD = 4;
    private static final String MEMORY_PREFIX = "以下是更早对话的摘要，仅作为背景；若与用户最新消息冲突，以最新消息为准：\n";

    private final ConversationMessageRepository messageRepository;
    private final ConversationMemoryRepository memoryRepository;
    private final TokenCounter tokenCounter;
    private final AiChatClient aiChatClient;
    private final String systemPrompt;
    private final int maxContextTokens;
    private final int rawTargetTokens;
    private final int safetyMargin;

    public ConversationContextService(
            ConversationMessageRepository messageRepository,
            ConversationMemoryRepository memoryRepository,
            TokenCounter tokenCounter,
            AiChatClient aiChatClient,
            @Value("${ai.system-prompt:You are a helpful enterprise work assistant.}") String systemPrompt,
            @Value("${ai.context.max-tokens:4000}") int maxContextTokens,
            @Value("${ai.context.raw-tokens:2000}") int rawTargetTokens,
            @Value("${ai.context.safety-margin:160}") int safetyMargin
    ) {
        this.messageRepository = messageRepository;
        this.memoryRepository = memoryRepository;
        this.tokenCounter = tokenCounter;
        this.aiChatClient = aiChatClient;
        this.systemPrompt = systemPrompt;
        this.maxContextTokens = maxContextTokens;
        this.rawTargetTokens = rawTargetTokens;
        this.safetyMargin = safetyMargin;
    }

    public PreparedContext prepare(Conversation conversation, Long currentMessageId, String currentPrompt) {
        List<ConversationMessage> history = messageRepository
                .findByConversation_IdAndStatusOrderByCreatedAtAscIdAsc(
                        conversation.getId(), ConversationMessageStatus.COMPLETED)
                .stream()
                .filter(message -> !message.getId().equals(currentMessageId))
                .toList();
        ConversationMemory memory = memoryRepository.findById(conversation.getId()).orElse(null);
        Long summarizedThrough = memory == null ? null : memory.getSummarizedThroughMessageId();
        List<ConversationMessage> unsummarized = history.stream()
                .filter(message -> summarizedThrough == null || message.getId() > summarizedThrough)
                .toList();

        List<List<ConversationMessage>> units = completeUnits(unsummarized);
        List<List<ConversationMessage>> selected = new ArrayList<>();
        int rawTokens = 0;
        int baseTokens = tokenCost(systemPrompt) + tokenCost(currentPrompt) + safetyMargin;
        if (baseTokens > maxContextTokens) {
            throw new BadRequestException("prompt exceeds the 4000-token context budget.");
        }
        int rawBudget = Math.max(0, Math.min(rawTargetTokens, maxContextTokens - baseTokens));
        for (int index = units.size() - 1; index >= 0; index--) {
            List<ConversationMessage> unit = units.get(index);
            int unitTokens = tokenCost(unit);
            if (rawTokens + unitTokens > rawBudget) {
                break;
            }
            selected.add(unit);
            rawTokens += unitTokens;
        }
        Collections.reverse(selected);
        int evictedUnitCount = units.size() - selected.size();
        List<ConversationMessage> evicted = units.subList(0, evictedUnitCount).stream().flatMap(List::stream).toList();

        int fixedTokens = baseTokens + rawTokens;
        int summaryBudget = Math.max(0,
                maxContextTokens - fixedTokens - tokenCounter.count(MEMORY_PREFIX) - MESSAGE_OVERHEAD);
        String summary = memory == null ? "" : memory.getSummaryContent();
        if (!evicted.isEmpty()) {
            summary = updateSummary(summary, evicted, summaryBudget);
            if (memory == null) {
                memory = new ConversationMemory();
                memory.setConversation(conversation);
            }
            memory.setSummaryContent(summary);
            memory.setSummaryTokenCount(tokenCounter.count(summary));
            memory.setSummarizedThroughMessageId(evicted.getLast().getId());
            memoryRepository.save(memory);
        } else if (tokenCounter.count(summary) > summaryBudget) {
            summary = tokenCounter.truncate(summary, summaryBudget);
        }

        List<AiChatMessage> prompt = new ArrayList<>();
        prompt.add(new AiChatMessage("system", systemPrompt));
        if (StringUtils.hasText(summary) && summaryBudget > 0) {
            prompt.add(new AiChatMessage("system", MEMORY_PREFIX + tokenCounter.truncate(summary, summaryBudget)));
        }
        selected.stream().flatMap(List::stream).forEach(message -> prompt.add(toAiMessage(message)));
        prompt.add(new AiChatMessage("user", currentPrompt));

        while (estimate(prompt) > maxContextTokens && prompt.size() > 2) {
            prompt.remove(1);
            if (prompt.size() > 2 && "assistant".equals(prompt.get(1).role())) {
                prompt.remove(1);
            }
        }
        return new PreparedContext(List.copyOf(prompt), estimate(prompt));
    }

    private String updateSummary(String previousSummary, List<ConversationMessage> evicted, int summaryBudget) {
        if (summaryBudget <= 0) {
            return "";
        }
        StringBuilder material = new StringBuilder();
        if (StringUtils.hasText(previousSummary)) {
            material.append("已有摘要：\n").append(previousSummary).append("\n新增历史：\n");
        }
        for (ConversationMessage message : evicted) {
            material.append(message.getRole().name()).append("：").append(message.getContent()).append('\n');
        }
        int inputBudget = Math.max(200, maxContextTokens - safetyMargin - 120);
        String boundedMaterial = keepTail(material.toString(), inputBudget);
        try {
            String generated = aiChatClient.complete(List.of(
                    new AiChatMessage("system", "将旧对话压缩成事实性摘要，保留任务目标、约束、关键事实、决定和未完成事项，不要编造。"),
                    new AiChatMessage("user", boundedMaterial)
            ), Math.max(32, summaryBudget), 0.1).content();
            return tokenCounter.truncate(generated, summaryBudget);
        } catch (RuntimeException ignored) {
            return tokenCounter.truncate(boundedMaterial, summaryBudget);
        }
    }

    private String keepTail(String value, int maxTokens) {
        if (tokenCounter.count(value) <= maxTokens) {
            return value;
        }
        int codePoints = value.codePointCount(0, value.length());
        int low = 0;
        int high = codePoints;
        while (low < high) {
            int middle = (low + high + 1) / 2;
            int start = value.offsetByCodePoints(0, codePoints - middle);
            if (tokenCounter.count(value.substring(start)) <= maxTokens) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return value.substring(value.offsetByCodePoints(0, codePoints - low));
    }

    private List<List<ConversationMessage>> completeUnits(List<ConversationMessage> messages) {
        List<List<ConversationMessage>> units = new ArrayList<>();
        for (int index = 0; index < messages.size();) {
            ConversationMessage current = messages.get(index);
            if (current.getRole() == ConversationMessageRole.USER && index + 1 < messages.size()
                    && messages.get(index + 1).getRole() == ConversationMessageRole.ASSISTANT) {
                units.add(List.of(current, messages.get(index + 1)));
                index += 2;
            } else {
                units.add(List.of(current));
                index++;
            }
        }
        return units;
    }

    private int tokenCost(List<ConversationMessage> messages) {
        return messages.stream().mapToInt(message -> tokenCost(message.getContent())).sum();
    }

    private int tokenCost(String content) {
        return tokenCounter.count(content) + MESSAGE_OVERHEAD;
    }

    private int estimate(List<AiChatMessage> messages) {
        return messages.stream().mapToInt(message -> tokenCost(message.content())).sum();
    }

    private AiChatMessage toAiMessage(ConversationMessage message) {
        String role = switch (message.getRole()) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case SYSTEM -> "system";
        };
        return new AiChatMessage(role, message.getContent());
    }

    public record PreparedContext(List<AiChatMessage> messages, int estimatedTokens) {
    }
}
