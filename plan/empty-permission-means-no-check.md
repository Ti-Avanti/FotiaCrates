## 任务

将宝箱配置中的 `permission` 语义调整为：

- 配置了权限节点时：按该权限检测
- 留空时：不做任何开箱权限检测

## 已确认的需求

- `permission: ""` 不再回退到 `fotiacrates.open.<crateId>`
- 留空即代表“无需权限即可打开该宝箱”
- 该行为需要在所有开箱入口保持一致

## 已定位的代码位置

### 1. 共享开箱权限判断

- 文件：`src/main/java/gg/fotia/crates/crate/CrateOpenService.java`
- 方法：`hasOpenPermission(Player player, Crate crate)`
- 当前行为：
  - 有 `fotiacrates.open.*` 直接通过
  - `permission` 留空时，回退检查 `fotiacrates.open.<crateId>`
  - 配置了自定义权限时，检查该权限

### 2. GUI 内部直接开箱逻辑

- 文件：`src/main/java/gg/fotia/crates/listener/GuiListener.java`
- 方法：`openCrate(Player player, Crate crate)`
- 当前行为：
  - 这里仍然直接硬编码检查 `fotiacrates.open.<crateId>` / `fotiacrates.open.*`
- 这会导致即使共享服务改了，GUI 打开抽奖箱的路径仍可能和命令/方块交互不一致

### 3. 配置注释与代码行为不一致

- 文件：
  - `src/main/resources/crates/common.yml`
  - `src/main/java/gg/fotia/crates/crate/CrateManager.java`
- 当前注释仍然写的是：
  - 留空则使用默认权限 `fotiacrates.open.<crateId>`

## 本次实现计划

### 1. 修改默认权限语义

- 在 `CrateOpenService#hasOpenPermission(...)` 中调整逻辑：
  - 如果玩家有 `fotiacrates.open.*`，仍然直接通过
  - 如果 `crate.getPermission()` 为空，直接返回 `true`
  - 如果配置了权限节点，检查该权限

### 2. 统一 GUI 内部开箱路径

- 修改 `GuiListener#openCrate(...)`
- 不再硬编码检查 `fotiacrates.open.<crateId>`
- 统一复用共享权限判断逻辑，避免再次出现入口分叉

### 3. 更新配置说明

- 修改默认示例宝箱配置注释
- 修改 `CrateManager` 中对应注释
- 让代码和配置说明保持一致：
  - 留空 = 不检测权限

## 预期结果

- 命令开箱、点击宝箱、GUI 中的打开按钮，权限行为统一
- `permission: ""` 的宝箱任何玩家都可以开
- 只有显式配置了权限节点的宝箱才做权限校验
- 示例配置和代码注释与实际行为一致

## 待确认

这次我默认只改“宝箱自身的开箱权限”这一层，不会改命令权限本身。

例如：

- `/crate open` 作为命令仍然需要命令节点权限
- 但进入具体宝箱后，是否允许打开，由宝箱 `permission` 配置决定

确认后开始开发。
