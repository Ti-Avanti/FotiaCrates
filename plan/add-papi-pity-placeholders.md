## 任务

新增 PlaceholderAPI 变量，用于显示：

1. 玩家当前在某个宝箱上的保底累计抽取次数
2. 距离下一次保底还差多少次

## 已确认的现状

### 1. 当前 PAPI 扩展只支持钥匙变量

- 文件：`src/main/java/gg/fotia/crates/hook/FotiaCratesExpansion.java`
- 现状：目前只支持 `keys_virtual_<crateId>`、`keys_physical_<crateId>`、`keys_total_<crateId>`。

### 2. 保底计数已有可直接读取的接口

- 文件：`src/main/java/gg/fotia/crates/pity/PityManager.java`
- 已有：`getPityCount(UUID, crateId)`

### 3. 宝箱对象已有多级保底信息

- 文件：`src/main/java/gg/fotia/crates/crate/Crate.java`
- 已有：`getPityTiers()`、`getTriggeredPityTier(int)`、`getMaxPityCount()`

## 计划实现

在 `FotiaCratesExpansion` 中新增保底相关变量，按 `crateId` 查询：

- 当前累计次数
- 距离下一次保底剩余次数
- 如果该宝箱未启用保底或宝箱不存在，返回 `0`

## 待确认

占位符命名当前有两种合理方案，需要用户确认其一：

### 方案 A

- `%fotiacrates_pity_count_<crateId>%`
- `%fotiacrates_pity_remaining_<crateId>%`

### 方案 B

- `%fotiacrates_pity_current_<crateId>%`
- `%fotiacrates_pity_next_remaining_<crateId>%`

推荐方案 A，命名更短，和当前 `keys_total_<crateId>` 的风格更一致。

## 预期结果

玩家或全息/记分板可直接通过 PAPI 变量显示：

- 当前某宝箱已累计抽取多少次
- 当前距离下一次保底还差多少次
