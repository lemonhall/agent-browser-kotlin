# PRD-0003：OpenAgentic 真 Agent 接入（WebView tool-loop + 人类可感知证据）

- 基线 PRD（历史愿景）：`docs/prd/archive/PRD-V4.md`
- 本 PRD 目标：在“snapshot → refs → action → re-snapshot”闭环已经稳定的基础上，**把真实 Agent（OpenAgentic SDK）接进来**，验证：
  - tool schema 是否可用、是否易用（参数/描述/约束是否会诱导模型跑偏）
  - prompt（system / task）是否能在真实 loop 下稳定驱动 WebView
  - tool.result 是否会引发 overflow / compaction / 追溯困难
  - 真机 E2E 是否能产出可回放、可审阅的证据链（mp4 + report + events）

## 1. 背景与问题

当前仓库已经在 API 层面完成：
- JS 注入 + snapshot JSON + Kotlin 预算渲染（给模型看的文本）
- action/query/page 的闭环与真机 E2E（截图帧 + snapshot artifacts + mp4/report）
- 推荐 tool schema 文件（`docs/tools/web-tools.openai.json`）

但缺少关键一环：**没有真实 Agent 参与**。

这会导致：
- tool schema 与实际 tool executor 之间可能存在暗漂移（字段名、默认值、边界）
- prompt 的有效性无法被“系统性验证”（仅靠人工阅读/手测不可靠）
- 证据链缺少“模型→tool_call→tool_result→下一轮”的可追溯事件（人类不安心）

## 2. 目标（Goals）

### G1：真实 Agent tool-loop 跑通（真机 + WebView）

在 Android instrumentation（真机）里使用 `openagentic-sdk-kotlin`：
- 使用真实网络 Provider（OpenAI Responses 协议），支持 `.env` 配置：
  - `OPENAI_API_KEY`（必需，仅本地，不入库）
  - `OPENAI_BASE_URL`（可选：proxy upstream）
  - `MODEL`（可选，默认安全值）
- Agent 能通过本仓库的 `web_*` tools 完成离线网页上的任务（assets e2e fixtures）。

### G2：tool schema / tool executor / prompt 三者对齐（防漂移）

- `web_*` tools 的 schema 必须与 `docs/tools/web-tools.openai.json` 对齐。
- tool executor 必须严格校验必填字段，错误要“对模型友好”（结构化 + 可行动建议）。
- prompt 必须显式强调：
  - 只能用 `ref` 操作；ref 短生命周期；必要时重新 snapshot
  - 禁止 outerHTML/整页 dump；工具返回应遵守预算

### G3：人类可感知的 E2E 证据链（可信 + 可浏览）

每次 agent-run E2E 必须留下：
- 真机截图帧（step-by-step，3–5s pause）→ 本地 `mp4`
- 每步 snapshot artifacts（txt/json）
- Agent sessions/events（`events.jsonl` + `meta.json`），可通过脚本一键拉取并在 `report.html` 中可阅读

## 3. 非目标（Non-goals）

- 不追求把 OpenAgentic SDK 变成最终交付物的一部分（本仓库交付物仍是 Kotlin 库 + JS 库）。
- 不做在线网站依赖的 E2E（避免网络波动）；主要基于 `file:///android_asset/e2e/*.html`。
- 不在仓库中存放任何真实 key/secret（`.env*` 始终忽略）。

## 4. 需求（Requirements）

> 需求编号仅服务追溯：`REQ-0003-XXX`

### REQ-0003-001：OpenAgentic 依赖接入（可复现）
- 以“可复现、可审阅”的方式把 `openagentic-sdk-kotlin` 接入到本仓库的测试工程（推荐 submodule + composite build）。

### REQ-0003-002：web tools（OpenAgentic ToolRegistry）
- 提供一组 `web_*` tools（最少覆盖 `docs/tools/web-tools.openai.json` 的全部 tool name）。
- 每个 tool：
  - 输入：严格解析/校验（缺字段返回结构化错误）
  - 输出：预算受控（尤其 `web_snapshot`），避免把大 JSON 直接塞进 tool.result

### REQ-0003-003：Agent-run instrumentation E2E（离线 fixtures）
- 新增至少 1 个“agent 驱动”的真机 E2E：
  - 页面：`complex.html`（或同级 fixtures）
  - 任务：Accept cookies → fill → click/toggle → query 验证
  - 断言：最终页面状态满足预期（用 query/value/text 校验）

### REQ-0003-004：证据链闭环（events + mp4/report）
- Agent-run E2E 必须把 `session_id` 与 `events.jsonl` 关联到同一个 runPrefix。
- `scripts/pull-e2e-video.ps1` 应能在有 session_id 时自动拉取 events/meta，并在 `report.html` 中提供可阅读入口（最少：下载链接 + 纯文本查看）。

### REQ-0003-005：可控开关（避免 CI/无 key 环境失败）
- 若未提供 `OPENAI_API_KEY`（instrumentation args 或环境），agent-run E2E 必须自动 skip（不失败）。

## 5. 验收（Acceptance / DoD）

- 能在真机上运行：`.\gradlew :app:connectedAndroidTest --no-daemon`
- Agent-run E2E：
  - 有实际 tool_calls（events.jsonl 可见）
  - 最终断言通过
- 证据拉取：`pwsh -File .\scripts\pull-e2e-video.ps1`
  - `adb_dumps/e2e/latest/index.html` 可离线打开
  - 对应 run 的 `report.html` 中能看到每步截图 + snapshot + events 链接/片段
- 不泄露 secrets：仓库中无 key，日志中不打印 key/base_url。

## 6. 迭代路线（建议 v15–v18）

- v15：最小闭环（submodule + 1 条 agent-run E2E + events 拉取/展示）
- v16：增强 prompt 模板与失败重试（ref 失效、overlay、超时等），扩展 2–3 条 agent tasks
- v17：Schema 漂移门禁（schema ↔ executor 自动比对测试），引入更多 query/kind 覆盖
- v18：更强证据（report 展示 tool_calls/结果摘要、关键断言的可视化标记）

