# Git SQL Runner（DDD + jQuery/Tailwind）

基于 Git/目录管理的 SQL 脚本自动化执行与审计系统。后端采用 Spring Boot + JDBC + SQLite，DDD 分层；前端采用 jQuery + Tailwind（极简）。

## 功能特性

- Git/目录集成：索引 sql/ 目录下的 .sql 文件，记录 checksum、防重复执行（已提供目录索引；可平滑扩展至 JGit 分支/commit/tag 检出）
- 执行模式：
  - 手动执行（支持执行前预览与事务模式选择）
  - 定时任务（示例：每 5 分钟索引目录，可扩展自动执行策略）
- 元数据与审计：使用 SQLite 记录 sql_file 与 execution_record（状态、时间、错误、执行来源）
- 前端页面：
  - 文件列表（执行、预览、筛选）
  - 执行记录（状态/错误筛选）
  - 预览支持代码高亮（highlight.js）

## 技术栈与主要文件

- 后端：Spring Boot 3、JDBC、SQLite
  - 启动类：[BootstrapApplication.java](file:///d:/Space/java/git-sql-runner/src/main/java/com/example/gitsqlrunner/BootstrapApplication.java)
  - 控制器（REST）：[SqlApiController.java](file:///d:/Space/java/git-sql-runner/src/main/java/com/example/gitsqlrunner/interfaces/web/SqlApiController.java)
  - 应用服务：
    - 执行用例：[SqlExecutionService.java](file:///d:/Space/java/git-sql-runner/src/main/java/com/example/gitsqlrunner/application/SqlExecutionService.java)
    - 同步索引用例：[GitSyncUseCase.java](file:///d:/Space/java/git-sql-runner/src/main/java/com/example/gitsqlrunner/application/GitSyncUseCase.java)
  - 仓储实现（SQLite/JDBC）：
    - [SqlFileRepositoryJdbc.java](file:///d:/Space/java/git-sql-runner/src/main/java/com/example/gitsqlrunner/infrastructure/persistence/jdbc/SqlFileRepositoryJdbc.java)
    - [ExecutionRecordRepositoryJdbc.java](file:///d:/Space/java/git-sql-runner/src/main/java/com/example/gitsqlrunner/infrastructure/persistence/jdbc/ExecutionRecordRepositoryJdbc.java)
  - 定时任务（索引目录）：[SchedulerTasks.java](file:///d:/Space/java/git-sql-runner/src/main/java/com/example/gitsqlrunner/infrastructure/scheduler/SchedulerTasks.java)
  - SQLite 表结构初始化：[schema.sql](file:///d:/Space/java/git-sql-runner/src/main/resources/schema.sql)
- 前端：jQuery + Tailwind（静态资源）
  - 首页（文件列表/记录/执行前预览）：[index.html](file:///d:/Space/java/git-sql-runner/src/main/resources/static/index.html)
- 构建：Maven
  - 项目配置：[pom.xml](file:///d:/Space/java/git-sql-runner/pom.xml)
- Git 忽略规则： [.gitignore](file:///d:/Space/java/git-sql-runner/.gitignore)

## 目录结构（简版）

```
git-sql-runner/
├─ src/main/java/com/example/gitsqlrunner
│  ├─ domain/sql/                # 领域模型与仓储接口
│  ├─ application/               # 用例服务（执行/同步）
│  ├─ infrastructure/            # JDBC 仓储、调度
│  └─ interfaces/web/            # REST 控制器
├─ src/main/resources/
│  ├─ static/index.html          # jQuery + Tailwind 页面
│  ├─ application.properties     # 配置
│  └─ schema.sql                 # SQLite 元数据表
├─ sql/                          # SQL 脚本目录（默认被 .gitignore 忽略）
├─ pom.xml
└─ README.md
```

## 环境要求

- JDK 17+
- Maven 3.9+（或使用 IDE 运行 Spring Boot 应用）

> 说明：SQLite 驱动已内置，meta.db 会在项目根目录自动创建（见 application.properties）。

## 快速开始

1. 准备 SQL 文件
   - 在项目根目录创建 sql/ 目录（注意当前默认 .gitignore 已忽略该目录，用于存放私有脚本不入库）
   - 放置 .sql 脚本（例如 sql/0001_xxx.sql）
2. 启动服务
   - IDE 运行：启动类 BootstrapApplication
   - Maven：`mvn spring-boot:run`（需本机已安装 Maven）
3. 打开页面
   - 浏览器访问 http://localhost:8080
   - 点击“同步本地 sql/ 目录” → 列出脚本
   - 点击“执行” → 弹出预览对话框，选择事务模式后确认执行
   - 点击“预览” → 新窗口只读查看脚本内容

## REST API（简要）

- GET /api/files?executed=true|false
- POST /api/files/{id}/execute?mode=FILE|STATEMENT
- GET /api/files/{id}/preview
- GET /api/records?status=SUCCESS|FAILED|RUNNING
- POST /api/sync/local?baseDir=.&sqlDir=sql

示例：

```bash
# 索引本地 sql/ 目录
curl -X POST "http://localhost:8080/api/sync/local"

# 查看未执行文件
curl "http://localhost:8080/api/files?executed=false"

# 预览某个文件内容
curl "http://localhost:8080/api/files/1/preview"

# 执行（整文件事务）
curl -X POST "http://localhost:8080/api/files/1/execute?mode=FILE"
```

## 配置与调度

- 应用配置：[application.properties](file:///d:/Space/java/git-sql-runner/src/main/resources/application.properties)
  - 端口、SQLite URL、初始化脚本等
- 定时任务：每 5 分钟索引本地 sql/ 目录（可扩展为 Git 检出 + 自动执行策略）
  - [SchedulerTasks.java](file:///d:/Space/java/git-sql-runner/src/main/java/com/example/gitsqlrunner/infrastructure/scheduler/SchedulerTasks.java)

## DDD 分层说明

- domain：领域模型（SqlFile、ExecutionRecord）与仓储接口，纯业务语义
- application：用例编排（执行、同步），依赖领域接口而不关心持久化细节
- infrastructure：JDBC/SQLite 持久化，调度等技术实现
- interfaces：HTTP 接口适配层（Controller）

## 扩展建议

- Git 集成：在 infrastructure 增加 JGit 适配器，实现按分支/commit/tag 检出后调用 `GitSyncUseCase` 完成落库
- 多数据源：新增数据源配置表与连接工厂，执行接口携带 profileId 选择目标库
- 审批与顺序控制：增加 version 字段与审批状态机，调度按版本升序执行；支持 *.down.sql 回滚
- 权限控制：接入 Spring Security + RBAC，区分查看/执行/审批角色

## 许可证

本项目默认以 MIT 协议发布（可按你的需要替换）。

---

如需我继续：
- 把 highlight.js、jQuery、Tailwind 切换为本地静态资源（内网/离线可用）
- 接入 JGit，落地“按分支/commit/tag 同步与执行”
- 增加多数据源与审批流程
