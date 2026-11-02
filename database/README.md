# 数据库说明

正式数据库结构以 `backend/src/main/resources/db/migration` 中的 Flyway 迁移为唯一执行源。`database` 目录存放人工初始化和运维辅助脚本，不复制一份可能漂移的建表 SQL。

- 数据库：MySQL 8.0+
- 字符集：`utf8mb4`
- 时区：`Asia/Shanghai`
- 表名：小写下划线
- 主键：`BIGINT` 自增
- 业务时间：`DATETIME(3)`
- 逻辑删除：仅需要保留历史引用的业务表使用 `deleted`

特别约束：

1. `application.cumulative_gpa` 只保存学生报名时填写的累计平均绩点。
2. `participant_term_grade.term_gpa` 只保存成绩单“平均学分绩点”。
3. `participant_term_grade.term_gpa_rank` 只保存成绩单“平均学分绩点排名”，不向学生接口返回。
4. 成绩导入仅持久化当期参与者，非参与者只出现在临时解析结果中。
5. 账号、报名、匹配、材料、成绩与期次变更通过独立历史表或 `operation_log` 留痕。
6. `V2__week_two_matching_workflow.sql` 为候选匹配增加双方决定、拒绝原因和发布时间，并建立状态查询索引。
7. Flyway 迁移必须顺序执行，禁止在已经运行的环境修改 V1 内容。
