package fun.jinwei.ai.biz.controller;

import fun.jinwei.ai.biz.entity.Conversation;
import fun.jinwei.ai.biz.repository.ConversationRepository;
import fun.jinwei.ai.biz.security.AccessGuard;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件导出下载端点
 */
@Slf4j
@RestController
@RequestMapping("/api/exports")
public class ExportController {

    private final Path exportDir;
    private final ConversationRepository conversationRepository;

    public ExportController(@Value("${app.export.dir:/tmp/bizagent/exports}") String exportPath,
                            ConversationRepository conversationRepository) {
        this.exportDir = Paths.get(exportPath).toAbsolutePath().normalize();
        this.conversationRepository = conversationRepository;
    }

    @GetMapping("/{filename:.+}")
    public void download(@PathVariable String filename, Authentication auth,
                         HttpServletResponse response) throws IOException {
        Path filePath = exportDir.resolve(filename).normalize().toAbsolutePath();

        // 安全检查: 确保路径在导出目录内
        if (!filePath.startsWith(exportDir.toAbsolutePath())) {
            log.warn("路径穿越尝试: {} not in {}", filePath, exportDir);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "禁止访问");
            return;
        }

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在或已过期");
            return;
        }

        // 归属校验：文件名前缀为会话 ID，仅会话归属人、智能体归属人或管理员可下载
        if (!canDownload(filename, AccessGuard.userId(auth), AccessGuard.isAdmin(auth))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "无权下载该文件");
            return;
        }

        // 从唯一名称中提取原始文件名 (conversationId_uuid_originalName.ext)
        String originalName = filename;
        int secondUnderscore = filename.indexOf('_', filename.indexOf('_') + 1);
        if (secondUnderscore > 0) {
            originalName = filename.substring(secondUnderscore + 1);
        }

        String contentType = filename.endsWith(".csv")
                ? "text/csv; charset=UTF-8"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

        response.setContentType(contentType);
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(originalName, StandardCharsets.UTF_8));
        response.setContentLengthLong(Files.size(filePath));

        try (OutputStream out = response.getOutputStream()) {
            Files.copy(filePath, out);
            out.flush();
        }

        log.info("文件下载: {} (原始名: {})", filename, originalName);
    }

    private boolean canDownload(String filename, Long userId, boolean admin) {
        if (admin) {
            return true;
        }
        Long conversationId = parseConversationId(filename);
        if (conversationId == null || userId == null) {
            return false;
        }
        return conversationRepository.findById(conversationId)
                .map(conversation -> isRelated(conversation, userId))
                .orElse(false);
    }

    private Long parseConversationId(String filename) {
        int underscore = filename.indexOf('_');
        if (underscore <= 0) {
            return null;
        }
        try {
            return Long.parseLong(filename.substring(0, underscore));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 会话归属人，或会话所绑定智能体的归属人（匿名分享会话归属智能体负责人） */
    private boolean isRelated(Conversation conversation, Long userId) {
        if (conversation.getUser() != null && userId.equals(conversation.getUser().getId())) {
            return true;
        }
        return conversation.getAgent() != null
                && conversation.getAgent().getOwner() != null
                && userId.equals(conversation.getAgent().getOwner().getId());
    }
}
