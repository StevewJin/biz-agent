package fun.jinwei.ai.biz.repository;

import fun.jinwei.ai.biz.entity.Extension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExtensionRepository extends JpaRepository<Extension, Long> {

    Optional<Extension> findByExtCode(String extCode);

    List<Extension> findAllByOrderByIdAsc();

    List<Extension> findByStatus(String status);
}
