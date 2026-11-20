package fun.jinwei.ai.biz.repository;

import fun.jinwei.ai.biz.entity.ToolInvocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ToolInvocationRepository extends JpaRepository<ToolInvocation, Long> {

    List<ToolInvocation> findTop50ByOrderByIdDesc();

    List<ToolInvocation> findTop50ByExtensionCodeOrderByIdDesc(String extensionCode);
}
