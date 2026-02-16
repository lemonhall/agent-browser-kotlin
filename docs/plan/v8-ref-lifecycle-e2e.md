# v8：Ref 生命周期（导航后旧 ref 失效）E2E（对齐 PRD-V4 §7）

## 摘差来源

- PRD-V4 §7：每次 snapshot 清理旧 `data-agent-ref` 并重新分配；旧 ref 在 DOM 变化/导航后必须返回 `ref_not_found`，上层应触发重新 snapshot。

## 用例设计（离线可复现）

- `nav1.html`：包含一个 link（点击后跳到 nav2），以及一个可点击按钮（用于拿到旧 ref）。
- `nav2.html`：展示不同内容（可截图识别），确保 nav1 的旧 ref 已不存在。

测试步骤（connectedAndroidTest）：

1. `file:///android_asset/e2e/nav1.html` 加载完成
2. 注入脚本 → snapshot（interactiveOnly=false）→ 记录一个按钮 ref（oldRef）
3. 点击 link 导航到 nav2（等待 onPageFinished）
4. 对 oldRef 执行 action（click）或 query，断言返回 `ref_not_found`
5. 对 nav2 重新 snapshot，落盘：
   - 截图帧：`Download/agent-browser-kotlin/e2e/frames/*.png`
   - snapshot dumps：`Download/agent-browser-kotlin/e2e/snapshots/*-snapshot.(txt|json)`

## DoD

- `connectedAndroidTest` 新增用例通过，并能在真机 Downloads 看到 nav1/nav2 的截图帧与 snapshot dumps。
- evidence：`docs/evidence/2026-02-16-v8-connectedAndroidTest.txt` 包含 BUILD SUCCESSFUL。

