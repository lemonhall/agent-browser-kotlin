# v15 对齐（开工前）：PRD-V4 vs v14 现状（新增“真实 Agent 接入”门禁）

- 基准 PRD：`docs/prd/archive/PRD-V4.md`
- 测试清单：`docs/prd/archive/tests-checklist.md`（优先对齐其中 ★ 项）
- 现状对齐（v14 结束）：`docs/align/v14-prd-v4-alignment.md`
- 扩展 PRD（本轮新增目标）：`docs/prd/PRD-0003-openagentic-agent-e2e.md`
- 日期：2026-02-16

## v14 结束后的总体判断

- PRD-V4 的“库能力 + 真机证据链（截图帧 + snapshot + mp4/report）”已基本可用，tests-checklist（★）覆盖也接近收敛。
- 但当前闭环仍是“人类在测试里写死动作序列”，缺少 **真实 Agent** 参与：
  - tool schema 是否真的可用（字段/默认值/描述是否会误导）
  - prompt 是否真的能驱动正确的 snapshot→action→re-snapshot loop
  - 模型实际的 tool_calls / tool_results 是否可追溯、可审阅

## v15 开工前差异清单（摘差）

### 1) PRD-V4 §8 “给模型看的 tool schema”缺少真实接入验证（核心缺口）

- 现状：
  - 已有：`docs/tools/web-tools.openai.json`（推荐 tool schema）+ README 映射说明
  - 缺失：一个真实 Agent runtime 把这些 tools 注册/执行、并在真机上完成任务的 E2E
- 期望：
  - 在 instrumentation（真机）里跑通 OpenAgentic tool-loop；
  - tool schema / tool executor / prompt 三者对齐（避免“文档看起来对，但模型一用就翻车”）。

### 2) 证据链缺少 sessions/events（人类无法审阅“模型到底怎么想/怎么调用工具”）

- 现状：report.html 只能看到 UI 截图 + snapshot 文本，缺少模型侧事件。
- 期望：同一 runPrefix 下能看到：
  - agent session_id
  - events.jsonl/meta.json（包含 tool_use/tool_result/assistant_message 等）
  - report.html 提供可离线审阅入口（链接或内嵌片段）。

## v15 证据口径（预设）

- JVM：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- 拉取/生成报告：`pwsh -File .\scripts\pull-e2e-video.ps1`

