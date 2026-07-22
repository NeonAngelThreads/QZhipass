package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.enums.UserRole;
import org.microsoft.qintelipass.exceptions.ConflictException;
import org.microsoft.qintelipass.exceptions.ForbiddenException;
import org.microsoft.qintelipass.exceptions.NotFoundException;
import org.microsoft.qintelipass.exceptions.UnauthorizedException;
import org.microsoft.qintelipass.models.Agent;
import org.microsoft.qintelipass.models.Conversation;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.models.UserAgentRemoval;
import org.microsoft.qintelipass.repository.AgentRepository;
import org.microsoft.qintelipass.repository.ConversationRepository;
import org.microsoft.qintelipass.repository.UserAgentRemovalRepository;
import org.microsoft.qintelipass.response.AgentDeleteCheckResponse;
import org.microsoft.qintelipass.response.AgentDeleteResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
public class AgentDeletionService {
    private static final String ACTION_REMOVE = "REMOVE_FROM_MY_LIST";
    private static final String ACTION_DELETE = "DELETE_GLOBALLY";

    private final AgentRepository agentRepository;
    private final UserAgentRemovalRepository removalRepository;
    private final ConversationRepository conversationRepository;
    private final UserService userService;

    public AgentDeletionService(
            AgentRepository agentRepository,
            UserAgentRemovalRepository removalRepository,
            ConversationRepository conversationRepository,
            UserService userService
    ) {
        this.agentRepository = agentRepository;
        this.removalRepository = removalRepository;
        this.conversationRepository = conversationRepository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public AgentDeleteCheckResponse checkDeletion(Long userId, String agentId) {
        User actor = requireActor(userId);
        Agent agent = requireAgent(agentId);
        boolean administrator = UserRole.ADMIN.equals(actor.getRole());
        assertPermission(actor, agent, administrator);

        String action = agent.isSystemPreset() && !administrator ? ACTION_REMOVE : ACTION_DELETE;
        boolean alreadyHandled = agent.isDeleted()
                || (ACTION_REMOVE.equals(action)
                && removalRepository.existsByUserIdAndAgentId(userId, agent.getId()));
        if (alreadyHandled) {
            return new AgentDeleteCheckResponse(
                    agent.getId(), agent.getName(), true, false, null, action, true,
                    ACTION_REMOVE.equals(action) ? "该 Agent 已从当前列表移除" : "该 Agent 已删除"
            );
        }

        Optional<Conversation> reference = findReference(userId, agent, administrator);
        if (reference.isPresent()) {
            String title = reference.get().getTitle();
            String message = "该 Agent 在【" + title + "】对话中被引用";
            return new AgentDeleteCheckResponse(
                    agent.getId(), agent.getName(), false, true, title, action, false, message
            );
        }

        return new AgentDeleteCheckResponse(
                agent.getId(), agent.getName(), true, false, null, action, false, "可以删除"
        );
    }

    @Transactional
    public AgentDeleteResponse delete(Long userId, String agentId) {
        AgentDeleteCheckResponse check = checkDeletion(userId, agentId);
        if (!check.canDelete()) {
            throw new ConflictException(check.message());
        }
        if (check.alreadyRemovedOrDeleted()) {
            return new AgentDeleteResponse(check.agentId(), check.action(), true, check.message());
        }

        Agent agent = requireAgent(agentId);
        if (ACTION_REMOVE.equals(check.action())) {
            UserAgentRemoval removal = new UserAgentRemoval();
            removal.setUserId(userId);
            removal.setAgentId(agent.getId());
            removalRepository.save(removal);
            return new AgentDeleteResponse(agent.getId(), check.action(), false, "已从当前 Agent 列表移除");
        }

        agent.setDeleted(true);
        agentRepository.save(agent);
        return new AgentDeleteResponse(agent.getId(), check.action(), false, "Agent 删除成功");
    }

    private User requireActor(Long userId) {
        User user = userId == null ? null : userService.getUserById(userId);
        if (user == null) {
            throw new UnauthorizedException("未登录或登录已失效");
        }
        return user;
    }

    private Agent requireAgent(String agentId) {
        if (!StringUtils.hasText(agentId)) {
            throw new NotFoundException("Agent 不存在");
        }
        return agentRepository.findById(agentId.trim())
                .orElseThrow(() -> new NotFoundException("Agent 不存在"));
    }

    private void assertPermission(User actor, Agent agent, boolean administrator) {
        if (administrator || agent.isSystemPreset()) {
            return;
        }
        if (agent.getCreatedBy() == null || !agent.getCreatedBy().equals(actor.getId())) {
            throw new ForbiddenException("无权删除其他用户创建的 Agent");
        }
    }

    private Optional<Conversation> findReference(Long userId, Agent agent, boolean administrator) {
        if (agent.isSystemPreset() && !administrator) {
            return conversationRepository
                    .findFirstByAgentIdAndUserIdOrderByCreatedAtAsc(agent.getId(), userId);
        }
        return conversationRepository.findFirstByAgentIdOrderByCreatedAtAsc(agent.getId());
    }
}
