# v10 对齐（开工前）：PRD-V4 vs v9 现状

- **PRD 基准**：`docs/prd/archive/PRD-V4.md`
- **现状对齐（v9 结束）**：`docs/align/v9-prd-v4-alignment.md`

## v9 结束后的总体判断

PRD-V4 5.x/6.x/7.x/8.x 的“核心闭环 + 口径 + 上层接入文档”已基本齐活：
- snapshot/refs/action/query/page
- JSON / PLAIN_TEXT_TREE 输出口径
- Ref 生命周期 E2E（导航后旧 ref 稳定 ref_not_found）
- README 提供推荐 tool schema（OpenAI function tools JSON）

当前剩余问题更多来自“人类可感知证据链”的工程化程度：虽然 connectedAndroidTest 会把 frames + snapshot dumps 写入 Downloads，但本机侧的一键拉取脚本仍只拉 frames 生成 mp4，**没有把 snapshots 同步回来**，导致证据回看时容易断链。

## 仍未完全对齐（v10 开工前差异清单）

### 证据链可回看（与 PRD 的“可验证/可复现”原则一致）

- `scripts/pull-e2e-video.ps1`：
  - 现状：只 `adb pull` frames 并用 ffmpeg 合成 mp4。
  - 期望：同时拉取 `Downloads/.../snapshots/`，并按 runId 归档到 `adb_dumps/e2e/<stamp>/snapshots/`，`latest/` 也有指针，形成“视频 + snapshot 文本/JSON”的闭环证据包。

### Tool schema 覆盖面（PRD-V4 §5.3/§5.4 的 action/query 能力）

- 现状：`docs/tools/web-tools.openai.json` 只覆盖了 PRD §8 示例列出的最小工具集。
- 期望：补齐常用但低成本的工具定义（如 `web_clear/web_focus/web_hover/web_scroll_into_view/web_query`），避免上层工具二次发散。

## v10 本轮摘差（计划解决）

- 升级 `scripts/pull-e2e-video.ps1` → 同步拉取 snapshots 并归档；输出更明确的本机产物路径。
- 扩充 `docs/tools/web-tools.openai.json`：把库已支持的 action/query 补齐到 schema 中（不改变核心实现）。

## 证据口径（v10 预设）

- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- Evidence：
  - `docs/evidence/2026-02-16-v10-connectedAndroidTest.txt`
  - `docs/evidence/2026-02-16-v10-pull-e2e-artifacts.txt`（脚本输出）

---

# v10 对齐（完成回填）

## v10 完成情况（结果回填）

- ✅ 一键拉取证据包（视频 + dumps）
  - `scripts/pull-e2e-video.ps1` 现在会同时拉取：
    - frames：`/sdcard/Download/agent-browser-kotlin/e2e/frames`
    - snapshots：`/sdcard/Download/agent-browser-kotlin/e2e/snapshots`
  - 本机归档：
    - `adb_dumps/e2e/<stamp>/frames`
    - `adb_dumps/e2e/<stamp>/snapshots`
    - 最新指针：`adb_dumps/e2e/latest/e2e-latest.mp4` + `adb_dumps/e2e/latest/snapshots/`
- ✅ Tool schema 覆盖面收敛
  - `docs/tools/web-tools.openai.json` 补齐 `web_clear/web_focus/web_hover/web_scroll_into_view/web_query`

证据：
- 真机：`docs/evidence/2026-02-16-v10-connectedAndroidTest.txt`
- 拉取脚本输出：`docs/evidence/2026-02-16-v10-pull-e2e-artifacts.txt`

## v10 仍未完全对齐（剩余差异）

- PRD-V4 §13 “后续迭代”（iframe / 文件上传 / waitFor* / 截图 base64）仍属于后续阶段，当前未纳入 v10 范围。
