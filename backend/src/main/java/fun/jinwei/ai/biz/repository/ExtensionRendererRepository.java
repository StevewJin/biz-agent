package fun.jinwei.ai.biz.repository;

import fun.jinwei.ai.biz.entity.ExtensionRendererEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExtensionRendererRepository extends JpaRepository<ExtensionRendererEntity, Long> {

    List<ExtensionRendererEntity> findByExtensionId(Long extensionId);

    void deleteByExtensionId(Long extensionId);
}
