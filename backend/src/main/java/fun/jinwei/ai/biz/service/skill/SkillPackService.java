package fun.jinwei.ai.biz.service.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.jinwei.ai.biz.dto.request.SkillRequest;
import fun.jinwei.ai.biz.dto.response.SkillResponse;
import fun.jinwei.ai.biz.entity.Skill;
import fun.jinwei.ai.biz.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 技能包导入导出服务。
 *
 * <p>技能包格式（zip）：</p>
 * <pre>
 * skill-pack/
 * ├── skill.json   # 元数据与资源引用
 * └── SKILL.md     # 提示词正文（支持 {{datasource:code}} 占位符）
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillPackService {

    private static final String META_FILE = "skill.json";
    private static final String PROMPT_FILE = "SKILL.md";

    private final SkillRepository skillRepository;
    private final SkillManager skillManager;
    private final SkillPromptResolver promptResolver;
    private final ObjectMapper objectMapper;

    /** 导入技能包（同名技能在权限允许时覆盖更新） */
    @Transactional
    public SkillResponse importPack(MultipartFile file, Long userId, boolean admin) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("技能包文件不能为空");
        }
        Map<String, byte[]> entries = readZip(file);
        byte[] metaBytes = entries.get(META_FILE);
        if (metaBytes == null) {
            metaBytes = entries.entrySet().stream()
                    .filter(e -> e.getKey().endsWith("/" + META_FILE))
                    .map(Map.Entry::getValue).findFirst().orElse(null);
        }
        if (metaBytes == null) {
            throw new IllegalArgumentException("技能包缺少 skill.json");
        }
        String prompt = null;
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (entry.getKey().equals(PROMPT_FILE) || entry.getKey().endsWith("/" + PROMPT_FILE)) {
                prompt = new String(entry.getValue(), StandardCharsets.UTF_8);
                break;
            }
        }

        SkillRequest request;
        try {
            request = objectMapper.readValue(metaBytes, SkillRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("skill.json 解析失败: " + e.getMessage());
        }
        if (prompt != null && !prompt.isBlank()) {
            request.setPromptMd(prompt);
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("skill.json 缺少 name");
        }

        Skill existing = skillRepository.findByName(request.getName()).orElse(null);
        skillManager.assertImportable(existing, userId, admin);
        SkillResponse response;
        if (existing != null) {
            response = skillManager.updateSkill(existing.getId(), request);
            response.setPackSource("upload");
            Skill entity = skillRepository.findById(existing.getId()).orElseThrow();
            entity.setPackSource("upload");
            skillRepository.save(entity);
        } else {
            response = skillManager.createSkill(request, userId, teamId());
            Skill entity = skillRepository.findById(response.getId()).orElseThrow();
            entity.setPackSource("upload");
            skillRepository.save(entity);
            response.setPackSource("upload");
        }
        promptResolver.invalidate();
        log.info("技能包导入成功: {} (by user {})", request.getName(), userId);
        return response;
    }

    /** 导出技能包 */
    public byte[] exportPack(Long skillId) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("技能不存在: " + skillId));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", skill.getName());
        meta.put("displayName", skill.getDisplayName());
        meta.put("description", skill.getDescription());
        meta.put("category", skill.getCategory());
        meta.put("version", skill.getSkillVersion());
        meta.put("icon", skill.getIcon());
        meta.put("iconColor", skill.getIconColor());
        meta.put("keywords", skill.getKeywords());
        meta.put("triggerMode", skill.getTriggerMode());
        SkillResources resources = SkillResources.parse(skill.getResourcesJson(), objectMapper);
        Map<String, Object> resourcesMap = new LinkedHashMap<>();
        resourcesMap.put("datasources", resources.datasources());
        resourcesMap.put("interfaces", resources.interfaces());
        resourcesMap.put("extensions", resources.extensions());
        resourcesMap.put("tools", resources.tools());
        meta.put("resources", resourcesMap);

        String prompt = skill.getPromptMd();

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry(META_FILE));
            zos.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(meta));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(PROMPT_FILE));
            zos.write(prompt != null ? prompt.getBytes(StandardCharsets.UTF_8) : new byte[0]);
            zos.closeEntry();
            zos.finish();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("技能包导出失败: " + e.getMessage(), e);
        }
    }

    /** 草稿预览（未保存的技能提示词） */
    public Map<String, Object> previewDraft(SkillRequest request, Long userId, Long teamId, boolean admin) {
        Skill transientSkill = Skill.builder()
                .name(request.getName() != null ? request.getName() : "draft")
                .displayName(request.getDisplayName() != null ? request.getDisplayName() : "草稿")
                .promptMd(request.getPromptMd())
                .resourcesJson(toResourcesJson(request))
                .build();
        return promptResolver.preview(transientSkill, userId, teamId, admin);
    }

    /** 导入时沿用当前团队上下文（个人空间为空） */
    private Long teamId() {
        return fun.jinwei.ai.biz.config.DataContextHolder.getTeamId();
    }

    private String toResourcesJson(SkillRequest request) {
        SkillResources resources = SkillResources.empty();
        if (request.getResources() != null) {
            resources = new SkillResources(
                    request.getResources().get("datasources"),
                    request.getResources().get("interfaces"),
                    request.getResources().get("extensions"),
                    request.getResources().get("tools"));
        }
        if (request.getRequiredTools() != null && !request.getRequiredTools().isEmpty()) {
            resources = resources.merge(new SkillResources(List.of(), List.of(), List.of(),
                    request.getRequiredTools()));
        }
        return resources.toJson(objectMapper);
    }

    private Map<String, byte[]> readZip(MultipartFile file) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (InputStream in = file.getInputStream(); ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("__MACOSX")) continue;
                entries.put(name, zis.readAllBytes());
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("技能包解析失败: " + e.getMessage(), e);
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("技能包内容为空");
        }
        return entries;
    }

    /** 内置技能包种子（供初始化使用） */
    public List<String> builtinSkillNames() {
        List<String> names = new ArrayList<>();
        skillRepository.findByEnabledTrue().forEach(skill -> {
            if ("builtin".equals(skill.getPackSource())) {
                names.add(skill.getName());
            }
        });
        return names;
    }
}
