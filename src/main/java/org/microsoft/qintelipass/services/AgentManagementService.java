package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.microsoft.qintelipass.exceptions.ForbiddenException;
import org.microsoft.qintelipass.exceptions.NotFoundException;
import org.microsoft.qintelipass.models.Agent;
import org.microsoft.qintelipass.models.UserAgentLibrary;
import org.microsoft.qintelipass.repository.AgentRepository;
import org.microsoft.qintelipass.repository.UserAgentLibraryRepository;
import org.microsoft.qintelipass.request.CreateAgentRequest;
import org.microsoft.qintelipass.response.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Stream;

@Service
public class AgentManagementService {
    public static final int MAX_PERSONAL_AGENTS = 10;
    public static final String REMOVE_FROM_USER_LIBRARY = "REMOVE_FROM_USER_LIBRARY";
    public static final String DELETE_USER_AGENT = "DELETE_USER_AGENT";

    private final AgentRepository agentRepository;
    private final UserAgentLibraryRepository libraryRepository;

    public AgentManagementService(
            AgentRepository agentRepository,
            UserAgentLibraryRepository libraryRepository
    ) {
        this.agentRepository = agentRepository;
        this.libraryRepository = libraryRepository;
    }

    @Transactional
    public AgentResponse create(Long userId, CreateAgentRequest request) {
        requireUserId(userId);
        if (agentRepository.countByDeletedFalseAndCreatedBy(userId) >= MAX_PERSONAL_AGENTS) {
            throw new BadRequestException("每个用户最多创建10个Agent");
        }
        String name = request.getName().trim();
        if (agentRepository.existsByDeletedFalseAndCreatedByAndNameIgnoreCase(userId, name)) {
            throw new BadRequestException("Agent名称已存在");
        }

        Agent agent = new Agent();
        agent.setName(name);
        agent.setPrompt(request.getPrompt().trim());
        agent.setCategory("自定义");
        agent.setCreatedBy(userId);
        return toResponse(agentRepository.save(agent));
    }

