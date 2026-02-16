明白，你要从 agent-browser 的源码里把所有测试用例抽出来，整理成一份测试清单，这样写 agent-browser-kotlin 的兄弟可以直接对照着写测试。

让我先去看看它的测试文件结构。

看到了，测试文件有这些：
- `actions.test.ts`
- `browser.test.ts`
- `daemon.test.ts`
- `encryption.test.ts`
- `ios-manager.test.ts`
- `protocol.test.ts`
- `state-utils.test.ts`
- `stream-server.test.ts`

让我逐个打开看内容。先从和我们 PRD 最相关的几个开始：

好，actions.test.ts 看完了。接着看其他测试文件：

好，browser.test.ts 拿到了。继续看其他测试文件：

好，所有 8 个测试文件都读完了。让我关闭浏览器，整理测试清单。

柠檬叔，整理好了。下面是从 agent-browser 全部 8 个测试文件中抽取的完整测试清单，按模块分组，标注了哪些和你们 agent-browser-kotlin 直接相关（★），哪些是 Playwright/Node 特有的可以跳过（○）。

---

## agent-browser 测试清单（供 agent-browser-kotlin 对照）

### 一、actions.test.ts — 错误处理与 AI 友好错误消息

| # | describe | it | 与 kotlin 库相关性 |
|---|---|---|---|
| 1 | toAIFriendlyError > element blocked by overlay | should detect intercepts pointer events even when Timeout is in message | ★ 需要适配：WebView 场景下元素被遮挡时，action 返回的错误消息应包含 "blocked by another element" + "modal or overlay"，不能说 "not found" |
| 2 | toAIFriendlyError > element blocked by overlay | should suggest dismissing cookie banners | ★ 错误消息应包含 "cookie banners" 提示 |

核心启示：action 失败时，错误消息要对模型友好，区分"找不到元素"和"元素被遮挡"。

---

### 二、browser.test.ts — 浏览器管理 + Snapshot + Action 交互

#### 2.1 launch and close（○ Playwright 特有，跳过）

| # | it | 说明 |
|---|---|---|
| 3 | should report as launched | ○ |
| 4 | should have a page | ○ |
| 5 | should reject invalid executablePath | ○ |
| 6 | should be no-op when relaunching with same options | ○ |
| 7 | should reconnect when CDP port changes | ○ |

#### 2.2 stale session recovery（○ Playwright 特有）

| # | it | 说明 |
|---|---|---|
| 8 | should recover when all pages are closed externally | ○ |
| 9 | should be a no-op when pages already exist | ○ |

#### 2.3 scrollintoview with refs（★ 直接相关）

| # | it | kotlin 测试要点 |
|---|---|---|
| 10 | should resolve refs in scrollintoview command | ★ snapshot 后拿到 ref → scrollIntoView(ref) 成功 |
| 11 | should resolve refs in scroll command with selector | ★ scroll 命令支持 ref 作为 selector |

#### 2.4 cursor-ref selector uniqueness（★ 直接相关）

| # | it | kotlin 测试要点 |
|---|---|---|
| 12 | should produce unique selectors for repeated DOM structures | ★ 深层嵌套的重复 DOM 结构中，cursor-interactive 元素的 ref 必须唯一，定位不能歧义 |
| 13 | should click the correct element when refs have repeated structure | ★ 点击 ref 后验证操作的是正确元素（不是同结构的另一个） |

#### 2.5 navigation（○ Playwright 特有）

| # | it | 说明 |
|---|---|---|
| 14 | should navigate to URL | ○ |
| 15 | should get page title | ○ |

#### 2.6 element interaction（○ Playwright 特有）

| # | it | 说明 |
|---|---|---|
| 16 | should find element by selector | ○ |
| 17 | should check element visibility | ○ |
| 18 | should count elements | ○ |

#### 2.7 screenshots（○ 跳过）

| # | it | 说明 |
|---|---|---|
| 19 | should take screenshot as buffer | ○ |

#### 2.8 evaluate（○ Playwright 特有）

