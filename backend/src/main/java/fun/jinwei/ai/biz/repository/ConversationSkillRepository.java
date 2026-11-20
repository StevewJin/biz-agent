package fun.jinwei.ai.biz.repository;

import fun.jinwei.ai.biz.entity.ConversationSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationSkillRepository extends JpaRepository<ConversationSkill, Long> {

    @Query("SELECT cs FROM ConversationSkill cs JOIN FETCH cs.skill WHERE cs.conversation.id = :conversationId AND cs.enabled = true")
    List<ConversationSkill> findByConversationIdAndEnabledTrue(@Param("conversationId") Long conversationId);

    @Query("SELECT cs FROM ConversationSkill cs JOIN FETCH cs.skill WHERE cs.conversation.id = :conversationId")
    List<ConversationSkill> findByConversationId(@Param("conversationId") Long conversationId);

    @Query("SELECT cs FROM ConversationSkill cs JOIN FETCH cs.skill WHERE cs.conversation.id = :conversationId AND cs.skill.id = :skillId")
    Optional<ConversationSkill> findByConversationIdAndSkillId(@Param("conversationId") Long conversationId, @Param("skillId") Long skillId);

    void deleteByConversationIdAndSkillId(Long conversationId, Long skillId);

    @Query("SELECT COUNT(cs) FROM ConversationSkill cs WHERE cs.conversation.id = :conversationId AND cs.enabled = true")
    long countByConversationIdAndEnabledTrue(@Param("conversationId") Long conversationId);
}
