package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.entity.ConversationMessage;
import org.microsoft.qintelipass.entity.ConversationMessageRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 对话消息查询入口，支持详情展示、消息计数和标题生成。
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {
    // 按时间顺序读取一个对话下的全部消息。
    List<ConversationMessage> findByConversation_IdOrderByCreatedAtAsc(Long conversationId);

    // 列表页展示每个对话的消息数量。
    long countByConversation_Id(Long conversationId);

    // 判断是否为第一条 Assistant 消息，避免重复自动改标题。
    long countByConversation_IdAndRole(Long conversationId, ConversationMessageRole role);

    // 自动标题优先取第一条 USER 消息作为标题来源。
    Optional<ConversationMessage> findFirstByConversation_IdAndRoleOrderByCreatedAtAsc(
            Long conversationId,
            ConversationMessageRole role
    );
}
