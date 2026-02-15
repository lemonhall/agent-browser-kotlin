好，给你拟一份 deep research 的指引：

---

## 研究课题：`Element.computedRole` / `Element.computedName` 在 Android WebView 中的可用性

### 1. 核心问题

我们计划在 Android WebView 中注入 JS，通过 `Element.computedRole` 和 `Element.computedName` 获取浏览器计算好的 accessibility tree 信息，用于构建页面 snapshot。需要确认这两个 API 在目标环境中是否可靠可用。

### 2. 需要调查的具体问题

**2.1 API 规范状态**
- `computedRole` 和 `computedName` 属于哪个规范？（AOM - Accessibility Object Model）
- 当前规范状态是什么？（草案 / 已发布 / 已废弃？）
- 是否有被废弃或替换的风险？如果有，替代方案是什么？

**2.2 Chromium 实现状态**
- 这两个属性从 Chromium 哪个版本开始支持？
- 是否需要 feature flag 才能启用？还是默认开启？
- Chrome Status（chromestatus.com）上的 feature entry 是什么状态？
- 是否有已知的 bug 或限制？（比如某些元素返回空字符串、某些 ARIA 模式不支持等）

**2.3 Android WebView 的对应关系**
- Android WebView 的 Chromium 版本和 Android 系统版本的对应关系是什么？
- 具体来说：
  - Android 10 (API 29) 对应的 WebView Chromium 版本范围？
  - Android 11 (API 30)？
  - Android 12 (API 31)？
  - Android 13 (API 33)？
  - Android 14 (API 34)？
  - Android 15 (API 35)？
- Android WebView 是通过 Google Play 独立更新的（Android 7+），所以实际版本可能远高于系统出厂版本——这个机制需要说明清楚
- 是否存在某些厂商（华为、小米等）的 WebView 不走 Google Play 更新、使用自研内核的情况？国内市场这个问题尤其重要

**2.4 实际兼容性验证数据**
- MDN 上这两个属性的 browser compatibility table 怎么说？
- caniuse.com 上有没有对应条目？
- 有没有来自社区的实测报告（Stack Overflow、GitHub issues 等）？
- 特别关注：有没有人报告过在 Android WebView 中这两个 API 返回 `undefined` 的情况？

**2.5 降级方案调研**
- 如果 `computedRole` / `computedName` 不可用，有哪些 JS 侧的 polyfill 或替代方案？
  - `aria-utils` / `accname` 等库？
  - 手动实现 WAI-ARIA implicit role 映射 + accessible name computation？
  - 这些替代方案的准确度和性能如何？
- Playwright 的 `ariaSnapshot()` 内部是怎么实现的？是用的 CDP（Chrome DevTools Protocol）的 `Accessibility.getFullAXTree` 还是 JS 侧的 `computedRole`？这个对我们理解"正确做法"有参考价值

### 3. 期望输出

一份结论清晰的报告，包含：

1. **结论**：能用 / 不能用 / 有条件能用（附最低版本要求）
2. **覆盖率估算**：在中国 Android 市场（考虑华为鸿蒙、各厂商 WebView 更新策略），大约能覆盖多少比例的用户？
3. **降级策略推荐**：如果需要 fallback，推荐哪种方案？成本多大？
4. **风险清单**：已知的坑、edge case、未来可能的变化

### 4. 推荐的调研起点

- https://chromestatus.com → 搜索 "computedRole" / "computedName" / "Accessibility Object Model"
- https://developer.mozilla.org/en-US/docs/Web/API/Element/computedRole
- https://github.com/nicolo-ribaudo/tc39-proposal-aom（或 WICG/aom）
- https://source.chromium.org → 搜索 computedRole 的实现
- https://bugs.chromium.org → 相关 bug
- Playwright 源码中 `ariaSnapshot` 的实现路径（确认它走的是 CDP 还是 JS API）
- Android WebView 版本发布记录：https://chromiumdash.appspot.com/releases?platform=Android%20WebView
- 国内 Android WebView 生态的分析文章（中文社区、知乎、掘金等）

---

这份指引覆盖了从规范层面到国内市场实际情况的完整链路。deep researcher 拿到这个应该能给你一份靠谱的结论。