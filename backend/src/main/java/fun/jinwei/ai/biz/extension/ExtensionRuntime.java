package fun.jinwei.ai.biz.extension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.jinwei.ai.biz.config.ExtensionProperties;
import fun.jinwei.ai.biz.entity.Extension;
import fun.jinwei.ai.biz.extension.api.ExtensionTool;
import fun.jinwei.ai.biz.extension.api.manifest.ExtensionManifest;
import fun.jinwei.ai.biz.repository.ExtensionRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 扩展包运行时注册表。
 *
 * <p>负责解析 manifest、为代码类扩展包创建独立 ClassLoader、实例化 SPI 实现，
 * 并提供按 toolKey 定位工具的能力。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExtensionRuntime {

    private static final String SPI_DESCRIPTOR = "META-INF/bizagent-extension.json";

    private final ExtensionRepository extensionRepository;
    private final ExtensionProperties properties;
    private final ObjectMapper objectMapper;

    private final Map<String, LoadedExtension> loaded = new ConcurrentHashMap<>();

    /** toolKey -> 工具句柄索引（避免每次线性扫描） */
    private volatile Map<String, ToolHandle> toolIndex = Map.of();

    public record LoadedExtension(Extension entity, ExtensionManifest manifest,
                                  URLClassLoader classLoader,
                                  Map<String, ExtensionTool> codeTools) {
    }

    public record ToolHandle(String toolKey, Extension extension,
                             ExtensionManifest.ToolDef tool, ExtensionTool codeTool) {
    }

    /** 重新加载所有已启用的扩展包 */
    public synchronized void reloadAll() {
        unloadAll();
        List<Extension> extensions = extensionRepository.findByStatus("enabled");
        for (Extension extension : extensions) {
            try {
                load(extension);
            } catch (Exception e) {
                log.error("扩展包加载失败: {} - {}", extension.getExtCode(), e.getMessage());
                extension.setStatus("error");
                extension.setErrorMsg(truncate(e.getMessage(), 1900));
                extensionRepository.save(extension);
            }
        }
        log.info("扩展包运行时已加载 {} 个扩展包, {} 个工具", loaded.size(), toolIndex.size());
    }

    /** 加载单个扩展包 */
    public synchronized void load(Extension extension) {
        unload(extension.getExtCode(), false);
        ExtensionManifest manifest = parseManifest(extension);
        Map<String, ExtensionTool> codeTools = Map.of();
        URLClassLoader classLoader = null;

        if ("jar".equalsIgnoreCase(extension.getPackType()) || "mixed".equalsIgnoreCase(extension.getPackType())) {
            classLoader = buildClassLoader(extension);
            codeTools = instantiateTools(extension, manifest, classLoader);
        }

        loaded.put(extension.getExtCode(), new LoadedExtension(extension, manifest, classLoader, codeTools));
        rebuildIndex();
        log.info("扩展包已加载: {} v{} ({} 个工具, {} 个代码实现)",
                extension.getExtCode(), extension.getVersion(),
                manifest.getTools().size(), codeTools.size());
    }

    public synchronized void unload(String extCode) {
        unload(extCode, true);
    }

    private synchronized void unload(String extCode, boolean remove) {
        LoadedExtension previous = remove ? loaded.remove(extCode) : loaded.get(extCode);
        if (previous == null) {
            return;
        }
        if (remove) {
            loaded.remove(extCode);
        }
        if (previous.classLoader() != null) {
            try {
                previous.classLoader().close();
                log.info("扩展包 ClassLoader 已关闭: {}", extCode);
            } catch (Exception e) {
                log.warn("关闭扩展包 ClassLoader 失败: {}", extCode, e);
            }
        }
        rebuildIndex();
    }

    private synchronized void unloadAll() {
        for (String code : List.copyOf(loaded.keySet())) {
            unload(code);
        }
    }

    @PreDestroy
    public void destroy() {
        unloadAll();
    }

    public Optional<LoadedExtension> get(String extCode) {
        return Optional.ofNullable(loaded.get(extCode));
    }

    public Collection<LoadedExtension> all() {
        return loaded.values();
    }

    public boolean isLoaded(String extCode) {
        return loaded.containsKey(extCode);
    }

    /** 按 toolKey 查找工具 */
    public Optional<ToolHandle> findTool(String toolKey) {
        return Optional.ofNullable(toolIndex.get(toolKey));
    }

    /** 列出所有可用工具 */
    public List<ToolHandle> allTools() {
        return List.copyOf(toolIndex.values());
    }

    /** 重建工具索引（已过滤禁用工具） */
    private void rebuildIndex() {
        Map<String, ToolHandle> index = new LinkedHashMap<>();
        for (LoadedExtension extension : loaded.values()) {
            for (ExtensionManifest.ToolDef def : extension.manifest().getTools()) {
                if (Boolean.FALSE.equals(def.getEnabled())) continue;
                String key = toolKey(extension.entity().getExtCode(), def.getCode());
                index.put(key, new ToolHandle(key, extension.entity(), def,
                        extension.codeTools().get(def.getCode())));
            }
        }
        toolIndex = index;
    }

    private Map<String, ExtensionTool> instantiateTools(Extension extension,
                                                        ExtensionManifest manifest,
                                                        URLClassLoader classLoader) {
        Map<String, String> implementations = readImplementations(extension, manifest);
        Map<String, ExtensionTool> tools = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : implementations.entrySet()) {
            String toolCode = entry.getKey();
            String className = entry.getValue();
            try {
                Class<?> cls = classLoader.loadClass(className);
                Object instance = cls.getDeclaredConstructor().newInstance();
                if (!(instance instanceof ExtensionTool tool)) {
                    log.error("扩展包工具类未实现 ExtensionTool: {} ({})", className, extension.getExtCode());
                    continue;
                }
                tools.put(toolCode, tool);
            } catch (Exception e) {
                log.error("实例化扩展包工具失败: {} -> {} - {}",
                        extension.getExtCode(), className, e.getMessage());
            }
        }
        return Map.copyOf(tools);
    }

    /** 读取 jar 内 SPI 描述文件；缺失时回退到 manifest 的 binding.class 声明 */
    private Map<String, String> readImplementations(Extension extension, ExtensionManifest manifest) {
        Map<String, String> result = new LinkedHashMap<>();
        if (extension.getJarPath() != null && Files.exists(Paths.get(extension.getJarPath()))) {
            try (JarFile jar = new JarFile(extension.getJarPath())) {
                JarEntry entry = jar.getJarEntry(SPI_DESCRIPTOR);
                if (entry != null) {
                    try (InputStream in = jar.getInputStream(entry)) {
                        Map<String, Object> json = objectMapper.readValue(in,
                                new TypeReference<Map<String, Object>>() {});
                        Object impl = json.get("implementations");
                        if (impl instanceof Map<?, ?> map) {
                            map.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("读取扩展包 SPI 描述失败: {} - {}", extension.getExtCode(), e.getMessage());
            }
        }
        for (ExtensionManifest.ToolDef def : manifest.getTools()) {
            String className = def.bindingString("class");
            if (className != null && !className.isBlank()) {
                result.putIfAbsent(def.getCode(), className);
            }
        }
        return result;
    }

    private URLClassLoader buildClassLoader(Extension extension) {
        try {
            Path jarPath = Paths.get(extension.getJarPath());
            if (!Files.exists(jarPath)) {
                throw new IllegalStateException("扩展包 jar 不存在: " + jarPath);
            }
            URL[] urls = new URL[]{jarPath.toUri().toURL()};
            return new URLClassLoader(urls, ExtensionTool.class.getClassLoader());
        } catch (Exception e) {
            throw new IllegalStateException("创建扩展包 ClassLoader 失败: " + e.getMessage(), e);
        }
    }

    public ExtensionManifest parseManifest(Extension extension) {
        if (extension.getManifestJson() == null || extension.getManifestJson().isBlank()) {
            throw new IllegalStateException("扩展包 manifest 为空: " + extension.getExtCode());
        }
        try {
            return objectMapper.readValue(extension.getManifestJson(), ExtensionManifest.class);
        } catch (Exception e) {
            throw new IllegalStateException("扩展包 manifest 解析失败: " + e.getMessage(), e);
        }
    }

    /** 全局工具键：包编码__工具编码 */
    public static String toolKey(String extCode, String toolCode) {
        return normalize(extCode) + "__" + normalize(toolCode);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    /** 扩展包前端资源根目录 */
    public Path assetsRoot(Extension extension) {
        if (extension.getAssetsPath() != null && !extension.getAssetsPath().isBlank()) {
            return Paths.get(extension.getAssetsPath()).toAbsolutePath().normalize();
        }
        return Paths.get(properties.getStorageDir(), extension.getExtCode(), extension.getVersion())
                .toAbsolutePath().normalize();
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }
}
