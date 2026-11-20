package fun.jinwei.ai.biz.dto.request;

import lombok.Data;

import java.util.Map;

@Data
public class SkillConfigRequest {

    private Map<String, Object> config;
}
