# 学业帮扶智慧平台

面向单学院、单期 100 人以内的响应式学业帮扶网站。正式工程与已确认原型分离：`prototype` 保持原样，仅作为交互和视觉参考；业务数据通过 Spring Boot API 持久化到 MySQL。

## 当前进度

已完成第一至四周首版核心模块：

- Vue 3 + TypeScript + Vite 响应式前端框架；
- Spring Boot 3 + Spring Security + MyBatis-Plus 后端框架；
- MySQL 8 + Flyway 版本化数据结构；
- JWT 登录、BCrypt 密码、账号停用与令牌立即失效；
- 学生注册、管理员人工审核、驳回原因、修改重提；
- 个人资料修改和身份资料变更后重新审核；
- 系统负责人创建、启用和停用管理员；
- 管理员重置学生密码；
- 活动期次基础配置、照片范围和六项匹配权重校验；
- 报名快照、匹配、材料、两类绩点、成绩修正、德育分文件和审计日志数据结构。
- 两类学生按期次报名、报名累计平均绩点、手填课程、学习风格和性格特点；
- 报名人工审核、驳回重提、匹配池、候补和本期未入选；
- 年级与报名累计平均绩点硬约束、课程别名匹配和六项可解释评分；
- 管理员运行匹配、人工调整、确认后发布；
- 双方确认或带原因拒绝、拒绝组合隔离、双方确认阶段内的局部重新匹配；
- 双方确认后生成正式帮扶对并开放联系方式。
- 全局阶段、业务时间窗口和学生个人状态分层展示；首次报名同时受阶段与报名时间硬限制。
- 管理员阶段推进支持前置条件预检、影响人数、提前推进风险确认和全过程留痕。
- 正式帮扶对过程照片上传、同伴真实性确认、管理员审核、带原因驳回和修改重提。
- 双方分别提交 300—500 字活动感悟，提交后锁定，管理员驳回后方可修改重提。
- 管理员解除帮扶对、人工验收，完成/终止记录进入学生历史视图。
- 站内通知、学生与管理员关键待办、管理员操作日志查询。
- JPG/JPEG/PNG 文件签名字节、声明 MIME、扩展名和 5 MB 大小联合校验。
- 私有文件随机存储；照片只能通过登录后的归属鉴权接口读取，无公开静态路径。
- 帮扶前/后整院成绩单上传预览、字段映射、参与者过滤、校验与导入；按年级和专业统计人数，计算专业前百分比与帮扶前后排名提升百分点，并精确区分“平均学分绩点”和“平均绩点”。
- 管理员成绩修正原因及前后值留痕；学生仅查看本人单学期平均学分绩点变化，不展示排名。
- 德育素质分 CSV/XLS/XLSX/PDF 私有上传、草稿、发布、撤回、软删除与学生下载。

## 目录

```text
frontend/    Vue 3 前端
backend/     Spring Boot 后端
database/    数据库人工初始化说明和脚本
docs/        API、部署、架构与开发决策
prototype/   已确认交互原型（不修改、不作为业务数据库）
```

## 开发环境

- JDK 21
- Maven 3.9+
- Node.js 24（Vue 官方要求 `^20.19.0 || >=22.12.0`）
- pnpm 11
- MySQL 8.0+

## 首次启动

### 1. 初始化 MySQL

使用有建库权限的账号执行 [create-database.sql](./database/create-database.sql)，并先替换其中的示例密码。不要把真实密码提交到仓库。

PowerShell 示例：

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/academic_support?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
$env:DB_USERNAME='academic_support'
$env:DB_PASSWORD='替换为数据库强密码'
$env:JWT_SECRET='替换为至少32字节的随机密钥'
$env:BOOTSTRAP_OWNER_LOGIN='owner'
$env:BOOTSTRAP_OWNER_PASSWORD='替换为至少10位且包含字母和数字的密码'
$env:PRIVATE_FILE_ROOT='D:\academic-support-data\private'
```

首次启动时仅当数据库中没有系统负责人且同时设置上述两个 `BOOTSTRAP_OWNER_*` 变量，后端才创建初始负责人。后续应移除引导变量。

### 2. 启动后端

```powershell
mvn -pl backend spring-boot:run
```

后端地址：`http://localhost:8080/api`。Flyway 会自动执行版本化迁移。

### 3. 启动前端

```powershell
Set-Location frontend
pnpm install
pnpm dev
```

前端地址：`http://localhost:5173`，Vite 会把 `/api` 代理到后端。

## 验证命令

```powershell
mvn -pl backend test
Set-Location frontend
pnpm build
```

后端测试使用 H2 的 MySQL 兼容模式，不依赖本机 MySQL 密码；覆盖第一至四周集成测试，包括匹配、材料、整院成绩统计、排名百分比、文件和权限闭环。生产结构仍以 Flyway MySQL 脚本为准。

## 默认限制

- 照片：JPG/JPEG/PNG，单张 5 MB；
- 成绩单：CSV/XLS/XLSX，单文件 20 MB；
- 德育分名单：CSV/XLS/XLSX/PDF，单文件 20 MB；
- JWT 有效期：默认 120 分钟；
- 学生密码：8—72 位，至少包含字母和数字；
- 管理员初始密码：10—72 位，至少包含字母和数字。

私有文件根目录通过 `PRIVATE_FILE_ROOT` 配置，禁止由 Nginx 或其他静态服务器直接映射。以上为不影响架构的首版默认值。


## 公开仓库范围

仓库包含前后端源码、Flyway迁移、隔离测试、部署模板及开发文档。真实学生资料、成绩原表、上传附件、截图、密钥、本地配置和数据库备份不纳入版本管理。部署前替换存储桶和CAM策略示例，并通过服务器私有环境文件提供凭证。

测试账号和密码仅用于H2隔离测试，不是生产环境初始化账号。生产负责人通过 `BOOTSTRAP_OWNER_*` 显式初始化，使用独立强密码。部署说明见 [docs/deployment.md](docs/deployment.md)。
