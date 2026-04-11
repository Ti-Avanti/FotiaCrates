## 任务

调整宝箱交互方式，并将抽奖历史改为 GUI 打开，完成后进行编译打包。

## 已确认需求

1. `潜行 + 右键宝箱` 改为十连抽
2. `潜行 + 左键宝箱` 改为移除宝箱
3. 抽奖历史需要打开 GUI
4. 修改完成后编译打包

## 已定位的现状

### 1. 当前潜行右键是删除宝箱

- 文件：`src/main/java/gg/fotia/crates/listener/BlockListener.java`
- 现状：`RIGHT_CLICK_BLOCK` 和 `PlayerInteractAtEntity` 中，潜行交互会直接删除宝箱。

### 2. 当前左键是预览

- 文件：`src/main/java/gg/fotia/crates/listener/BlockListener.java`
- 现状：普通左键打开预览 GUI。
- 需要调整为：
  - 普通左键仍保留预览
  - `潜行 + 左键` 才删除宝箱

### 3. 十连抽入口目前只有命令

- 文件：`src/main/java/gg/fotia/crates/command/subcommand/OpenCommand.java`
- 现状：`/crate open <crate> 10` 已支持多连抽。
- 需要把这套逻辑接到 `潜行 + 右键` 的交互流程上。

### 4. 历史 GUI 已存在，但命令还在聊天输出

- 文件：
  - `src/main/java/gg/fotia/crates/command/subcommand/HistoryCommand.java`
  - `src/main/java/gg/fotia/crates/gui/GuiManager.java`
- 现状：`openHistoryGui(...)` 已实现，但 `HistoryCommand` 仍输出文本历史。
- 计划：玩家执行 `/crate history` 时打开 GUI；控制台仍保留文本模式或直接提示不可用。

## 本次实现计划

### 交互调整

- 修改方块交互和 ModelEngine 实体交互：
  - `潜行 + 右键` -> 十连抽
  - `潜行 + 左键` -> 删除宝箱
  - 普通右键 -> 单抽
  - 普通左键 -> 预览

### 十连抽逻辑

- 复用现有多连抽逻辑
- 尊重：
  - `multi-open.enabled`
  - `multi-open.max`
  - 钥匙数量
  - 开箱权限

### 历史 GUI

- 玩家执行 `/crate history`
  - 自己查看时打开自己的历史 GUI
  - 查看别人时，如有权限则打开目标玩家历史 GUI
- GUI 分页沿用现有实现

### 编译打包

- 修改完成后执行 Maven 打包
- 不运行服务器，不做额外测试

## 需要你确认的一点

关于 `潜行 + 右键 = 十连抽`：

- 如果某个宝箱 `multi-open.enabled: false`，我计划回退为单抽，而不是报错。
- 如果你更希望这种情况下直接提示“该宝箱未启用多连抽”，我可以按这个行为做。
