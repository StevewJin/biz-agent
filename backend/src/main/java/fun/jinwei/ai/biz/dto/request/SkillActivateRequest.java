package fun.jinwei.ai.biz.dto.request;

import lombok.Data;

import java.util.Map;

@Data
public class SkillActivateRequest {

    private Map<String, Object> configOverrides;
}
