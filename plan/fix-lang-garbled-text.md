## 任务

修复语言文件中的乱码和不一致文本。

## 已定位的现状

### 1. 语言文件本体不是整体编码损坏

- 文件：
  - `src/main/resources/lang/zh_CN.yml`
  - `src/main/resources/lang/en_US.yml`
- 已确认：
  - `zh_CN.yml` 原始字节是有效 UTF-8
  - `en_US.yml` 也是正常文本

说明：

- 这次问题不是“整份文件编码损坏”
- 而是“文件内部混入了少量错误文本/转义文本”

### 2. 已发现的中文语言文件问题

- 文件：`src/main/resources/lang/zh_CN.yml`
- 当前已确认有这些需要修复的点：

1. `history-cleared`
- 当前是 Unicode 转义形式
- 可读性差，不利于直接维护

2. `help-claim`
- 当前值是实际乱码

3. `pending-reward-unavailable`
- 当前值是实际乱码

### 3. 英文语言文件当前未发现乱码

- 文件：`src/main/resources/lang/en_US.yml`
- 当前读取内容正常

## 本次实现计划

### 1. 修复中文语言文本

- 将 `history-cleared` 改为直接可读的中文
- 修复 `help-claim` 的乱码
- 修复 `pending-reward-unavailable` 的乱码

### 2. 做一轮语言文件静态复查

- 复查 `zh_CN.yml` 是否还残留明显乱码或 `\u` 转义消息
- 不改未确认有问题的英文文件

### 3. 保持文件结构不变

- 不改 key 名
- 不改语言加载逻辑
- 不新增消息节点

## 预期结果

- `zh_CN.yml` 可直接阅读和维护
- 插件读到的中文提示不再出现乱码
- 英文语言文件保持不变

## 待确认

这次我默认只修“语言文本内容”，不改成 BOM、也不改语言加载逻辑。

确认后开始开发。
