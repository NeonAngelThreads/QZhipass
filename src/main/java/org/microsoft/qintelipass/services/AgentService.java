package org.microsoft.qintelipass.services;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.agent.runtime.AgentRuntimeCacheService;
import org.microsoft.qintelipass.agent.runtime.AgentRuntimeConfigAssembler;
import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.microsoft.qintelipass.exceptions.ConflictException;
import org.microsoft.qintelipass.exceptions.NotFoundException;
import org.microsoft.qintelipass.models.PublicAgentTemplate;
import org.microsoft.qintelipass.models.UserAgent;
import org.microsoft.qintelipass.repository.PublicAgentTemplateRepository;
import org.microsoft.qintelipass.repository.UserAgentRepository;
import org.microsoft.qintelipass.request.CreateAgentRequest;
import org.microsoft.qintelipass.response.AgentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AgentService {

    private static final int MAX_AGENT_NAME_LENGTH = 20;
    private static final int MAX_AGENTS_PER_USER = 10;
    // 仅允许中英文、数字、下划线、短横线
    private static final Pattern AGENT_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+$");

    private final UserAgentRepository userAgentRepository;
    private final PublicAgentTemplateRepository publicTemplateRepository;
    private final AgentRuntimeConfigAssembler runtimeConfigAssembler;
    private final AgentRuntimeCacheService runtimeCacheService;

    public AgentService(
            UserAgentRepository userAgentRepository,
            PublicAgentTemplateRepository publicTemplateRepository,
            AgentRuntimeConfigAssembler runtimeConfigAssembler,
            AgentRuntimeCacheService runtimeCacheService
    ) {
        this.userAgentRepository = userAgentRepository;
        this.publicTemplateRepository = publicTemplateRepository;
        this.runtimeConfigAssembler = runtimeConfigAssembler;
        this.runtimeCacheService = runtimeCacheService;
    }

    /**
     * 创建用户自定义Agent
     */
    @Transactional
    public AgentResponse createAgent(Long userId, CreateAgentRequest request) {
        String name = normalizeAgentName(request == null ? null : request.getName());
        String prompt = normalizePrompt(request == null ? null : request.getPrompt());

        // 检查Agent数量上限
        long count = userAgentRepository.countByUserIdAndStatus(userId, UserAgent.STATUS_ACTIVE);
        if (count >= MAX_AGENTS_PER_USER) {
            throw new BadRequestException("创建Agent数量已达上限（" + MAX_AGENTS_PER_USER + "个），无法继续创建");
        }

        // 检查名称是否重复
        userAgentRepository.findByUserIdAndNameAndStatus(userId, name, UserAgent.STATUS_ACTIVE)
                .ifPresent(existing -> {
                    throw new ConflictException("该Agent名称已存在，保存失败，请重新输入");
                });

        UserAgent agent = UserAgent.builder()
                .userId(userId)
                .name(name)
                .prompt(prompt)
                .status(UserAgent.STATUS_ACTIVE)
                .build();

        UserAgent saved = userAgentRepository.saveAndFlush(agent);
        runtimeCacheService.putAfterCommit(runtimeConfigAssembler.fromUserAgent(saved));
        log.info("User {} created agent: id={}, name={}", userId, saved.getId(), saved.getName());
        return AgentResponse.fromUserAgent(saved);
    }

    /**
     * 获取Agent调用列表：包含用户的Agent + 公共模板
     */
    @Transactional(readOnly = true)
    public List<AgentResponse> listAgents(Long userId) {
        List<AgentResponse> result = new ArrayList<>();

        // 1. 公共模板
        List<PublicAgentTemplate> templates = publicTemplateRepository
                .findByStatusOrderByCreatedAtAsc(PublicAgentTemplate.STATUS_ACTIVE);
        for (PublicAgentTemplate t : templates) {
            result.add(AgentResponse.fromTemplate(t.getId(), t.getName(), t.getPrompt()));
        }

        // 2. 用户自己的Agent
        List<UserAgent> userAgents = userAgentRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(userId, UserAgent.STATUS_ACTIVE);
        for (UserAgent a : userAgents) {
            result.add(AgentResponse.fromUserAgent(a));
        }

        return result;
    }

    /**
     * 物理删除用户自己的Agent。
     */
    @Transactional
    public void deleteAgent(Long userId, Long agentId) {
        UserAgent agent = userAgentRepository.findByIdAndUserId(agentId, userId)
                .orElseThrow(() -> new NotFoundException("Agent不存在或不属于当前用户"));

        int deleted = userAgentRepository.hardDeleteByIdAndUserId(agentId, userId);
        if (deleted != 1 || userAgentRepository.existsById(agentId)) {
            throw new IllegalStateException("Agent物理删除失败");
        }
        runtimeCacheService.evictUserAgentAfterCommit(userId, agentId);
        log.info("User {} physically deleted agent: id={}, name={}", userId, agent.getId(), agent.getName());
    }

    // ---- 校验逻辑 ----

    private String normalizeAgentName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BadRequestException("Agent名称不能为空");
        }
        String normalized = name.trim();

        if (normalized.codePointCount(0, normalized.length()) > MAX_AGENT_NAME_LENGTH) {
            throw new BadRequestException("Agent名称长度不得多于" + MAX_AGENT_NAME_LENGTH + "个字符");
        }

        if (!AGENT_NAME_PATTERN.matcher(normalized).matches()) {
            throw new BadRequestException("不得使用特殊字符");
        }

        return normalized;
    }

    private String normalizePrompt(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            throw new BadRequestException("提示词内容不能为空");
        }
        String normalized = prompt.trim();
        if (normalized.codePointCount(0, normalized.length()) > 5_000) {
            throw new BadRequestException("提示词内容过长，最多5000字符");
        }
        return normalized;
    }
}
