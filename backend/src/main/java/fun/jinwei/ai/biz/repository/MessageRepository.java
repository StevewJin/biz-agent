package fun.jinwei.ai.biz.repository;

import fun.jinwei.ai.biz.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 消息数据仓库
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /** 按对话查找消息 (按序列号升序) */
    List<Message> findByConversationIdOrderBySequenceNumAsc(Long conversationId);

    /** 按对话查找消息 (分页) */
    Page<Message> findByConversationIdOrderBySequenceNumAsc(Long conversationId, Pageable pageable);

    /** 获取对话中最大的消息序号 */
    @Query("SELECT COALESCE(MAX(m.sequenceNum), 0) FROM Message m WHERE m.conversation.id = :conversationId")
    Integer findMaxSequenceNum(@Param("conversationId") Long conversationId);

    /** 统计对话内消息数量 */
    long countByConversationId(Long conversationId);

    /** 批量统计多个对话的消息数，避免列表页 N+1 查询 */
    @Query("SELECT m.conversation.id, COUNT(m) FROM Message m WHERE m.conversation.id IN :ids GROUP BY m.conversation.id")
    List<Object[]> countGroupByConversationIds(@Param("ids") List<Long> ids);
}
