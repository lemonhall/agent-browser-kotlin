# v2-perf-budget：REQ-0002-002 的证据口径

## PRD Trace

- REQ-0002-002：对真实大页面（新闻首页、电商商品页），`snapshot.js` 执行时间 < 100ms，输出 JSON < 100KB。

## v2 策略

先把“证据口径”稳定下来，再逐步扩展到真实站点：

1. **离线 stress fixture**（可提交到仓库，避免网络与版权不确定性）
   - 生成大量节点（list/table/card 混合），并包含少量可交互节点
   - 作为固定基准：确保 `interactiveOnly=true` 时 JSON 严格 < 100KB
2. **真实站点**（后续 v3）
   - 通过可控的抓取/缓存方式固定输入（或仅做手动测试 + 视频证据）

## E2E-PERF-1（离线基准）

在 instrumentation test 中：

- `snapshot(interactiveOnly=true)`：
  - 断言：`rawJson.length < 100 * 1024`
  - 记录：JS 侧 `jsTimeMs`（建议放入 snapshot stats 内部字段）

### 验证命令

- `.\gradlew :app:connectedAndroidTest --no-daemon`

### 证据

- `docs/evidence/` 新增一份当日 evidence（命令 + 关键输出摘要）

