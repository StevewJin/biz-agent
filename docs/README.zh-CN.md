# BizAgent 文档索引

[English](README.md) · **简体中文**

| 文档 | 面向 | 内容 |
|---|---|---|
| [../README.zh-CN.md](../README.zh-CN.md) | 所有人 | 项目简介、架构、快速开始、环境变量 |
| [extension-authoring.zh-CN.md](extension-authoring.zh-CN.md) | 扩展包开发者 | `manifest.json` 规范、`sql`/`http`/`render`/`export` 四类声明式工具、jar 代码插件、前端渲染器 |
| [skill-authoring-guide.zh-CN.md](skill-authoring-guide.zh-CN.md) | 业务配置人员 | 技能包编写：提示词占位符、资源引用、触发方式、表结构注入模式 |
| [database-setup.zh-CN.md](database-setup.zh-CN.md) | 运维 / 部署 | 建表脚本、环境变量、容器化与容量规划 |

## 相关示例

- [../extensions-samples/chart-pro](../extensions-samples/chart-pro)：可直接导入的扩展包示例（前端渲染器 + 打包脚本）
- [market-material-skills.sql](market-material-skills.sql)：3 个市场物料相关技能包的示例数据，可直接导入体验占位符与工具白名单

## 阅读顺序建议

1. 先看根 [README](../README.zh-CN.md) 了解「Agent Loop + 技能包 + 扩展包」三段式设计
2. 需要接入自己的业务系统 → [database-setup.zh-CN.md](database-setup.zh-CN.md)
3. 需要写技能提示词 → [skill-authoring-guide.zh-CN.md](skill-authoring-guide.zh-CN.md)
4. 需要新增工具或前端图表 → [extension-authoring.zh-CN.md](extension-authoring.zh-CN.md)