| # | it | 说明 |
|---|---|---|
| 20 | should evaluate JavaScript | ○ |
| 21 | should evaluate with arguments | ○ |

#### 2.9 tabs（○ Playwright 特有）

| # | it | 说明 |
|---|---|---|
| 22-25 | create/list/close/auto-switch tab | ○ |

#### 2.10 context operations（○ Playwright 特有）

| # | it | 说明 |
|---|---|---|
| 26-30 | cookies get/set/clear | ○ |

#### 2.11 localStorage / sessionStorage（○ Playwright 特有）

| # | it | 说明 |
|---|---|---|
| 31-37 | localStorage/sessionStorage CRUD | ○ |

#### 2.12 viewport（○ Playwright 特有）

| # | it | 说明 |
|---|---|---|
| 38 | should set viewport | ○ |

#### 2.13 snapshot（★★★ 核心，全部需要对照）

| # | it | kotlin 测试要点 |
|---|---|---|
| 39 | should get snapshot with refs | ★ snapshot 输出包含 heading、文本内容，refs 是 object |
| 40 | should get interactive-only snapshot | ★ interactive=true 时输出 ≤ 全量输出 |
| 41 | should get snapshot with depth limit | ★ maxDepth 限制后行数 ≤ 全量 |
| 42 | should get compact snapshot | ★ compact=true 时输出 ≤ 全量 |
| 43 | should not capture cursor-interactive elements without cursor flag | ★ 无 cursor flag 时：标准 button 有 ref，cursor:pointer 的 div 没有 ref，不出现 "clickable" 角色 |
| 44 | should capture cursor-interactive elements with cursor flag | ★ 有 cursor flag 时：出现 "Cursor-interactive elements" 区域，clickable div 和 onclick span 都有 ref |
| 45 | should click cursor-interactive elements via refs | ★ cursor-interactive 元素拿到 ref 后，click 能触发真实行为 |

#### 2.14 locator resolution（部分相关）

| # | it | 说明 |
|---|---|---|
| 46 | should resolve CSS selector | ○ Playwright 特有 |
| 47 | should resolve ref from snapshot | ★ snapshot 后 ref 可用于定位 |

#### 2.15 scoped headers（○ Playwright 特有）

| # | it | 说明 |
|---|---|---|
| 48-54 | setScopedHeaders / clearScopedHeaders | ○ |

#### 2.16 CDP session（○ Playwright 特有）

| # | it | 说明 |
|---|---|---|
| 55-57 | CDP session create/reuse/filter | ○ |

#### 2.17 screencast（○ Playwright 特有）

| # | it | 说明 |
|---|---|---|
| 58-62 | screencast start/stop/options/duplicate/idle | ○ |

#### 2.18 tab switch invalidates CDP（○ Playwright 特有）

| # | it | 说明 |
|---|---|---|
| 63-65 | CDP invalidation on tab switch | ○ |

#### 2.19 input injection（○ Playwright CDP 特有）

| # | it | 说明 |
|---|---|---|
| 66-73 | mouse/keyboard/touch injection via CDP | ○ |

---

### 三、protocol.test.ts — 命令协议解析

