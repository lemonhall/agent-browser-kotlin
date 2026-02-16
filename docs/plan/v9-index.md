# v9-index：agent-browser-kotlin

- **PRD 基准**：`docs/prd/archive/PRD-V4.md`
- **版本**：v9
- **日期**：2026-02-16
- **对齐起点**：`docs/align/v9-prd-v4-alignment.md`

## v9 目标

补齐 PRD-V4 的两处“执行口径”硬差异：

1) Kotlin `OutputFormat.JSON` 输出语义对齐（renderSnapshot 不再返回空文本；并统一 JS/Kotlin 的截断口径）。
2) README / docs 补齐 PRD-V4 §8 的推荐 tool schema（给模型看的函数定义），提供可复制的 OpenAI function tools JSON。

## 里程碑（v9）

| Milestone | 范围 | DoD（可二元判定） | 验证方式 | 状态 |
|---|---|---|---|---|
| M33 | JSON 输出口径 | `format=JSON` 时 `renderSnapshot().text` 为完整 snapshot JSON；`stats.truncated` 与 `truncateReasons` 合并 JS 侧口径 | `:agent-browser-kotlin:test` | done |
| M34 | Tool schema | 新增 `docs/tools/web-tools.openai.json`；README 引用并解释与 Kotlin/JS 映射关系 | 文档 review + tests | done |
| M35 | v9 证据 | v9 全量回归并落盘 evidence | `:agent-browser-kotlin:test` + `:app:connectedAndroidTest` | done |

## 计划索引

- `docs/plan/v9-tool-schema-and-json-format.md`
