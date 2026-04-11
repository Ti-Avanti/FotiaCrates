## 任务

修复 ModelEngine 模式下模型残留导致同位置出现双模型重叠的问题，并在完成后将版本号更新到 `1.0.9`。

## 已确认现状

### 1. 模型是否存在的判断只依赖内存映射

- 文件：`src/main/java/gg/fotia/crates/modelengine/ModelEngineManager.java`
- 当前逻辑：
  - `hasModel(Location)` 仅检查 `crateModels` 这张内存 `Map`
  - 不会检查世界中该位置附近是否已经存在残留的隐形 `ArmorStand` / ModeledEntity
- 风险：
  - 一旦插件重载、异常中断、或 `crateModels` 与世界实际状态失步
  - `spawnAllCrateModels()` 会把该位置误判为“没有模型”
  - 然后再次生成一只新的模型，导致两个模型重叠

### 2. 生成模型前没有先清理同位置旧模型

- 文件：`src/main/java/gg/fotia/crates/modelengine/ModelEngineManager.java`
- 当前逻辑：
  - `spawnCrateModel(...)` 会直接在位置上生成新的基底实体与模型
  - 没有先调用移除逻辑清理该位置旧模型
- 风险：
  - 即使同位置已经有旧模型，只要内存映射没记录到，也会再次生成

### 3. 全局清理逻辑弱于单点移除逻辑

- 文件：
  - `src/main/java/gg/fotia/crates/modelengine/ModelEngineManager.java`
  - `src/main/java/gg/fotia/crates/FotiaCrates.java`
- 当前逻辑：
  - `removeCrateModel(...)` 会尝试调用 `removeModeledEntity(...)`，并扫描附近隐形 `ArmorStand` 做兜底清理
  - 但 `cleanup()` 只按 `crateModels` 中记录的 UUID 调 `entity.remove()`
  - `cleanup()` 不会调用完整的单点清理，也不会扫描残留实体
- 风险：
  - 关闭插件或其它需要全局清理的场景下，旧模型可能没有被完整移除

### 4. 问题最容易在以下场景触发

- 插件启动后延迟补模型时：`spawnAllCrateModels()`
- 插件 `/crate reload` 后重新补模型时：`spawnAllCrateModels()`
- 同位置再次触发模型生成时：`spawnCrateModel(...)`

## 本次实现计划

### 1. 强化模型存在判定

- 修改 `hasModel(Location)`：
  - 不再只看 `crateModels`
  - 增加对世界中该位置附近残留基底实体的兜底检查
  - 若内存映射指向的 UUID 已失效，则自动清理失效映射

### 2. 模型生成前先做同位置清理

- 修改 `spawnCrateModel(...)`：
  - 在正式生成前，先对该位置执行一次强制清理
  - 保证同一位置最多只保留一套模型基底实体

### 3. 统一全局清理与单点清理逻辑

- 修改 `cleanup()`：
  - 不再只按内存 UUID 简单 `remove()`
  - 改为复用完整移除逻辑，确保 ModeledEntity 与附近残留基底实体都能一起清掉

### 4. 保持现有交互行为不变

- 不改模型动画触发逻辑
- 不改宝箱放置、移除、开启交互方式
- 只修正模型残留和重复生成问题

### 5. 更新版本号

- 将 `pom.xml` 中版本从 `1.0.8` 更新到 `1.0.9`
- 保持 `plugin.yml` 继续使用 `${project.version}`

## 预期结果

- ModelEngine 模式下同位置不会再因残留模型导致双模型重叠
- 插件重载和启动补模型时，旧模型会被正确识别或清理
- 源码版本更新为 `1.0.9`

## 待确认

当前方案没有功能歧义，我默认按以上范围直接修复：

1. 只修复模型残留/重叠问题，不额外改动画表现
2. 完成后同步把版本号提升到 `1.0.9`
