# v7 对齐（开工前）：PRD-V4 vs v6 现状

- **PRD 基准**：`docs/prd/archive/PRD-V4.md`
- **现状对齐（v6 结束）**：`docs/align/v6-prd-v4-alignment.md`

## v6 结束后的总体判断

核心闭环（snapshot → action/query/page → render）与 Snapshot 5.x / Kotlin 6.x 关键差异已基本收敛。当前剩余差异主要集中在：

- **Kotlin 核心 API 的“便捷入口”**（PRD-V4 6.2 的 page helpers / query limit 直参风格）
- **E2E 的“人类可感知证据”颗粒度**：除截图帧之外，希望能落盘 snapshot 文本/JSON，方便肉眼核对（用户明确需求）

## 仍未完全对齐（v7 开工前差异清单）

### Kotlin 侧核心 API（PRD-V4 6.2）

- 缺少 PRD-V4 6.2 中的便捷 JS 生成函数（当前只能用 `pageJs(PageKind, PagePayload)` 拼装）：
  - `scrollJs(direction: String, amount: Int = 300)`
  - `pressKeyJs(key: String)`
  - `getUrlJs()`
  - `getTitleJs()`
- `queryJs` 的 PRD 口径为 `queryJs(ref, kind, limit=2000)`（直参 limit），当前仅提供 `QueryPayload(limitChars=...)` 的形式；可补一个 overload 兼容 PRD 文档口径。

### E2E 证据（用户需求增强）

- 目前真机 E2E 已能产出截图帧 + mp4，但缺少“可拉取的 snapshot 文本/JSON 文件”，导致人类难以离线核对 snapshot 结构与 refs。

## v7 本轮摘差（计划解决）

- Kotlin API：补齐 PRD-V4 6.2 的 page helpers + query limit overload（不破坏现有 API）。
- Android E2E：每个关键 step 额外落盘 `snapshot.txt`（以及可选 `snapshot.json`）到 Downloads，形成可感知证据链（与截图帧并行）。

## 证据口径（v7 预设）

- JVM：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- Evidence：`docs/evidence/2026-02-16-v7-connectedAndroidTest.txt`

---

# v7 对齐（完成回填）

## v7 完成情况（结果回填）

- ✅ Kotlin API（PRD-V4 6.2 helpers）
  - 新增 `scrollJs/pressKeyJs/getUrlJs/getTitleJs`
  - 新增 `queryJs(ref, kind, limitChars: Int)` overload
- ✅ E2E 证据（人类可感知增强）
  - connectedAndroidTest 在 Downloads 额外落盘 `snapshots/*.txt` 与 `snapshots/*.json`

证据：
- `docs/evidence/2026-02-16-v7-connectedAndroidTest.txt`

## v7 仍未完全对齐（剩余差异）

- 无新增差异；后续迭代优先转向 PRD-V4 §7（Ref 生命周期）在“导航/页面切换”场景下的 E2E 覆盖与证据。
