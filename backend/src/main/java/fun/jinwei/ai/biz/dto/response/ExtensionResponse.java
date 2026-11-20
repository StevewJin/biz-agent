package fun.jinwei.ai.biz.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 扩展包展示对象。
 */
@Data
@Builder
public class ExtensionResponse {
    private Long id;
    private String extCode;
    private String extName;
    private String version;
    private String packType;
    private String status;
    private Boolean systemPack;
    /** 归属人；为空表示系统内置 */
    private Long ownerId;
    private Long teamId;
    /** personal / team / public */
    private String visibility;
    /** draft / pending / approved / rejected */
    private String publishStatus;
    private String rejectReason;
    private LocalDateTime publishedAt;
    private String entryFile;
    private String sha256;
    private String errorMsg;
    private LocalDateTime installedAt;
    private LocalDateTime updatedAt;
    private boolean loaded;
    private String vendor;
    private String description;
    private String icon;
    private List<ToolView> tools;
    private List<RendererView> renderers;

    @Data
    @Builder
    public static class ToolView {
        private String toolKey;
        private String toolCode;
        private String displayName;
        private String description;
        private String toolKind;
        private String riskLevel;
        private Boolean anonymousAllowed;
        private Boolean requiresConfirm;
        private String renderType;
        private String parameterSchema;
        private String bindingJson;
    }

    @Data
    @Builder
    public static class RendererView {
        private String renderType;
        private String entry;
        private String supportedTools;
    }
}
