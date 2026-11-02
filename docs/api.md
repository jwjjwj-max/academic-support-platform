# REST API（第一至四周）

统一前缀：`/api`。统一响应：

```json
{"code":"OK","message":"操作成功","data":{},"timestamp":"2026-08-12T12:00:00Z"}
```

除注册、登录和健康检查外，请求需带 `Authorization: Bearer <JWT>`。

## 公开接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/students/register` | 学生注册并进入待审核 |
| POST | `/auth/login` | 学生、管理员、系统负责人登录 |
| GET | `/actuator/health` | 健康检查 |

## 已登录通用接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/auth/me` | 全部 | 当前账号、学生审核状态 |
| GET | `/periods` | 全部 | 期次列表 |

## 学生接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/students/me/profile` | 只返回本人资料 |
| PUT | `/students/me/profile` | 修改本人资料；身份资料变化后重新审核 |
| GET | `/applications/mine` | 本人各期报名记录 |
| POST | `/applications` | 报名阶段首次提交；报名审核阶段仅允许被驳回学生修改重提 |
| GET | `/matching/mine` | 双方确认阶段开始后，返回本人已发布匹配与正式帮扶对；匹配阶段不提前暴露 |
| POST | `/matching/candidates/{candidateId}/decision` | 仅双方确认阶段：确认或拒绝；拒绝必须有原因 |
| GET | `/materials/mine` | 本人正式帮扶对、照片、本人活动感悟和历史验收状态；学生不返回另一方感悟 |
| POST | `/materials/pairs/{pairId}/photos` | 跟踪或验收阶段新增/补交过程照片，`multipart/form-data` |
| POST | `/materials/photos/{photoId}/resubmit` | 跟踪或验收阶段由上传人修改重提被驳回照片 |
| POST | `/materials/photos/{photoId}/peer-confirm` | 仅结对同学确认对方照片真实性 |
| POST | `/materials/pairs/{pairId}/reflections` | 跟踪或验收阶段提交本人 300—500 字感悟；提交后管理员可查看且不可自行修改 |
| GET | `/materials/photos/{photoId}/file` | 仅本组成员或管理员鉴权读取私有照片 |
| GET | `/notifications/mine` | 本人站内通知与已读状态 |
| POST | `/notifications/{id}/read` | 标记本人通知已读 |
| GET | `/todos/mine` | 本人照片确认、感悟重提和未读通知待办 |
| GET | `/grades/mine` | 本人各期帮扶前/后平均学分绩点及变化；不含排名和他人数据 |
| GET | `/moral-files?periodId=` | 已发布德育素质分文件 |
| GET | `/moral-files/{id}/file` | 下载已发布德育素质分文件 |

不存在传入任意学生 ID 查询资料的学生接口，避免横向越权。

