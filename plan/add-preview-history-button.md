## 任务

在奖品预览界面新增一个按钮，用于打开当前宝箱的抽奖历史 GUI。

## 已定位的现状

### 1. 预览 GUI 已支持固定按钮和动作

- 文件：`src/main/resources/guis/preview.yml`
- 现状：预览界面使用布局字符和 `action` 驱动，当前已有：
  - `prev_page`
  - `next_page`
  - `open_crate`

### 2. 预览 GUI 点击已经走统一动作分发

- 文件：`src/main/java/gg/fotia/crates/listener/GuiListener.java`
- 现状：`handlePreviewClick(...)` 会读取 `preview.yml` 中按钮的 `action`，再交给 `handleAction(...)` 处理。

### 3. 历史 GUI 已存在

- 文件：`src/main/java/gg/fotia/crates/gui/GuiManager.java`
- 已有：`openHistoryGui(Player player, UUID targetUuid, String targetName, int page)`

## 本次实现计划

- 在 `preview.yml` 中新增一个历史按钮
- 为该按钮增加新动作，例如：`open_history`
- 在 `GuiListener#handleAction(...)` 中接入该动作
- 点击后打开当前玩家在当前宝箱相关的历史界面

## 设计说明

- 按钮放在预览 GUI 底部工具栏区域
- 复用当前玩家身份打开历史 GUI
- 如果预览 GUI 中 `holder.getCrate()` 为空，则不执行

## 待确认

当前历史 GUI 展示的是玩家的抽奖总历史，不区分某一个宝箱。

这次我默认先做成：
- 从预览界面点进去后，打开“该玩家的历史 GUI”，仍显示全部历史

如果你希望它只显示“当前这个宝箱”的历史，那就不是加一个按钮就够了，还需要扩展历史 GUI/数据查询过滤逻辑。