| # | describe | 测试数量 | kotlin 相关性 |
|---|---|---|---|
| 74-76 | navigation (navigate/back/forward/reload) | 6 | ★ 部分：navigate 需要 url 校验 |
| 77-78 | click | 2 | ★ click 需要 selector/ref |
| 79 | type | 1 | ★ type 需要 selector + text |
| 80 | fill | 1 | ★ fill 需要 selector + value |
| 81-83 | wait | 3 | ★ wait 支持 selector/timeout/text |
| 84-86 | screenshot | 3 | ○ |
| 87-96 | cookies (get/set/clear) | 10 | ○ |
| 97-105 | storage (get/set/clear) | 9 | ○ |
| 106-111 | semantic locators (getbyrole/getbytext/getbylabel/getbyplaceholder) | 6 | ○ Playwright 特有 |
| 112-115 | tabs | 4 | ○ |
| 116-121 | snapshot 命令解析 | 6 | ★★ 全部：basic / interactive / compact / maxDepth / selector / all options |
| 122-128 | launch | 7 | ○ |
| 129-132 | mouse actions | 4 | ★ mousemove/mousedown/mouseup/wheel 参数校验 |
| 133-134 | scroll | 2 | ★ scroll direction+amount / scrollintoview+selector |
| 135-137 | element state (isvisible/isenabled/ischecked) | 3 | ★ 查询元素状态 |
| 138-139 | viewport/geolocation/offline | 3 | ○ |
| 140-141 | trace | 2 | ○ |
| 142-143 | console/errors | 3 | ○ |
| 144-146 | dialog | 3 | ○ |
| 147-148 | frame/mainframe | 2 | ○ 后续迭代 |
| 149-154 | screencast_start/stop | 6 | ○ |
| 155-170 | input_mouse | 8 | ○ CDP 特有 |
| 171-175 | input_keyboard | 5 | ★ 部分：keyDown/keyUp/char 参数校验 |
| 176-183 | input_touch | 8 | ○ |
| 184-186 | invalid commands | 3 | ★ unknown action / missing id / invalid JSON |

---

### 四、state-utils.test.ts — 会话状态管理与安全

| # | describe | 测试数量 | kotlin 相关性 |
|---|---|---|---|
| 187-202 | isValidSessionName | 16 | ○ Node 文件系统特有 |
| 203-212 | getAutoStateFilePath | 10 | ○ |
| 213-220 | safeHeaderMerge（防原型污染） | 8 | ○ |
| 221 | listStateFiles | 1 | ○ |
| 222-223 | cleanupExpiredStates | 2 | ○ |

---

### 五、daemon.test.ts — 守护进程安全

| # | describe | 测试数量 | kotlin 相关性 |
|---|---|---|---|
| 224-229 | HTTP request detection (security) | 6 | ○ Unix socket 特有 |
| 230-235 | getSocketDir | 6 | ○ |

---

### 六、encryption.test.ts — 状态加密

| # | describe | 测试数量 | kotlin 相关性 |
|---|---|---|---|
| 236-240 | encryptData / decryptData round-trip | 5 | ○ |
| 241-242 | IV uniqueness | 2 | ○ |
| 243-245 | authentication (tamper detection) | 3 | ○ |
| 246-247 | wrong key handling | 2 | ○ |
| 248-252 | malformed payload detection | 5 | ○ |
| 253-261 | getEncryptionKey | 9 | ○ |
| 262-272 | isEncryptedPayload | 11 | ○ |

---

### 七、stream-server.test.ts — WebSocket origin 校验

| # | describe | 测试数量 | kotlin 相关性 |
|---|---|---|---|
| 273-279 | allowed origins | 7 | ○ |
| 280-283 | rejected origins | 4 | ○ |

---

### 八、ios-manager.test.ts — iOS 模拟器管理

| # | describe | 测试数量 | kotlin 相关性 |
|---|---|---|---|
| 284-285 | listDevices | 2 | ○ |
| 286 | isLaunched | 1 | ○ |
| 287-290 | getRefData（ref 解析：@e1 / ref=e2 / e3 格式） | 4 | ★ ref 格式解析逻辑：支持 @e1、ref=e1、e1 三种写法 |
| 291-293 | integration (skip) | 3 | ○ |

---

## 汇总：agent-browser-kotlin 需要重点对照的测试

按优先级排列：

### P0 — Snapshot 核心

