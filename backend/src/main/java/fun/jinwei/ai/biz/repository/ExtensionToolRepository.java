package fun.jinwei.ai.biz.repository;

import fun.jinwei.ai.biz.entity.ExtensionToolEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExtensionToolRepository extends JpaRepository<ExtensionToolEntity, Long> {

    List<ExtensionToolEntity> findByExtensionIdOrderByToolCodeAsc(Long extensionId);

    Optional<ExtensionToolEntity> findByToolKey(String toolKey);

    List<ExtensionToolEntity> findByEnabledTrue();

    void deleteByExtensionId(Long extensionId);
}
