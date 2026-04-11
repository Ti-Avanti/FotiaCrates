## 任务

修复保底设置 GUI 的空状态显示问题。

## 已确认的问题

- 当前打开“保底设置”GUI时
- 如果该宝箱还没有添加任何保底等级
- GUI 里的说明区域仍会展示稀有度列表
- 这会让界面表现得像已经默认设置了“最低保底稀有度”
- 但实际需求是：只有玩家手动添加保底等级之后，才开始设置具体的保底稀有度

## 已定位的代码位置

### 1. 保底编辑 GUI 入口

- 文件：`src/main/java/gg/fotia/crates/gui/GuiManager.java`
- 方法：`openPityEditGui(Player player, Crate crate)`

### 2. 保底说明文案生成

- 文件：`src/main/java/gg/fotia/crates/gui/GuiManager.java`
- 方法：`buildPityInfoLoreSafe(Crate crate)`
- 当前行为：
  - 有保底档位时，展示当前档位
  - 没有保底档位时，仍展示全部可用稀有度

### 3. 添加保底等级的默认稀有度

- 文件：`src/main/java/gg/fotia/crates/listener/GuiListener.java`
- 方法：`handlePityEditClick(...)`
- 当前添加档位时才会调用 `getDefaultPityRarityId()`
- 这部分逻辑本身符合需求，不需要改动

## 本次实现计划

- 调整 `buildPityInfoLoreSafe(Crate crate)` 的空状态显示
- 当 `crate.getPityTiers()` 为空时：
  - 不显示任何“当前保底稀有度”或“可用稀有度列表”
  - 改为显示“当前未设置保底等级，请先添加保底等级”的提示
- 当已经存在至少一个保底等级时：
  - 继续显示当前保底档位
  - 继续显示可用稀有度列表，方便右键切换时参考

## 预期结果

- 未添加保底等级时，GUI 不再给人一种已经默认设置最低稀有度的错觉
- 添加第一条保底等级后，才开始显示具体稀有度相关内容
- 不修改实际保底触发逻辑
- 不新增配置项

## 待确认

这次我默认只修“保底设置 GUI 的空状态说明”，不改别的交互和数据结构。

确认后开始开发。