| 编号 | 测试点 | 来源 |
|---|---|---|
| S-01 | snapshot 输出包含语义角色（heading/link/button）和文本内容 | browser.test #39 |
| S-02 | refs 是 Map 结构，key 为 ref 字符串 | browser.test #39 |
| S-03 | interactive=true 时输出长度 ≤ 全量输出 | browser.test #40 |
| S-04 | maxDepth 限制后输出行数 ≤ 全量 | browser.test #41 |
| S-05 | compact=true 时输出长度 ≤ 全量 | browser.test #42 |
| S-06 | 无 cursor flag：标准 button 有 ref，cursor:pointer div 无 ref | browser.test #43 |
| S-07 | 有 cursor flag：cursor:pointer div 和 onclick span 都有 ref，角色为 clickable | browser.test #44 |
| S-08 | snapshot 命令支持 interactive / compact / maxDepth / selector 参数组合 | protocol.test #116-121 |
| S-09 | maxCharsTotal 预算截断：输出 ≤ 预算，truncated=true，truncateReasons 正确 | PRD 自有 |
| S-10 | maxNodes 预算截断 | PRD 自有 |
| S-11 | 不可见元素跳过（display:none / visibility:hidden / opacity:0 / aria-hidden=true） | PRD 自有 |
| S-12 | compact 剪枝：无 ref 子孙的结构节点被移除 | PRD 自有 |

### P0 — Action 核心

| 编号 | 测试点 | 来源 |
|---|---|---|
| A-01 | click ref 后触发真实行为（验证 DOM 变化） | browser.test #45, #13 |
| A-02 | 重复 DOM 结构中 ref 唯一，click 正确元素 | browser.test #12, #13 |
| A-03 | scrollIntoView(ref) 成功 | browser.test #10 |
| A-04 | scroll 命令支持 ref 作为 selector | browser.test #11 |
| A-05 | ref_not_found 时返回明确错误，不崩溃 | PRD 自有 |
| A-06 | fill 触发 input + change 事件（React/Vue 兼容） | PRD 自有 |
| A-07 | select 按 value 或 textContent 匹配 | PRD 自有 |
| A-08 | check/uncheck checkbox 状态正确 | PRD 自有 |
| A-09 | 元素被遮挡时错误消息包含 "blocked by another element" | actions.test #1 |
| A-10 | cookie banner 遮挡时提示 "cookie banners" | actions.test #2 |

### P0 — Ref 生命周期

| 编号 | 测试点 | 来源 |
|---|---|---|
| R-01 | 重新 snapshot 后旧 ref 失效（data-agent-ref 被清除重分配） | PRD 自有 |
| R-02 | ref 格式解析：支持 @e1、ref=e1、e1 三种写法 | ios-manager.test #287-290 |
| R-03 | 同一输入同一 options 下 refs 顺序一致（幂等性） | PRD 自有 |

### P1 — 协议解析

| 编号 | 测试点 | 来源 |
|---|---|---|
| P-01 | click 命令需要 ref/selector，缺失时失败 | protocol.test #77-78 |
| P-02 | fill 命令需要 ref + value | protocol.test #80 |
| P-03 | scroll 命令需要 direction + amount | protocol.test #133 |
| P-04 | 未知 action 返回失败 | protocol.test #184 |
| P-05 | 缺少 id 返回失败 | protocol.test #185 |
| P-06 | 非法 JSON 返回失败 | protocol.test #186 |

### P1 — 边界情况

| 编号 | 测试点 | 来源 |
|---|---|---|
| E-01 | 空 DOM（body 无子元素） | PRD 自有 |
| E-02 | 纯文本页面（无交互元素） | PRD 自有 |
| E-03 | 深度嵌套（超过 maxDepth） | PRD 自有 |
| E-04 | 超大 DOM（500+ 节点）预算控制 | PRD 自有 |
| E-05 | 文本截断（超过 maxTextPerNode） | PRD 自有 |
| E-06 | 属性值截断（超过 maxAttrValueLen） | PRD 自有 |

---

总计 agent-browser 原始测试约 290 个 case，其中和 agent-browser-kotlin 直接相关的约 30 个（上面 ★ 标记的），加上 PRD 自有的边界测试约 12 个，你们兄弟需要写的测试大概 42 个 case。

核心就三块：snapshot 输出正确性（12）、action 交互正确性（10）、ref 生命周期 + 协议解析 + 边界（20）。这份清单直接丢给他就行。