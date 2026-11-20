package fun.jinwei.ai.biz.repository;

import fun.jinwei.ai.biz.entity.ApiEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, Long> {

    Optional<ApiEndpoint> findByApiCode(String apiCode);

    List<ApiEndpoint> findByEnabledTrue();

    List<ApiEndpoint> findAllByOrderByIdDesc();
}
