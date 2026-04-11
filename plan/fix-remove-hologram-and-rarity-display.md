## 任务

修复两个已确认问题：

1. 使用 `/crate remove` 删除宝箱时，全息显示未同步删除。
2. 稀有度相关显示和切换逻辑未完全按照 `config.yml` 中的 `rarities` 配置工作。

## 已定位的问题

### 1. 删除命令遗漏全息清理

- 文件：`src/main/java/gg/fotia/crates/command/subcommand/RemoveCommand.java`
- 现状：命令删除只调用了 ModelEngine 模型移除和宝箱位置移除，没有调用 `HologramManager#removeHologram(Location)`。
- 结果：命令删除后，全息可能残留。

### 2. 稀有度逻辑仍有硬编码

- 文件：`src/main/java/gg/fotia/crates/listener/GuiListener.java`
- 现状：保底档位右键切换稀有度时，仍写死 `common/uncommon/rare/epic/legendary`。

- 文件：`src/main/java/gg/fotia/crates/crate/Crate.java`
- 现状：稀有度比较顺序仍使用硬编码等级，不跟随 `config.yml` 中 `rarities` 的定义顺序。

- 文件：`src/main/java/gg/fotia/crates/gui/GuiManager.java`
- 现状：部分界面文案示例仍写死旧稀有度示例；稀有度图标材质也仍是代码内映射。

## 本次修复范围

### 必修

- 在 `/crate remove` 路径中补上全息清理。
- 将保底稀有度切换改为读取 `ConfigManager#getRarityIds()`。
- 将保底稀有度比较顺序改为基于 `config.yml` 中 `rarities` 的顺序。
- 将界面中写死的稀有度示例文案改为与当前配置一致，避免和配置脱节。

### 本次不扩展

- 不新增“稀有度材质”配置项。
- 现有稀有度图标材质仍保留代码默认映射，仅修复“名称/颜色/顺序/切换”与配置不一致的问题。

## 预期结果

- `/crate remove` 删除宝箱后，不再残留全息。
- GUI 中稀有度名称、颜色、切换顺序、保底判断顺序，与 `config.yml -> rarities` 保持一致。
