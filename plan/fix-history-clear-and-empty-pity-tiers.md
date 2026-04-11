## 任务

修复三个问题：

1. 历史 GUI 的“清空历史”按钮点击无反应
2. 当配置为 `pity.tiers: {}` 时，GUI 仍然显示一个保底档位
3. 历史记录物品应显示抽中奖励的图标，而不是仅显示宝箱图标

## 已定位的问题

### 1. 清空历史按钮没有实现动作

- 文件：`src/main/resources/guis/history.yml`
- 当前 `X` 按钮配置了 `action: clear_history`
- 但文件：`src/main/java/gg/fotia/crates/listener/GuiListener.java`
- `handleAction(...)` 里没有 `clear_history` 分支

结论：
- 按钮能被点击
- 但没有任何后续逻辑，所以表现为“没反应”

### 2. `tiers: {}` 仍然被兼容逻辑补出一个保底档位

- 文件：`src/main/java/gg/fotia/crates/crate/CrateManager.java`
- 当前加载逻辑：
  - 先读取 `pity.tiers`
  - 如果 `pityTiers` 为空且 `pity.enabled = true`
  - 就继续回退到旧版 `pity.count` + `pity.rarity`

这会导致：
- 即使配置里显式写了 `tiers: {}`
- 只要还保留了旧版 `count/rarity`
- 仍然会被自动组装成一个保底档位

结论：
- 你现在看到的“明明没设置保底等级，GUI 里还是有一个保底物品”
- 根因不在 GUI 文案
- 而在 crate 配置加载阶段

### 3. 历史记录物品当前使用的是宝箱图标

- 文件：`src/main/java/gg/fotia/crates/gui/GuiManager.java`
- 方法：`createHistoryItem(HistoryManager.HistoryEntry entry)`
- 当前行为：
  - 根据 `crate_id` 取宝箱
  - 直接用宝箱方块材质作为历史记录图标

而历史数据里本身已经有：

- `crate_id`
- `reward_id`
- `reward_name`

结论：
- 可以优先根据 `crate_id + reward_id` 找到当前奖励配置
- 然后使用该奖励的 `displayItem` 作为历史 GUI 图标
- 如果奖励已被删除或找不到，再回退到宝箱图标或默认物品

## 本次实现计划

### 1. 实现历史清空动作

- 在 `HistoryManager` 增加历史删除方法
- 在 `GuiListener#handleAction(...)` 接入 `clear_history`
- 清空范围按“当前界面所显示的范围”处理：
  - 如果当前历史 GUI 是按宝箱过滤打开的，只清空该玩家该宝箱的历史
  - 如果当前历史 GUI 是普通历史页，则清空该玩家全部历史

### 2. 处理清空后的界面反馈

- 清空成功后刷新当前历史 GUI
- 使用语言文件发送成功提示
- 复用已有权限逻辑：
  - 清自己的历史，沿用已有 `fotiacrates.history`
  - 清别人当前正在查看的历史，沿用已有 `fotiacrates.history.others`

### 3. 修复空 `tiers` 的旧版兼容回退

- 调整 `CrateManager` 的保底配置读取逻辑
- 仅当 `pity.tiers` 节点“完全不存在”时，才回退到旧版 `count/rarity`
- 如果 `pity.tiers` 明确存在但为空对象 `{}`：
  - 视为“当前没有任何保底档位”
  - 不再自动补一个兼容档位

### 4. 调整历史记录物品图标

- 修改 `GuiManager#createHistoryItem(...)`
- 优先从当前宝箱奖励列表中按 `reward_id` 查找奖励
- 找到时使用奖励的 `displayItem` 作为图标
- 保留当前历史名称、时间等 lore 展示
- 如果找不到对应奖励：
  - 回退到宝箱图标
  - 再兜底为默认材质，避免空指针或空气物品

### 5. 保持兼容性

- 老配置如果没有 `pity.tiers` 节点，仍然继续兼容旧版单级保底
- 新配置如果明确使用 `tiers: {}`，则严格按“无保底档位”处理

### 6. 更新语言文件

- 新增“历史已清空”相关提示
- 同步更新：
  - `src/main/resources/lang/zh_CN.yml`
  - `src/main/resources/lang/en_US.yml`

## 预期结果

- 历史 GUI 的清空按钮变为可用
- 从预览页打开的“当前宝箱历史”只清空当前宝箱历史
- 普通 `/crate history` 页面清空全部历史
- 历史记录条目优先显示对应奖励的图标
- `pity.tiers: {}` 时不再在 GUI 中出现伪造的默认保底档位

## 待确认

这次我默认：

- “清空历史”清的是当前页面正在看的历史范围，而不是始终全清
- 不新增新的清空权限节点，直接沿用现有历史查看权限体系

确认后开始开发。
