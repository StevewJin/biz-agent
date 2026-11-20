package fun.jinwei.ai.biz.service.share;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import fun.jinwei.ai.biz.service.encryption.EncryptionService;

/**
 * AES 加密服务 - 用于智能体 URL 中的外部用户名加解密
 */
@Service
@RequiredArgsConstructor
public class AesCryptoService {

    private final EncryptionService encryptionService;

    /** 加密外部用户名 */
    public String encrypt(String username, String hexKey) {
        return encryptionService.encryptWithHexKey(username, hexKey);
    }

    /** 解密外部用户名 */
    public String decrypt(String encryptedUser, String hexKey) {
        return encryptionService.decryptWithHexKey(encryptedUser, hexKey);
    }
}
