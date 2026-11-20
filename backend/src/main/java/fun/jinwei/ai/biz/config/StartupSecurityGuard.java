package fun.jinwei.ai.biz.config;

import fun.jinwei.ai.biz.service.encryption.EncryptionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 启动期密钥守卫。
 *
 * <p>公开仓库里带着「开发默认值」，如果部署时忘记覆盖，任何人都能用仓库里的值伪造凭证。
 * 因此启动时做三件事：</p>
 * <ol>
 *   <li>JWT 仍是仓库默认值：生产环境直接拒绝启动，开发环境打印醒目告警</li>
 *   <li>本次启动新生成 AES 主密钥：生产环境拒绝启动（多实例会互相解不开），开发环境告警</li>
 *   <li>主密钥来自本地文件：提示生产环境改用环境变量固定并备份</li>
 * </ol>
 */
@Slf4j
@Component
@DependsOn("encryptionService")
@RequiredArgsConstructor
public class StartupSecurityGuard {

    /** 与 application.yml 中的默认值保持一致；一旦改动请同步这里 */
    static final String DEFAULT_JWT_SECRET =
            "Yml6YWdlbnQtZGV2LW9ubHktand0LXNlY3JldC1wbGVhc2UtY2hhbmdlLWluLXByb2R1Y3Rpb24tMDEyMzQ1Njc4OWFiY2RlZg==";

    private static final String LINE = "==========================================================================";

    private final Environment environment;
    private final EncryptionService encryptionService;

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${app.security.master-key:}")
    private String configuredMasterKey;

    /** 在容器刷新阶段执行：配置不合规时直接让应用启动失败，不会出现「Started 之后崩」 */
    @PostConstruct
    void check() {
        boolean production = environment.acceptsProfiles(Profiles.of("prod", "production"));

        checkJwtSecret(production);
        checkMasterKey(production);
    }

    private void checkJwtSecret(boolean production) {
        boolean usingDefault = jwtSecret == null || jwtSecret.isBlank()
                || DEFAULT_JWT_SECRET.equals(jwtSecret.trim());
        if (!usingDefault) {
            return;
        }
        String message = String.join("\n",
                LINE,
                "  [安全告警] JWT 签名密钥仍是仓库内置的默认值",
                "  任何拿到源码的人都可以用它签发管理员令牌。",
                "  修复：设置环境变量 BIZAGENT_JWT_SECRET（Base64，解码后 ≥ 64 字节）",
                "        生成示例：openssl rand -base64 64 | tr -d '\\n'",
                LINE);
        if (production) {
            throw new IllegalStateException(message + "\n已在生产环境（profile=prod）终止启动。");
        }
        log.warn(message);
    }

    private void checkMasterKey(boolean production) {
        if (encryptionService.isGeneratedMasterKey()) {
            String message = String.join("\n",
                    LINE,
                    "  [安全告警] 本次启动新生成了 AES 主密钥（data/master.key）",
                    "  该密钥用于加密 LLM API Key、数据源密码与接口鉴权配置：",
                    "  · 请立即备份该文件，丢失后库内密文不可解密",
                    "  · 多实例部署请改用 BIZAGENT_MASTER_KEY 统一配置，否则各实例互相解不开",
                    LINE);
            if (production) {
                throw new IllegalStateException(message + "\n已在生产环境（profile=prod）终止启动。");
            }
            log.warn(message);
            return;
        }
        if (production && (configuredMasterKey == null || configuredMasterKey.isBlank())) {
            log.warn("生产环境未设置 BIZAGENT_MASTER_KEY，当前使用 data/master.key 文件。"
                    + "多实例部署或容器重建时请改用环境变量固定主密钥。");
        }
    }
}
