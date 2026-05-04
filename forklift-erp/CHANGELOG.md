# 变更日志 (Changelog)

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/) 和 [Keep a Changelog](https://keepachangelog.com/zh-CN/) 规范。

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