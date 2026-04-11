## 任务

在奖品预览界面新增一个“历史”按钮，点击后打开“当前玩家在当前宝箱上的抽奖历史 GUI”。

## 已确认的需求

- 按钮入口放在宝箱预览界面
- 打开的不是玩家全部历史
- 只显示当前宝箱的历史记录
- 需要继续复用现有历史 GUI，而不是改成命令输出

## 当前代码现状

### 1. 历史数据已经带有宝箱维度

- 文件：`src/main/java/gg/fotia/crates/history/HistoryManager.java`
- 当前 `crate_history` 表已经保存 `crate_id`
- 现有查询 `getHistory(UUID uuid, int limit)` 只按玩家查，没有按宝箱过滤

### 2. 历史 GUI 已存在，但上下文只有玩家

- 文件：`src/main/java/gg/fotia/crates/gui/GuiManager.java`
- 当前入口：`openHistoryGui(Player player, UUID targetUuid, String targetName, int page)`
- 当前 holder 里只保存：
  - `target_uuid`
  - `target_name`
- 分页时也只会继续按“玩家全部历史”翻页

### 3. 预览 GUI 已支持动作按钮

- 文件：`src/main/resources/guis/preview.yml`
- 文件：`src/main/java/gg/fotia/crates/listener/GuiListener.java`
- 预览界面固定按钮已经通过 `action` 分发
- 可以新增一个 `open_history` 动作接到当前预览的 `crate`

## 本次实现计划

### 1. 给历史查询增加按宝箱过滤能力

- 在 `HistoryManager` 新增按 `uuid + crateId` 查询的方法
- 保留原有全历史查询，避免影响 `/crate history` 和其他已有入口

### 2. 扩展历史 GUI 上下文

- 扩展 `openHistoryGui(...)`，支持可选的 `crateId`
- 在 GUI holder 中保存当前历史页面绑定的 `crate_id`
- 翻页时继续带上 `crate_id`，保证分页结果仍然只看当前宝箱

### 3. 调整历史 GUI 展示信息

- 历史总数、内容列表、翻页结果都改为基于过滤后的数据
- 标题或占位符保持兼容；如果现有标题不包含宝箱名，则先不额外新增配置项

### 4. 在预览 GUI 增加历史按钮

- 修改 `preview.yml` 增加按钮位和 `open_history` 动作
- `GuiListener#handleAction(...)` 接入该动作
- 点击时读取当前预览持有的 `crate`，打开当前玩家在该宝箱下的历史 GUI

## 边界处理

- 如果预览 GUI 没有关联宝箱，则点击历史按钮不执行跳转
- 如果该玩家在该宝箱没有历史记录，仍然打开空历史界面，由现有空列表表现处理
- `/crate history` 保持现有行为，不强制改成按宝箱过滤

## 待确认

这次我默认：

- 只从“预览界面新增的历史按钮”进入按宝箱过滤历史
- 原 `/crate history` 仍然显示玩家全部历史
- 历史 GUI 标题先继续沿用现有配置，不新增 `{crate}` 占位符

确认后开始开发。
