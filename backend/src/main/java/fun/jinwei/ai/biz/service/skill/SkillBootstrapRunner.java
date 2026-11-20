package fun.jinwei.ai.biz.service.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.jinwei.ai.biz.entity.Skill;
import fun.jinwei.ai.biz.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内置技能包种子数据。
 *
 * <p>原来的代码技能（DataAnalysisSkill / ReportGenerationSkill）已改为数据库技能包，
 * 启动时若不存在则写入，便于用户直接在其基础上编辑或导出为技能包。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillBootstrapRunner implements ApplicationRunner {

    private final SkillRepository skillRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) {
        seed(
                "data_analysis",
                "数据分析",
                "自动分析业务系统数据，输出洞察并可视化",
                "data",
                "bar-chart-3",
                "数据,分析,统计,查询,图表,趋势,对比,汇总,报表,洞察",
                List.of("builtin_db__query", "builtin_db__list_tables",
                        "builtin_db__describe_table", "builtin_chart__generate_chart"),
                """
                        你是资深数据分析专家，面向业务系统数据提供分析与洞察。

                        ## 工作流程
                        1. 先用 `builtin_db__list_tables` 了解数据源中的表（datasource 参数取自「可访问的业务数据源」）
                        2. 用 `builtin_db__describe_table` 查看相关表的字段与注释
                        3. 用 `builtin_db__query` 执行只读 SQL：SQL 中使用 `#{名称}` 占位符，参数放在 `params` 中
                        4. 对结果做汇总、对比、趋势分析，提炼业务结论
                        5. 需要可视化时调用 `builtin_chart__generate_chart`，labels 与 datasets 长度必须一致

                        ## 注意事项
                        - 仅允许 SELECT / WITH，禁止任何写操作
                        - 大数据量查询务必加 LIMIT 或先聚合
                        - 结论要落到业务动作上，不要只罗列数字
                        """);

        seed(
                "report_generation",
                "报告生成",
                "根据业务数据撰写结构化报告并支持导出",
                "report",
                "file-text",
                "报告,导出,汇报,总结,撰写,文档",
                List.of("builtin_db__query", "builtin_db__list_tables",
                        "builtin_db__describe_table", "builtin_export__export_file"),
                """
                        你是业务报告撰写专家。

                        ## 工作流程
                        1. 明确报告主题、时间范围与受众
                        2. 使用 `builtin_db__query` / `builtin_db__list_tables` 获取数据；无可用数据时先向用户确认
                        3. 组织报告结构：结论先行，再给数据支撑，最后给建议
                        4. 用户需要下载时，用 `builtin_export__export_file` 导出 xlsx 或 csv

                        ## 要求
                        - 中文撰写，结构清晰，使用小标题与要点
                        - 数据必须来自工具查询结果，不得编造
                        - 结尾给出可执行的建议
                        """);
    }

    private void seed(String name, String displayName, String description, String category,
                      String icon, String keywords, List<String> toolKeys, String prompt) {
        Skill existing = skillRepository.findByName(name).orElse(null);
        SkillResources resources = new SkillResources(List.of(), List.of(), List.of(), toolKeys);
        if (existing != null) {
            // 仅在用户未修改提示词时保持与种子一致
            if (existing.getPromptMd() == null || existing.getPromptMd().isBlank()) {
                existing.setPromptMd(prompt);
                existing.setResourcesJson(resources.toJson(objectMapper));
                skillRepository.save(existing);
            }
            return;
        }
        Skill skill = Skill.builder()
                .name(name)
                .displayName(displayName)
                .description(description)
                .category(category)
                .promptMd(prompt)
                .resourcesJson(resources.toJson(objectMapper))
                .keywords(keywords)
                .triggerMode("keyword")
                .skillVersion("1.0.0")
                .packSource("builtin")
                .icon(icon)
                .enabled(true)
                .build();
        skillRepository.save(skill);
        log.info("内置技能包已初始化: {}", name);
    }
}
