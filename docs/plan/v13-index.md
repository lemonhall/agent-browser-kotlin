# v13：继续对齐 tests-checklist（补齐可迁移 ★ 项）

基准：
- PRD：`docs/prd/archive/PRD-V4.md`
- 测试清单：`docs/prd/archive/tests-checklist.md`
- 对齐文档：`docs/align/v13-prd-v4-alignment.md`

## 目标

继续对齐 tests-checklist 中 ★ 项里“WebView 注入脚本侧可迁移/可实现”的部分，并产出真机可感知证据：

- action：新增 `type(text)`（逐字符输入语义）
- page：新增基础导航（navigate/back/forward/reload）
- page：新增鼠标事件（mouseMove/mouseDown/mouseUp/wheel）
- Kotlin：新增 `parse*Safe`，把“invalid JSON”类异常降级为结构化错误结果

## 里程碑

1. JS：扩展 `action('type')`；扩展 `page(kind=...)` 支持导航/鼠标事件
2. Kotlin：扩展 `ActionKind/PageKind/PagePayload` + `AgentBrowser.*Js()` helper；增加 `parse*Safe`
3. Android：新增离线 fixtures + connectedAndroidTest 覆盖与截图/快照落盘
4. 证据：生成 `docs/evidence/` 日志 + `adb_dumps/e2e/latest` 可回看材料

## 验证命令（PowerShell）

- Kotlin 单测：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- Android 真机/模拟器：`.\gradlew :app:connectedAndroidTest --no-daemon`
- 拉取真机证据并合成视频：`pwsh -File .\scripts\pull-e2e-video.ps1`

