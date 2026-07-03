package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.entity.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 对话主表查询入口，Service 通过它按用户范围读取和校验对话。
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    // 查询当前用户最近对话列表，避免返回其他用户的数据。
    List<Conversation> findByUserIdOrderByLastMessageAtDescUpdatedAtDesc(String userId, Pageable pageable);

    // 可用于 conversationId + userId 的归属校验。
    Optional<Conversation> findByIdAndUserId(Long id, String userId);
}
