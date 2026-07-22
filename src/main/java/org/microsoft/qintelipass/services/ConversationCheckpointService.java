package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.entity.Conversation;
import org.microsoft.qintelipass.repository.ConversationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ConversationCheckpointService {
    private final ConversationRepository conversationRepository;

    public ConversationCheckpointService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    @Scheduled(fixedDelayString = "${conversation.checkpoint.interval-ms:300000}")
    @Transactional
    public void checkpointRecentlyActiveConversations() {
        LocalDateTime now = LocalDateTime.now();
        List<Conversation> active = conversationRepository
                .findByStatusAndFirstAnsweredAtIsNotNullAndLastMessageAtAfter(
                        Conversation.STATUS_ACTIVE, now.minusMinutes(30));
        active.forEach(conversation -> conversation.setLastSavedAt(now));
    }
}
