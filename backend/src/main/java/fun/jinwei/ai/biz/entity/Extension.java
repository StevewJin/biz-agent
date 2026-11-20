package fun.jinwei.ai.biz.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 已安装的扩展包。
 */
@Entity
@Table(name = "ba_extensions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Extension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ext_code", nullable = false, length = 100)
    private String extCode;

    @Column(name = "ext_name", nullable = false, length = 200)
    private String extName;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(name = "pack_type", nullable = false, length = 20)
    @Builder.Default
    private String packType = "declarative";

    @Column(name = "manifest_json", length = 20000)
    private String manifestJson;

    @Column(name = "jar_path", length = 1000)
    private String jarPath;

    @Column(name = "assets_path", length = 1000)
    private String assetsPath;

    @Column(name = "entry_file", length = 500)
    private String entryFile;

    @Column(length = 128)
    private String sha256;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "enabled";

    @Column(name = "system_pack", nullable = false)
    @Builder.Default
    private Boolean systemPack = Boolean.FALSE;

    @Column(name = "error_msg", length = 2000)
    private String errorMsg;

    /** 归属人；为空表示系统内置 */
    @Column(name = "owner_id")
    private Long ownerId;

    /** 归属团队；为空表示个人 */
    @Column(name = "team_id")
    private Long teamId;

    /** 可见范围：personal / team / public */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String visibility = "personal";

    /** 上架状态：draft / pending / approved / rejected */
    @Column(name = "publish_status", nullable = false, length = 20)
    @Builder.Default
    private String publishStatus = "draft";

    /** 审核驳回原因 */
    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    /** 审核通过时间 */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "installed_by")
    private Long installedBy;

    @Column(name = "installed_at", nullable = false)
    @Builder.Default
    private LocalDateTime installedAt = LocalDateTime.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isEnabled() {
        return "enabled".equals(status);
    }
}
