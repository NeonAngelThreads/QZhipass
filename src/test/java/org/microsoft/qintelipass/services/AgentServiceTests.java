package org.microsoft.qintelipass.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.microsoft.qintelipass.agent.runtime.AgentRuntimeCacheService;
import org.microsoft.qintelipass.agent.runtime.AgentRuntimeConfigAssembler;
import org.microsoft.qintelipass.exceptions.NotFoundException;
import org.microsoft.qintelipass.models.UserAgent;
import org.microsoft.qintelipass.repository.PublicAgentTemplateRepository;
import org.microsoft.qintelipass.repository.UserAgentRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTests {
    @Mock private UserAgentRepository userAgentRepository;
    @Mock private PublicAgentTemplateRepository publicTemplateRepository;
    @Mock private AgentRuntimeConfigAssembler runtimeConfigAssembler;
    @Mock private AgentRuntimeCacheService runtimeCacheService;

    private AgentService agentService;

    @BeforeEach
    void setUp() {
        agentService = new AgentService(
                userAgentRepository,
                publicTemplateRepository,
                runtimeConfigAssembler,
                runtimeCacheService
        );
    }

    @Test
    void physicallyDeletesOnlyTheCurrentUsersAgentAndEvictsCacheAfterCommit() {
        UserAgent agent = UserAgent.builder()
                .id(91L)
                .userId(1001L)
                .name("测试Agent")
                .prompt("请回答问题")
                .status(UserAgent.STATUS_ACTIVE)
                .build();
        when(userAgentRepository.findByIdAndUserId(91L, 1001L)).thenReturn(Optional.of(agent));
        when(userAgentRepository.hardDeleteByIdAndUserId(91L, 1001L)).thenReturn(1);
        when(userAgentRepository.existsById(91L)).thenReturn(false);

        agentService.deleteAgent(1001L, 91L);

        verify(userAgentRepository).hardDeleteByIdAndUserId(91L, 1001L);
        verify(userAgentRepository, never()).save(any());
        verify(runtimeCacheService).evictUserAgentAfterCommit(1001L, 91L);
    }

    @Test
    void refusesToDeleteAnotherUsersAgent() {
        when(userAgentRepository.findByIdAndUserId(91L, 1001L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> agentService.deleteAgent(1001L, 91L));

        verify(userAgentRepository, never()).hardDeleteByIdAndUserId(anyLong(), anyLong());
        verify(runtimeCacheService, never()).evictUserAgentAfterCommit(anyLong(), anyLong());
    }
}
