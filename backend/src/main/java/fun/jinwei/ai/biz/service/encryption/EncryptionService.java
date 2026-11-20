package fun.jinwei.ai.biz.service.encryption;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;

/**
 * AES-256-GCM 加密服务
 * 用于加密 LLM API Key 和智能体 AES 密钥相关操作
 */
@Slf4j
@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final SecretKey masterKey;

    /** 本次启动是否新生成主密钥（供启动守卫告警/阻断） */
    private volatile boolean generatedMasterKey;

    /**
     * 主密钥来源优先级：
     * 1. 配置项 {@code app.security.master-key}（Base64 或 Hex，32 字节）
     * 2. {@code ${app.data.dir}/master.key} 持久化文件（首次启动自动生成）
     *
     * <p>不能每次随机生成，否则重启后历史密文（LLM API Key、数据源密码）全部无法解密。</p>
     */
    public EncryptionService(@Value("${app.security.master-key:}") String configuredKey,
                             @Value("${app.data.dir:./data}") String dataDir) {
        this.masterKey = new SecretKeySpec(resolveKeyBytes(configuredKey, dataDir), ALGORITHM);
        log.info("EncryptionService 初始化完成 (主密钥来源: {})",
                isBlank(configuredKey) ? "本地密钥文件" : "配置项 app.security.master-key");
    }

    private byte[] resolveKeyBytes(String configuredKey, String dataDir) {
        if (!isBlank(configuredKey)) {
            return decodeKey(configuredKey);
        }
        Path keyFile = Paths.get(dataDir, "master.key").toAbsolutePath().normalize();
        if (Files.exists(keyFile)) {
            try {
                String content = Files.readString(keyFile, StandardCharsets.UTF_8).trim();
                if (!content.isEmpty()) {
                    return decodeKey(content);
                }
            } catch (IOException e) {
                throw new IllegalStateException("读取主密钥文件失败: " + keyFile, e);
            }
        }
        return generateAndPersistKey(keyFile);
    }

    /** 本次启动是否新生成主密钥 */
    public boolean isGeneratedMasterKey() {
        return generatedMasterKey;
    }

    private byte[] generateAndPersistKey(Path keyFile) {
        generatedMasterKey = true;
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        try {
            Files.createDirectories(keyFile.getParent());
            Files.writeString(keyFile, Base64.getEncoder().encodeToString(keyBytes), StandardCharsets.UTF_8);
            applyOwnerOnlyPermissions(keyFile);
            log.warn("未配置 app.security.master-key，已生成主密钥文件 {}。"
                    + "生产环境请通过环境变量 BIZAGENT_MASTER_KEY 固定配置并妥善备份，否则密钥文件丢失将导致密文不可解密。",
                    keyFile);
        } catch (IOException e) {
            throw new IllegalStateException("主密钥文件写入失败: " + keyFile, e);
        }
        return keyBytes;
    }

    private void applyOwnerOnlyPermissions(Path keyFile) {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(keyFile, perms);
        } catch (Exception ignored) {
            // 非 POSIX 文件系统忽略
        }
    }

    /** 支持 Base64（标准/URL）与 Hex 两种编码；其它形式按 UTF-8 补齐到 32 字节 */
    private byte[] decodeKey(String raw) {
        String value = raw.trim();
        try {
            byte[] base64 = decodeBase64Lenient(value);
            if (base64.length == 32) return base64;
        } catch (IllegalArgumentException ignored) {
            // 继续尝试其它编码
        }
        if (value.matches("[0-9a-fA-F]{64}")) {
            byte[] keyBytes = new byte[32];
            for (int i = 0; i < 32; i++) {
                keyBytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
            }
            return keyBytes;
        }
        byte[] fallback = new byte[32];
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(utf8, 0, fallback, 0, Math.min(utf8.length, 32));
        return fallback;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 加密 */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, spec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes());

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    /** 解密 */
    public String decrypt(String ciphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted));
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }

    /** 生成 AES-256 密钥 (hex 编码，64字符) */
    public String generateAesKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(256);
            SecretKey key = keyGen.generateKey();
            byte[] keyBytes = key.getEncoded();
            StringBuilder hex = new StringBuilder();
            for (byte b : keyBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("密钥生成失败", e);
        }
    }

    /** 用 hex 密钥解密 (用于智能体 AES 解密外部用户名)，兼容 URL 安全 Base64 和标准 Base64 */
    public String decryptWithHexKey(String ciphertext, String hexKey) {
        try {
            byte[] keyBytes = new byte[hexKey.length() / 2];
            for (int i = 0; i < keyBytes.length; i++) {
                keyBytes[i] = (byte) Integer.parseInt(hexKey.substring(i * 2, i * 2 + 2), 16);
            }
            SecretKeySpec key = new SecretKeySpec(keyBytes, ALGORITHM);

            byte[] combined = decodeBase64Lenient(ciphertext);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted));
        } catch (Exception e) {
            log.error("AES 解密失败 (hex key): {}", e.getMessage());
            return null;
        }
    }

    /** 兼容 URL 安全 Base64、标准 Base64、以及 URL 传参时 + 被转成空格的场景 */
    private byte[] decodeBase64Lenient(String input) {
        // 1. 先尝试 URL 安全 Base64 (新格式)
        try {
            return Base64.getUrlDecoder().decode(input);
        } catch (IllegalArgumentException ignored) {
        }
        // 2. 尝试标准 Base64
        try {
            return Base64.getDecoder().decode(input);
        } catch (IllegalArgumentException ignored) {
        }
        // 3. 浏览器把 + 解析为空格，还原后重试标准 Base64
        String restored = input.replace(' ', '+');
        return Base64.getDecoder().decode(restored);
    }

    /** 用 hex 密钥加密 */
    public String encryptWithHexKey(String plaintext, String hexKey) {
        try {
            byte[] keyBytes = new byte[hexKey.length() / 2];
            for (int i = 0; i < keyBytes.length; i++) {
                keyBytes[i] = (byte) Integer.parseInt(hexKey.substring(i * 2, i * 2 + 2), 16);
            }
            SecretKeySpec key = new SecretKeySpec(keyBytes, ALGORITHM);

            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes());

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }
}
