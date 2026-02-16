# v10：一键拉取证据包 + tool schema 补齐

## 摘差来源

`docs/align/v10-prd-v4-alignment.md`

## 交付项

### 1) `scripts/pull-e2e-video.ps1` 升级

- 在现有 frames 拉取基础上，新增：
  - 拉取：`/sdcard/Download/agent-browser-kotlin/e2e/snapshots`
  - 归档：`adb_dumps/e2e/<stamp>/snapshots`
  - 最新指针：`adb_dumps/e2e/latest/snapshots`

### 2) `docs/tools/web-tools.openai.json` 补齐

- 新增函数定义：
  - `web_clear`（action clear）
  - `web_focus`（action focus）
  - `web_hover`（action hover）
  - `web_scroll_into_view`（action scroll_into_view）
  - `web_query`（query kind + max_length）

## 验证与证据

- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- 拉取脚本：`pwsh -File .\scripts\pull-e2e-video.ps1`
- Evidence：
  - `docs/evidence/2026-02-16-v10-connectedAndroidTest.txt`
  - `docs/evidence/2026-02-16-v10-pull-e2e-artifacts.txt`