## 管理员接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/students?status=PENDING&page=1&size=20` | 分页查看学生审核队列 |
| POST | `/admin/students/{userId}/review` | `APPROVED` 或 `REJECTED`，驳回必须有原因 |
| POST | `/admin/students/{userId}/password-reset` | 人工核验后重置学生密码，必须有原因 |
| POST | `/periods` | 创建期次，六项权重合计必须 100 |
| PUT | `/periods/{periodId}` | 编辑期次配置，不能改变阶段 |
| POST | `/periods/{periodId}/status` | 按顺序变更期次阶段，必须有原因 |
| GET | `/periods/{periodId}/transition-preview` | 管理员推进前预检：返回前置阻断、风险提醒和受影响人数 |
| GET | `/admin/applications?periodId=&status=` | 报名审核队列 |
| POST | `/admin/applications/{id}/review` | 仅报名审核阶段：人工通过或带原因驳回 |
| POST | `/admin/applications/{id}/selection` | 仅报名审核阶段且报名已通过：设置匹配池、候补、本期未入选或撤出匹配池；可将已撤出报名重新放回匹配池 |
| POST | `/admin/matching/periods/{periodId}/rounds` | 仅匹配阶段：使用审核通过且已入匹配池的报名运行新一轮可解释匹配 |
| POST | `/admin/matching/periods/{periodId}/rematch` | 仅双方确认阶段：对新拒绝涉及的人员生成局部重匹配轮次，不改变其他已发布或已确认组合 |
| GET | `/admin/matching/periods/{periodId}/candidates` | 查看当前轮候选与得分明细 |
| POST | `/admin/matching/candidates/{candidateId}/select` | 人工调整拟发布组合；普通轮次限匹配阶段，局部重匹配轮次限双方确认阶段 |
| POST | `/admin/matching/rounds/{roundId}/publish` | 确认并发布选中组合；阶段必须与轮次类型一致 |
| GET | `/admin/materials/pairs?periodId=` | 查看正式帮扶对及本组材料 |
| POST | `/admin/materials/photos/{photoId}/review` | 审核照片；驳回必须填写原因 |
| POST | `/admin/materials/pairs/{pairId}/accept` | 验收阶段人工验收，必须填写备注 |
| POST | `/admin/materials/pairs/{pairId}/end` | 跟踪/验收阶段解除帮扶对，必须填写原因 |
| POST | `/periods/{periodId}/acceptance-extension` | 验收阶段延长学生材料提交截止时间，必须填写原因 |
| POST | `/admin/notifications` | 向全部学生或本期审核通过参与者发布站内通知 |
| GET | `/admin/todos` | 照片审核、活动感悟查看和帮扶对待办 |
| GET | `/admin/audit-logs` | 查询最近关键操作留痕，最多 500 条 |
| POST | `/admin/grades/preview` | 上传整院 CSV/XLS/XLSX 成绩单并返回表头、样例、精确建议映射和参与者计数 |
| POST | `/admin/grades/batches/{id}/import` | 确认学号、年级、专业、平均学分绩点及排名映射，统计年级专业人数并仅保存正式参与者成绩 |
| GET | `/admin/grades/batches?periodId=` | 成绩导入批次 |
| GET | `/admin/grades/batches/{id}/file` | 仅管理员下载私有原始成绩单 |
| GET | `/admin/grades/cohort-stats?periodId=` | 最近一次帮扶前/后导入按年级、专业汇总的人数 |
| GET | `/admin/grades?periodId=` | 参与者成绩及管理员专用排名、专业人数和专业前百分比 |
| PATCH | `/admin/grades/{id}` | 带原因修正成绩与排名并保留前后值历史 |
| POST | `/admin/moral-files` | 上传德育素质分文件为草稿 |
| GET | `/admin/moral-files?periodId=` | 文件发布管理列表 |
| POST | `/admin/moral-files/{id}/publish` | 发布或重新发布 |
| POST | `/admin/moral-files/{id}/withdraw` | 撤回已发布文件 |
| DELETE | `/admin/moral-files/{id}` | 软删除非发布状态文件 |
| GET | `/admin/moral-files/{id}/file` | 管理员下载任意未删除文件 |

## 第四周成绩与成果约束

- `BEFORE` 成绩可在跟踪、验收、结束或归档阶段导入；`AFTER` 成绩从验收阶段开始导入。
- 每次 `BEFORE` 或 `AFTER` 导入一份整院成绩表；学号、年级、专业、平均学分绩点和平均学分绩点排名均为必选映射。
- 预览不会保存学生成绩行；确认映射后，系统按“年级 + 专业”统计整院人数并写入 `grade_cohort_stat`，但只对本期正式帮扶对双方保存 `participant_term_grade`，整院其他学生的成绩不得入库。
- 自动映射只精确识别“平均学分绩点”和“平均学分绩点排名”。后端明确拒绝把“平均绩点”或其排名映射为目标字段。
- 同一张表内学号不得重复；排名必须为正整数且不得大于对应“年级 + 专业”的人数。
- 管理员页面按 `专业前百分比 = 专业内排名 ÷ 专业人数 × 100%` 展示；排名变化为 `帮扶前专业前百分比 - 帮扶后专业前百分比`，正数表示提升，单位为百分点。
- 已确认导入的批次不能再次导入，避免同一原始表被重复确认；新上传的同阶段批次会更新正式参与者对应阶段成绩，并以其分组统计作为最新人数快照。
- 管理员修正必须提交原因，`grade_correction_history` 保存修改前后绩点、排名、操作人和时间。
- 学生结果 DTO 只含本人帮扶前/后平均学分绩点和差值，不含学号、姓名、排名、专业人数、排名变化或其他学生数据。
- 原始成绩单与德育文件均使用随机私有存储键；原始成绩单无学生下载接口。
- 德育文件发布前、撤回后或删除后学生均不可见；平台不计算德育素质分。

## 第三周材料与文件约束

- 学生只能访问自己正式帮扶对的数据，传入其他帮扶对或照片编号仍会由后端归属校验拒绝。
- 照片声明类型、扩展名和文件签名字节必须一致，仅允许 JPG/JPEG/PNG，默认单张不超过 5 MB。
- 文件使用 UUID 随机存储键保存在私有目录，不提供静态公开 URL；下载响应为 `private, no-store`。
- 照片须由非上传方确认真实性后才能进入管理员审核；上传人不能自行确认。
- 照片和感悟提交后锁定，只有管理员带原因驳回后，原提交人才可修改重提。
- 感悟按 Unicode 字符计数，去除首尾空白后必须为 300—500 字。
- 新增照片在跟踪或验收阶段的对应截止时间前开放；验收阶段可补交照片、提交感悟或重提被驳回材料。
- 验收阶段可由管理员延长学生材料提交截止时间，必须填写原因并写入操作日志。
- 验收只校验材料前置条件，最终完成状态仅由管理员在验收阶段手工设置，绝不自动完成。
- 所有提交、同伴确认、审核、驳回、重提、解除和验收操作均写入操作日志，材料动作另存修订历史。

