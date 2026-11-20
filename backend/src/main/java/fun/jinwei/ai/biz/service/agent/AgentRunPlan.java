package fun.jinwei.ai.biz.service.agent;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单次 Agent 运行的执行计划。
 *
 * <p>由智能体配置 + 技能包 + 扩展包共同决定：系统提示词、可用工具白名单、
 * 资源作用域与匿名策略。</p>
 */
public record AgentRunPlan(String systemPrompt,
                           List<String> toolKeys,
                           Set<String> allowedDatasourceCodes,
                           Set<String> allowedApiCodes,
                           boolean anonymous,
                           List<String> skillTitles,
                           Map<String, String> toolSkillNames) {

    public static AgentRunPlan empty(String systemPrompt, boolean anonymous) {
        return new AgentRunPlan(systemPrompt, List.of(), Set.of(), Set.of(), anonymous,
                List.of(), Map.of());
    }

    /** 触发该工具的技能包名称（无技能时为空） */
    public String skillNameOf(String toolKey) {
        return toolKey != null && toolSkillNames != null ? toolSkillNames.get(toolKey) : null;
    }
}
