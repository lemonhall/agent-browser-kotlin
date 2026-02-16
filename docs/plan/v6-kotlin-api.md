# v6：Kotlin API 形态收敛（PRD-V4 6.x）

## 目标

- `RenderOptions` 增加 `format: OutputFormat`，支持：
  - `PLAIN_TEXT_TREE`：返回给模型的缩进文本
  - `JSON`：返回结构化（SnapshotResult/refs/stats）
- 提供一个“单入口”渲染方法：一次输入 snapshot JSON，输出：
  - `text`（当 format=PLAIN_TEXT_TREE）
  - `refs`（ref -> 节点信息）
  - `stats`（JS stats + Kotlin 侧裁剪/预算的结果摘要）

## DoD

- JVM：单测覆盖 format 分支（PLAIN_TEXT_TREE/JSON）与结果字段不为空（至少 refs/stats 可回收）。
- Android：E2E 使用新入口仍能跑通并产生同等人类可感知证据截图。

