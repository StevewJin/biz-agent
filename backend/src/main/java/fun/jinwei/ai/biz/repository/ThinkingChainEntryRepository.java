package fun.jinwei.ai.biz.repository;

import fun.jinwei.ai.biz.entity.ThinkingChainEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ThinkingChainEntryRepository extends JpaRepository<ThinkingChainEntry, Long> {

    List<ThinkingChainEntry> findByMessageIdOrderBySequenceNumAsc(Long messageId);

    List<ThinkingChainEntry> findByMessageIdInOrderBySequenceNumAsc(List<Long> messageIds);
}
