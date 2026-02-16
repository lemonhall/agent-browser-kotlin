# v12：对齐 tests-checklist（继续）

基准：
- PRD：`docs/prd/archive/PRD-V4.md`
- 测试清单：`docs/prd/archive/tests-checklist.md`
- 对齐文档：`docs/align/v12-prd-v4-alignment.md`

## 目标

继续对齐 tests-checklist 中 ★ 相关项，补齐：
- 键盘输入能力（更细粒度）+ 真机 E2E 证据；
- 元素状态查询（可见/可用/选中）+ 真机 E2E 证据。

## 里程碑

1. JS：新增 `page.keyDown/keyUp/char`，新增 `query(isvisible|isenabled|ischecked)`
2. Kotlin：扩展 `PageKind` / `QueryKind` + 对应 `AgentBrowser.*Js()` helper
3. Android：新增离线 fixture + 新增/扩展 connectedAndroidTest 覆盖与截图/快照落盘
4. 证据：生成 `docs/evidence/` 日志 + `adb_dumps/e2e/latest` 可回放材料

## 验证命令（PowerShell）

- Kotlin 单测：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- Android 真机/模拟器：`.\gradlew :app:connectedAndroidTest --no-daemon`
- 拉取真机证据并合成视频：`pwsh -File .\\scripts\\pull-e2e-video.ps1`

## 风险/注意事项

- 华为 ROM 对 `adb shell screencap`/权限较敏感：本项目继续以 instrumentation + MediaStore 写入 Downloads 的方式落地“人类可感知证据”。
- `KeyboardEvent` 在 WebView 中不一定导致 input value 自动变化：fixture 需用监听器（keydown/keyup/input）显式写入 UI 作为验收信号。

