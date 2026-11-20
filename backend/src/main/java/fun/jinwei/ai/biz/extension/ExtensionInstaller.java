package fun.jinwei.ai.biz.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.jinwei.ai.biz.config.ExtensionProperties;
import fun.jinwei.ai.biz.entity.Extension;
import fun.jinwei.ai.biz.entity.ExtensionRendererEntity;
import fun.jinwei.ai.biz.entity.ExtensionToolEntity;
import fun.jinwei.ai.biz.extension.api.manifest.ExtensionManifest;
import fun.jinwei.ai.biz.repository.ExtensionRendererRepository;
import fun.jinwei.ai.biz.repository.ExtensionRepository;
import fun.jinwei.ai.biz.repository.ExtensionToolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 扩展包安装服务。
 *
 * <p>支持从 zip(.bpk) 导入扩展包，以及启动时从 classpath 导入内置扩展包。
 * 包结构：manifest.json（必需）、lib/*.jar（代码插件）、ui/entry.js（前端 ESM）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtensionInstaller {

    private static final String MANIFEST_FILE = "manifest.json";

    private final ExtensionProperties properties;
    private final ExtensionRepository extensionRepository;
    private final ExtensionToolRepository toolRepository;
    private final ExtensionRendererRepository rendererRepository;
    private final ExtensionRuntime runtime;
    private final ObjectMapper objectMapper;

    /** 从 zip 包安装（或升级）扩展包 */
    @Transactional
    public Extension installFromZip(MultipartFile file, Long userId, Long teamId, boolean admin) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("扩展包文件不能为空");
        }
        long maxBytes = (long) properties.getMaxPackageMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("扩展包超过体积上限 " + properties.getMaxPackageMb() + "MB");
        }

        Path storageRoot = Paths.get(properties.getStorageDir()).toAbsolutePath().normalize();
        Path staging = storageRoot.resolve(".staging-" + UUID.randomUUID());
        try {
            Files.createDirectories(staging);
            String sha256 = unzipAndDigest(file, staging);

            Path manifestPath = staging.resolve(MANIFEST_FILE);
            if (!Files.exists(manifestPath)) {
                throw new IllegalArgumentException("扩展包缺少 manifest.json");
            }
            ExtensionManifest manifest = objectMapper.readValue(manifestPath.toFile(), ExtensionManifest.class);
            validateManifest(manifest);

            boolean hasJar = !listFiles(staging.resolve("lib"), ".jar").isEmpty();
            boolean needsCode = hasJar || "jar".equalsIgnoreCase(manifest.getPackType())
                    || "mixed".equalsIgnoreCase(manifest.getPackType());
            if (needsCode && !properties.isAllowCodePacks()) {
                throw new IllegalArgumentException("当前环境禁止导入含代码的扩展包");
            }
            if (needsCode && !admin) {
                // 含 jar 的包等价服务端任意代码，仅管理员可导入
                throw new org.springframework.security.access.AccessDeniedException(
                        "仅管理员可导入包含代码（jar）的扩展包");
            }

            Path targetDir = storageRoot.resolve(manifest.getCode()).resolve(manifest.getVersion());
            Files.createDirectories(targetDir.getParent());
            if (Files.exists(targetDir)) {
                deleteRecursively(targetDir);
            }
            moveDirectory(staging, targetDir);

            List<Path> jars = listFiles(targetDir.resolve("lib"), ".jar");
            String jarPath = jars.isEmpty() ? null : jars.get(0).toString();
            String entryFile = manifest.getRenderers().stream()
                    .map(ExtensionManifest.RendererDef::getEntry)
                    .filter(e -> e != null && !e.isBlank())
                    .findFirst().orElse(null);

            Extension extension = persist(manifest, targetDir, jarPath, entryFile, sha256, userId, teamId, false);
            cleanupOtherVersions(manifest.getCode(), manifest.getVersion());
            runtime.load(extension);
            log.info("扩展包安装成功: {} v{}", extension.getExtCode(), extension.getVersion());
            return extension;
        } catch (IOException e) {
            throw new IllegalStateException("扩展包安装失败: " + e.getMessage(), e);
        } finally {
            deleteQuietly(staging);
        }
    }

    /** 启动时导入 classpath 下的内置扩展包 */
    @Transactional
    public void bootstrapBuiltin() {
        if (!properties.isBootstrapBuiltin()) {
            return;
        }
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(
                    properties.getBuiltinLocation() + "/*/" + MANIFEST_FILE);
            for (Resource resource : resources) {
                try (InputStream in = resource.getInputStream()) {
                    ExtensionManifest manifest = objectMapper.readValue(in, ExtensionManifest.class);
                    validateManifest(manifest);
                    Optional<Extension> existing = extensionRepository.findByExtCode(manifest.getCode());
                    if (existing.isPresent()
                            && manifest.getVersion().equals(existing.get().getVersion())
                            && "enabled".equals(existing.get().getStatus())) {
                        continue;
                    }
                    String entryFile = manifest.getRenderers().stream()
                            .map(ExtensionManifest.RendererDef::getEntry)
                            .filter(e -> e != null && !e.isBlank())
                            .findFirst().orElse(null);
                    Extension extension = persist(manifest, null, null, entryFile,
                            null, null, null, true);
                    extension.setSystemPack(true);
                    if (extension.getStatus() == null) {
                        extension.setStatus("enabled");
                    }
                    extensionRepository.save(extension);
                    log.info("内置扩展包已导入: {} v{}", extension.getExtCode(), extension.getVersion());
                } catch (Exception e) {
                    log.error("内置扩展包导入失败: {} - {}", resource.getFilename(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("扫描内置扩展包失败: {}", e.getMessage());
        }
    }

    private Extension persist(ExtensionManifest manifest, Path assetsDir, String jarPath,
                              String entryFile, String sha256, Long userId, Long teamId, boolean system) {
        Extension extension = extensionRepository.findByExtCode(manifest.getCode()).orElseGet(Extension::new);
        extension.setExtCode(manifest.getCode());
        extension.setExtName(manifest.getName());
        extension.setVersion(manifest.getVersion());
        extension.setPackType(manifest.getPackType() != null ? manifest.getPackType() : "declarative");
        extension.setManifestJson(toJson(manifest));
        extension.setJarPath(jarPath);
        extension.setAssetsPath(assetsDir != null ? assetsDir.toString() : null);
        extension.setEntryFile(entryFile);
        extension.setSha256(sha256);
        extension.setSystemPack(system || Boolean.TRUE.equals(extension.getSystemPack()));
        if (system) {
            extension.setOwnerId(null);
            extension.setTeamId(null);
            extension.setVisibility("public");
            extension.setPublishStatus("approved");
        } else {
            extension.setOwnerId(userId);
            extension.setTeamId(teamId);
            extension.setVisibility(teamId != null ? "team" : "personal");
            extension.setPublishStatus("draft");
        }
        extension.setErrorMsg(null);
        // 新安装的包默认启用；已禁用或异常状态保持不变
        if (extension.getStatus() == null) {
            extension.setStatus("enabled");
        }
        if (userId != null) {
            extension.setInstalledBy(userId);
        }
        extension = extensionRepository.save(extension);

        toolRepository.deleteByExtensionId(extension.getId());
        rendererRepository.deleteByExtensionId(extension.getId());

        List<ExtensionToolEntity> tools = new ArrayList<>();
        for (ExtensionManifest.ToolDef def : manifest.getTools()) {
            tools.add(ExtensionToolEntity.builder()
                    .extensionId(extension.getId())
                    .toolCode(def.getCode())
                    .toolKey(ExtensionRuntime.toolKey(manifest.getCode(), def.getCode()))
                    .displayName(def.getDisplayName())
                    .description(def.getDescription())
                    .toolKind(def.getKind() != null ? def.getKind() : "jar")
                    .parameterSchema(def.getParameterSchema() != null ? def.getParameterSchema().toString() : null)
                    .bindingJson(toJson(def.getBinding()))
                    .renderType(def.getRenderType())
                    .riskLevel(def.getRiskLevel() != null ? def.getRiskLevel() : "low")
                    .anonymousAllowed(Boolean.TRUE.equals(def.getAnonymousAllowed()))
                    .requiresConfirm(Boolean.TRUE.equals(def.getRequiresConfirm()))
                    .enabled(!Boolean.FALSE.equals(def.getEnabled()))
                    .build());
        }
        toolRepository.saveAll(tools);

        List<ExtensionRendererEntity> renderers = new ArrayList<>();
        for (ExtensionManifest.RendererDef def : manifest.getRenderers()) {
            renderers.add(ExtensionRendererEntity.builder()
                    .extensionId(extension.getId())
                    .renderType(def.getType())
                    .entry(def.getEntry())
                    .propsSchema(def.getPropsSchema() != null ? def.getPropsSchema().toString() : null)
                    .supportedTools(def.getToolKeys() != null ? String.join(",", def.getToolKeys()) : null)
                    .enabled(true)
                    .build());
        }
        rendererRepository.saveAll(renderers);
        return extension;
    }

    private void validateManifest(ExtensionManifest manifest) {
        if (manifest.getCode() == null || !manifest.getCode().matches("[a-zA-Z][a-zA-Z0-9_\\-]{1,99}")) {
            throw new IllegalArgumentException("扩展包 code 不合法（需字母开头，仅字母数字下划线中划线）");
        }
        if (manifest.getName() == null || manifest.getName().isBlank()) {
            throw new IllegalArgumentException("扩展包 name 不能为空");
        }
        if (manifest.getVersion() == null || manifest.getVersion().isBlank()) {
            throw new IllegalArgumentException("扩展包 version 不能为空");
        }
        if (manifest.getApiVersion() != null && !"1".equals(manifest.getApiVersion())) {
            throw new IllegalArgumentException("不支持的扩展 API 版本: " + manifest.getApiVersion());
        }
        if (manifest.getTools() == null || manifest.getTools().isEmpty()) {
            throw new IllegalArgumentException("扩展包至少需要声明一个工具");
        }
        for (ExtensionManifest.ToolDef def : manifest.getTools()) {
            if (def.getCode() == null || !def.getCode().matches("[a-zA-Z][a-zA-Z0-9_\\-]{1,63}")) {
                throw new IllegalArgumentException("工具 code 不合法: " + def.getCode());
            }
            if (def.getKind() == null || def.getKind().isBlank()) {
                throw new IllegalArgumentException("工具 " + def.getCode() + " 缺少 kind");
            }
        }
    }

    private String unzipAndDigest(MultipartFile file, Path targetDir) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        try (InputStream raw = file.getInputStream();
             DigestInputStream dis = new DigestInputStream(raw, digest);
             ZipInputStream zis = new ZipInputStream(dis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("__MACOSX") || name.isBlank()) {
                    continue;
                }
                Path outPath = targetDir.resolve(name).normalize();
                if (!outPath.startsWith(targetDir)) {
                    throw new IllegalArgumentException("扩展包包含非法路径: " + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
        // 兼容单层目录压缩（如 chart-pro/manifest.json）
        Path singleManifest = targetDir.resolve(MANIFEST_FILE);
        if (!Files.exists(singleManifest)) {
            try (Stream<Path> children = Files.list(targetDir)) {
                List<Path> dirs = children.filter(Files::isDirectory).toList();
                if (dirs.size() == 1) {
                    Path inner = dirs.get(0).resolve(MANIFEST_FILE);
                    if (Files.exists(inner)) {
                        moveDirectoryContent(dirs.get(0), targetDir);
                    }
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void moveDirectoryContent(Path source, Path target) throws IOException {
        try (Stream<Path> children = Files.list(source)) {
            for (Path child : children.toList()) {
                Path destination = target.resolve(child.getFileName().toString());
                if (Files.isDirectory(child)) {
                    moveDirectory(child, destination);
                } else {
                    Files.move(child, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void moveDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // 跨文件系统时回退为拷贝
            try (Stream<Path> walk = Files.walk(source)) {
                for (Path path : walk.toList()) {
                    Path destination = target.resolve(source.relativize(path).toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            deleteRecursively(source);
        }
    }

    private List<Path> listFiles(Path dir, String suffix) {
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(suffix))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private void cleanupOtherVersions(String code, String currentVersion) {
        Path codeDir = Paths.get(properties.getStorageDir()).toAbsolutePath().normalize().resolve(code);
        if (!Files.exists(codeDir)) {
            return;
        }
        try (Stream<Path> children = Files.list(codeDir)) {
            children.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().equals(currentVersion))
                    .filter(p -> !p.getFileName().toString().equals("work"))
                    .forEach(this::deleteRecursively);
        } catch (IOException e) {
            log.warn("清理旧版本扩展包目录失败: {}", e.getMessage());
        }
    }

    private void deleteQuietly(Path path) {
        if (path != null && Files.exists(path)) {
            deleteRecursively(path);
        }
    }

    private void deleteRecursively(Path path) {
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 忽略
                }
            });
        } catch (IOException e) {
            log.warn("删除目录失败: {} - {}", path, e.getMessage());
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
