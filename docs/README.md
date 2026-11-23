# Documentation Index

**English** · [简体中文](README.zh-CN.md)

| Document | Audience | Contents |
|---|---|---|
| [../README.md](../README.md) | Everyone | Overview, architecture, quick start, environment variables |
| [extension-authoring.md](extension-authoring.md) | Extension developers | `manifest.json` schema, `sql`/`http`/`render`/`export` declarative tools, jar plugins, frontend renderers |
| [skill-authoring-guide.md](skill-authoring-guide.md) | Business configurators | Writing skill packs: prompt placeholders, resource references, trigger modes, schema injection |
| [database-setup.md](database-setup.md) | Ops / deployment | Schema script, environment variables, containers, capacity planning |

## Related Samples

- [../extensions-samples/chart-pro](../extensions-samples/chart-pro): a ready-to-import extension pack (frontend renderer + pack script)
- [market-material-skills.sql](market-material-skills.sql): sample data for three market-material skill packs; import it to try placeholders and tool whitelists

## Suggested Reading Order

1. Start with the root [README](../README.md) to understand the "Agent Loop + skill packs + extension packs" design
2. Connecting your own business systems → [database-setup.md](database-setup.md)
3. Writing skill prompts → [skill-authoring-guide.md](skill-authoring-guide.md)
4. Adding tools or frontend charts → [extension-authoring.md](extension-authoring.md)
