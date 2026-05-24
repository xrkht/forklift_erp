# 变更日志 (Changelog)

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/) 和 [Keep a Changelog](https://keepachangelog.com/zh-CN/) 规范。

## [0.1.17] - 2026-05-25 - xrkht

### 修改
- **改装工单旧件处理下拉**
  - 新建改装工单时，旧件处理不再把“拆下件入库”真实写入下拉输入框，而是用灰色占位提示“默认：拆下件入库”
  - 未手动选择旧件处理时，提交工单仍由前后端兜底按 `STOCK_IN` 处理；手动选择“不入库”时才写入 `DISCARD`
  - Service Worker 缓存版本升级为 `forklift-erp-client-v9`，确保浏览器刷新后加载新的下拉逻辑

### 验证
- 内置 Node：`node --check src\main\resources\static\assets\app.js` 通过
- 内置 Node：`node --check src\main\resources\static\sw.js` 通过
- `.\mvnw.cmd test` 通过，累计 `11` 个测试通过
- `.\mvnw.cmd package -DskipTests` 通过
- Chromium 验证通过：旧件处理初始输入值和隐藏值为空，灰色提示显示“默认：拆下件入库”，下拉展开后“拆下件入库”和“不入库”均可直接选择
- 通过接口写入测试配置、车辆、配件和两张改装工单：默认旧件处理工单完成后生成拆下件入库记录，`DISCARD` 工单完成后不生成拆下件入库记录

## [0.1.16] - 2026-05-24 - xrkht

### 新增
- **配置字典与配件选型**
  - 配置项编码支持自动生成 `CFG-0001` 风格递增值，用户未手动填写时由前后端共同兜底填充
  - 新增可输入筛选的通用下拉控件，配置项大类、子类、配置值、整车、配件、状态等弹窗下拉均支持输入关键字缩小候选范围
  - 新增配件时，配件名称可直接从配置字典配置值中选择，选择后按配置项子类自动回填配件分类

### 修改
- **改装工单配置匹配**
  - 改装工单和配件替换的同类型判断改为优先按配置字典子类匹配，兼容“电动车/手动车/内燃车”大类下的“货叉/电池/轮胎”等配件类型
  - 改装工单原配置下拉展示配置字典的大类、子类和配置项名称，库存配件筛选同步使用配置字典分类

### 验证
- `.\mvnw.cmd test` 通过，累计 `11` 个测试通过，新增覆盖配置项编码自动递增和字典子类匹配的改装工单流程
- `.\mvnw.cmd -DskipTests package` 通过
- 内置 Node：`node --check src\main\resources\static\assets\app.js` 通过
- Chromium 验证通过：登录后配置项编码自动填充，输入“电”仅筛出“电动车”，新增配件输入“充”筛出“充气胎”并自动回填分类“轮胎”

## [0.1.15] - 2026-05-24 - xrkht

### 新增
- **改装工单模块**
  - 新增 Flyway `V6__modification_work_order.sql`，创建 `modification_work_order` 和 `modification_work_order_line`，用于记录客户出库前改装需求、替换明细和执行状态
  - 新增改装工单实体、DTO、Repository、Service 和 `/api/modification-work-orders` 接口，支持工单列表、按车辆查询、新建、完成和取消
  - 工单完成时统一调用配件替换流程，自动扣减新配件库存、按旧件处理方式回收入库、更新整车真实配置，并将 `stock_movement` 来源标记为 `MODIFICATION_WORK_ORDER`
  - 新增 `ModificationWorkOrderIntegrationTests`，覆盖创建工单、完成工单、库存流水、拆下件回库和车辆配置更新

### 修改
- **整车入库与状态模型**
  - 整车新建默认库存数量为 `1`，有库存时默认入库时间为当前时间，库存状态默认进入 `IN_STOCK`
  - 入库配置明细支持 `FACTORY_STANDARD` 与 `FACTORY_OPTIONAL`，厂家选配不再和后期仓库改装混在一起
  - 整车库存状态补齐 `PENDING_INBOUND`、`PENDING_MODIFICATION`、`MODIFYING`、`PENDING_OUTBOUND`、`OUTBOUND`，出库后状态进入 `OUTBOUND`
  - 配件替换支持数量和旧件处理方式，默认旧件入库，也允许标记为不入库
- **前端工作流**
  - 新增“改装工单”导航与列表，展示工单号、车辆、客户、销售单、状态和替换明细
  - 车辆详情中的直接配件替换入口改为“生成工单”，弹窗自动带出原配置、按配置类别筛选新配件，并默认旧件拆下入库
  - 改装工单列表支持完成和取消，完成动作再触发库存与配置变化
  - Service Worker 缓存版本升级为 `forklift-erp-client-v8`

### 验证
- `.\mvnw.cmd test` 通过，累计 `10` 个测试通过，包含新增 `ModificationWorkOrderIntegrationTests`
- `.\mvnw.cmd package -DskipTests` 通过
- 浏览器验证通过：登录后可见“改装工单”菜单，工单列表和新建弹窗正常渲染，控制台无错误

## [0.1.14] - 2026-05-24 - xrkht

### 新增
- **仓库与库存台账结构**
  - 新增 Flyway `V5__inventory_ledger.sql`，补充 `warehouse`、`stock_balance`、`stock_movement`、`stock_movement_line` 与 `repair_part_usage` 表
  - 为整车与配件库存增加 `warehouse_id`，为整车增加 `stock_status`，并把历史库存初始化到默认仓库与库存余额表
  - 新增 `Warehouse`、`StockBalance`、`StockMovement`、`StockMovementLine` 实体与对应仓储接口
  - 新增 `StockLedgerService`，统一维护默认仓库解析、库存余额同步和库存移动明细写入

### 修改
- **库存写入接入台账**
  - 整车/配件新增档案时，初始数量会同步写入库存余额和库存移动明细
  - 整车/配件编辑档案导致数量变化时，会按 `ADJUST` 写入库存流水与新台账
  - 整车/配件出入库、配置替换消耗新件、拆下件自动入库均同步维护 `stock_balance` 和 `stock_movement_line`
  - 前端整车表单、列表与详情页补充库存状态展示，Service Worker 缓存版本升级为 `forklift-erp-client-v7`

### 验证
- 内置 Node：`node --check src\main\resources\static\assets\app.js` 通过
- 内置 Node：`node --check src\main\resources\static\sw.js` 通过
- `.\mvnw.cmd test` 通过，累计 `9` 个测试通过，包含新增 `InventoryLedgerIntegrationTests`
- `.\mvnw.cmd package -DskipTests` 通过
- Flyway V5 在本地 MySQL 8.0.43 上应用成功，schema 当前版本为 `5`

## [0.1.13] - 2026-05-24 - xrkht

### 修改
- **前端壳层与日志页交互优化**
  - 左侧导航栏改为桌面端 `sticky` 固定显示，页面下滑时保持可见，并为导航自身增加滚动能力
  - 顶部栏在桌面端固定显示，窄屏端恢复普通流式布局，避免和顶部导航互相遮挡
  - 日志查看页新增显示数量提示，默认分批渲染日志并提供“加载更多”，降低大量日志一次性撑高页面的问题
  - 日志表格增加独立滚动区域，表头继续固定，长列表滚动时侧栏和表头都保持可用
  - 切换模块时自动回到工作区顶部，避免从长日志页切到其他页面后仍停留在页面下方
  - Service Worker 缓存版本升级为 `forklift-erp-client-v6`

### 验证
- 内置 Node：`node --check src\main\resources\static\assets\app.js` 通过
- 内置 Node：`node --check src\main\resources\static\sw.js` 通过
- `.\mvnw.cmd test` 通过，累计 `8` 个测试通过
- `.\mvnw.cmd package -DskipTests` 通过

## [0.1.12] - 2026-05-24 - xrkht

### 新增
- **多用户协同写入控制**
  - 新增协作版本字段 `version`、`last_modified_by`、`last_modified_role`、`last_modified_priority`，覆盖整车、配件、维修、配置、车辆配置和用户等核心可写数据
  - 新增 `CollaborationService`，并按 `SUPER_ADMIN > ADMIN > USER` 处理同一数据的并发旧版本写入优先级
  - 新增悲观写锁查询方法，更新、删除、出入库、配件替换、配置替换和用户维护等关键写入流程先锁定目标数据再校验版本
  - 新增全局并发冲突处理，JPA 乐观锁、悲观锁获取失败和业务版本冲突统一返回 HTTP `409`
  - 新增 `CollaborationIntegrationTests`，覆盖管理员先写入、超级管理员用旧版本优先覆盖、管理员继续用旧版本被拒绝的协作场景

### 修改
- **前端协作版本传递**
  - 编辑、删除、用户维护、库存调整和配件替换提交时自动携带当前 `version`
  - 保存接口改为 `saveAndFlush` / `saveAllAndFlush`，确保响应中的 `version` 是数据库刷新后的最新值
  - Flyway 新增 `V4__collaboration_priority_control.sql`，由迁移脚本统一补齐协作字段
  - Service Worker 缓存版本升级为 `forklift-erp-client-v5`

### 验证
- `.\mvnw.cmd test` 通过，累计 `8` 个测试通过
- 内置 Node：`node --check src\main\resources\static\assets\app.js` 通过
- 内置 Node：`node --check src\main\resources\static\sw.js` 通过
- `.\mvnw.cmd package -DskipTests` 通过
- V4 Flyway 迁移在本地 MySQL 8.0.43 上应用成功，schema 当前版本为 `4`

## [0.1.11] - 2026-05-24 - xrkht

### 新增
- **HTTPS 访问**
  - 默认服务端口升级为 `8443` HTTPS，新增本地开发自签名证书 `src/main/resources/certs/forklift-erp-dev.p12`
  - 新增 `HttpsRedirectConfig`，在启用 SSL 时保留 `8080` HTTP 入口并统一跳转到 HTTPS
  - 示例 `.http` 请求地址更新为 `https://localhost:8443`

### 验证
- `.\mvnw.cmd test` 通过，累计 `7` 个测试通过
- `.\mvnw.cmd package -DskipTests` 通过
- 启动验证通过：Tomcat 启动在 `8443 (https)` 和 `8080 (http)`
- HTTPS/跳转验证通过：OpenSSL 请求 `https://127.0.0.1:8443/` 返回 `200 text/html`，`http://127.0.0.1:8080/` 返回 `302 Location: https://127.0.0.1:8443/`

## [0.1.10] - 2026-05-23 - xrkht

### 新增
- **统一关键操作审计**
  - 新增 `operation_audit_log` 表与 `OperationAuditLog`、`OperationAuditService`
  - 车辆、配件、维修、配置替换、库存出入库和用户维护等关键写操作统一写入审计日志
  - `/api/logs` 优先展示统一审计流，并兼容历史维修、替换和库存流水记录

- **统计财报**
  - 新增 `/api/statistics/finance` 统计接口，按年份输出月度财报、年度对比、资源分类收支、出库收益 TOP、当前库存价值和低库存预警
  - 入库按采购价/结算价估算成本，出库按销售价估算收入并按采购价估算成本，维修总费用单独计入服务收入
  - 前端新增“统计财报”菜单，展示年度总收入、入库成本、出库毛利、维修收入与多张统计表

### 修改
- **日志与前端缓存**
  - 日志页标题调整为“关键操作审计”，动作标签补充新增、更新、删除、锁定、密码维护等业务动作
  - Service Worker 缓存版本升级为 `forklift-erp-client-v4`

### 验证
- 内置 Node：`node --check src\main\resources\static\assets\app.js` 通过
- 内置 Node：`node --check src\main\resources\static\assets\modules\session.js` 通过
- `.\mvnw.cmd test` 通过，累计 `7` 个测试通过
- `.\mvnw.cmd package -DskipTests` 通过
- 浏览器验证通过：`http://localhost:8086/` 登录后可见“统计财报”和“日志查看”，统计页包含月度财报、年度对比、当前库存价值和低库存预警，控制台无错误

## [0.1.9] - 2026-05-23 - xrkht

### 新增
- **精细权限模型**
  - 新增 `permissions` 与 `role_permissions` 表，并通过 `V2__permission_model.sql` 初始化权限点
  - 新增 `Permission`、`PermissionRepository`、`PermissionService` 与 `PermissionCodes`
  - 登录响应与用户列表响应新增 `permissions` 字段，前端可直接按权限点控制菜单和按钮

### 修改
- **接口鉴权**
  - 库存、配件、维修、配置、替换、日志与用户接口从角色判断切换为权限点判断
  - 保留 `ROLE_` authority 兼容旧服务层判断，同时新增 `PERM_` authority 支持细粒度授权
  - `ADMIN` 默认拥有业务写入、库存调整、日志查看、用户创建/普通用户密码重置权限，但不拥有 `user:admin`
- **前端权限感知**
  - 日志查看和用户管理菜单改为读取 `log:read`、`user:read`
  - 新增、编辑、删除、出入库、配件替换、用户维护等按钮按对应权限显隐
  - Service Worker 缓存版本升级为 `forklift-erp-client-v3`

### 验证
- 内置 Node：`node --check src\main\resources\static\assets\app.js` 通过
- 内置 Node：`node --check src\main\resources\static\assets\modules\session.js` 通过
- `.\mvnw.cmd test` 通过，累计 `6` 个测试通过
- `.\mvnw.cmd package -DskipTests` 通过
- 浏览器验证通过：`http://localhost:8080/` 正常加载，`/assets/app.js` 以 `type="module"` 运行，控制台无错误，超级管理员可见 `log:read` / `user:read` 菜单

## [0.1.8] - 2026-05-23 - xrkht

### 新增
- **数据库迁移管理**
  - 新增 Flyway 依赖与 `src/main/resources/db/migration/V1__init_schema.sql`
  - 首版迁移脚本覆盖用户、角色、库存、配置、维修、替换日志和库存流水等核心表
  - 启用 `baseline-on-migrate`，让已有 MySQL 非空库可平滑接入 Flyway 管理

- **用户权限集成测试**
  - 新增 `AuthIntegrationTests`，覆盖超级管理员创建、改名、改密、删除用户流程
  - 覆盖普通管理员禁止调用超级管理员专用用户维护接口
  - 覆盖管理员不能通过旧重置密码接口修改超级管理员密码
  - 覆盖管理员用户列表仅能看到普通用户

- **前端基础模块**
  - 新增 `assets/modules/session.js`，统一管理 token、用户登录态和 API 请求封装
  - `app.js` 改为 ES module 入口，减少登录态和请求逻辑散落

### 修改
- **数据库启动策略**
  - `spring.jpa.hibernate.ddl-auto` 从 `update` 调整为 `validate`，结构变更改由 Flyway 迁移控制
  - Service Worker 缓存版本升级为 `forklift-erp-client-v2`，并缓存新增前端模块

### 验证
- `node --check src\main\resources\static\assets\app.js` 通过
- `node --check src\main\resources\static\assets\modules\session.js` 通过
- `.\mvnw.cmd test` 通过，累计 `5` 个测试通过
- `.\mvnw.cmd package -DskipTests` 通过
- 浏览器验证通过：登录页正常加载，`/assets/app.js` 以 `type="module"` 运行，控制台无错误

## [0.1.7] - 2026-05-23 - xrkht

### 新增
- **超级管理员用户维护**
  - 新增 `PUT /api/auth/users/{id}/username`，支持超级管理员修改管理员或普通用户的用户名
  - 新增 `PUT /api/auth/users/{id}/password`，支持超级管理员重置管理员或普通用户的登录密码
  - 新增 `DELETE /api/auth/users/{id}`，支持超级管理员删除管理员或普通用户账号
  - 用户管理前端列表新增“改用户名”“改密码”“删除”行内操作，仅超级管理员可见

### 修改
- **用户权限保护**
  - 禁止通过新接口修改或删除超级管理员账号，避免误操作导致最高权限账号不可用
  - 删除用户时禁止删除当前登录账号
  - 收紧旧的 `/api/auth/reset-password` 权限判断，管理员不再可能重置超级管理员密码

### 验证
- `node --check src\main\resources\static\assets\app.js` 通过
- `.\mvnw.cmd test` 通过
- `.\mvnw.cmd package -DskipTests` 通过
- 接口验证通过：超级管理员创建用户、修改用户名、修改密码、删除用户；普通管理员调用修改用户名返回 `403`；旧密码和已删除用户登录均返回 `401`
- 浏览器验证通过：超级管理员进入用户管理页后可见“改用户名”“改密码”“删除”操作按钮

## [0.1.6] - 2026-05-23 - xrkht

### 新增
- **业务日志查看**
  - 新增 `/api/logs` 聚合日志接口，仅 `ADMIN`、`SUPER_ADMIN` 可访问
  - 日志查看界面汇总配件替换、维修记录、整车出入库、配件出入库流水
  - 新增 `stock_operation_log` 库存流水表，记录出入库对象、数量、前后库存、操作人和备注

### 修改
- **配件替换入口**
  - 前端移除重复的“配置替换”入口，仅保留“配件替换”
  - 配件替换弹窗直接按车辆配置筛选同类型库存配件，下拉选择后执行替换
  - 配件替换继续写入 `ConfigReplaceLog`，并同步记录新配件出库、拆下件入库流水

### 验证
- `node --check src\main\resources\static\assets\app.js` 通过
- `.\mvnw.cmd test` 通过
- `.\mvnw.cmd package -DskipTests` 通过
- 接口验证：管理员访问 `/api/logs` 返回 `200`，未登录访问返回 `401`

## [0.1.5] - 2026-05-23 - xrkht

### 新增
- **出入库前端操作**
  - 车辆库存页新增整车入库、整车出库入口，支持对已有整车库存数量做增减
  - 配件库存页新增配件入库、配件出库入口，支持按配件编码调整库存数量
  - 列表行操作同步增加入库、出库快捷按钮，便于直接处理当前记录

- **配件替换**
  - 车辆详情新增配件替换入口，可选择车辆已安装配置并从库存中挑选同类型配件替换
  - 替换时自动扣减新配件库存，并将拆下来的旧配件以来源 `REMOVED` 自动写入配件仓库
  - 新增配件替换接口 `/api/replace/part`，替换日志会记录新旧配件和拆下件入库信息

### 修改
- **整车库存接口**
  - 新增 `/api/inventory/{id}/inbound` 与 `/api/inventory/{id}/outbound`，管理员和超级管理员可对已有整车库存做入库、出库调整

### 验证
- `.\mvnw.cmd test` 通过
- `.\mvnw.cmd package -DskipTests` 通过
- `node --check src\main\resources\static\assets\app.js` 通过
- 浏览器验证：登录后车辆库存页显示整车入库/出库，配件库存页显示配件入库/出库，车辆详情页显示配件替换入口

## [0.1.4] - 2026-05-22 - xrkht

### 新增
- **方法级权限保护**
  - 为整车库存、配件库存、配置项、配置值、维修记录、配置替换等写操作补充 `@PreAuthorize`
  - 新增/修改/删除/入库/出库类接口统一限制为 `ADMIN` 或 `SUPER_ADMIN` 角色
  - 普通 `USER` 保留查询能力，禁止直接执行业务数据写操作

- **权限异常处理**
  - `GlobalExceptionHandler` 新增 `AccessDeniedException` 处理，方法级权限不足时统一返回 `403`

### 修改
- **Java 与构建配置**
  - 项目编译目标由 Java 25 调整为 Java 21 LTS，降低 Lombok、Mockito、Maven 插件与新 JDK 的兼容风险
  - `pom.xml` 显式指定 Lombok `1.18.46`
  - 新增 `maven-compiler-plugin` 配置，使用 `release 21` 并明确 Lombok annotation processor

- **认证入口策略**
  - `SecurityConfig` 仅放行 `/api/auth/login`
  - `/api/auth/register` 改为必须登录后由管理员或超级管理员调用，避免未登录注册与方法权限校验冲突

- **数据库配置**
  - MySQL 连接串增加 `allowPublicKeyRetrieval=true`，解决 MySQL 8/9 `caching_sha2_password` 场景下启动失败问题
  - 移除显式 `database-platform` 配置，交由 Hibernate 自动识别 MySQL 方言，减少启动告警

- **Maven Wrapper**
  - 修复 `mvnw.cmd` 在 Windows PowerShell 环境下 `.m2` 目录 Target 判断为空导致无法启动的问题
  - 增加本地 Maven wrapper 缓存复用逻辑，避免已有 Maven 发行版时仍重复下载

### 修复
- 修复源码执行 `mvn test` 时 Lombok getter/setter 与 `@Slf4j` 日志字段无法生成导致的编译失败
- 修复应用默认配置连接 MySQL 报 `Public Key Retrieval is not allowed` 导致无法启动的问题
- 修复业务异常 HTTP 状态码与响应体 `code` 不一致的问题：
  - 未认证/错误密码返回 `401`
  - 权限不足返回 `403`
  - 资源不存在返回 `404`
  - 数据重复、库存不足等业务冲突返回 `409`
- 修复未登录调用注册接口返回 `500 SYSTEM_ERROR` 的问题
- 修复普通用户可新增/删除整车等业务数据的权限过宽问题

### 验证
- `.\mvnw.cmd test` 通过
- 接口回归验证：管理员登录、创建普通用户、普通用户登录、未登录注册拦截、普通用户写操作拦截均通过

## [0.1.3] - 2026-05-18 - xrkht

### 新增
- **用户认证与授权模块**
  - 新增 `User`、`Role` 实体，支持用户启用状态、角色多对多关联及创建/更新时间审计字段
  - 新增 `UserRepository`、`RoleRepository`，支持按用户名、角色名查询及用户名重复校验
  - 新增 `AuthController`，提供登录、用户创建、重置密码接口，继续沿用统一 `Result<T>` 响应格式
  - 新增 `LoginRequest`、`TokenResponse`、`RegisterRequest`、`ResetPasswordRequest` 等认证请求/响应 DTO，并加入必填参数校验

- **JWT 无状态认证**
  - 新增 `JwtTokenProvider`，支持 Token 生成、用户名解析和有效性校验
  - 新增 `JwtAuthenticationFilter`，从 `Authorization: Bearer` 请求头读取 Token 并写入 Spring Security 上下文
  - 新增 `UserDetailsServiceImpl`，将系统用户和角色转换为 Spring Security 的认证用户及 `ROLE_` 权限

- **权限与初始化配置**
  - 新增 `SecurityConfig`，启用 Spring Security、方法级权限控制和无状态 Session 策略
  - 新增 `SecurityExceptionHandler`，统一处理 401 未认证和 403 无权限响应
  - 新增 `DataInitializer`，系统启动时自动初始化 `SUPER_ADMIN`、`ADMIN`、`USER` 角色及默认超级管理员账号
  - 新增 `SecurityUtils`，封装当前用户角色判断能力，便于后续业务模块复用权限判断

### 修改
- **接口访问控制**
  - 除认证相关入口外，业务接口默认需要通过 JWT 认证后访问
  - 用户创建和密码重置接口增加 `@PreAuthorize` 权限限制，仅管理员及超级管理员可操作
  - 角色分配增加层级约束：禁止普通管理员创建管理员，禁止通过接口创建超级管理员

- **依赖与配置**
  - `pom.xml` 新增 `spring-boot-starter-security` 与 `jjwt-api`、`jjwt-impl`、`jjwt-jackson` 依赖
  - `application.yml` 新增 JWT 密钥、过期时间和请求头配置
  - `ResultCode` 补充 `UNAUTHORIZED`、`FORBIDDEN`、`PARAM_ERROR`、`DATA_DUPLICATE` 等认证与参数相关状态码

### 修复
- 统一认证失败和权限不足时的 JSON 返回格式，避免 Spring Security 默认错误页或非统一响应体
- 登录、创建用户、重置密码等认证接口加入参数校验，减少空用户名、空密码等无效请求进入业务逻辑

## [0.1.2] - 2026-05-08 - xrkht

### 新增
- **DTO 层（数据隔离）**
  - 新增 `MachineInventoryVO`、`MachineInventoryCreateDTO`、`MachineConfigVO`、`MachineDetailVO`
  - 新增 `PartInventoryVO`、`PartInventoryCreateDTO`
  - 新增 `RepairRecordVO`、`RepairRecordCreateDTO`
  - 新增 `ConfigItemVO`、`ConfigValueVO`、`ConfigReplaceLogVO`
  - 新增 `ConfigReplaceRequestDTO`，替换请求专用 DTO
  - 所有 DTO 均实现 `toEntity()` 和 `updateEntity()` 方法，与实体层解耦

- **Bean Validation 参数校验**
  - `MachineInventoryCreateDTO`、`PartInventoryCreateDTO`、`RepairRecordCreateDTO` 等加入 `@NotBlank`、`@NotNull`、`@Size`、`@Min` 等校验注解
  - `InboundRequestDTO.ConfigSelection` 增加必填校验
  - `ConfigReplaceRequestDTO` 增加 `@NotNull`、`@NotBlank` 校验
  - 校验失败统一由 `GlobalExceptionHandler` 返回 400 错误

- **业务增强**
  - `ConfigReplaceService` + `ConfigReplaceServiceImpl`：实现完整配置替换流程（更新/新增车辆配置、扣减配件库存、生成替换日志）
  - `PartInventoryService` 增加配件出入库业务（`inbound`/`outbound`），包含库存不足检查
  - `PartInventoryServiceImpl` 增加配件编码唯一性检查
  - `ConfigItemServiceImpl` 删除配置项/配置值时增加车辆引用检查，已用则抛出 `CONFIG_IN_USE`

### 修改
- **Controller 全量重构**
  - `MachineInventoryController`、`PartInventoryController`、`RepairRecordController`、`ConfigItemController`、`ConfigReplaceController` 全面改为接收 DTO、返回 VO
  - 所有接口统一添加 `@Valid`，启用参数校验
  - 将原有的 `ResponseEntity` 返回全部替换为 `Result<T>` 格式（`ConfigItemController`、`ConfigReplaceController` 遗留接口已改造）
- **`InboundRequestDTO` 调整**：`machineInventory` 字段类型由 `MachineInventory` 实体改为 `MachineInventoryCreateDTO`，封装前端必要字段
- **`PartInventoryCreateDTO` 扩展**：补充 `partBrand`、`manufacturingDate`、`inboundDate` 等字段，支持完整配件信息传递
- **`ConfigReplaceController` 重构**：复用 `ConfigReplaceService.performReplace()` 执行替换，原有纯日志记录接口保留

### 修复
- 解决 `InboundRequestDTO` 内实体无 `toEntity()` 方法导致的类型错误
- 修正 MySQL 列名 `vehicle_number` 与实体字段映射不一致问题（测试辅助脚本）

## [0.1.2] - 2026-05-04 - xrkht

### 新增
- **配置替换业务模块**
  - 新增 `ConfigReplaceService` 接口及 `ConfigReplaceServiceImpl` 实现，完成配置替换核心逻辑（更新车辆配置、配件库存变动、记录替换日志）
  - 新增 `ConfigReplaceRequestDTO`，封装替换请求参数（车辆、配置项、新旧值、替换类型、配件处理等）

- **配件库存业务模块**
  - 新增 `PartInventoryService` 接口及 `PartInventoryServiceImpl` 实现，支持配件 CRUD、按分类/来源/库存查询、出入库操作
  - 配件出入库时进行库存校验，库存不足抛出 `BusinessException`

- **维修记录业务模块**
  - 新增 `RepairRecordService` 接口及 `RepairRecordServiceImpl` 实现，支持维修记录 CRUD、按车辆/人员/状态/日期查询

- **Controller 层统一响应格式改造**
  - `ConfigItemController` 全部接口返回 `Result<T>`，添加 `@Valid` 校验，异常通过全局处理器统一处理
  - `ConfigReplaceController` 新增 `performReplace` 接口，调用替换业务并返回统一格式
  - `RepairRecordController` 完成基础 CRUD 及条件查询，返回统一 `Result`

- **MachineInventory 实体**
  - 新增 `annualInspectionDate` 字段（年审日期）

### 修改
- `ConfigItemServiceImpl`
  - 删除配置项/配置值时增加车辆引用检查，存在引用则抛出 `CONFIG_IN_USE` 业务异常，防止误删
- `PartInventoryServiceImpl`
  - 新增配件编码唯一性校验，保存时自动检查重复编码

## [0.1.1] - 2026-05-03 - xrkht

### 新增
- **统一响应格式**
  - 新增 `Result<T>` 通用响应类（`common/Result.java`）
  - 新增 `ResultCode` 状态码枚举（`common/ResultCode.java`）
  - 统一封装 code/message/data/timestamp 四个字段
  - @JsonInclude 过滤 null 值

- **全局异常处理**
  - 新增 `BusinessException` 业务异常类（`exception/BusinessException.java`）
  - 新增 `GlobalExceptionHandler` 全局异常处理器（`handler/GlobalExceptionHandler.java`）
  - 支持统一的异常拦截与响应格式

### 修改
- `MachineInventoryController.java`
  - 返回类型从 ResponseEntity 统一改为 Result<T>
  - 使用 BusinessException 替代硬编码的 notFound 响应
  - 添加 @Valid 参数校验注解
  - 添加 @Slf4j 操作日志记录
  - 保留原有业务注释

- `application.yml`
  - 修复注释乱码
  - 新增 spring.mvc.throw-exception-if-no-handler-found: true
  - 新增 spring.web.resources.add-mappings: false
  - 优化日志级别配置

---

## [0.1.0] - 2026-05-02 - xrkht

### 新增
#### 整车配置管理模块
- 创建车辆实际配置实体类 `MachineConfig`，记录每台车具体装了什么配置
- 创建 `MachineConfigRepository` 数据访问层，支持按车辆ID查询、按配置项ID/配置值ID反查、按车辆ID删除
- 创建 `MachineConfigService` 接口及其实现类，提供配置明细的增删改查
- 实现车辆完整信息查询接口 `GET /api/inventory/{id}/detail`，返回车辆基本信息及配置明细
- 实现完整的入库接口 `POST /api/inventory/inbound`，可一次性保存车辆基本信息和多条配置明细
- 实现车辆配置更新接口 `PUT /api/inventory/{id}/configs`，先删除旧配置再保存新配置
- 创建入库请求 DTO `InboundRequestDTO`，封装车辆信息和配置选择列表
- 删除车辆时级联删除关联的配置明细

#### 系统字典（配置项）管理模块
- 创建配置项实体类 `ConfigItem`，支持一级分类、二级分类、输入类型（SELECT/MULTI_SELECT/NUMBER/TEXT/BOOLEAN）、是否必选、排序等
- 创建配置项可选值实体类 `ConfigValue`，支持值标签、值编码、默认值标记、排序
- 创建 `ConfigItemRepository`，支持按编码查询、按分类查询、按排序号排序查询
- 创建 `ConfigValueRepository`，支持按配置项ID查询可选值、查询默认值、按配置项ID删除所有可选值
- 创建 `ConfigItemService` 接口及其实现类，提供配置项 CRUD 和可选值管理
- 创建 `ConfigItemController` REST API 控制器
  - `GET /api/config/items` - 查询所有配置项
  - `GET /api/config/items/category/{category}` - 按分类查询
  - `GET /api/config/items/{id}` - 查询单个配置项
  - `POST /api/config/items` - 新增配置项
  - `DELETE /api/config/items/{id}` - 删除配置项（级联删除可选值）
  - `GET /api/config/items/{itemId}/values` - 查询某配置项的所有可选值
  - `POST /api/config/values` - 新增可选值
  - `DELETE /api/config/values/{id}` - 删除可选值

#### 配置替换日志模块
- 创建配置替换记录实体类 `ConfigReplaceLog`，记录车辆配置的变更历史（旧值→新值、替换类型、操作人等）
- 创建 `ConfigReplaceLogRepository`，支持按车辆ID查询替换记录（按时间倒序）
- 创建 `ConfigReplaceLogService` 接口及其实现类
- 创建 `ConfigReplaceController` REST API 控制器
  - `GET /api/replace/machine/{machineId}` - 查询某车辆的所有替换记录
  - `POST /api/replace` - 记录一次配置替换

#### 配件库存模块
- 创建配件库存实体类 `PartInventory`，支持配件编码（唯一）、品牌、名称、规格、分类、适用车型、来源（采购/拆车/退货）、来源车辆编号、库存数量等完整字段
- 创建 `PartInventoryRepository`，支持按配件编码查询、按分类查询、按来源查询、按来源车辆查询、按库存数量大于阈值查询

#### 外出维修记录模块
- 创建维修记录实体类 `RepairRecord`，支持维修日期、关联车辆、客户信息、故障描述、维修内容、维修人员、使用配件、工时/费用明细、状态（待维修/维修中/已完成）
- 创建 `RepairRecordRepository`，支持按车辆查询、按维修人员查询、按状态查询、按日期区间查询

### 变更
- `MachineInventoryController` 由 `@Controller` 改为 `@RestController`，确保返回 JSON 格式
- 车辆删除接口新增级联删除配置明细的逻辑
- `MachineInventory` 实体新增 `machineType` 字段（所属车辆类型），支持内燃叉车、电动叉车、托盘搬运车等多种类型

---

## [0.0.1] - 2026-05-01 - xrkht

### 新增
- 初始化叉车进销存ERP系统项目
- 创建整机库存实体类 `MachineInventory`，包含入库、出库、销售等完整字段
- 创建配置项实体类 `ConfigItem`，用于系统字典数据管理
- 实现整机库存数据访问层 `MachineInventoryRepository`，继承 JpaRepository
- 实现整机库存业务逻辑层 `MachineInventoryService` 及其实现类
- 实现整机库存 REST API 控制器 `MachineInventoryController`，提供 CRUD 接口
- 配置 MySQL 数据库连接（localhost:3306/forklift_erp）
- 配置 JPA 自动建表策略（ddl-auto: update）
- 启用 SQL 日志输出和格式化显示
- 添加 Lombok 依赖简化代码开发（getter/setter/toString 等）
- 实现实体审计字段（createdAt/updatedAt）自动填充
- 添加车号/产品编号唯一性约束
- 实现根据车号查询的自定义方法

### 技术栈
- Spring Boot 3.5.14
- Java 25
- MySQL 8.0
- Spring Data JPA
- Lombok
- Maven 构建工具

### 配置
- 服务器端口：8080
- 数据库时区：Asia/Shanghai
- 字符编码：UTF-8
- 日志级别：debug（com.example.forklift_erp 包）

---

## 版本说明

### 版本号规则

- **主版本号（Major）**：不兼容的API修改（如 1.0.0 → 2.0.0）
- **次版本号（Minor）**：向后兼容的功能性新增（如 1.0.0 → 1.1.0）
- **修订号（Patch）**：向后兼容的问题修正（如 1.0.0 → 1.0.1）

### 变更记录类型

- **Added（新增）**：新功能
- **Changed（变更）**：现有功能的变更
- **Deprecated（即将废弃）**：即将在未来版本中移除的功能
- **Removed（移除）**：已移除的功能
- **Fixed（修复）**：Bug 修复
- **Security（安全）**：安全性相关的修复或改进

---

**作者**: xrkht
**项目**: 叉车进销存ERP系统 (Forklift ERP)  
**仓库**: D:\erp\forklift-erp
