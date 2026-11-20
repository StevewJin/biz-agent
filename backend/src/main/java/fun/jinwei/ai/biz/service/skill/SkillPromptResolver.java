package fun.jinwei.ai.biz.service.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.jinwei.ai.biz.entity.ApiEndpoint;
import fun.jinwei.ai.biz.entity.Datasource;
import fun.jinwei.ai.biz.entity.DatasourceColumn;
import fun.jinwei.ai.biz.entity.DatasourceTable;
import fun.jinwei.ai.biz.entity.Skill;
import fun.jinwei.ai.biz.security.ResourceVisibility;
import fun.jinwei.ai.biz.extension.ExtensionRuntime;
import fun.jinwei.ai.biz.extension.api.manifest.ExtensionManifest;
import fun.jinwei.ai.biz.service.apicatalog.ApiEndpointService;
import fun.jinwei.ai.biz.service.datasource.DatasourceIntrospectionService;
import fun.jinwei.ai.biz.service.datasource.DatasourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 技能提示词解析器。
 *
 * <p>把技能提示词中的占位符展开为真实资源说明，并汇总出该技能需要的工具白名单：</p>
 * <pre>
 * {{datasource:ds_sales}}  数据源信息 + 表清单 + 字段摘要 + 推荐工具
 * {{api:crm_order}}        接口方法/路径 + 入参说明 + 调用方式
 * {{extension:chart_pro}}  扩展包能力与工具清单
 * {{tool:chart_pro__chart}} 单个工具说明
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillPromptResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile(
            "\\{\\{\\s*(datasource|db|api|interface|extension|ext|tool)\\s*:\\s*([A-Za-z0-9_.\\-]+)\\s*}}");
    private static final int MAX_TABLES = 40;
    private static final int MAX_COLUMNS_PER_TABLE = 12;
    private static final int CACHE_LIMIT = 500;

    private final DatasourceService datasourceService;
    private final DatasourceIntrospectionService introspectionService;
    private final fun.jinwei.ai.biz.repository.DatasourceTableRepository datasourceTableRepository;
    private final ApiEndpointService apiEndpointService;
    private final ExtensionRuntime extensionRuntime;
    private final ObjectMapper objectMapper;
    private final ResourceVisibility visibility;

    private final Map<String, ResolvedSkill> cache = new ConcurrentHashMap<>();

    /** 解析结果：展开后的提示词 + 能力作用域 */
    public record ResolvedSkill(String skillCode, String title, String prompt,
                                List<String> toolKeys, List<String> datasourceCodes,
                                List<String> apiCodes, List<String> warnings) {
    }

    public ResolvedSkill resolve(Skill skill, Long userId, Long teamId, boolean admin) {
        String basePrompt = skill.getPromptMd() != null ? skill.getPromptMd() : "";
        SkillResources declared = SkillResources.parse(skill.getResourcesJson(), objectMapper);

        String cacheKey = cacheKey(skill, basePrompt, declared);
        ResolvedSkill cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Set<String> toolKeys = new LinkedHashSet<>(declared.tools());
        Set<String> datasourceCodes = new LinkedHashSet<>();
        Set<String> apiCodes = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();
        boolean lazySchema = "lazy".equalsIgnoreCase(skill.getSchemaMode());

        StringBuilder rendered = new StringBuilder();
        Matcher matcher = PLACEHOLDER.matcher(basePrompt);
        int last = 0;
        while (matcher.find()) {
            rendered.append(basePrompt, last, matcher.start());
            String kind = matcher.group(1).toLowerCase();
            String code = matcher.group(2);
            String block = switch (kind) {
                case "datasource", "db" -> datasourceBlock(code, userId, teamId,
                        toolKeys, datasourceCodes, warnings, lazySchema);
                case "api", "interface" -> apiBlock(code, userId, teamId,
                        toolKeys, apiCodes, warnings);
                case "extension", "ext" -> extensionBlock(code, userId, admin, toolKeys, warnings);
                default -> toolBlock(code, toolKeys, warnings);
            };
            rendered.append(block);
            last = matcher.end();
        }
        rendered.append(basePrompt.substring(last));

        // 声明但未在提示词中引用的资源，追加简要清单，确保模型可见
        appendDeclaredSummary(rendered, declared, userId, teamId, toolKeys,
                datasourceCodes, apiCodes, warnings);

        ResolvedSkill resolved = new ResolvedSkill(
                skill.getName(),
                skill.getDisplayName(),
                rendered.toString(),
                List.copyOf(toolKeys),
                List.copyOf(datasourceCodes),
                List.copyOf(apiCodes),
                List.copyOf(warnings));

        if (cache.size() > CACHE_LIMIT) {
            cache.clear();
        }
        cache.put(cacheKey, resolved);
        return resolved;
    }

    /** 清空解析缓存（资源变更后调用） */
    public void invalidate() {
        cache.clear();
    }

    private String datasourceBlock(String code, Long userId, Long teamId,
                                   Set<String> toolKeys, Set<String> datasourceCodes,
                                   List<String> warnings, boolean lazySchema) {
        Optional<Datasource> found = datasourceService.findVisibleByCode(code, userId, teamId);
        if (found.isEmpty()) {
            warnings.add("数据源不存在或无权访问: " + code);
            return "> 数据源 `" + code + "` 不存在或当前用户无权访问，请勿使用相关工具。\n";
        }
        Datasource ds = found.get();
        datasourceCodes.add(ds.getDsCode());
        toolKeys.addAll(toolKeysByKind("sql"));

        List<DatasourceTable> tables = introspectionService.listTables(ds.getId());
        Map<String, List<DatasourceColumn>> columnsByTable = new LinkedHashMap<>();
        if (!lazySchema) {
            for (DatasourceColumn column : introspectionService.listAllColumns(ds.getId())) {
                columnsByTable.computeIfAbsent(column.getTableName(), k -> new ArrayList<>()).add(column);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("### 数据源：").append(ds.getDsName()).append(" (`").append(ds.getDsCode()).append("`)\n");
        sb.append("- 类型：").append(ds.getDbType())
                .append("；只读：").append(Boolean.TRUE.equals(ds.getReadOnly()) ? "是" : "否")
                .append("；单次最多返回：").append(ds.getQueryMaxRows()).append(" 行\n");
        sb.append("- 查询方式：调用 `").append(firstSqlTool()).append("`，SQL 使用 `#{参数名}` 占位符，")
                .append("并在参数 `datasource` 传 `").append(ds.getDsCode()).append("`；只支持 SELECT / WITH。\n");
        if (tables.isEmpty()) {
            sb.append("- 表结构尚未同步，请先在数据源管理中执行「同步表结构」。\n");
        } else if (lazySchema) {
            sb.append("- 表清单（共 ").append(tables.size())
                    .append(" 张）：字段结构请先调用 `builtin_db__describe_table` 查看，避免一次性注入过多内容。\n");
            int index = 0;
            for (DatasourceTable table : tables) {
                if (index++ >= 100) {
                    sb.append("  - ...（其余表请用 `list_tables` 查看）\n");
                    break;
                }
                sb.append("  - ").append(table.getTableName());
                if (table.getTableComment() != null && !table.getTableComment().isBlank()) {
                    sb.append("（").append(table.getTableComment()).append("）");
                }
                sb.append("\n");
            }
        } else {
            sb.append("- 表与主要字段（共 ").append(tables.size()).append(" 张）：\n");
            int index = 0;
            for (DatasourceTable table : tables) {
                if (index++ >= MAX_TABLES) {
                    sb.append("  - ...（其余 ").append(tables.size() - MAX_TABLES)
                            .append(" 张表请用 `list_tables` 查看）\n");
                    break;
                }
                sb.append("  - ").append(table.getTableName());
                if (table.getTableComment() != null && !table.getTableComment().isBlank()) {
                    sb.append("（").append(table.getTableComment()).append("）");
                }
                List<DatasourceColumn> columns = columnsByTable.getOrDefault(table.getTableName(), List.of());
                if (!columns.isEmpty()) {
                    sb.append(": ");
                    List<String> parts = new ArrayList<>();
                    for (int i = 0; i < Math.min(columns.size(), MAX_COLUMNS_PER_TABLE); i++) {
                        DatasourceColumn column = columns.get(i);
                        parts.add(column.getColumnName() + " " + (column.getDataType() != null ? column.getDataType() : ""));
                    }
                    if (columns.size() > MAX_COLUMNS_PER_TABLE) {
                        parts.add("...");
                    }
                    sb.append(String.join(", ", parts));
                }
                sb.append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    private String apiBlock(String code, Long userId, Long teamId,
                            Set<String> toolKeys, Set<String> apiCodes, List<String> warnings) {
        Optional<ApiEndpoint> found = apiEndpointService.findVisibleByCode(code, userId, teamId);
        if (found.isEmpty()) {
            warnings.add("接口不存在或无权访问: " + code);
            return "> 接口 `" + code + "` 不存在或当前用户无权访问，请勿调用。\n";
        }
        ApiEndpoint endpoint = found.get();
        apiCodes.add(endpoint.getApiCode());
        toolKeys.addAll(toolKeysByKind("http"));

        StringBuilder sb = new StringBuilder();
        sb.append("### 接口：").append(endpoint.getApiName()).append(" (`").append(endpoint.getApiCode()).append("`)\n");
        sb.append("- 请求：").append(endpoint.getHttpMethod()).append(" ")
                .append(endpoint.getBaseUrl())
                .append(endpoint.getPath() != null ? endpoint.getPath() : "")
                .append("\n");
        if (endpoint.getDescription() != null && !endpoint.getDescription().isBlank()) {
            sb.append("- 说明：").append(endpoint.getDescription()).append("\n");
        }
        String schema = endpoint.getParamSchema();
        if (schema != null && !schema.isBlank()) {
            sb.append("- 入参：").append(compact(schema, 1200)).append("\n");
        }
        if (endpoint.getRequestExample() != null && !endpoint.getRequestExample().isBlank()) {
            sb.append("- 请求示例：").append(endpoint.getRequestExample()).append("\n");
        }
        sb.append("- 调用方式：使用 `").append(firstHttpTool()).append("`，参数 `endpoint` = `")
                .append(endpoint.getApiCode()).append("`，业务参数放在 `params` 中。");
        sb.append("鉴权由平台自动注入，禁止自行拼接 URL 或传入密钥。\n\n");
        return sb.toString();
    }

    private String extensionBlock(String extCode, Long userId, boolean admin,
                                  Set<String> toolKeys, List<String> warnings) {
        Optional<ExtensionRuntime.LoadedExtension> found = extensionRuntime.get(extCode);
        if (found.isEmpty()) {
            warnings.add("扩展包未安装或未启用: " + extCode);
            return "> 扩展包 `" + extCode + "` 未安装或未启用。\n";
        }
        var ext = found.get().entity();
        if (!visibility.isVisible(ext.getOwnerId(), ext.getTeamId(), ext.getVisibility(),
                ext.getPublishStatus(), userId, admin)) {
            warnings.add("扩展包不存在或无权使用: " + extCode);
            return "> 扩展包 `" + extCode + "` 不存在或当前用户无权使用。\n";
        }
        ExtensionRuntime.LoadedExtension extension = found.get();
        StringBuilder sb = new StringBuilder();
        sb.append("### 扩展能力：").append(extension.entity().getExtName())
                .append(" (`").append(extCode).append("` v").append(extension.entity().getVersion()).append(")\n");
        for (ExtensionManifest.ToolDef def : extension.manifest().getTools()) {
            if (Boolean.FALSE.equals(def.getEnabled())) continue;
            String toolKey = ExtensionRuntime.toolKey(extCode, def.getCode());
            toolKeys.add(toolKey);
            sb.append("- `").append(toolKey).append("` ");
            if (def.getDisplayName() != null) {
                sb.append(def.getDisplayName()).append("：");
            }
            if (def.getDescription() != null) {
                sb.append(def.getDescription());
            }
            sb.append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String toolBlock(String toolKey, Set<String> toolKeys, List<String> warnings) {
        Optional<ExtensionRuntime.ToolHandle> found = extensionRuntime.findTool(toolKey);
        if (found.isEmpty()) {
            warnings.add("工具不存在: " + toolKey);
            return "> 工具 `" + toolKey + "` 不存在或所在扩展包未启用。\n";
        }
        ExtensionRuntime.ToolHandle handle = found.get();
        toolKeys.add(toolKey);
        StringBuilder sb = new StringBuilder();
        sb.append("### 工具：`").append(toolKey).append("`\n");
        if (handle.tool().getDescription() != null) {
            sb.append(handle.tool().getDescription()).append("\n");
        }
        if (handle.tool().getParameterSchema() != null) {
            sb.append("参数：").append(compact(handle.tool().getParameterSchema().toString(), 1200)).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private void appendDeclaredSummary(StringBuilder rendered, SkillResources declared,
                                       Long userId, Long teamId, Set<String> toolKeys,
                                       Set<String> datasourceCodes, Set<String> apiCodes,
                                       List<String> warnings) {
        List<String> lines = new ArrayList<>();
        for (String code : declared.datasources()) {
            if (datasourceCodes.contains(code)) continue;
            Optional<Datasource> ds = datasourceService.findVisibleByCode(code, userId, teamId);
            if (ds.isEmpty()) {
                warnings.add("数据源不存在或无权访问: " + code);
                continue;
            }
            datasourceCodes.add(code);
            toolKeys.addAll(toolKeysByKind("sql"));
            lines.add("- 数据源 `" + code + "`：" + ds.get().getDsName());
        }
        for (String code : declared.interfaces()) {
            if (apiCodes.contains(code)) continue;
            Optional<ApiEndpoint> ep = apiEndpointService.findVisibleByCode(code, userId, teamId);
            if (ep.isEmpty()) {
                warnings.add("接口不存在或无权访问: " + code);
                continue;
            }
            apiCodes.add(code);
            toolKeys.addAll(toolKeysByKind("http"));
            lines.add("- 接口 `" + code + "`：" + ep.get().getApiName());
        }
        for (String code : declared.extensions()) {
            if (extensionRuntime.get(code).isEmpty()) {
                warnings.add("扩展包未安装或未启用: " + code);
                continue;
            }
            extensionRuntime.get(code).ifPresent(loaded -> {
                for (ExtensionManifest.ToolDef def : loaded.manifest().getTools()) {
                    if (!Boolean.FALSE.equals(def.getEnabled())) {
                        toolKeys.add(ExtensionRuntime.toolKey(code, def.getCode()));
                    }
                }
                lines.add("- 扩展包 `" + code + "`：" + loaded.entity().getExtName());
            });
        }
        if (!lines.isEmpty()) {
            rendered.append("\n### 本技能已授权的资源\n");
            lines.forEach(line -> rendered.append(line).append("\n"));
        }
    }

    private List<String> toolKeysByKind(String kind) {
        List<String> keys = new ArrayList<>();
        for (ExtensionRuntime.ToolHandle handle : extensionRuntime.allTools()) {
            if (kind.equalsIgnoreCase(handle.tool().getKind())) {
                keys.add(handle.toolKey());
            }
        }
        return keys;
    }

    private String firstSqlTool() {
        for (ExtensionRuntime.ToolHandle handle : extensionRuntime.allTools()) {
            if ("sql".equalsIgnoreCase(handle.tool().getKind())
                    && "query".equalsIgnoreCase(handle.tool().getCode())) {
                return handle.toolKey();
            }
        }
        List<String> keys = toolKeysByKind("sql");
        return keys.isEmpty() ? "sql 工具（未安装）" : keys.get(0);
    }

    private String firstHttpTool() {
        List<String> keys = toolKeysByKind("http");
        return keys.isEmpty() ? "http 工具（未安装）" : keys.get(0);
    }

    private String cacheKey(Skill skill, String prompt, SkillResources resources) {
        // 数据源内省（表数/最近同步时间）或扩展包版本变化时自动失效
        java.time.LocalDateTime lastSync = datasourceTableRepository.findMaxSyncedAt();
        int extHash = extensionRuntime.all().stream()
                .map(loaded -> loaded.entity().getExtCode() + ":" + loaded.entity().getVersion())
                .sorted()
                .toList()
                .hashCode();
        return skill.getId() + "|" + prompt.hashCode() + "|"
                + resources.toJson(objectMapper).hashCode() + "|"
                + datasourceTableRepository.count() + "|" + lastSync + "|" + extHash;
    }

    private String compact(String text, int max) {
        if (text == null) return "";
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() > max ? normalized.substring(0, max) + "..." : normalized;
    }

    /** 用于管理页面的预览：返回展开后的提示词与告警 */
    public Map<String, Object> preview(Skill skill, Long userId, Long teamId, boolean admin) {
        ResolvedSkill resolved = resolve(skill, userId, teamId, admin);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prompt", resolved.prompt());
        result.put("toolKeys", resolved.toolKeys());
        result.put("datasourceCodes", resolved.datasourceCodes());
        result.put("apiCodes", resolved.apiCodes());
        result.put("warnings", resolved.warnings());
        return result;
    }

    /** 供编辑器做参数提示：返回资源的精简描述 */
    public JsonNode describeDatasource(Datasource ds) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("dsCode", ds.getDsCode());
        info.put("dsName", ds.getDsName());
        info.put("dbType", ds.getDbType());
        info.put("tableCount", introspectionService.listTables(ds.getId()).size());
        return objectMapper.valueToTree(info);
    }
}
