package fun.jinwei.ai.biz.repository;

import fun.jinwei.ai.biz.entity.Conversation;
import fun.jinwei.ai.biz.entity.enums.ConversationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 对话数据仓库
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long>,
        JpaSpecificationExecutor<Conversation> {

    /** 按用户查找所有对话 (分页，按更新时间倒序) */
    Page<Conversation> findByUserIdAndStatusNotOrderByUpdatedAtDesc(
            Long userId, ConversationStatus status, Pageable pageable);

    /** 按用户查找所有活跃对话 */
    List<Conversation> findByUserIdAndStatus(Long userId, ConversationStatus status);
}
