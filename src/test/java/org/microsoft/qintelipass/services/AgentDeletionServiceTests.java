package org.microsoft.qintelipass.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.microsoft.qintelipass.enums.UserRole;
import org.microsoft.qintelipass.exceptions.ConflictException;
import org.microsoft.qintelipass.exceptions.ForbiddenException;
import org.microsoft.qintelipass.models.Agent;
import org.microsoft.qintelipass.models.Conversation;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.repository.AgentRepository;
import org.microsoft.qintelipass.repository.ConversationRepository;
import org.microsoft.qintelipass.repository.UserAgentRemovalRepository;
import org.microsoft.qintelipass.response.AgentDeleteCheckResponse;
import org.microsoft.qintelipass.response.AgentDeleteResponse;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentDeletionServiceTests {
    @Mock
    private AgentRepository agentRepository;
    @Mock
    private UserAgentRemovalRepository removalRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private UserService userService;

    private AgentDeletionService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AgentDeletionService(
                agentRepository,
                removalRepository,
                conversationRepository,
                userService
        );
        user = user(10L, UserRole.USER);
        lenient().when(userService.getUserById(user.getId())).thenReturn(user);
    }

    @Test
    void ordinaryUserRemovesSystemAgentWithoutDeletingMasterRecord() {
        Agent agent = agent("data-analyst", true, null);
        when(agentRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
        when(conversationRepository.findFirstByAgentIdAndUserIdOrderByCreatedAtAsc(agent.getId(), user.getId()))
                .thenReturn(Optional.empty());

        AgentDeleteResponse response = service.delete(user.getId(), agent.getId());

        assertEquals("REMOVE_FROM_MY_LIST", response.action());
        assertFalse(agent.isDeleted());
        verify(removalRepository).save(any());
        verify(agentRepository, never()).save(agent);
    }

    @Test
    void ordinaryUserDeletesOwnAgent() {
        Agent agent = agent("mine", false, user.getId());
        when(agentRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
        when(conversationRepository.findFirstByAgentIdOrderByCreatedAtAsc(agent.getId()))
                .thenReturn(Optional.empty());

        AgentDeleteResponse response = service.delete(user.getId(), agent.getId());

        assertEquals("DELETE_GLOBALLY", response.action());
        assertTrue(agent.isDeleted());
        verify(agentRepository).save(agent);
    }

    @Test
    void ordinaryUserCannotDeleteAnotherUsersAgent() {
        Agent agent = agent("other", false, 99L);
        when(agentRepository.findById(agent.getId())).thenReturn(Optional.of(agent));

        assertThrows(ForbiddenException.class, () -> service.delete(user.getId(), agent.getId()));
        verify(agentRepository, never()).save(any());
    }

    @Test
    void administratorGloballyDeletesSystemAgent() {
        User admin = user(1L, UserRole.ADMIN);
        Agent agent = agent("system", true, null);
        when(userService.getUserById(admin.getId())).thenReturn(admin);
        when(agentRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
        when(conversationRepository.findFirstByAgentIdOrderByCreatedAtAsc(agent.getId()))
                .thenReturn(Optional.empty());

        AgentDeleteResponse response = service.delete(admin.getId(), agent.getId());

        assertEquals("DELETE_GLOBALLY", response.action());
        assertTrue(agent.isDeleted());
        verify(agentRepository).save(agent);
    }

    @Test
    void referencedAgentReturnsConversationTitleAndCannotBeDeleted() {
        Agent agent = agent("data-analyst", true, null);
        Conversation conversation = new Conversation();
        conversation.setTitle("季度分析");
        when(agentRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
        when(conversationRepository.findFirstByAgentIdAndUserIdOrderByCreatedAtAsc(agent.getId(), user.getId()))
                .thenReturn(Optional.of(conversation));

        AgentDeleteCheckResponse check = service.checkDeletion(user.getId(), agent.getId());

        assertFalse(check.canDelete());
        assertEquals("季度分析", check.conversationTitle());
        assertEquals("该 Agent 在【季度分析】对话中被引用", check.message());
        assertThrows(ConflictException.class, () -> service.delete(user.getId(), agent.getId()));
    }

    @Test
    void repeatedRemovalIsIdempotent() {
        Agent agent = agent("data-analyst", true, null);
        when(agentRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
        when(removalRepository.existsByUserIdAndAgentId(user.getId(), agent.getId())).thenReturn(true);

        AgentDeleteResponse response = service.delete(user.getId(), agent.getId());

        assertTrue(response.alreadyRemovedOrDeleted());
        verify(removalRepository, never()).save(any());
        verify(agentRepository, never()).save(any());
    }

    private User user(Long id, UserRole role) {
        User actor = new User();
        actor.setId(id);
        actor.setName("user-" + id);
        actor.setRole(role);
        return actor;
    }

    private Agent agent(String id, boolean systemPreset, Long createdBy) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setName(id);
        agent.setSystemPreset(systemPreset);
        agent.setCreatedBy(createdBy);
        return agent;
    }
}