    @Transactional(readOnly = true)
    public List<AgentSummaryResponse> listVisible(Long userId, String keyword) {
        requireUserId(userId);
        Set<Long> libraryIds = libraryAgentIds(userId);
        Stream<Agent> personal = agentRepository
                .findByDeletedFalseAndCreatedByOrderByCreatedAtDesc(userId)
                .stream();
        Stream<Agent> presets = agentRepository
                .findByDeletedFalseAndCreatedByIsNullOrderByIdAsc()
                .stream()
                .filter(agent -> libraryIds.contains(agent.getId()));
        return Stream.concat(personal, presets)
                .filter(agent -> matches(agent, keyword))
                .map(agent -> toSummary(agent, userId, libraryIds))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AgentResponse> listPersonal(Long userId) {
        requireUserId(userId);
        return agentRepository.findByDeletedFalseAndCreatedByOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AgentResponse> listPresets() {
        return agentRepository.findByDeletedFalseAndCreatedByIsNullOrderByIdAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AgentSummaryResponse> listCatalog(Long userId, String keyword) {
        requireUserId(userId);
        Set<Long> libraryIds = libraryAgentIds(userId);
        return agentRepository.findByDeletedFalseAndCreatedByIsNullOrderByIdAsc()
                .stream()
                .filter(agent -> matches(agent, keyword))
                .map(agent -> toSummary(agent, userId, libraryIds))
                .toList();
    }

    @Transactional(readOnly = true)
    public long count(Long userId) {
        requireUserId(userId);
        return agentRepository.countByDeletedFalseAndCreatedBy(userId)
                + libraryRepository.countByUserId(userId);
    }

    @Transactional
    public AgentLibraryResponse addToLibrary(Long userId, Long agentId) {
        requireUserId(userId);
        Agent agent = requireAgent(agentId);
        if (!agent.isSystemPreset()) {
            throw new BadRequestException("只有系统预置Agent可以加入用户库");
        }
        if (libraryRepository.existsByUserIdAndAgentId(userId, agentId)) {
            return new AgentLibraryResponse(agentId, agent.getName(), false, true);
        }
        libraryRepository.save(new UserAgentLibrary(userId, agentId));
        return new AgentLibraryResponse(agentId, agent.getName(), true, false);
    }

    @Transactional(readOnly = true)
    public AgentDeleteCheckResponse getDeleteCheck(Long userId, Long agentId) {
        requireUserId(userId);
        Agent agent = requireAgentIncludingDeleted(agentId);
        if (agent.isSystemPreset()) {
            boolean inLibrary = libraryRepository.existsByUserIdAndAgentId(userId, agentId);
            return new AgentDeleteCheckResponse(
                    agentId,
                    agent.getName(),
                    REMOVE_FROM_USER_LIBRARY,
                    inLibrary,
                    inLibrary ? null : "NOT_IN_LIBRARY",
                    inLibrary ? null : "Agent不在当前用户库中",
                    List.of(),
                    "确认从我的Agent中移除「" + agent.getName() + "」？"
            );
        }
        requireOwner(userId, agent);
        return new AgentDeleteCheckResponse(
                agentId,
                agent.getName(),
                DELETE_USER_AGENT,
                !agent.isDeleted(),
                agent.isDeleted() ? "ALREADY_DELETED" : null,
                agent.isDeleted() ? "Agent已删除" : null,
                List.of(),
                "确认删除个人Agent「" + agent.getName() + "」？"
        );
    }

    @Transactional
    public AgentDeleteResultResponse delete(Long userId, Long agentId) {
        requireUserId(userId);
        Agent agent = requireAgentIncludingDeleted(agentId);
        if (agent.isSystemPreset()) {
            boolean existed = libraryRepository.existsByUserIdAndAgentId(userId, agentId);
            if (existed) {
                libraryRepository.deleteByUserIdAndAgentId(userId, agentId);
            }
            return new AgentDeleteResultResponse(
                    agentId, agent.getName(), REMOVE_FROM_USER_LIBRARY, existed, !existed);
        }
        requireOwner(userId, agent);
        if (agent.isDeleted()) {
            return new AgentDeleteResultResponse(
                    agentId, agent.getName(), DELETE_USER_AGENT, false, true);
        }
        agent.setDeleted(true);
        agentRepository.save(agent);
        return new AgentDeleteResultResponse(
                agentId, agent.getName(), DELETE_USER_AGENT, true, false);
    }

    @Transactional(readOnly = true)
    public Agent requireInvokableAgent(Long userId, Long agentId) {
        requireUserId(userId);
        Agent agent = requireAgent(agentId);
        if (agent.isSystemPreset()) {
            if (!libraryRepository.existsByUserIdAndAgentId(userId, agentId)) {
                throw new ForbiddenException("请先将系统Agent加入个人工作台");
            }
            return agent;
        }
        requireOwner(userId, agent);
        return agent;
    }

    private Agent requireAgent(Long agentId) {
        Agent agent = requireAgentIncludingDeleted(agentId);
        if (agent.isDeleted()) {
            throw new NotFoundException("Agent不存在");
        }
        return agent;
    }

    private Agent requireAgentIncludingDeleted(Long agentId) {
        if (agentId == null || agentId <= 0) {
            throw new BadRequestException("agentId格式无效");
        }
        return agentRepository.findById(agentId)
                .orElseThrow(() -> new NotFoundException("Agent不存在"));
    }

    private void requireOwner(Long userId, Agent agent) {
        if (!Objects.equals(userId, agent.getCreatedBy())) {
            throw new ForbiddenException("无权管理该Agent");
        }
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new ForbiddenException("未登录或登录已失效");
        }
    }

    private Set<Long> libraryAgentIds(Long userId) {
        Set<Long> ids = new HashSet<>();
        libraryRepository.findByUserId(userId).forEach(item -> ids.add(item.getAgentId()));
        return ids;
    }

    private boolean matches(Agent agent, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        return agent.getName().toLowerCase(Locale.ROOT).contains(normalized)
                || (agent.getCategory() != null
                && agent.getCategory().toLowerCase(Locale.ROOT).contains(normalized));
    }

    private AgentSummaryResponse toSummary(Agent agent, Long userId, Set<Long> libraryIds) {
        boolean inLibrary = agent.isSystemPreset() && libraryIds.contains(agent.getId());
        boolean owned = Objects.equals(userId, agent.getCreatedBy());
        return new AgentSummaryResponse(
                agent.getId(),
                agent.getName(),
                agent.getAgentType(),
                agent.isSystemPreset() ? REMOVE_FROM_USER_LIBRARY : DELETE_USER_AGENT,
                agent.isSystemPreset() ? inLibrary : owned,
                inLibrary
        );
    }

    private AgentResponse toResponse(Agent agent) {
        return new AgentResponse(
                agent.getId(),
                agent.getName(),
                agent.getPrompt(),
                agent.getCategory(),
                agent.getAgentType(),
                agent.getCreatedAt()
        );
    }
}
