package fun.jinwei.ai.biz.repository;

import fun.jinwei.ai.biz.entity.ShareSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShareSessionRepository extends JpaRepository<ShareSession, Long> {

    Optional<ShareSession> findByConversationId(Long conversationId);

    List<ShareSession> findByAgentIdOrderByCreatedAtDesc(Long agentId);
}
