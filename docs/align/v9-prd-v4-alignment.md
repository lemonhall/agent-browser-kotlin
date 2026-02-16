# v9 对齐（开工前）：PRD-V4 vs v8 现状

- **PRD 基准**：`docs/prd/archive/PRD-V4.md`
- **现状对齐（v8 结束）**：`docs/align/v8-prd-v4-alignment.md`

## v8 结束后的总体判断

核心闭环（snapshot → refs → action → re-snapshot）与 Ref 生命周期 E2E 已对齐 PRD-V4 的 5.x/6.x/7.x 主线。

但在“口径一致性/可用性”上仍有两处硬差异，容易让上层工具集成时跑偏：

1) **`OutputFormat.JSON` 未按 PRD 语义输出**：当前 `renderSnapshot(..., format=JSON)` 返回 `text=""`，与 PRD-V4 6.1 中 “JSON = 给程序用的结构化 JSON” 的口径不一致（至少应回传可直接使用的 JSON 字符串或等价表示）。
2) **README 缺少 PRD-V4 §8 的 tool schema**：PRD 明确要求 README 包含推荐 tool schema（给模型看的函数定义），当前仓库只提供了 API 示例与 E2E 证据，缺少“上层工具接入”最关键的稳定接口说明。

## 仍未完全对齐（v9 开工前差异清单）

### Kotlin 输出格式（PRD-V4 6.1 / 6.4）

- `OutputFormat.JSON`：
  - 期望：`renderSnapshot` 在 `format=JSON` 时，返回内容应可直接用于程序（至少包含完整 snapshot JSON 文本），并且 `stats.truncated / truncateReasons` 不能与 JS 侧 `stats.truncated` 相互矛盾。
  - 现状：`text` 为空；`stats.truncated` 只反映 Kotlin render 过程，不反映 JS snapshot 侧 `truncated`。

### 上层工具定义（PRD-V4 §8 + 11.4 README）

- 期望：仓库提供一份“可直接复制”的 tool schema（包含 `web_snapshot/web_click/web_fill/...` 的参数定义），并在 README 中给出引用与接入说明。
- 现状：README 缺失该段；仓库中也缺少 schema 文件。

## v9 本轮摘差（计划解决）

- 补齐 Kotlin `OutputFormat.JSON` 的输出语义，并把“JS snapshot 截断信息”合并进 Kotlin render 的 `truncated/ truncateReasons` 口径。
- 新增 tool schema 文件（OpenAI function tools JSON 口径），并在 README 中补齐“推荐 tool schema + 接入映射”。

## 证据口径（v9 预设）

- JVM：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- Evidence：
  - `docs/evidence/2026-02-16-v9-agent-browser-kotlin-test.txt`
  - `docs/evidence/2026-02-16-v9-connectedAndroidTest.txt`

---

# v9 对齐（完成回填）

## v9 完成情况（结果回填）

- ✅ Kotlin 输出格式（PRD-V4 6.1 / 6.4）
  - `OutputFormat.JSON`：`renderSnapshot(..., format=JSON)` 不再返回空文本，`text` 为完整 snapshot JSON（等价于 `normalizeJsEvalResult(...)`）。
  - 截断口径：Kotlin render 的 `truncated / truncateReasons` 会合并 JS snapshot 侧的 `stats.truncated / truncateReasons`（避免 header/统计自相矛盾）。
- ✅ 上层工具定义（PRD-V4 §8 + 11.4 README）
  - 新增 OpenAI function tools JSON：`docs/tools/web-tools.openai.json`
  - README 补齐“推荐 tool schema + tool → AgentBrowser 映射”

证据：
- JVM：`docs/evidence/2026-02-16-v9-agent-browser-kotlin-test.txt`
- 真机：`docs/evidence/2026-02-16-v9-connectedAndroidTest.txt`

## v9 仍未完全对齐（剩余差异）

- PRD-V4 §7 第 4 条“上层工具收到 ref_not_found 自动 re-snapshot”属于上层工具编排，不在本仓库强制范围内（保持与 v8 口径一致）。
