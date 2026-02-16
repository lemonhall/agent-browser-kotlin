# v12：tests-checklist 摘差（继续）

来源：`docs/prd/archive/tests-checklist.md`

## 本轮覆盖点（★）

### 1) input_keyboard（★）

- 增加更细粒度能力：`keyDown` / `keyUp` / `char`
- 以离线页面（asset）监听事件并渲染到 DOM 作为验收信号
- 以 connectedAndroidTest 录屏帧 + snapshot txt/json 作为“人类可感知证据”

### 2) element state queries（★）

- 增加 `query(isvisible|isenabled|ischecked)`（或等价 wire）能力
- 以离线页面构造：disabled button、checkbox、可切换隐藏元素
- 以 connectedAndroidTest 验收返回值与 UI 变化一致