## 第二周关键约束

- 同一学生同一期次只允许一条报名，非驳回状态不能重复提交或自行退出。
- 报名快照在首次提交时固定；后续个人资料变更不会改变该期报名快照。
- 领航者年级入学年份必须小于等于攀登者，且领航者报名累计平均绩点必须更高。
- 匹配评分使用期次保存的六项权重，总分和每项解释均持久化。
- 系统生成方案后仍需管理员调整、确认和发布；不会直接面向学生发布。
- 任一方拒绝后保存拒绝原因，同组合默认不再推荐；管理员在双方确认阶段运行局部重匹配，不需要也不允许倒退期次阶段，其他已发布或已确认组合保持不变。
- 双方均确认后才生成正式帮扶对，并仅向双方返回微信号和选填联系电话。

### 报名与匹配阶段权限矩阵

| 期次阶段 | 学生首次报名 | 驳回后修改重提 | 管理员审核/驳回 | 管理员设置入选状态 | 普通匹配 | 局部重匹配 | 学生确认/拒绝 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `REGISTRATION` 报名阶段 | 允许 | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 |
| `REVIEW` 报名审核阶段 | 禁止 | 允许 | 允许 | 仅审核通过后允许 | 禁止 | 禁止 | 禁止 |
| `MATCHING` 匹配阶段 | 禁止 | 禁止 | 禁止 | 禁止 | 允许，仅使用 `APPROVED + MATCHING_POOL` | 禁止 | 禁止，且不展示结果 |
| `CONFIRMATION` 双方确认阶段 | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 | 仅有新拒绝时允许 | 允许，受确认截止时间约束 |
| `TRACKING` 及之后 | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 |
| 其他阶段 | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 | 禁止 |

以上限制由后端服务读取报名所属期次后强制校验；前端按钮禁用和中文提示只用于操作引导，不作为权限边界。

### 阶段、时间窗口与个人状态

- 全局阶段是所有角色共享的唯一业务阶段，也是后端权限判断的最终依据。
- 学生首次报名必须同时满足：期次为 `REGISTRATION`，且当前时间位于 `[registrationStart, registrationEnd)`。
- 报名截止时间到达后自动禁止新报名，但不自动改变全局阶段；管理员确认后手动推进至 `REVIEW`。
- 被驳回报名不受首次报名时间窗口限制，只允许在 `REVIEW` 修改重提。
- `REVIEW`、`MATCHING`、`CONFIRMATION` 不按时间自动推进，避免未处理数据被自动锁死。
- 学生端同时展示“活动阶段”和“我的状态”，不展示匹配池等管理员内部操作术语。

阶段推进前置规则：

| 推进 | 后端规则 |
| --- | --- |
| `ANNOUNCED → REGISTRATION` | 未到报名开始时间允许推进，但必须二次确认；学生仍需等到开始时间才能报名 |
| `REGISTRATION → REVIEW` | 未到报名结束时间属于提前关闭报名，必须二次确认并填写原因 |
| `REVIEW → MATCHING` | 不得存在待审核/审核中报名；所有审核通过报名必须明确为匹配池、候补或本期未入选 |
| `MATCHING → CONFIRMATION` | 至少已有一组经管理员确认并发布的匹配结果 |
| `CONFIRMATION → TRACKING` | 未完成双方确认或未到帮扶开始时间时必须二次确认 |
| `TRACKING → ACCEPTANCE` | 未到验收开始时间时必须二次确认 |
| `ACCEPTANCE → ENDED` | 所有帮扶对必须已有验收完成或终止结果 |

前端推进对话框必须先调用预检接口，展示影响人数、阻断原因和风险；风险推进需勾选明确确认。后端仍会在正式推进时重复校验，避免绕过前端。

## 系统负责人接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/owner/admins` | 管理员列表 |
| POST | `/owner/admins` | 创建独立管理员账号 |
| PUT | `/owner/admins/{adminId}/status` | 启用或停用，必须有原因；立即撤销已有令牌 |

## 状态码

- `400`：字段或业务规则不满足；
- `401`：未登录、令牌失效、密码错误；
- `403`：账号有效但角色无权；
- `404`：业务对象不存在；
- `409` 预留给后续并发冲突；
- `429`：15 分钟内同账号与来源登录失败达到 5 次；
- `500`：未预期系统错误，不向前端泄露堆栈。
