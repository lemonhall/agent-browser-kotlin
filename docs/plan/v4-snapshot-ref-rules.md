# v4-snapshot-ref-rules：CONTENT refs + attrs/query 对齐

## Scope

- Snapshot：
  - interactiveOnly=false 时，为 CONTENT 节点分配 ref（heading/listitem/img/p 等）
  - 保持 STRUCTURAL 节点不分配 ref（仅用于结构）
- Query：
  - `attrs` 改为全量 attributes（PRD-V4 5.4 口径）
- Stats：
  - 增加 `skippedHidden`、`domNodes`（最小集，便于解释压缩率）

## DoD

- JVM 单测：渲染输出中可看到内容节点的 `[ref=...]`，且 attrs/query 可解析
- 真机 E2E：滚动页面 + snapshot 看到内容节点 ref（人类可感知）

