## 任务

当玩家抽取物品奖励时，如果背包空间不足，优先通过 SweetMail API 将溢出物品发送到玩家邮箱；若 SweetMail 不可用或发送失败，则回退为原有掉落到玩家脚下的行为。

## 已确认现状

### 1. 当前物品溢出行为

- 文件：
  - `src/main/java/gg/fotia/crates/reward/ItemReward.java`
  - `src/main/java/gg/fotia/crates/reward/CommandReward.java`
- 当前逻辑：
  - 先调用 `player.getInventory().addItem(...)`
  - 放不进背包的剩余物品直接 `dropItemNaturally(...)`
- 结果：
  - 背包满时，奖励会掉在玩家脚下

### 2. SweetMail 可用 API

- 参考工程：`reference/SweetMail-main/SweetMail-main`
- 已确认接口：
  - `IMail.api()`
  - `createSystemMail(String senderDisplay)`
  - `setReceiver(OfflinePlayer)`
  - `setIcon(String)`
  - `setTitle(String)`
  - `addContent(...)`
  - `addAttachments(...)`
  - `AttachmentItem.build(ItemStack)`
- 说明：
  - SweetMail 已提供现成 API，无需直接写其数据库表
  - 插件名为 `SweetMail`，适合作为 `softdepend`

### 3. 图标格式要求

- SweetMail `setIcon(String)` 支持以下字符串格式：
  - 原版材质：`CHEST`
  - 原版材质 + CMD：`PAPER#1001`
  - `itemsadder-物品ID`
  - `mythic-物品ID`
  - `head-base64-数据值`
  - `craftengine-命名空间:物品ID`
  - `neigeitems-物品ID;数据值`

## 本次实现计划

### 1. 接入 SweetMail 软依赖与编译期 API

- 在 `plugin.yml` 中加入 `SweetMail` 到 `softdepend`
- 在 `pom.xml` 中加入 SweetMail 的 `compileOnly` 依赖和仓库配置

### 2. 新增邮箱溢出配置

- 在 `config.yml` 中新增 `overflow-mail` 配置段
- 先提供以下真实配置：
  - `enabled`
  - `icon`
  - `sender-display`
  - `title`
  - `content`

### 3. 封装统一的物品溢出投递服务

- 新增独立服务类处理奖励物品发放
- 统一流程：
  - 先尝试放入玩家背包
  - 收集剩余物品
  - 若无剩余则结束
  - 若启用且 SweetMail 可用，则使用 API 发系统邮件
  - 若不可用、发送失败或部分附件不合法，则对应剩余物品回退到地上

### 4. 调整奖励发放入口

- `ItemReward` 与 `CommandReward` 的物品发放改为走统一服务
- 保持命令执行逻辑不变
- 使 `ITEM` 奖励和带附加物品的 `COMMAND` 奖励都支持邮箱补发

### 5. 更新语言消息

- 新增玩家提示消息并同步更新：
  - `zh_CN.yml`
  - `en_US.yml`
- 至少包含：
  - 邮箱发送成功提示
  - 部分发送邮箱、部分掉地提示

## 预期结果

- SweetMail 已安装时，背包放不下的奖励物品优先进入邮箱
- SweetMail 未安装或发送失败时，仍保持当前掉地逻辑
- 功能由配置控制，可关闭
- 不直接耦合 SweetMail 数据库结构
