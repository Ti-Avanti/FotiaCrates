## 任务

让插件最终构建出来的 jar 包里不再包含 `org.slf4j` 相关类和元数据。

## 已定位的现状

### 1. 当前产物里确实包含 `org.slf4j`

- 文件：`target/FotiaCrates-1.0.6.jar`
- 已确认包含：
  - `org/slf4j/**`
  - `META-INF/maven/org.slf4j/**`

### 2. 当前依赖链来源

- 文件：`pom.xml`
- 直接依赖：
  - `com.zaxxer:HikariCP:5.1.0`
- `org.slf4j` 不是项目直接声明的依赖
- 目前是通过 `HikariCP` 的传递依赖一起被 shade 进最终 jar

## 本次实现计划

### 1. 从依赖链中排除 `slf4j-api`

- 在 `HikariCP` 依赖上增加 `exclusions`
- 排除：
  - `org.slf4j:slf4j-api`

### 2. 不在插件 jar 中重新打包 `slf4j`

- 保持当前 shade 逻辑
- 但因为 `slf4j-api` 已不在运行时打包依赖图中，所以最终 jar 不再包含：
  - `org/slf4j/**`
  - `META-INF/maven/org.slf4j/**`

### 3. 运行时依赖假设

- 这次默认依赖服务端环境提供 `slf4j-api`
- 对 Paper 服务端这是合理假设
- 也符合“不要把 `org.slf4j` 打进插件 jar”这个目标

## 方案取舍

### 选择：排除，不重定位

我这次默认不做 `org.slf4j` relocation，原因是：

- 你要求的是“jar 包里不再有 `org.slf4j`”
- `slf4j` 本身属于通用日志 API，不适合插件私有化打包一份
- 对 Paper 环境，直接依赖服务端提供的 API 更干净

### 不选：重定位 `org.slf4j`

- 这样虽然能让 jar 里没有原始 `org.slf4j` 路径
- 但本质上还是把一套 `slf4j` 类打进插件
- 也更容易引入日志绑定和兼容问题

## 预期结果

- `pom.xml` 更新后重新打包
- 最终 `FotiaCrates-1.0.6.jar` 中不再出现 `org/slf4j/**`
- Hikari 继续保留在插件 jar 中

## 待确认

这次我默认：

- 采用“排除 `slf4j-api` 传递依赖”的方案
- 修改 `pom.xml`
- 重新打包并验证 jar 内容

确认后开始开发。
