package fun.jinwei.ai.biz.controller;

import fun.jinwei.ai.biz.config.ExtensionProperties;
import fun.jinwei.ai.biz.entity.Extension;
import fun.jinwei.ai.biz.repository.ExtensionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * 扩展包前端资源服务。
 *
 * <p>扩展包的前端 ESM 产物通过 {@code /ext-assets/{code}/{version}/**} 暴露，
 * 前端在运行时动态 import。同源、只读、可缓存，因此在安全配置中放行。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ExtensionAssetController {

    private static final String PREFIX = "/ext-assets/";

    private final ExtensionRepository extensionRepository;
    private final ExtensionProperties properties;

    @GetMapping("/ext-assets/{code}/{version}/**")
    public ResponseEntity<Resource> asset(@PathVariable String code,
                                          @PathVariable String version,
                                          HttpServletRequest request) {
        String uri = request.getRequestURI();
        String prefix = PREFIX + code + "/" + version + "/";
        if (!uri.startsWith(prefix)) {
            return ResponseEntity.notFound().build();
        }
        String relativePath = uri.substring(prefix.length());
        if (relativePath.isBlank() || relativePath.contains("..")) {
            return ResponseEntity.badRequest().build();
        }

        Optional<Extension> extension = extensionRepository.findByExtCode(code)
                .filter(ext -> ext.getVersion().equals(version));
        if (extension.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Path baseDir = extension.get().getAssetsPath() != null
                ? Paths.get(extension.get().getAssetsPath()).toAbsolutePath().normalize()
                : Paths.get(properties.getStorageDir(), code, version).toAbsolutePath().normalize();
        Path file = baseDir.resolve(relativePath).normalize();
        if (!file.startsWith(baseDir) || !Files.isRegularFile(file)) {
            log.debug("扩展包资源不存在: {}/{}", code, relativePath);
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = mediaType(relativePath);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file));
    }

    private MediaType mediaType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) {
            return MediaType.valueOf("text/javascript; charset=utf-8");
        }
        if (lower.endsWith(".css")) {
            return MediaType.valueOf("text/css; charset=utf-8");
        }
        if (lower.endsWith(".json")) {
            return MediaType.APPLICATION_JSON;
        }
        if (lower.endsWith(".svg")) {
            return MediaType.valueOf("image/svg+xml");
        }
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".woff2")) return MediaType.valueOf("font/woff2");
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
