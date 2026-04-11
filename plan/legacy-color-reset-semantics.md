## 任务

调整 legacy 颜色码转 MiniMessage 的行为，让以下颜色格式在转换后带有“reset 语义”，更接近原版 Minecraft：

- `&0-9a-f`
- `&#RRGGBB`
- `&x&R&R&G&G&B&B`

## 已定位的代码位置

### 1. 转换入口

- 文件：`src/main/java/gg/fotia/crates/util/MessageUtil.java`
- 方法：`parse(String message)`
- 当前行为：
  - 如果文本里包含 `&` 或 `§`
  - 就调用 `LegacyColorConverter.convertToMiniMessage(message)`

### 2. 具体转换实现

- 文件：`src/main/java/gg/fotia/crates/util/LegacyColorConverter.java`
- 当前行为：
  - `&a` -> `<green>`
  - `&#ff0000` -> `<#ff0000>`
  - `&x&f&f&0&0&0&0` -> `<#ff0000>`
  - `&l/&n/&o/...` 单独转为对应 MiniMessage 装饰标签
  - `&r` 转为 `<reset>`

## 当前问题

现在颜色码只是在做“颜色映射”，没有体现 legacy 颜色码本身的 reset 行为。

例如原版里：

- 颜色码会清除前面的粗体、斜体、下划线、删除线、混淆等格式
- 这和 MiniMessage 里单纯的 `<green>` / `<#ff0000>` 不一样

所以当前可能出现：

- `&l&cText` 转出来后仍然保留粗体语义
- `&o&#ff0000Text` 转出来后仍然保留前面的斜体语义

这和原版 Minecraft 的 legacy 颜色行为不一致

## 本次实现计划

### 1. 为所有“颜色类代码”补 reset 语义

- 在 `LegacyColorConverter` 中统一调整：
  - 普通颜色 `&0-9a-f`
  - `&#RRGGBB`
  - `&x&R&R&G&G&B&B`
- 这些在转换时不再只输出颜色标签
- 而是输出“先 reset，再应用颜色”的 MiniMessage 片段

### 2. 保持格式代码语义不变

- `&k/&l/&m/&n/&o` 继续只映射为对应装饰标签
- `&r` 继续映射为 `<reset>`

### 3. 保持 mixed 文本兼容

- 不改 `MessageUtil.parse(...)` 的入口逻辑
- 继续兼容“MiniMessage + legacy 颜色码混写”的现有用法

## 设计说明

这次我默认把“颜色码的 reset 语义”实现为：

- 颜色标签前自动补 `<reset>`

如果结合当前项目大量使用 `<!i>` 的习惯，为了更接近原版“颜色码会把斜体也重置掉”的效果，我推荐进一步做成：

- `<reset><!i><color>`

这样在物品名、lore 之类上下文里，颜色切换后不会因为 reset 把文本重新带回斜体。

## 待确认

我推荐采用这个版本：

- `&a` -> `<reset><!i><green>`
- `&#ff0000` -> `<reset><!i><#ff0000>`
- `&x&f&f&0&0&0&0` -> `<reset><!i><#ff0000>`

原因：

- 更接近原版 legacy 颜色“会重置样式”的行为
- 也更贴合你这个项目里普遍使用 `<!i>` 禁用斜体的现状

如果你确认，我就按这个方案实现。
