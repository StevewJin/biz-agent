package fun.jinwei.ai.biz.service.skill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 技能包引用的资源集合。
 */
public record SkillResources(List<String> datasources,
                             List<String> interfaces,
                             List<String> extensions,
                             List<String> tools) {

    public SkillResources {
        datasources = datasources != null ? datasources : List.of();
        interfaces = interfaces != null ? interfaces : List.of();
        extensions = extensions != null ? extensions : List.of();
        tools = tools != null ? tools : List.of();
    }

    public static SkillResources empty() {
        return new SkillResources(List.of(), List.of(), List.of(), List.of());
    }

    public static SkillResources parse(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return empty();
        }
        try {
            SkillResources parsed = objectMapper.readValue(json, new TypeReference<SkillResources>() {
            });
            return parsed != null ? parsed : empty();
        } catch (Exception e) {
            return empty();
        }
    }

    public String toJson(ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }

    public SkillResources merge(SkillResources other) {
        if (other == null) {
            return this;
        }
        return new SkillResources(
                union(datasources, other.datasources),
                union(interfaces, other.interfaces),
                union(extensions, other.extensions),
                union(tools, other.tools));
    }

    private static List<String> union(List<String> a, List<String> b) {
        List<String> result = new ArrayList<>(a);
        for (String value : b) {
            if (!result.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }
}
