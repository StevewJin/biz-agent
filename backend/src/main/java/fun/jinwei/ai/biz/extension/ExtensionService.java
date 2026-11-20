package fun.jinwei.ai.biz.extension;

import fun.jinwei.ai.biz.config.ExtensionProperties;
import fun.jinwei.ai.biz.dto.response.ExtensionResponse;
import fun.jinwei.ai.biz.entity.Extension;
import fun.jinwei.ai.biz.entity.ExtensionRendererEntity;
import fun.jinwei.ai.biz.entity.ExtensionToolEntity;
import fun.jinwei.ai.biz.extension.api.manifest.ExtensionManifest;
import fun.jinwei.ai.biz.repository.ExtensionRendererRepository;
import fun.jinwei.ai.biz.repository.ExtensionRepository;
import fun.jinwei.ai.biz.repository.ExtensionToolRepository;
import fun.jinwei.ai.biz.security.ResourceVisibility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 扩展包目录服务：查询、启停、卸载、重载。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtensionService {

    private final ExtensionRepository extensionRepository;
    private final ExtensionToolRepository toolRepository;
    private final ExtensionRendererRepository rendererRepository;
    private final ExtensionRuntime runtime;
    private final ExtensionProperties properties;
    private final ResourceVisibility visibility;

    /**
     * 扩展包列表：按归属与可见性过滤。
     *
     * @param publishStatus 管理员审核时可按状态过滤（pending / approved / rejected）
     */
    public List<ExtensionResponse> list(Long userId, Long teamId, boolean admin, String publishStatus) {
        return extensionRepository.findAllByOrderByIdAsc().stream()
                .filter(ext -> admin || visibility.isVisible(ext.getOwnerId(), ext.getTeamId(),
                        ext.getVisibility(), ext.getPublishStatus(), userId, false))
                .filter(ext -> publishStatus == null || publishStatus.isBlank()
                        || publishStatus.equalsIgnoreCase(ext.getPublishStatus()))
                .map(this::toResponse)
                .toList();
    }

    public ExtensionResponse detail(String extCode, Long userId, boolean admin) {
        return toResponse(requireVisible(extCode, userId, admin));
    }

    /** 启用扩展包 */
    @Transactional
    public ExtensionResponse enable(String extCode, Long userId, boolean admin) {
        Extension extension = requireManageable(extCode, userId, admin);
        extension.setStatus("enabled");
        extension.setErrorMsg(null);
        extensionRepository.save(extension);
        try {
            runtime.load(extension);
        } catch (Exception e) {
            extension.setStatus("error");
            extension.setErrorMsg(e.getMessage());
            extensionRepository.save(extension);
            throw new IllegalStateException("扩展包加载失败: " + e.getMessage(), e);
        }
        return toResponse(extension);
    }

    /** 禁用扩展包（保留安装记录与文件） */
    @Transactional
    public ExtensionResponse disable(String extCode, Long userId, boolean admin) {
        Extension extension = requireManageable(extCode, userId, admin);
        extension.setStatus("disabled");
        extensionRepository.save(extension);
        runtime.unload(extCode);
        return toResponse(extension);
    }

    /** 重载扩展包 */
    @Transactional
    public ExtensionResponse reload(String extCode, Long userId, boolean admin) {
        Extension extension = requireManageable(extCode, userId, admin);
        runtime.load(extension);
        return toResponse(extension);
    }

    /** 卸载扩展包 */
    @Transactional
    public void uninstall(String extCode, Long userId, boolean admin) {
        Extension extension = requireManageable(extCode, userId, admin);
        if (Boolean.TRUE.equals(extension.getSystemPack())) {
            throw new IllegalArgumentException("系统内置扩展包不允许卸载，可改为禁用");
        }
        runtime.unload(extCode);
        toolRepository.deleteByExtensionId(extension.getId());
        rendererRepository.deleteByExtensionId(extension.getId());
        extensionRepository.delete(extension);
        deleteDirectory(Paths.get(properties.getStorageDir()).toAbsolutePath().normalize()
                .resolve(extension.getExtCode()));
        log.info("扩展包已卸载: {}", extCode);
    }

    /** 工具清单（技能包编辑器使用）：仅返回已启用且已加载的扩展包工具 */
    public List<Map<String, Object>> listTools(Long userId, Long teamId, boolean admin) {
        java.util.Set<Long> enabledIds = extensionRepository.findByStatus("enabled").stream()
                .filter(ext -> runtime.isLoaded(ext.getExtCode()))
                .filter(ext -> admin || visibility.isVisible(ext.getOwnerId(), ext.getTeamId(),
                        ext.getVisibility(), ext.getPublishStatus(), userId, false))
                .map(Extension::getId)
                .collect(java.util.stream.Collectors.toSet());
        return toolRepository.findAll().stream()
                .filter(tool -> enabledIds.contains(tool.getExtensionId()))
                .map(tool -> Map.<String, Object>of(
                        "toolKey", tool.getToolKey(),
                        "toolCode", tool.getToolCode(),
                        "extensionId", tool.getExtensionId(),
                        "displayName", tool.getDisplayName() != null ? tool.getDisplayName() : tool.getToolCode(),
                        "description", tool.getDescription() != null ? tool.getDescription() : "",
                        "toolKind", tool.getToolKind(),
                        "riskLevel", tool.getRiskLevel(),
                        "anonymousAllowed", tool.getAnonymousAllowed(),
                        "renderType", tool.getRenderType() != null ? tool.getRenderType() : ""))
                .toList();
    }

    /** 前端渲染器清单（前端启动时拉取） */
    public List<Map<String, Object>> listRenderers() {
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (ExtensionRendererEntity renderer : rendererRepository.findAll()) {
            Extension extension = extensionRepository.findById(renderer.getExtensionId()).orElse(null);
            if (extension == null || !"enabled".equals(extension.getStatus())) {
                continue;
            }
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("renderType", renderer.getRenderType());
            item.put("extCode", extension.getExtCode());
            item.put("version", extension.getVersion());
            item.put("entry", renderer.getEntry() != null && !renderer.getEntry().isBlank()
                    ? renderer.getEntry() : extension.getEntryFile());
            item.put("enabled", renderer.getEnabled());
            result.add(item);
        }
        return result;
    }

    /** 提交上架申请：进入待审核 */
    @Transactional
    public ExtensionResponse submitPublish(String extCode, Long userId, boolean admin) {
        Extension extension = require(extCode);
        if (!visibility.canPublish(extension.getOwnerId(), extension.getTeamId(), userId, admin)) {
            throw new AccessDeniedException("仅归属人或团队管理者可申请上架");
        }
        extension.setVisibility(ResourceVisibility.VISIBILITY_PUBLIC);
        extension.setPublishStatus(ResourceVisibility.STATUS_PENDING);
        extension.setRejectReason(null);
        extensionRepository.save(extension);
        log.info("扩展包提交上架: {} by {}", extCode, userId);
        return toResponse(extension);
    }

    /** 撤回上架申请，退回个人可见 */
    @Transactional
    public ExtensionResponse withdrawPublish(String extCode, Long userId, boolean admin) {
        Extension extension = require(extCode);
        if (!visibility.canPublish(extension.getOwnerId(), extension.getTeamId(), userId, admin)) {
            throw new AccessDeniedException("仅归属人或团队管理者可撤回上架");
        }
        extension.setVisibility(ResourceVisibility.defaultVisibility(extension.getTeamId()));
        extension.setPublishStatus(ResourceVisibility.STATUS_DRAFT);
        extension.setRejectReason(null);
        extensionRepository.save(extension);
        return toResponse(extension);
    }

    /** 管理员审核 */
    @Transactional
    public ExtensionResponse review(String extCode, boolean approve, String reason) {
        Extension extension = require(extCode);
        if (!ResourceVisibility.STATUS_PENDING.equalsIgnoreCase(extension.getPublishStatus())) {
            throw new IllegalArgumentException("该扩展包没有待审核的上架申请");
        }
        if (approve) {
            extension.setVisibility(ResourceVisibility.VISIBILITY_PUBLIC);
            extension.setPublishStatus(ResourceVisibility.STATUS_APPROVED);
            extension.setRejectReason(null);
            extension.setPublishedAt(java.time.LocalDateTime.now());
        } else {
            extension.setVisibility(ResourceVisibility.defaultVisibility(extension.getTeamId()));
            extension.setPublishStatus(ResourceVisibility.STATUS_REJECTED);
            extension.setRejectReason(reason != null && !reason.isBlank() ? reason : "未通过审核");
        }
        extensionRepository.save(extension);
        log.info("扩展包审核完成: {} approve={}", extCode, approve);
        return toResponse(extension);
    }

    /** 校验当前用户可使用该扩展包（绑定智能体 / 技能引用时使用） */
    public Extension requireUsable(String extCode, Long userId, boolean admin) {
        return requireVisible(extCode, userId, admin);
    }

    private Extension requireVisible(String extCode, Long userId, boolean admin) {
        Extension extension = require(extCode);
        if (!visibility.isVisible(extension.getOwnerId(), extension.getTeamId(),
                extension.getVisibility(), extension.getPublishStatus(), userId, admin)) {
            throw new AccessDeniedException("无权访问该扩展包: " + extCode);
        }
        return extension;
    }

    private Extension requireManageable(String extCode, Long userId, boolean admin) {
        Extension extension = require(extCode);
        if (Boolean.TRUE.equals(extension.getSystemPack())) {
            if (!admin) {
                throw new AccessDeniedException("系统内置扩展包仅管理员可操作");
            }
            return extension;
        }
        if (!visibility.canManage(extension.getOwnerId(), extension.getTeamId(), userId, admin)) {
            throw new AccessDeniedException("无权操作该扩展包: " + extCode);
        }
        return extension;
    }

    private Extension require(String extCode) {
        return extensionRepository.findByExtCode(extCode)
                .orElseThrow(() -> new IllegalArgumentException("扩展包不存在: " + extCode));
    }

    private ExtensionResponse toResponse(Extension extension) {
        ExtensionManifest manifest = parseManifestQuietly(extension);
        List<ExtensionToolEntity> tools = toolRepository.findByExtensionIdOrderByToolCodeAsc(extension.getId());
        List<ExtensionRendererEntity> renderers = rendererRepository.findByExtensionId(extension.getId());

        return ExtensionResponse.builder()
                .id(extension.getId())
                .extCode(extension.getExtCode())
                .extName(extension.getExtName())
                .version(extension.getVersion())
                .packType(extension.getPackType())
                .status(extension.getStatus())
                .systemPack(extension.getSystemPack())
                .ownerId(extension.getOwnerId())
                .teamId(extension.getTeamId())
                .visibility(extension.getVisibility())
                .publishStatus(extension.getPublishStatus())
                .rejectReason(extension.getRejectReason())
                .publishedAt(extension.getPublishedAt())
                .entryFile(extension.getEntryFile())
                .sha256(extension.getSha256())
                .errorMsg(extension.getErrorMsg())
                .installedAt(extension.getInstalledAt())
                .updatedAt(extension.getUpdatedAt())
                .loaded(runtime.isLoaded(extension.getExtCode()))
                .vendor(manifest != null ? manifest.getVendor() : null)
                .description(manifest != null ? manifest.getDescription() : null)
                .icon(manifest != null && manifest.getUi() != null ? manifest.getUi().getIcon() : null)
                .tools(tools.stream().map(tool -> ExtensionResponse.ToolView.builder()
                        .toolKey(tool.getToolKey())
                        .toolCode(tool.getToolCode())
                        .displayName(tool.getDisplayName())
                        .description(tool.getDescription())
                        .toolKind(tool.getToolKind())
                        .riskLevel(tool.getRiskLevel())
                        .anonymousAllowed(tool.getAnonymousAllowed())
                        .requiresConfirm(tool.getRequiresConfirm())
                        .renderType(tool.getRenderType())
                        .parameterSchema(tool.getParameterSchema())
                        .bindingJson(tool.getBindingJson())
                        .build()).toList())
                .renderers(renderers.stream().map(renderer -> ExtensionResponse.RendererView.builder()
                        .renderType(renderer.getRenderType())
                        .entry(renderer.getEntry())
                        .supportedTools(renderer.getSupportedTools())
                        .build()).toList())
                .build();
    }

    private ExtensionManifest parseManifestQuietly(Extension extension) {
        try {
            return runtime.parseManifest(extension);
        } catch (Exception e) {
            return null;
        }
    }

    private void deleteDirectory(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 忽略
                }
            });
        } catch (IOException e) {
            log.warn("删除扩展包目录失败: {} - {}", path, e.getMessage());
        }
    }
}
