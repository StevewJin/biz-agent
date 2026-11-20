package fun.jinwei.ai.biz.service.agent;

import org.springframework.stereotype.Component;

/**
 * 系统提示词构建器 - 组装注入给 LLM 的系统级指令
 */
@Component
public class SystemPromptBuilder {

    /** 默认系统提示词 */
    private static final String BASE_SYSTEM_PROMPT =
            "你是 BizAgent，一个智能业务助手。\n\n"
            + "核心能力：\n"
            + "- 数据分析与洞察\n"
            + "- 报告生成与导出\n"
            + "- 业务系统接口调用\n"
            + "- 工作流自动化\n\n"
            + "工具使用原则：\n"
            + "- 只使用当前会话提供的工具，不要臆造不存在的工具或参数\n"
            + "- 工具由扩展包提供，分为数据查询（sql）、接口调用（http）、渲染（render）与导出（export）\n"
            + "- 调用数据或接口工具时，必须使用系统提示词中列出的数据源 / 接口编码\n"
            + "- 工具结果会反馈给你，基于结果继续推理或直接回复\n"
            + "- 不要在前端已渲染结果时重复粘贴原始数据\n\n"
            + "回答要求：\n"
            + "- 使用中文回复\n"
            + "- 简洁清晰，直接给出可执行的建议\n"
            + "- 如果信息不足，主动询问补充";

    /** 匿名用户的限制提示 */
    private static final String ANONYMOUS_RESTRICTION =
            "\n\n## 安全限制\n"
            + "当前用户为匿名访问，只能使用标记为匿名可用的工具。\n"
            + "如果用户请求涉及数据查询、接口调用或文件导出，请礼貌告知需要登录后才能使用。";

    /**
     * 构建系统提示词
     * @param customPrompt 用户自定义的系统提示词 (可为 null)
     * @param skillsPrompt 活跃技能注入的提示词 (可为 null 或空白)
     * @return 完整的系统提示词
     */
    public String build(String customPrompt, String skillsPrompt) {
        return build(customPrompt, skillsPrompt, null);
    }

    /**
     * 构建系统提示词 (含用户信息)
     * @param customPrompt 用户自定义的系统提示词 (可为 null)
     * @param skillsPrompt 活跃技能注入的提示词 (可为 null 或空白)
     * @param username 当前用户名 (可为 null，表示匿名)
     * @return 完整的系统提示词
     */
    public String build(String customPrompt, String skillsPrompt, String username) {
        StringBuilder sb = new StringBuilder();

        if (customPrompt != null && !customPrompt.isBlank()) {
            sb.append(customPrompt).append("\n\n---\n");
        }

        sb.append(BASE_SYSTEM_PROMPT);

        // 注入当前用户信息
        if (username != null && !username.isBlank()) {
            sb.append("\n\n当前用户: ").append(username);
        } else {
            sb.append(ANONYMOUS_RESTRICTION);
        }

        if (skillsPrompt != null && !skillsPrompt.isBlank()) {
            sb.append(skillsPrompt);
        }

        return sb.toString();
    }

    /**
     * 构建系统提示词 (无技能注入)
     */
    public String build(String customPrompt) {
        return build(customPrompt, null, null);
    }
}
