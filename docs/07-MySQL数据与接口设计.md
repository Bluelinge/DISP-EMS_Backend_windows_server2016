# DISP-EMS · MySQL 数据与接口设计

版本：1.0　编写日期：2026-09-30　适用：当前操作端、管理端及投影窗口。

2026-10-20 范围值说明：本文关系表 SQL 是历史设计草案，其 `minimum_value`、`maximum_value` 和闭区间约束不足以完整表示新的单侧及开闭边界。当前运行实现直接保存在 `steps_json` 及任务快照的 `referenceValue` 中，按四段格式解析，不执行本文 SQL、不新增字段或迁移；实际接口与回归要求见 `02-接口契约.md`、`05-验收与回归清单.md`。

本文是独立的后端开发交付文档：包含页面数据依据、MySQL 表结构、数值编码、REST 接口、统计公式及验收场景。项目目前仍是 Vue + Pinia + localStorage 原型，本文描述后续服务端实现，不代表后端已经上线。

事实来源：`src/main.js` 的实际路由，`src/store.js` 的任务/交接规则，`src/views/{Login,SelectProcess,Work,Complete,Projector,Admin}.vue`，`src/components/{ProjectionCanvas,ProjectorEditor,ProductionInsightsCharts,UserProfileDialog}.vue`，以及 `src/utils/videoBinding.js`。旧文档与代码冲突时，采用本文注明的当前代码行为或明确的服务端设计修正。

## 1. 统一约定

### 1.1 字段、时间与数据归属

- MySQL 8.0.17 及以上，InnoDB，utf8mb4，使用 `utf8mb4_0900_bin` 区分业务编号和参数文本的大小写。所有连接设置 UTC；数据库 `DATETIME(3)` 保存 UTC，HTTP 时间返回 ISO 8601，例如 `2026-09-30T02:00:00.000Z`。日期筛选按 `Asia/Shanghai` 的自然日转换为 UTC 查询边界。
- 数据库 snake_case、接口 camelCase；本文字段表和 SQL 是数据库名，示例和接口字段是 HTTP 名。
- 永久主键为 `BIGINT UNSIGNED AUTO_INCREMENT`，HTTP 中所有主外键均为字符串；工位编号、步骤号、编码、数量是 JSON 数字。任务展示编号 `taskNo` 与永久 `id` 分开。
- 状态、类型、来源、权限、标记一律使用整数编码；不使用 MySQL ENUM，也不使用 `"进行中"`、`"manual"`、`"icon"` 等字符串作为机器状态。中文名称用于显示，不作为判断依据。字号、颜色、型号、钢印号、文本、月份、单位等自然业务数据仍为字符串或数值。
- `null` 表示缺失；合法实际值 `"0"` 不得作为未填写。不再以字符串 `NONE` 表示视频解绑，改为 `videoId: null`。
- 查询默认 `is_deleted=0`；历史快照/审计查询按历史 ID 读取保留记录，不能因为主数据软删除而丢失历史。
- 文件内容存对象存储/文件服务，MySQL 保存元数据和对象键；播放/预览地址由文件接口签发，不保存浏览器 `blob:` 地址或 base64 图片。
- 默认单工厂、单生产区域；型号数量与工位配置来自数据库，不把“4个型号”“11个展示格”解释为数据库数量上限。

### 1.2 每张表必须包含的公共字段

下文每条 CREATE TABLE 都实际列出这些字段，而不只用备注代替。

| 数据库字段 | HTTP 字段 | 类型 | 依据与规则 |
|---|---|---|---|
| created_by | createdBy | BIGINT UNSIGNED | 创建人永久用户 ID，服务端取当前登录用户 |
| created_at | createdAt | DATETIME(3) | 服务端首次入库 UTC 时间 |
| updated_by | updatedBy | BIGINT UNSIGNED | 最近修改人永久用户 ID；新增时等于创建人 |
| updated_at | updatedAt | DATETIME(3) | 服务端最近修改 UTC 时间 |
| is_deleted | isDeleted | TINYINT UNSIGNED | 0 未删除，1 已删除 |

系统初始化及后台计算使用 `created_by=0 / updated_by=0`，表示系统主体；人员表保留 ID=0 的系统主体，不允许登录或人工编辑。公共操作者字段保留永久 ID，服务端校验存在性，不添加循环的数据库外键。可额外展开 `createdByName / updatedByName`；历史事件另有冻结姓名，优先使用冻结姓名。

创建/更新请求不能指定上述五个字段；后端忽略或拒绝客户端伪造。审计与快照行虽有公共字段，但正常业务不提供 DELETE 或覆盖历史内容的接口。新增审计行的创建人与更新人相同，更新字段随后不变；快照的元数据不能被当作操作员实际完成时间。

统计、分页容器、权限派生值、文件 URL 和不存在实体的“未配置工位格”是查询结果，不是数据库实体，不虚构创建人等字段。返回 `asOf`、筛选条件、数值来源；其中嵌套真实实体仍返回公共字段。

### 1.3 编码表

| 字段/用途 | 数字含义 |
|---|---|
| 通用删除、是否默认、是否内置、是否允许播放、是否闪烁、是否已填写、是否偏离 | 0 否/未删除，1 是/已删除 |
| 人员 role_code | 0 操作员，1 管理员，2 超级管理员 |
| 人员 user_kind | 0 人员，1 系统主体 |
| 人员 status_code；型号 status_code | 0 启用/在岗，1 停用 |
| 工艺偏好 scope_code | 0 操作端偏好，1 管理端偏好 |
| 任务开始 start_mode | 0 新建，1 恢复 |
| 播放 playable / 流程权限 / 可编辑 editable | 0 否，1 是 |
| 播放 unavailable_code | 0 可播放，1 禁止播放，2 待解析，3 解析失败，4 已删除 |
| 流程 block_code | 0 可用，1 前序未确认，2 已交接，3 任务完成，4 无本站步骤，5 尚未处理完，6 参数未填 |
| 登录入口 port_code | 0 操作端，1 管理端 |
| 会话 session_state | 0 有效，1 已退出/撤销；过期由 expires_at 判定 |
| 工位 capture_mode | 0 手动录入，1 自动读取绑定 |
| 工序 importance_code | 0 普通，1 重要 |
| 参数 parameter_type | 0 手动输入，1 单选，2 范围值 |
| 参考校验 reference_mode | 0 严格文本，1 四段边界范围，2 第一项合格；仅历史迁移的 type=0 区间允许 mode=1 |
| 视频分类 level_no | 0 一级，1 二级 |
| 文件 file_kind | 0 工艺 Excel，1 视频，2 投影 PNG |
| 文件 processing_state | 0 待解析，1 可用，2 解析失败 |
| 素材 source_code | 0 内置素材，1 上传素材 |
| 工艺版本 revision_state | 0 预检草稿，1 已发布；当前版本由 current_revision_id 决定 |
| 导入 import_state | 0 预检通过待确认，1 预检失败，2 已提交，3 已过期 |
| 导入问题 severity_code | 0 错误，1 提示 |
| 投影 element_type | 0 图标，1 文本 |
| 投影 bind_code | 0 纯文本，1 步骤标题，2 步骤号，3 参数1名，4 参数1值，5 参数2名，6 参数2值，7 参数3名，8 参数3值，9 进度，10 型号，11 钢印号，12 任务编号，13 操作员姓名 |
| 任务 status_code | 0 进行中，1 已完成 |
| 步骤结果 action_code | 0 实际完成，1 普通工序跳过，2 重要工序跳过；无结果行表示未操作 |
| 审计 event_code | 0 步骤保存/回改，1 普通跳过，2 重要跳过，3 汇总参数修改，4 本站确认，5 最终提交，6 钢印号修改，7 产品编码替换，8 新建任务，9 恢复任务 |
| 审计 reason_code | 0 无，1 工位内跳转，2 进入参数汇总，3 人工修改，4 开始或恢复时替换编码 |
| 汇总 scope_code | 0 本站，1 全任务最终汇总 |
| 汇总 summary_state | 0 草稿，1 已确认 |
| 在制绑定 binding_state | 0 生效，1 已归档解除 |
| 参数来源 source_code | 0 步骤保存，1 本站汇总，2 最终汇总 |
| 文件版本 replace_mode | 0 仅新增，1 替换既有资源 |
| 工位热力 heat_code | 0 空闲，1 正常，2 繁忙，3 拥堵，4 未配置 |
| 比较 comparison_state | 0 无同期数据，1 持平，2 上升，3 下降 |
| 比较单位 comparison_unit | 0 百分比，1 百分点 |
| 数据来源 data_source | 0 真实业务，1 演示预览 |
| 查询参数 preset_code | 0 自定义，1 近7日，2 近30日 |
| 删除引用阻塞 block_code | 0 可删除，1 默认项保护，2 存在子分类，3 当前配置引用，4 在制快照引用 |
| 操作明细 row_type/result_code | row_type：0 步骤，1 钢印号修改；result_code：0 未操作，1 已完成，2 普通跳过，3 重要跳过，4 编号已修改 |
| 幂等处理 request_state | 0 处理中，1 已成功 |

规则：所有编码只在对应字段内有意义；`importanceCode=1` 与 `roleCode=1` 不代表同一含义。接口会返回标签便于显示，但标签不是状态字段。服务端定义数值常量并验证允许范围，禁止请求中出现任意字符串状态。

### 1.4 HTTP 与权限

- 基地址 `/api/v1`；HTTPS；认证使用 Bearer Token。用户 ID、工位权限均从登录会话验证，不信任请求中的操作者姓名。
- 权限标记：P 登录前读取；U 任意在岗登录用户；O 操作端身份且选择有效工位；A 管理员/超级管理员；S 超级管理员。roleCode=0只能进入操作端；roleCode=1/2可以进入管理端，也可选择工位切换到操作端。A切换时仍为原用户和原角色，不能像原型那样伪装成另一个账号。
- S 独占人员新增、编辑、删除、重置密码；O 只能写当前工位且满足交接规则的任务步骤，跨工位可查看。A/S 可读任务审计及统计，不提供直接篡改历史任务接口。
- 成功：`{code:0,message:"成功",data:...,requestId:"..."}`；分页 `data={items,page,pageSize,total}`。page>=1，pageSize=10/20/50/100，默认20；服务端最多100。所有列表稳定按业务排序字段和 id 排序。
- 错误：`{code:数值,message:中文信息,data:null,errors:[],requestId:"..."}`。errors 可包含 `sourceRow / field / code / message`，不返回密码、令牌哈希或内部异常堆栈。
- POST 创建/确认/上传替换/跳步等写操作携带 `Idempotency-Key`；更新已有根实体同时携带 `expectedVersion`。读接口不改变进度、不写跳步。
- `row_version` 在有效写入后加1；事务更新条件包含 expectedVersion，过期版本返回409。软删除也更新操作者、更新时间及版本。

| HTTP | code | 情况 |
|---|---|---|
| 400 | 1001 | 字段格式或数值编码不合法 |
| 401 | 1002 | 未登录/会话过期 |
| 403 | 1003 | 无角色权限或工位写权限 |
| 404 | 1004 | 资源不存在/已删除且不允许历史访问 |
| 409 | 1005 | 唯一键冲突，如已有在制钢印号或重名资源 |
| 409 | 1006 | expectedVersion 或幂等请求内容冲突 |
| 422 | 1007 | 工艺导入预检错误 |
| 422 | 1008 | 前序未确认 |
| 422 | 1009 | 存在未填写参数 |
| 422 | 1010 | 已交接/已归档不可修改 |
| 422 | 1011 | 删除引用保护 |
| 422 | 1012 | 型号与在制任务不一致 |
| 422 | 1013 | 同名视频需选择替换或取消 |
| 422 | 1014 | 未处理完本站步骤，需由汇总入口完成跳步 |
| 500 | 1500 | 服务异常 |

## 2. 页面数据来源总表

下表覆盖实际路由。未列出的页面修饰图形、按钮文案、颜色、响应式布局属于前端静态资源；状态标签由1.3编码表翻译，不另造业务数据。

### 2.1 登录及公共页面

| 页面/展示项 | 表或文件依据 | 公式/规则 | HTTP 数据/接口 |
|---|---|---|---|
| /login：工号、登录身份 | sys_user | 工号唯一、在岗且未删除；哈希校验密码，返回数值角色 | POST /auth/login → user.no/name/roleCode/statusCode |
| 工位下拉、识别模式 | workstation | 有效工位按 station_no 升序；登录前仅公开这些基础字段 | GET /workstations/options → id/stationNo/name/captureMode |
| 当前登录人、个人信息 | sys_user、auth_session | 登录人档案；手机存真实号码，展示时按前端规则处理 | GET /auth/me |
| 自助改密 | sys_user.password_hash | 服务端校验原密码，修改后撤销全部旧会话 | POST /auth/change-password |
| 切换端口/退出 | auth_session | 保留原用户权限；退出撤销会话 | POST /auth/port、POST /auth/logout |
| 品牌、减振器图、公司名、版本 | public/syncdot-logo*.svg、public/yyjzq-clean.png、Login.vue、package.json | 静态文件和构建版本 | 前端静态资源；无虚构数据库字段 |
| 重置演示数据 | 当前原型 localStorage | 仅原型功能 | 生产不提供业务库重置接口 |

### 2.2 操作端

| 路由/展示项 | 来源表 | 数据依据 | 接口/返回字段 |
|---|---|---|---|
| /op/select：当前工位名称、手动/自动提示 | workstation、auth_session | 会话绑定的永久工位 ID | GET /auth/me → workstation |
| 产品编码、型号、钢印编号 | task_binding、product_model | 手动工位扫码后由用户填写；自动工位按有效产品编码绑定读取 | GET /bindings/by-product-code → productCode/model/steelNo/taskId |
| 型号下拉 | product_model | 启用型号；初始4个型号，支持后续增加 | GET /models → items |
| 匹配工艺卡、来源文件、本工位工序数、重要工序数 | process_definition/revision/step、stored_file | 有效当前版本、型号精确匹配、包含当前工位步骤；重要数为整个工艺中 importance=1 数量 | GET /processes?modelId=&workstationId= → items、stationStepCount/importantStepCount |
| 上次选择的工艺 | user_station_preference | 当前用户+永久工位+型号 | GET/PUT /operator/preferences |
| 开始作业/恢复任务 | task、task_binding、快照表 | 同一钢印号在制任务必须恢复，不重复创建 | POST /tasks/start → task、startMode(0新建/1恢复)、cursor |
| /op/work/:taskId：任务概况、步骤标题、重要标记、工位 | task、task_station_snapshot、task_step_snapshot | 冻结定义优先，工位不能被当前名称替换 | GET /tasks/{id}/work → task/stations/steps/currentStep |
| 工序进度节点、跳转列表、黄星 | task_step_snapshot、task_step_result | 按 step_no 排序；已完成为 action_code=0；黄星由 importance_code=1 | work.steps[].importanceCode/result |
| 本工位进度 | 同上 | 已有步骤结果行数量 / 本站快照步骤数量（包括跳过）；另返回实际完成数，避免混淆 | work.progress.processed/total/completed |
| 可编辑、前序等待、交接冻结、汇总入口 | task、task_summary、task_station_snapshot | 后端按流程派生权限，不能由前端禁用控件代替 | work.permissions.canEdit/canOpenSummary/blockCode |
| 视频名、播放地址、不可播放原因 | 冻结 video_id → video_resource → 当前 video_file_version → stored_file | 绑定稳定视频 ID；显式替换媒体后提供最新可用版本 | work.currentStep.video |
| 参数名称、类型、参考、范围、候选项 | task_parameter_snapshot、task_parameter_option_snapshot | 冻结数据；type=2显示四段边界；单选第一项为合格项 | work.currentStep.parameters[] |
| 实际录入/历史值 | task_parameter_value | parameterSnapshotId 对应实际值，保留异常文本 | work.currentStep.parameters[].actualValue |
| 保存继续/回改/跳步 | task_step_result、task_operation_event、task_parameter_value | 当前结果可更新，事件保留每次变更 | PUT /tasks/{id}/steps/{stepId}/result、POST /tasks/{id}/jumps |
| /op/work/:taskId/complete：本站/最终标题、工位、型号、产品编码、钢印号 | task、task_summary、task_station_snapshot | 已确认最终本站且任务未完成→最终汇总；已完成→最终只读 | GET /tasks/{id}/summary → scopeCode/editable/task |
| 汇总步骤、全部参数、缺失数 | 参数快照、实际值、task_summary_item | 本站范围或全任务范围；无参数步骤也展示；空白计数不忽略0 | summary.steps/items/unfilledCount/stepCount |
| 修改钢印编号 | task、task_binding、task_operation_event | 同事务修改映射并保存修改前后、操作者、工位名称 | POST /tasks/{id}/steel-no |
| 交接下一工位/确认完成任务 | task_summary、task_summary_item、task、task_binding | 所有必填值已填，前序已确认；交接冻结本站；最终提交归档 | POST /tasks/{id}/stations/{stationId}/confirm、POST /tasks/{id}/complete |
| /op/projector：任务、型号、当前步骤、元素、实时变量 | task_view_cursor、任务快照、实际值、视频/图标文件 | 独立用户/任务/工位游标；轮询数据更新，不能读取浏览器内存作为权威 | GET /tasks/{id}/projection?cursorId= |
| 副屏名称/分辨率、权限、自动窗口位置 | Window Management API、浏览器本机偏好 | localStorage键 disp-mes-projector-screen-v1；无业务库数据 | 浏览器能力，不需要业务接口 |

### 2.3 管理端

| 路由/展示项 | 来源表 | 数据依据 | 接口/返回字段 |
|---|---|---|---|
| /admin/processes：有效型号卡、列表数量 | process_definition、product_model | 未删除且有当前发布工艺的型号/工艺数；每型号一份有效工艺 | GET /processes → total；GET /processes/metrics → modelCount |
| 当前工艺标记、重要工序卡 | user_station_preference、process_step | 当前登录用户选中的管理工艺；重要数只统计其当前版本 | metrics.selectedProcessId/importantStepCount；PUT /operator/preferences |
| 工艺列表型号、标题、步骤数、来源文件 | process_definition/revision/step、stored_file | 最新发布版本，不取在制任务快照 | GET /processes → model/title/stepCount/sourceWorkbook |
| 导入文件名、预检行错误、工位回退数量 | process_import_job/issue、stored_file | 服务端解析 Excel，返回来源行及校验结果 | POST /process-imports/precheck；POST /process-imports/{id}/confirm |
| /admin/processes/:id：左侧步骤、黄星、详情、参数、视频三级选择 | process_step/parameter/option、workstation、video_category/resource | 当前发布版本；视频一级→二级→视频；每步最多3组参数 | GET /processes/{id}、GET /video-categories/tree、GET /videos |
| 步骤编辑、模板选择、投影配置 | 工艺版本及投影配置表 | 编辑产生新发布版本，既有任务快照不变 | PATCH /processes/{id}/steps/{stepId}、PUT /processes/{id}/steps/{stepId}/projection |
| /admin/processes/:id/projector-preview：画布、图层、属性 | process_step_projection、process_projection_element | 1920×1080，步骤已复制的独立投影配置 | GET/PUT 步骤projection接口 |
| /admin/processes/:id/projector-preview/live：实况预览 | 同上及工艺步骤、参数 | 管理预览不读取真实任务实际值 | GET /processes/{id}/steps/{stepId}/projection-preview |
| /admin/videos：目录、分类数量、名称、两级分类、时长、批次、分页 | video_category/resource/file_version、stored_file | 一级包含其直属和二级视频；有效资源分页，时长由文件解析 | GET /video-categories/tree、GET /videos → name/categoryPath/durationSec/batchNo |
| 重名提示、替换或取消、视频预览 | video_resource/file_version | 标准名全库唯一；替换保留video ID及旧文件版本 | POST /videos；POST /videos/{id}/versions；GET /videos/{id}/playback |
| /admin/icons：名称、PNG预览、来源、内置标记 | projection_icon、stored_file | 内置与上传合并返回，隐藏已删除 | GET /projector/icons → name/url/sourceCode/isBuiltIn |
| /admin/projector/templates：名称、默认、说明、数量、画布、操作 | projection_template、projection_template_element | 默认模板不可删；数量COUNT有效元素 | GET /projector/templates |
| /admin/projector/templates/:id：名称、图层、位置、缩放、旋转、字号、颜色、绑定、闪烁 | 模板及元素表 | 类型/绑定数值编码，颜色为字符串 | GET/PUT /projector/templates/{id} |
| /admin/projector/templates/:id/preview：模板预览 | 同上 + 选择的预览工艺 | 模板元素和预览样例/参考值 | GET /projector/templates/{id}/preview?processId=&stepNo= |
| /admin/tasks：任务号、产品编码、型号、钢印号、开始时间、状态、时间/型号筛选 | task | 包括全部真实在制及已完成任务，按开始时间倒序稳定分页；日期筛选按 Asia/Shanghai 自然日转换为 UTC 边界 | GET /tasks?page=&pageSize=&startedFrom=&startedTo=&model=；GET /tasks/models |
| /admin/tasks/:id/audit：每工序一行、所属工位、结果、操作员、时间、原因 | task_step_snapshot LEFT JOIN task_step_result、task_operation_event | 未操作步骤不能因没有日志被遗漏；钢印号事件额外一行 | GET /tasks/{id}/operation-details |
| 审计中的历次回改、替换编码与修改前后值 | task_operation_event、task_parameter_value_history | 事件追加不可覆盖，操作者和工位冻结 | GET /tasks/{id}/events |
| /admin/users：工号、姓名、角色、部门、手机、状态 | sys_user | S权限；不返回密码或passwordHash | GET/POST/PATCH/DELETE /users，POST /users/{id}/reset-password |
| /admin/workstations：编号、名称、识别模式、说明 | workstation | stationNo复用，永久id不复用；默认工位1不能删 | GET/POST/PATCH/DELETE /workstations |
| /admin/stats：六卡、11工位、参数排行、环形图、月趋势 | 真实任务、快照、操作值及确认表 | 详见第6节；没有真实数据就返回空状态 | /stats/overview、/stats/stations、/stats/parameter-deviations、/stats/step-duration-share、/stats/capacity-monthly |
| 所有资源删除提示：当前/在制/历史引用数量 | 当前工艺、模板、任务快照关联 | 服务端扫描全部任务，不只看当前打开任务 | GET 对应资源/{id}/references |
| 管理端菜单、用户角色权限 | auth_session + sys_user.role_code | 菜单按角色派生，不冒充演示管理员 | GET /auth/me → permissions |
| 统计专注模式、日期选择、目录折叠、分页页码 | 前端页面状态 | 专注不持久化；目录折叠和页码不属于业务实体 | 无数据库表；筛选条件传给相应GET接口 |
| /admin/dicts | 路由重定向 | 当前页面未启用；固定数值编码可由metadata接口提供 | GET /metadata/codes；不虚构字典CRUD页面 |

### 2.4 投影变量依据

| bindCode | 当前兼容占位符 | 值依据 |
|---|---|---|
| 0 | 自由文本，可包含以下占位符 | text_content，服务端按白名单替换 |
| 1 / 2 | stepTitle / stepNo | 冻结步骤标题、步骤号；步骤号显示可补两位但数据库不补零 |
| 3 / 5 / 7 | paramName1 / paramName2 / paramName3 | 冻结参数 parameter_index=1/2/3 的名称 |
| 4 / 6 / 8 | paramValue1 / paramValue2 / paramValue3 | 当前实际值；未填写回落冻结参考值，仅用于投影显示，不写成真实值 |
| 9 | progress | 当前查看步骤号 / 全任务步骤数量，兼容现有投影；另返回实际完成数用于作业进度 |
| 10 / 11 / 12 | model / steelNo / taskNo | 任务冻结型号、当前钢印号、task_no |
| 13 | operator | 发起任务的 operator_name，兼容现有画布；另返回当前操作人字段便于以后调整 |

管理预览使用所选工艺参考值，taskId=null。后端返回 rawText、resolvedText；JSON中的bindCode为数字，占位符是文本模板语法。预览样例标记dataSource=1，不写任务、结果或统计库。

## 3. 数据关系与建表 SQL

### 3.1 表分组

| 分组 | 数据表 | 说明 |
|---|---|---|
| 账号与设置 | sys_user、auth_session、product_model、workstation、user_station_preference | 主数据、登录会话、本人工艺选择 |
| 文件与视频 | stored_file、video_category、video_resource、video_file_version | 文件元数据、两级分类、稳定资源与替换版本 |
| 投影 | projection_icon、projection_template、projection_template_element | 素材与模板配置 |
| 工艺 | process_definition、process_revision、process_step、process_parameter、process_parameter_option、process_step_projection、process_projection_element | 当前定义、发布版本和独立投影配置 |
| 导入 | process_import_job、process_import_issue | 服务端预检及逐行错误 |
| 任务冻结 | task、task_station_snapshot、task_step_snapshot、task_parameter_snapshot、task_parameter_option_snapshot、task_projection_element_snapshot | 冻结工位、步骤、参数、候选项和投影配置 |
| 作业与审计 | task_step_result、task_parameter_value、task_operation_event、task_parameter_value_history | 最新步骤结果及参数、不可覆盖的历史变更 |
| 汇总与绑定 | task_summary、task_summary_item、task_binding、task_view_cursor | 本站/最终确认、冻结表单、在制映射和投影游标 |
| 幂等 | api_idempotency | 防止重复开始、保存、交接、替换与提交 |

所有主数据生成的有效唯一键在is_deleted=1时返回NULL，允许软删除后重建，同时保留多条删除历史。工位默认初始化1–7，状态图固定展示1–11；新增按最小空缺station_no分配，不能复用主键id。

所有外键默认RESTRICT，无ON DELETE CASCADE。业务软删除不触发外键操作；服务端必须额外校验“引用方是否有效、是否在制、是否默认”等语义。task内主数据外键用于追溯，展示必须读快照字符串。

### 3.2 完整 SQL

以下按依赖顺序建立36张表。生成列由MySQL计算，不允许接口写入。comment即该业务字段的字段说明；未重复展开每表的公共字段说明，见1.2。

```sql
CREATE DATABASE IF NOT EXISTS disp_ems
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
USE disp_ems;
SET time_zone = '+00:00';
CREATE TABLE sys_user (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  no VARCHAR(64) NOT NULL COMMENT '唯一工号；系统主体固定SYS',
  name VARCHAR(100) NOT NULL COMMENT '姓名',
  user_kind TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0人员1系统主体',
  role_code TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0操作员1管理员2超级管理员',
  status_code TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0在岗1停用',
  department VARCHAR(100) NOT NULL DEFAULT '' COMMENT '部门',
  phone VARCHAR(32) NOT NULL DEFAULT '' COMMENT '手机号',
  password_hash VARCHAR(255) NOT NULL COMMENT '口令哈希，禁止API返回',
  row_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '并发版本',
  UNIQUE KEY uk_user_no (no),
  KEY ix_user_list (is_deleted, user_kind, role_code, status_code),
  CHECK (user_kind IN (0,1) AND role_code IN (0,1,2) AND status_code IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='人员与系统主体';

CREATE TABLE product_model (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  model_code VARCHAR(100) NOT NULL COMMENT '产品型号，如DISPEN9243-40',
  name VARCHAR(100) NOT NULL COMMENT '型号显示名称',
  status_code TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0启用1停用',
  sort_order INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '显示顺序',
  row_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '并发版本',
  active_code VARCHAR(100) GENERATED ALWAYS AS (IF(is_deleted=0,model_code,NULL)) STORED,
  UNIQUE KEY uk_model_active (active_code),
  CHECK (status_code IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='产品型号';

CREATE TABLE workstation (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  station_no INT UNSIGNED NOT NULL COMMENT '可复用工位编号，正整数',
  name VARCHAR(100) NOT NULL COMMENT '工位名称',
  capture_mode TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '0手动1自动',
  is_default TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0普通1默认工位',
  sort_order INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '初始化等于编号，流程按冻结编号排序',
  row_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '并发版本',
  active_no INT UNSIGNED GENERATED ALWAYS AS (IF(is_deleted=0,station_no,NULL)) STORED,
  default_key TINYINT UNSIGNED GENERATED ALWAYS AS (IF(is_deleted=0 AND is_default=1,1,NULL)) STORED,
  UNIQUE KEY uk_workstation_no (active_no),
  UNIQUE KEY uk_workstation_default (default_key),
  CHECK (station_no>0 AND capture_mode IN (0,1) AND is_default IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='工位永久实体';

CREATE TABLE auth_session (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  user_id BIGINT UNSIGNED NOT NULL COMMENT '登录人',
  workstation_id BIGINT UNSIGNED NULL COMMENT '操作端选择的永久工位ID',
  port_code TINYINT UNSIGNED NOT NULL COMMENT '0操作端1管理端',
  token_hash CHAR(64) NOT NULL COMMENT '随机令牌的SHA256摘要',
  session_state TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0有效1撤销',
  expires_at DATETIME(3) NOT NULL COMMENT '过期时间UTC',
  last_seen_at DATETIME(3) NOT NULL COMMENT '最近活动时间UTC',
  UNIQUE KEY uk_session_token (token_hash),
  KEY ix_session_user (user_id, session_state, expires_at),
  FOREIGN KEY (user_id) REFERENCES sys_user(id),
  FOREIGN KEY (workstation_id) REFERENCES workstation(id),
  CHECK (port_code IN (0,1) AND session_state IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='登录会话';

CREATE TABLE stored_file (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
  object_key VARCHAR(512) NOT NULL COMMENT '存储对象键，不是临时URL',
  mime_type VARCHAR(100) NOT NULL COMMENT '文件媒体类型',
  file_kind TINYINT UNSIGNED NOT NULL COMMENT '0Excel1视频2PNG',
  byte_size BIGINT UNSIGNED NOT NULL COMMENT '文件字节数',
  sha256 CHAR(64) NOT NULL COMMENT '文件摘要',
  processing_state TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0待解析1可用2失败',
  duration_ms BIGINT UNSIGNED NULL COMMENT '视频媒体时长，毫秒',
  width_px INT UNSIGNED NULL COMMENT '图像或视频宽度',
  height_px INT UNSIGNED NULL COMMENT '图像或视频高度',
  UNIQUE KEY uk_file_object (object_key),
  KEY ix_file_sha (sha256),
  CHECK (file_kind IN (0,1,2) AND processing_state IN (0,1,2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='文件对象元数据';

CREATE TABLE video_category (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  name VARCHAR(100) NOT NULL COMMENT '分类名称',
  normalized_name VARCHAR(100) NOT NULL COMMENT '去首尾空格后标准分类名',
  parent_id BIGINT UNSIGNED NULL COMMENT '一级为NULL，二级指向一级',
  level_no TINYINT UNSIGNED NOT NULL COMMENT '0一级1二级',
  sort_order INT UNSIGNED NOT NULL COMMENT '同层新增追加',
  row_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '并发版本',
  active_parent BIGINT UNSIGNED GENERATED ALWAYS AS (IF(is_deleted=0,IFNULL(parent_id,0),NULL)) STORED,
  active_name VARCHAR(100) GENERATED ALWAYS AS (IF(is_deleted=0,normalized_name,NULL)) STORED,
  UNIQUE KEY uk_category_sibling (active_parent,active_name),
  FOREIGN KEY (parent_id) REFERENCES video_category(id),
  CHECK ((level_no=0 AND parent_id IS NULL) OR (level_no=1 AND parent_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='视频两级分类';

CREATE TABLE video_resource (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  name VARCHAR(255) NOT NULL COMMENT '当前资源文件名',
  normalized_name VARCHAR(255) NOT NULL COMMENT 'NFKC、去路径/扩展名、trim、合并空白、转小写',
  category_id BIGINT UNSIGNED NOT NULL COMMENT '可关联一级或二级分类',
  allow_playback TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '0禁用作业播放1允许',
  current_version_id BIGINT UNSIGNED NULL COMMENT '当前文件版本，外键稍后添加',
  row_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '并发版本',
  active_name VARCHAR(255) GENERATED ALWAYS AS (IF(is_deleted=0,normalized_name,NULL)) STORED,
  UNIQUE KEY uk_video_name (active_name),
  KEY ix_video_category (category_id,is_deleted),
  FOREIGN KEY (category_id) REFERENCES video_category(id),
  CHECK (allow_playback IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='稳定的视频资源';

CREATE TABLE video_file_version (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  video_id BIGINT UNSIGNED NOT NULL COMMENT '稳定资源ID',
  version_no INT UNSIGNED NOT NULL COMMENT '从1递增，替换追加版本',
  file_id BIGINT UNSIGNED NOT NULL COMMENT '不可覆盖的文件对象',
  batch_no VARCHAR(100) NOT NULL COMMENT '本次批量上传批次号',
  UNIQUE KEY uk_video_version (video_id,version_no),
  FOREIGN KEY (video_id) REFERENCES video_resource(id),
  FOREIGN KEY (file_id) REFERENCES stored_file(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='视频文件替换历史';

CREATE TABLE projection_icon (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  name VARCHAR(100) NOT NULL COMMENT '素材名称',
  file_id BIGINT UNSIGNED NOT NULL COMMENT 'PNG文件',
  source_code TINYINT UNSIGNED NOT NULL COMMENT '0内置1上传',
  is_built_in TINYINT UNSIGNED NOT NULL COMMENT '0否1是',
  row_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '并发版本',
  active_name VARCHAR(100) GENERATED ALWAYS AS (IF(is_deleted=0,name,NULL)) STORED,
  UNIQUE KEY uk_icon_name (active_name),
  FOREIGN KEY (file_id) REFERENCES stored_file(id),
  CHECK (source_code IN (0,1) AND is_built_in IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='投影图标素材';

CREATE TABLE projection_template (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  name VARCHAR(100) NOT NULL COMMENT '模板名称',
  description VARCHAR(500) NOT NULL DEFAULT '' COMMENT '说明',
  width_px INT UNSIGNED NOT NULL DEFAULT 1920 COMMENT '逻辑画布宽',
  height_px INT UNSIGNED NOT NULL DEFAULT 1080 COMMENT '逻辑画布高',
  is_default TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '默认模板不可删除',
  row_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '并发版本',
  default_key TINYINT UNSIGNED GENERATED ALWAYS AS (IF(is_deleted=0 AND is_default=1,1,NULL)) STORED,
  UNIQUE KEY uk_template_default (default_key),
  CHECK (width_px=1920 AND height_px=1080 AND is_default IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='投影模板';

CREATE TABLE projection_template_element (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  template_id BIGINT UNSIGNED NOT NULL COMMENT '所属模板',
  element_type TINYINT UNSIGNED NOT NULL COMMENT '0图标1文字',
  icon_id BIGINT UNSIGNED NULL COMMENT '图标资源ID，文字为NULL',
  text_content TEXT NULL COMMENT '文字或带白名单占位符的内容',
  bind_code TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0至13见编码表',
  x DECIMAL(10,3) NOT NULL COMMENT '横坐标',
  y DECIMAL(10,3) NOT NULL COMMENT '纵坐标',
  scale DECIMAL(8,3) NOT NULL DEFAULT 1 COMMENT '缩放',
  rotation DECIMAL(8,3) NOT NULL DEFAULT 0 COMMENT '旋转角度',
  font_size INT UNSIGNED NULL COMMENT '文字字号',
  color VARCHAR(32) NULL COMMENT '文字颜色',
  blink_flag TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0不闪烁1闪烁',
  z_index INT UNSIGNED NOT NULL COMMENT '层级',
  KEY ix_template_elements (template_id,is_deleted,z_index),
  FOREIGN KEY (template_id) REFERENCES projection_template(id),
  FOREIGN KEY (icon_id) REFERENCES projection_icon(id),
  CHECK (element_type IN (0,1) AND bind_code<=13 AND blink_flag IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='投影模板元素';

CREATE TABLE process_definition (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  model_id BIGINT UNSIGNED NOT NULL COMMENT '产品型号',
  title VARCHAR(255) NOT NULL COMMENT '工艺标题',
  current_revision_id BIGINT UNSIGNED NULL COMMENT '当前发布版本，外键稍后添加',
  row_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '并发版本',
  active_model BIGINT UNSIGNED GENERATED ALWAYS AS (IF(is_deleted=0,model_id,NULL)) STORED,
  UNIQUE KEY uk_process_model (active_model),
  FOREIGN KEY (model_id) REFERENCES product_model(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='每型号当前工艺入口';

CREATE TABLE process_revision (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  process_id BIGINT UNSIGNED NOT NULL COMMENT '工艺入口',
  revision_no INT UNSIGNED NOT NULL COMMENT '递增版本号',
  version_label VARCHAR(64) NOT NULL COMMENT '显示版本，如v1',
  title VARCHAR(255) NOT NULL COMMENT '版本工艺标题',
  model_code VARCHAR(100) NOT NULL COMMENT '当时型号编码',
  source_file_id BIGINT UNSIGNED NULL COMMENT '来源Excel文件',
  source_document VARCHAR(500) NOT NULL DEFAULT '' COMMENT '原始来源文档名',
  revision_state TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '0草稿1发布',
  published_at DATETIME(3) NULL COMMENT '发布时间UTC',
  UNIQUE KEY uk_process_revision (process_id,revision_no),
  FOREIGN KEY (process_id) REFERENCES process_definition(id),
  FOREIGN KEY (source_file_id) REFERENCES stored_file(id),
  CHECK (revision_state IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='不可改写的已发布工艺版本';

CREATE TABLE process_step (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  revision_id BIGINT UNSIGNED NOT NULL COMMENT '所属工艺版本',
  step_no INT UNSIGNED NOT NULL COMMENT '正整数步骤号，不补零',
  title VARCHAR(255) NOT NULL COMMENT '步骤标题',
  workstation_id BIGINT UNSIGNED NOT NULL COMMENT '永久工位ID',
  importance_code TINYINT UNSIGNED NOT NULL COMMENT '0普通1重要',
  video_id BIGINT UNSIGNED NULL COMMENT '关联视频资源，无视频为NULL',
  source_video_name VARCHAR(255) NULL COMMENT 'Excel原始视频名称，用于导入追溯',
  source_row INT UNSIGNED NULL COMMENT 'Excel来源行',
  UNIQUE KEY uk_process_step (revision_id,step_no),
  KEY ix_process_station (workstation_id,revision_id),
  FOREIGN KEY (revision_id) REFERENCES process_revision(id),
  FOREIGN KEY (workstation_id) REFERENCES workstation(id),
  FOREIGN KEY (video_id) REFERENCES video_resource(id),
  CHECK (step_no>0 AND importance_code IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='版本内步骤定义';

CREATE TABLE process_parameter (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  step_id BIGINT UNSIGNED NOT NULL COMMENT '版本步骤ID',
  parameter_index TINYINT UNSIGNED NOT NULL COMMENT '组号1至3',
  name VARCHAR(255) NOT NULL COMMENT '参数名称',
  parameter_type TINYINT UNSIGNED NOT NULL COMMENT '0输入1单选2范围',
  reference_mode TINYINT UNSIGNED NOT NULL COMMENT '0文本1闭区间2首选项',
  reference_value TEXT NOT NULL COMMENT '原参考文本；范围使用四段逗号格式，兼容旧两行闭区间',
  minimum_value DECIMAL(30,10) NULL COMMENT '范围下限，边界包含',
  maximum_value DECIMAL(30,10) NULL COMMENT '范围上限，边界包含',
  source_type_missing TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0类型齐全1原文件缺失',
  UNIQUE KEY uk_process_param (step_id,parameter_index),
  FOREIGN KEY (step_id) REFERENCES process_step(id),
  CHECK (parameter_index BETWEEN 1 AND 3 AND parameter_type IN (0,1,2) AND reference_mode IN (0,1,2) AND source_type_missing IN (0,1)),
  CHECK (reference_mode<>1 OR (minimum_value IS NOT NULL AND maximum_value IS NOT NULL AND minimum_value<=maximum_value))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='步骤参数定义';

CREATE TABLE process_parameter_option (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  parameter_id BIGINT UNSIGNED NOT NULL COMMENT '参数ID',
  option_no INT UNSIGNED NOT NULL COMMENT '顺序从1开始；第一项合格',
  option_text VARCHAR(1000) NOT NULL COMMENT '选项原文',
  UNIQUE KEY uk_process_option_no (parameter_id,option_no),
  FOREIGN KEY (parameter_id) REFERENCES process_parameter(id),
  CHECK (option_no>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='单选参数选项';

CREATE TABLE process_step_projection (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  step_id BIGINT UNSIGNED NOT NULL COMMENT '版本步骤ID',
  source_template_id BIGINT UNSIGNED NULL COMMENT '初次套用模板ID，不动态引用模板内容',
  width_px INT UNSIGNED NOT NULL DEFAULT 1920 COMMENT '画布宽',
  height_px INT UNSIGNED NOT NULL DEFAULT 1080 COMMENT '画布高',
  UNIQUE KEY uk_step_projection (step_id),
  FOREIGN KEY (step_id) REFERENCES process_step(id),
  FOREIGN KEY (source_template_id) REFERENCES projection_template(id),
  CHECK (width_px=1920 AND height_px=1080)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='步骤复制后的投影配置';

CREATE TABLE process_projection_element (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  projection_id BIGINT UNSIGNED NOT NULL COMMENT '步骤投影配置',
  element_type TINYINT UNSIGNED NOT NULL COMMENT '0图标1文字',
  icon_id BIGINT UNSIGNED NULL COMMENT '图标资源ID',
  text_content TEXT NULL COMMENT '文字/占位符',
  bind_code TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0至13绑定字段',
  x DECIMAL(10,3) NOT NULL COMMENT '横坐标',
  y DECIMAL(10,3) NOT NULL COMMENT '纵坐标',
  scale DECIMAL(8,3) NOT NULL DEFAULT 1 COMMENT '缩放',
  rotation DECIMAL(8,3) NOT NULL DEFAULT 0 COMMENT '旋转',
  font_size INT UNSIGNED NULL COMMENT '字号',
  color VARCHAR(32) NULL COMMENT '颜色',
  blink_flag TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '闪烁',
  z_index INT UNSIGNED NOT NULL COMMENT '层级',
  KEY ix_process_elements (projection_id,is_deleted,z_index),
  FOREIGN KEY (projection_id) REFERENCES process_step_projection(id),
  FOREIGN KEY (icon_id) REFERENCES projection_icon(id),
  CHECK (element_type IN (0,1) AND bind_code<=13 AND blink_flag IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='版本步骤独立投影元素';

CREATE TABLE user_station_preference (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  user_id BIGINT UNSIGNED NOT NULL COMMENT '选择用户',
  workstation_id BIGINT UNSIGNED NULL COMMENT '操作端工位；管理端为NULL',
  model_id BIGINT UNSIGNED NULL COMMENT '操作端型号；管理端可NULL',
  selected_process_id BIGINT UNSIGNED NOT NULL COMMENT '本次选中工艺',
  scope_code TINYINT UNSIGNED NOT NULL COMMENT '0操作端1管理端偏好',
  station_key BIGINT UNSIGNED GENERATED ALWAYS AS (IFNULL(workstation_id,0)) STORED,
  model_key BIGINT UNSIGNED GENERATED ALWAYS AS (IFNULL(model_id,0)) STORED,
  UNIQUE KEY uk_preference (user_id,scope_code,station_key,model_key),
  FOREIGN KEY (user_id) REFERENCES sys_user(id),
  FOREIGN KEY (workstation_id) REFERENCES workstation(id),
  FOREIGN KEY (model_id) REFERENCES product_model(id),
  FOREIGN KEY (selected_process_id) REFERENCES process_definition(id),
  CHECK (scope_code IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='本人工艺选择记忆';

CREATE TABLE process_import_job (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  file_id BIGINT UNSIGNED NOT NULL COMMENT '上传Excel文件',
  base_process_id BIGINT UNSIGNED NULL COMMENT '将覆盖的工艺入口',
  base_version INT UNSIGNED NULL COMMENT '预检时入口并发版本',
  model_code VARCHAR(100) NOT NULL DEFAULT '' COMMENT '识别型号',
  import_state TINYINT UNSIGNED NOT NULL COMMENT '0通过1失败2提交3过期',
  auto_fix_type TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0不修正1按输入修正缺少类型',
  fallback_station_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '默认改绑工位1步骤数量',
  video_matched_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '视频匹配成功数量',
  preview_payload JSON NULL COMMENT '预检定义；所有类型/状态是数字',
  expires_at DATETIME(3) NOT NULL COMMENT '预检24小时过期',
  committed_revision_id BIGINT UNSIGNED NULL COMMENT '成功发布的工艺版本',
  FOREIGN KEY (file_id) REFERENCES stored_file(id),
  FOREIGN KEY (base_process_id) REFERENCES process_definition(id),
  FOREIGN KEY (committed_revision_id) REFERENCES process_revision(id),
  CHECK (import_state IN (0,1,2,3) AND auto_fix_type IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='工艺文件预检任务';

CREATE TABLE process_import_issue (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  job_id BIGINT UNSIGNED NOT NULL COMMENT '所属预检',
  source_row INT UNSIGNED NULL COMMENT 'Excel行号，全局错误为NULL',
  field_name VARCHAR(100) NULL COMMENT '问题字段名',
  severity_code TINYINT UNSIGNED NOT NULL COMMENT '0阻塞错误1提示',
  issue_code INT UNSIGNED NOT NULL COMMENT '第5节数值错误码',
  message VARCHAR(1000) NOT NULL COMMENT '可显示的中文错误说明',
  KEY ix_import_issue (job_id,severity_code,source_row),
  FOREIGN KEY (job_id) REFERENCES process_import_job(id),
  CHECK (severity_code IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='预检逐行问题';

CREATE TABLE task (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  task_no VARCHAR(64) NOT NULL COMMENT '独立任务展示号，不能截取毫秒时间生成',
  model_id BIGINT UNSIGNED NOT NULL COMMENT '原型号永久ID',
  model_code VARCHAR(100) NOT NULL COMMENT '任务冻结型号编码',
  process_id BIGINT UNSIGNED NOT NULL COMMENT '工艺入口ID',
  revision_id BIGINT UNSIGNED NOT NULL COMMENT '开始时发布版本ID',
  product_code VARCHAR(128) NOT NULL COMMENT '当前扫码载体编码',
  steel_no VARCHAR(128) NOT NULL COMMENT '当前钢印编号，trim且区分大小写',
  operator_id BIGINT UNSIGNED NOT NULL COMMENT '发起人永久ID',
  operator_name VARCHAR(100) NOT NULL COMMENT '发起人冻结姓名',
  status_code TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0进行中1已完成',
  started_at DATETIME(3) NOT NULL COMMENT '任务首次开始时间UTC',
  ended_at DATETIME(3) NULL COMMENT '最终提交UTC时间',
  duration_ms BIGINT UNSIGNED NULL COMMENT '服务端ended-started，毫秒',
  row_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '任务整体并发版本',
  active_steel_no VARCHAR(128) GENERATED ALWAYS AS (IF(is_deleted=0 AND status_code=0,steel_no,NULL)) STORED,
  UNIQUE KEY uk_task_no (task_no),
  UNIQUE KEY uk_task_active_steel (active_steel_no),
  KEY ix_task_completed (is_deleted,status_code,ended_at,model_id),
  KEY ix_task_started (is_deleted,status_code,started_at),
  KEY ix_task_steel_history (steel_no,ended_at),
  KEY ix_task_product (product_code),
  FOREIGN KEY (model_id) REFERENCES product_model(id),
  FOREIGN KEY (process_id) REFERENCES process_definition(id),
  FOREIGN KEY (revision_id) REFERENCES process_revision(id),
  FOREIGN KEY (operator_id) REFERENCES sys_user(id),
  CHECK (status_code IN (0,1) AND CHAR_LENGTH(TRIM(steel_no))>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='真实生产任务主记录';

CREATE TABLE task_station_snapshot (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  task_id BIGINT UNSIGNED NOT NULL COMMENT '任务ID',
  workstation_id BIGINT UNSIGNED NOT NULL COMMENT '永久工位ID',
  station_no INT UNSIGNED NOT NULL COMMENT '冻结业务编号',
  station_name VARCHAR(100) NOT NULL COMMENT '冻结工位名称',
  sequence_no INT UNSIGNED NOT NULL COMMENT '任务内工位顺序1起',
  UNIQUE KEY uk_task_station (task_id,workstation_id),
  UNIQUE KEY uk_task_station_order (task_id,sequence_no),
  FOREIGN KEY (task_id) REFERENCES task(id),
  FOREIGN KEY (workstation_id) REFERENCES workstation(id),
  CHECK (station_no>0 AND sequence_no>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='任务工位冻结顺序';

CREATE TABLE task_step_snapshot (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  task_id BIGINT UNSIGNED NOT NULL COMMENT '任务ID',
  source_step_id BIGINT UNSIGNED NOT NULL COMMENT '来源版本步骤ID',
  task_station_id BIGINT UNSIGNED NOT NULL COMMENT '任务冻结工位ID',
  step_no INT UNSIGNED NOT NULL COMMENT '冻结步骤号',
  title VARCHAR(255) NOT NULL COMMENT '冻结步骤标题',
  importance_code TINYINT UNSIGNED NOT NULL COMMENT '0普通1重要',
  video_id BIGINT UNSIGNED NULL COMMENT '冻结视频绑定ID，媒体可显式替换',
  video_name VARCHAR(255) NULL COMMENT '冻结视频名便于历史展示',
  UNIQUE KEY uk_task_step (task_id,step_no),
  KEY ix_task_station_steps (task_station_id,step_no),
  FOREIGN KEY (task_id) REFERENCES task(id),
  FOREIGN KEY (source_step_id) REFERENCES process_step(id),
  FOREIGN KEY (task_station_id) REFERENCES task_station_snapshot(id),
  FOREIGN KEY (video_id) REFERENCES video_resource(id),
  CHECK (importance_code IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='任务步骤冻结定义';

CREATE TABLE task_parameter_snapshot (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  task_step_id BIGINT UNSIGNED NOT NULL COMMENT '任务步骤快照ID',
  source_parameter_id BIGINT UNSIGNED NOT NULL COMMENT '来源版本参数ID',
  parameter_index TINYINT UNSIGNED NOT NULL COMMENT '参数组号1至3',
  name VARCHAR(255) NOT NULL COMMENT '冻结参数名',
  parameter_type TINYINT UNSIGNED NOT NULL COMMENT '0输入1单选2范围',
  reference_mode TINYINT UNSIGNED NOT NULL COMMENT '0文本1闭区间2首选项',
  reference_value TEXT NOT NULL COMMENT '冻结原参考文本',
  minimum_value DECIMAL(30,10) NULL COMMENT '冻结区间下限',
  maximum_value DECIMAL(30,10) NULL COMMENT '冻结区间上限',
  UNIQUE KEY uk_task_param (task_step_id,parameter_index),
  FOREIGN KEY (task_step_id) REFERENCES task_step_snapshot(id),
  FOREIGN KEY (source_parameter_id) REFERENCES process_parameter(id),
  CHECK (parameter_index BETWEEN 1 AND 3 AND parameter_type IN (0,1,2) AND reference_mode IN (0,1,2)),
  CHECK (reference_mode<>1 OR (minimum_value IS NOT NULL AND maximum_value IS NOT NULL AND minimum_value<=maximum_value))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='任务参数冻结定义';

CREATE TABLE task_parameter_option_snapshot (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  parameter_snapshot_id BIGINT UNSIGNED NOT NULL COMMENT '冻结参数ID',
  option_no INT UNSIGNED NOT NULL COMMENT '冻结顺序，第一项合格',
  option_text VARCHAR(1000) NOT NULL COMMENT '冻结选项文本',
  UNIQUE KEY uk_task_option (parameter_snapshot_id,option_no),
  FOREIGN KEY (parameter_snapshot_id) REFERENCES task_parameter_snapshot(id),
  CHECK (option_no>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='任务单选候选项冻结';

CREATE TABLE task_projection_element_snapshot (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  task_step_id BIGINT UNSIGNED NOT NULL COMMENT '任务步骤快照ID',
  source_template_id BIGINT UNSIGNED NULL COMMENT '来源模板ID',
  element_type TINYINT UNSIGNED NOT NULL COMMENT '0图标1文字',
  icon_id BIGINT UNSIGNED NULL COMMENT '来源图标ID',
  icon_name VARCHAR(100) NULL COMMENT '冻结图标名',
  icon_file_id BIGINT UNSIGNED NULL COMMENT '冻结图标文件，后续改图标不变',
  text_content TEXT NULL COMMENT '冻结文字模板',
  bind_code TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '数字绑定字段',
  x DECIMAL(10,3) NOT NULL COMMENT '横坐标',
  y DECIMAL(10,3) NOT NULL COMMENT '纵坐标',
  scale DECIMAL(8,3) NOT NULL DEFAULT 1 COMMENT '缩放',
  rotation DECIMAL(8,3) NOT NULL DEFAULT 0 COMMENT '旋转',
  font_size INT UNSIGNED NULL COMMENT '字号',
  color VARCHAR(32) NULL COMMENT '颜色',
  blink_flag TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '闪烁',
  z_index INT UNSIGNED NOT NULL COMMENT '层级',
  KEY ix_task_projection (task_step_id,z_index),
  FOREIGN KEY (task_step_id) REFERENCES task_step_snapshot(id),
  FOREIGN KEY (source_template_id) REFERENCES projection_template(id),
  FOREIGN KEY (icon_id) REFERENCES projection_icon(id),
  FOREIGN KEY (icon_file_id) REFERENCES stored_file(id),
  CHECK (element_type IN (0,1) AND bind_code<=13 AND blink_flag IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='任务步骤投影冻结元素';

CREATE TABLE task_step_result (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  task_id BIGINT UNSIGNED NOT NULL COMMENT '任务ID',
  task_step_id BIGINT UNSIGNED NOT NULL COMMENT '冻结步骤ID',
  action_code TINYINT UNSIGNED NOT NULL COMMENT '0完成1普通跳过2重要跳过',
  reason_code TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0无1跳转2汇总3人工修改4替换载体',
  reason_text VARCHAR(500) NOT NULL DEFAULT '' COMMENT '原因显示文本',
  operator_id BIGINT UNSIGNED NOT NULL COMMENT '最近实际完成或跳过操作员',
  operator_name VARCHAR(100) NOT NULL COMMENT '冻结操作者姓名',
  operated_at DATETIME(3) NOT NULL COMMENT '最近实际完成/跳过时间，汇总改值不重写',
  row_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '步骤结果并发版本',
  UNIQUE KEY uk_task_result (task_step_id),
  KEY ix_result_time (task_id,action_code,operated_at),
  FOREIGN KEY (task_id) REFERENCES task(id),
  FOREIGN KEY (task_step_id) REFERENCES task_step_snapshot(id),
  FOREIGN KEY (operator_id) REFERENCES sys_user(id),
  CHECK (action_code IN (0,1,2) AND reason_code<=4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='每步骤最新处理结果';

CREATE TABLE task_parameter_value (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  task_id BIGINT UNSIGNED NOT NULL COMMENT '任务ID',
  parameter_snapshot_id BIGINT UNSIGNED NOT NULL COMMENT '冻结参数ID',
  actual_value TEXT NULL COMMENT '实际原文，包括异常值',
  is_filled TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'trim后非空为1，0值仍为已填写',
  numeric_value DECIMAL(30,10) NULL COMMENT '严格十进制解析值，非数值为NULL',
  deviation_flag TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '根据冻结规则计算，不能由客户端指定',
  source_code TINYINT UNSIGNED NOT NULL COMMENT '0步骤1本站汇总2最终汇总',
  UNIQUE KEY uk_task_value (parameter_snapshot_id),
  KEY ix_value_task (task_id,deviation_flag),
  FOREIGN KEY (task_id) REFERENCES task(id),
  FOREIGN KEY (parameter_snapshot_id) REFERENCES task_parameter_snapshot(id),
  CHECK (is_filled IN (0,1) AND deviation_flag IN (0,1) AND source_code IN (0,1,2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='参数当前实际录入值';

CREATE TABLE task_operation_event (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  task_id BIGINT UNSIGNED NOT NULL COMMENT '任务ID',
  task_station_id BIGINT UNSIGNED NOT NULL COMMENT '冻结工位ID',
  task_step_id BIGINT UNSIGNED NULL COMMENT '步骤事件关联步骤',
  event_code TINYINT UNSIGNED NOT NULL COMMENT '0至9见编码表',
  reason_code TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0至4见编码表',
  reason_text VARCHAR(500) NOT NULL DEFAULT '' COMMENT '原因',
  operator_id BIGINT UNSIGNED NOT NULL COMMENT '实际操作人',
  operator_name VARCHAR(100) NOT NULL COMMENT '冻结实际操作人姓名',
  station_no INT UNSIGNED NOT NULL COMMENT '当时工位编号',
  station_name VARCHAR(100) NOT NULL COMMENT '当时工位名称',
  before_value TEXT NULL COMMENT '修改前钢印号/产品编码等',
  after_value TEXT NULL COMMENT '修改后值',
  occurred_at DATETIME(3) NOT NULL COMMENT '服务端事件时间',
  request_key VARCHAR(64) NOT NULL COMMENT '幂等请求标识',
  KEY ix_event_task (task_id,occurred_at,id),
  KEY ix_event_type (task_id,event_code),
  FOREIGN KEY (task_id) REFERENCES task(id),
  FOREIGN KEY (task_station_id) REFERENCES task_station_snapshot(id),
  FOREIGN KEY (task_step_id) REFERENCES task_step_snapshot(id),
  FOREIGN KEY (operator_id) REFERENCES sys_user(id),
  CHECK (event_code<=9 AND reason_code<=4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='追加式操作审计事件';

CREATE TABLE task_parameter_value_history (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  event_id BIGINT UNSIGNED NOT NULL COMMENT '关联不可变审计事件',
  parameter_snapshot_id BIGINT UNSIGNED NOT NULL COMMENT '冻结参数ID',
  before_value TEXT NULL COMMENT '修改前原文',
  after_value TEXT NULL COMMENT '修改后原文',
  before_deviation TINYINT UNSIGNED NOT NULL COMMENT '修改前是否偏离',
  after_deviation TINYINT UNSIGNED NOT NULL COMMENT '修改后是否偏离',
  UNIQUE KEY uk_value_event (event_id,parameter_snapshot_id),
  FOREIGN KEY (event_id) REFERENCES task_operation_event(id),
  FOREIGN KEY (parameter_snapshot_id) REFERENCES task_parameter_snapshot(id),
  CHECK (before_deviation IN (0,1) AND after_deviation IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='每次录入或回改前后参数值';

CREATE TABLE task_summary (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  task_id BIGINT UNSIGNED NOT NULL COMMENT '任务ID',
  task_station_id BIGINT UNSIGNED NULL COMMENT '本站范围关联冻结工位，最终汇总为NULL',
  scope_code TINYINT UNSIGNED NOT NULL COMMENT '0本站1最终',
  summary_state TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0草稿1确认',
  confirmed_by BIGINT UNSIGNED NULL COMMENT '确认人',
  confirmed_name VARCHAR(100) NULL COMMENT '冻结确认人姓名',
  confirmed_at DATETIME(3) NULL COMMENT '确认时间',
  row_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '并发版本',
  station_key BIGINT UNSIGNED GENERATED ALWAYS AS (IFNULL(task_station_id,0)) STORED,
  UNIQUE KEY uk_task_summary (task_id,scope_code,station_key),
  FOREIGN KEY (task_id) REFERENCES task(id),
  FOREIGN KEY (task_station_id) REFERENCES task_station_snapshot(id),
  FOREIGN KEY (confirmed_by) REFERENCES sys_user(id),
  CHECK ((scope_code=0 AND task_station_id IS NOT NULL) OR (scope_code=1 AND task_station_id IS NULL)),
  CHECK (summary_state IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='本站及最终汇总状态和确认';

CREATE TABLE task_summary_item (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  summary_id BIGINT UNSIGNED NOT NULL COMMENT '本站或最终汇总ID',
  parameter_snapshot_id BIGINT UNSIGNED NOT NULL COMMENT '冻结参数定义ID',
  actual_value TEXT NULL COMMENT '该次汇总保存或确认的值',
  deviation_flag TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '按冻结规则计算的偏离',
  UNIQUE KEY uk_summary_param (summary_id,parameter_snapshot_id),
  FOREIGN KEY (summary_id) REFERENCES task_summary(id),
  FOREIGN KEY (parameter_snapshot_id) REFERENCES task_parameter_snapshot(id),
  CHECK (deviation_flag IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='汇总保存/确认的参数表单';

CREATE TABLE task_binding (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  task_id BIGINT UNSIGNED NOT NULL COMMENT '唯一任务',
  steel_no VARCHAR(128) NOT NULL COMMENT '最新钢印号',
  product_code VARCHAR(128) NOT NULL COMMENT '最新扫码产品编码',
  model_id BIGINT UNSIGNED NOT NULL COMMENT '型号',
  updated_station_id BIGINT UNSIGNED NOT NULL COMMENT '最近建立/替换映射的工位',
  binding_state TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0生效1归档解除',
  active_steel VARCHAR(128) GENERATED ALWAYS AS (IF(is_deleted=0 AND binding_state=0,steel_no,NULL)) STORED,
  active_product VARCHAR(128) GENERATED ALWAYS AS (IF(is_deleted=0 AND binding_state=0,product_code,NULL)) STORED,
  UNIQUE KEY uk_binding_task (task_id),
  UNIQUE KEY uk_binding_steel (active_steel),
  UNIQUE KEY uk_binding_product (active_product),
  FOREIGN KEY (task_id) REFERENCES task(id),
  FOREIGN KEY (model_id) REFERENCES product_model(id),
  FOREIGN KEY (updated_station_id) REFERENCES workstation(id),
  CHECK (binding_state IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='钢印号与扫码载体在制映射';

CREATE TABLE task_view_cursor (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  task_id BIGINT UNSIGNED NOT NULL COMMENT '所查看任务',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '实际登录用户',
  workstation_id BIGINT UNSIGNED NOT NULL COMMENT '当前操作工位',
  viewed_step_id BIGINT UNSIGNED NOT NULL COMMENT '当前查看步骤快照，允许跨工位只读',
  row_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '游标并发版本',
  UNIQUE KEY uk_task_cursor (task_id,user_id,workstation_id),
  FOREIGN KEY (task_id) REFERENCES task(id),
  FOREIGN KEY (user_id) REFERENCES sys_user(id),
  FOREIGN KEY (workstation_id) REFERENCES workstation(id),
  FOREIGN KEY (viewed_step_id) REFERENCES task_step_snapshot(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='用户工位任务查看与投影游标';

CREATE TABLE api_idempotency (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '永久主键',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID，0系统',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
  updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人用户ID，0系统',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间UTC',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未删除1已删除',
  PRIMARY KEY (id),
  CHECK (is_deleted IN (0,1)),
  user_id BIGINT UNSIGNED NOT NULL COMMENT '请求用户',
  request_key VARCHAR(64) NOT NULL COMMENT 'Idempotency-Key',
  route_key VARCHAR(255) NOT NULL COMMENT '方法和实际路径',
  request_hash CHAR(64) NOT NULL COMMENT '规范请求体摘要',
  request_state TINYINT UNSIGNED NOT NULL COMMENT '0处理中1成功',
  response_status SMALLINT UNSIGNED NULL COMMENT '成功HTTP状态码',
  response_body JSON NULL COMMENT '成功响应，不保存密码/令牌响应',
  expires_at DATETIME(3) NOT NULL COMMENT '重放保证期，默认24小时',
  UNIQUE KEY uk_api_request (user_id,request_key),
  FOREIGN KEY (user_id) REFERENCES sys_user(id),
  CHECK (request_state IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='API写请求幂等记录';

ALTER TABLE process_definition
  ADD CONSTRAINT fk_process_current_revision
  FOREIGN KEY (current_revision_id) REFERENCES process_revision(id);
ALTER TABLE video_resource
  ADD CONSTRAINT fk_video_current_version
  FOREIGN KEY (current_version_id) REFERENCES video_file_version(id);

```

### 3.3 初始化与表字段补充规则

初始化仅插入系统主体、四个已知型号、工位1–7、默认投影模板及初始视频分类，不创建虚假的生产任务。以下SQL可在新库执行；不会创建演示人员密码：

```sql
-- ID=0保留给系统主体；必须启用NO_AUTO_VALUE_ON_ZERO。
SET SESSION sql_mode=CONCAT_WS(',',@@sql_mode,'NO_AUTO_VALUE_ON_ZERO');
INSERT INTO sys_user
 (id,no,name,user_kind,role_code,status_code,password_hash,created_by,updated_by)
 VALUES (0,'SYS','系统',1,0,1,'!LOGIN_DISABLED',0,0);

INSERT INTO product_model(model_code,name,sort_order,created_by,updated_by)
 VALUES ('DISPEN9148-40','DISPEN9148-40',1,0,0),
        ('DISPEN9241-40','DISPEN9241-40',2,0,0),
        ('DISPEN9242-40','DISPEN9242-40',3,0,0),
        ('DISPEN9243-40','DISPEN9243-40',4,0,0);

INSERT INTO workstation(station_no,name,capture_mode,is_default,sort_order,created_by,updated_by)
 VALUES (1,'工位1',0,1,1,0,0),(2,'工位2',1,0,2,0,0),
        (3,'工位3',1,0,3,0,0),(4,'工位4',1,0,4,0,0),
        (5,'工位5',1,0,5,0,0),(6,'工位6',1,0,6,0,0),
        (7,'工位7',1,0,7,0,0);

INSERT INTO projection_template(name,description,is_default,created_by,updated_by)
 VALUES ('默认工序引导','系统默认模板，导入步骤复制本模板元素',1,0,0);

INSERT INTO video_category(name,normalized_name,parent_id,level_no,sort_order,created_by,updated_by)
 VALUES ('油压减振器','油压减振器',NULL,0,1,0,0);
SET @damper_category_id=LAST_INSERT_ID();
INSERT INTO video_category(name,normalized_name,parent_id,level_no,sort_order,created_by,updated_by)
 VALUES ('DISPEN9243-40','DISPEN9243-40',@damper_category_id,1,1,0,0),
        ('DISPEN9242-40','DISPEN9242-40',@damper_category_id,1,2,0,0),
        ('DISPEN9241-40','DISPEN9241-40',@damper_category_id,1,3,0,0),
        ('DISPEN9148-40','DISPEN9148-40',@damper_category_id,1,4,0,0),
        ('通用','通用',@damper_category_id,1,5,0,0),
        ('其他','其他',NULL,0,2,0,0);
```

部署程序另行创建首位超级管理员并写口令哈希；不使用原型的明文密码。内置PNG按实际文件上传后创建stored_file及projection_icon，文件摘要按实物计算；默认模板元素按现有initialTemplates中的位置和文字配置插入。默认模板必须只有一个且不可删。

| 表/字段 | 必须由业务层执行的约束 |
|---|---|
| sys_user.no | 工号trim后非空；永久不复用已删除人员工号，避免人员审计混淆；系统主体不可登录 |
| workstation.station_no | 并发新增时按最小空缺分配，遇唯一键冲突重算重试；station_no=1必须是默认工位 |
| video_category | 父级有效且level_no=0；不形成环；最多两层；同级名称去首尾空格后唯一 |
| video_resource.normalized_name | NFKC→trim→去路径→去末尾mp4/webm/ogg/ogv/mov/avi/mkv/m4v扩展名→连续空白合并成一个空格→小写；不能删除词中有意义的空格 |
| video_resource.current_version_id | 必须引用该video_id的文件版本，不得引用另一个视频的版本 |
| projection_*_element | 图标必须有icon_id；文字必须有text_content；x 0–1919，y 0–1079，scale 0.2–4，rotation -180–180，字号16–160，层级1–99，颜色采用#RRGGBB；模板/工艺元素状态均为数字 |
| process_definition.current_revision_id | 必须是该process_id的已发布版本；更新在同一事务内完成 |
| process_parameter | type=0新定义必须mode=0；type=1必须mode=2且至少两个不同选项；type=2必须mode=1且四段范围格式合法，旧两行闭区间兼容；最多3组；当前 JSON 接口按十进制精确比较，不受二进制浮点精度影响 |
| process_parameter_option | option_no连续从1起；trim后非空且不重复；单选定义reference_value由选项按换行重建 |
| 所有任务从属表 | taskId、taskStationId、taskStepId、parameterSnapshotId之间归属一致，不能仅因单个外键存在就接受跨任务拼接 |
| task_parameter_value | actual_value保留输入原文；严格十进制解析采用符号、小数、至少一位数字，拒绝NaN/Infinity/十六进制；超过DECIMAL存储精度时numeric_value=NULL、范围值记偏离，但保留actual_value |
| task_station_snapshot | 从当前版本实际使用的工位生成，按station_no升序确定sequence_no；没有该任务步骤的工位不要求空交接 |
| task_summary | scope=0确认后表单永久只读；最终scope=1确认后全任务归档；confirmed字段只在确认时写入 |
| task_binding | 单条任务的当前钢印号与当前产品编码必须对应task主表；归档后binding_state=1，不物理删除 |
| api_idempotency | 24小时内同一用户同key、相同方法路径与请求摘要返回原结果；不同内容返回1006；业务记录不依赖过期幂等缓存保证唯一性 |

已发布工艺行、任务定义快照、已确认表单项及审计事件正常情况下不可修改/软删除。对主数据只做软删除；被快照引用的文件对象继续保留，不能随资源删除清除。没有业务需要时不为历史任务提供删除按钮或删除接口。

### 3.4 核心关系

```text
型号 → 工艺入口 → 发布版本 → 步骤 → 参数 → 候选项
                         └→ 步骤投影配置 → 独立元素
视频分类 → 视频资源 → 视频文件版本 → 文件对象
投影模板 → 模板元素 → 图标素材 → 文件对象
工艺发布版本 → 开始任务 → 工位/步骤/参数/选项/投影快照
任务步骤快照 → 最新步骤结果 + 当前参数值
操作事件 → 每次参数修改前后值
任务 → 本站/最终汇总 → 当次参数表单
任务 → 当前钢印号/产品编码绑定
用户 + 工位 + 任务 → 查看游标 → 实时投影
```

## 4. 接口目录与请求/返回契约

所有路径均省略前缀 `/api/v1`。相同接口供操作端与管理端复用，权限在服务端校验。以下是生产设计；前端现有字段由store.api适配。

### 4.1 公共实体与分页返回

每个真实实体对象都展开以下公共字段，后续表中“实体”表示实际返回包含它们，不是接口省略。快照、候选项、元素、结果、表单项和事件同样展开：

```json
{
  "id": "101",
  "createdBy": "12",
  "createdAt": "2026-09-30T02:00:00.000Z",
  "updatedBy": "12",
  "updatedAt": "2026-09-30T02:00:00.000Z",
  "isDeleted": 0
}
```

如涉及显示：createdByName、updatedByName来自sys_user.name，历史operatorName、stationName来自冻结记录。实体rowVersion存在时同时返回数字rowVersion。技术表中tokenHash/passwordHash、对象访问密钥、幂等响应缓存不向业务页面返回。

```json
{
  "code": 0,
  "message": "成功",
  "requestId": "req-20260930-001",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [{
      "id": "301",
      "name": "拧紧机.mp4",
      "categoryId": "205",
      "categoryPath": {"primaryId": "201", "primaryName": "油压减振器", "secondaryId": "205", "secondaryName": "通用"},
      "durationSec": 84.5,
      "batchNo": "B-20260930-001",
      "allowPlayback": 1,
      "rowVersion": 2,
      "createdBy": "12",
      "createdAt": "2026-09-29T02:00:00.000Z",
      "updatedBy": "12",
      "updatedAt": "2026-09-30T02:00:00.000Z",
      "isDeleted": 0
    }]
  }
}
```

非分页实体读详情返回单对象。集合过滤项没命中返回items=[]；真实业务为空时不伪造数据。树节点是分类实体加children和videoCount（聚合字段）；有媒体时返回URL与expiresAt，URL不是实体永久字段。

### 4.2 认证、人员、工位、型号和偏好

| 方法 路径 | 权限 | 请求/筛选字段 | data返回 |
|---|---|---|---|
| POST /auth/login | P | no、password、portCode、workstationId（操作端必填） | accessToken、expiresAt、user实体、workstation实体或null、permissions |
| GET /auth/me | U | 无 | user、当前portCode、workstation、permissions |
| POST /auth/port | U | portCode、workstationId（切操作端时必填） | 更新会话；原用户不变，允许的端口权限由roleCode派生 |
| POST /auth/logout | U | 无 | null；会话立即撤销 |
| POST /auth/change-password | U | currentPassword、newPassword、confirmPassword | null；所有旧会话撤销，重新登录 |
| GET /users | S | page/pageSize、keyword（工号/姓名）、roleCode、statusCode | 人员分页实体；排除系统主体 |
| POST /users | S | no、name、roleCode、department、phone、statusCode、password | 新人员实体，不返回密码 |
| PATCH /users/{id} | S | name/roleCode/department/phone/statusCode、expectedVersion | 更新实体；不可改工号 |
| DELETE /users/{id} | S | expectedVersion | null；禁止删除自己、系统主体或最后一个在岗超级管理员 |
| POST /users/{id}/reset-password | S | newPassword、expectedVersion | null；撤销该用户旧会话，管理员明确设置密码 |
| GET /workstations/options | P | 无 | 仅有效工位的id/stationNo/name/captureMode及公共字段 |
| GET /workstations | U | page/pageSize；options接口供不限页下拉 | 工位分页实体 |
| POST /workstations | A | name可选、captureMode默认1 | 新实体；后端分配最小空缺stationNo |
| PATCH /workstations/{id} | A | name/captureMode、expectedVersion | 更新工位实体 |
| DELETE /workstations/{id} | A | expectedVersion | null；默认保护和在制引用校验，当前工艺改绑1号工位 |
| GET /workstations/{id}/references | A | 无 | 当前步骤/在制任务/历史任务数量、blockCode、message |
| GET /models | U | page/pageSize、statusCode、keyword | 型号分页实体；操作下拉只读启用型号 |
| POST/PATCH/DELETE /models[/{id}] | A | 新增modelCode/name/sortOrder；更新name/statusCode/sortOrder/expectedVersion；删除expectedVersion | 型号实体或null；存在当前工艺/在制引用不可删除；属解除硬编码的后端能力，当前没有独立型号页 |
| GET /operator/preferences | U | scopeCode、workstationId、modelId | 对应偏好实体或null |
| PUT /operator/preferences | U | scopeCode、workstationId、modelId、selectedProcessId | 偏好实体；管理员选中工艺不改变其它用户 |
| GET /metadata/codes | U | 无 | 第1.3节各字段的数字value及中文label数组 |

工位选择必须与会话一致；新增工位name为空时生成“工位N”。工位option公开接口不暴露人员或生产数据。用户停用/删除/角色变更时会话失效，API必须拒绝继续写入。

```json
{
  "no": "OP-018",
  "password": "登录时输入的密码",
  "portCode": 0,
  "workstationId": "1"
}
```

permissions对象采用名称作字段键、0/1作值，例如canManageUsers=1、canManageProcesses=1、canOperate=1，不使用布尔或字符串权限状态。口令仅通过HTTPS传入，采用Argon2id编码哈希（含随机盐与算法参数）保存；改密至少6位、不等于原密码、两次一致，由后端复核。生产没有演示快捷登录与一键清空数据库接口。

### 4.3 文件、视频分类和视频上传替换

| 方法 路径 | 权限 | 请求 | data返回 |
|---|---|---|---|
| POST /files | A | multipart：file、fileKind；一次一个文件 | stored_file安全实体（originalName/mimeType/byteSize/fileKind/processingState/durationMs/尺寸/公共字段），不公开对象密钥 |
| GET /files/{id} | U | 无 | 文件安全实体及processingState；仅可读业务可见文件 |
| GET /files/{id}/url | U | 无 | url、expiresAt；验证资源访问权 |
| GET /video-categories/tree | A/O | 无 | 分类树实体；children、直属directVideoCount、含二级totalVideoCount |
| POST /video-categories | A | name、parentId（一级null） | 分类实体；levelNo及sortOrder服务端生成 |
| PATCH /video-categories/{id} | A | name、expectedVersion | 分类实体；本期不移动父级或排序 |
| DELETE /video-categories/{id} | A | expectedVersion | null；有子分类或有效视频禁止删 |
| GET /video-categories/{id}/references | A | 无 | childCount、videoCount、blockCode、message |
| GET /videos | A/O | page/pageSize、keyword、categoryId；一级默认含二级 | 视频分页实体 |
| POST /videos | A | categoryId、files:[{fileId,name}]、replaceMode默认0 | 批次号、各视频实体、replacedCount；整批原子提交 |
| POST /videos/{id}/versions | A | fileId、name、categoryId、expectedVersion | 保留原id的视频实体，新增versionNo |
| GET /videos/{id}/versions | A | page/pageSize | 视频文件版本分页实体及文件元数据 |
| PATCH /videos/{id} | A | allowPlayback/categoryId、expectedVersion | 视频实体；不通过此接口任意改名 |
| GET /videos/{id}/playback | A/O | 无 | 视频实体、versionId、url、expiresAt、playable(0/1)、unavailableCode |
| GET /videos/{id}/references | A | 无 | currentStepCount/runningTaskCount/historyTaskCount、blockCode |
| DELETE /videos/{id} | A | expectedVersion | null；当前步骤或在制绑定引用时先解绑再删，历史引用不清除文件 |

上传顺序：上传文件→解析结果可用→提交视频资源。新视频严格全库标准名唯一，包含不同分类；先检查同一批重复，再检查现有有效资源。replaceMode=0发现重名返回1013及duplicateVideo实体列表，整批不修改。用户取消则停止；用户选择替换，原请求replaceMode=1重试，并提供duplicates:[{videoId,expectedVersion}]。后台在事务内重新核对重名与版本；保留资源ID、追加文件版本、更新资源名称分类及当前版本，不修改工艺videoId。非重复项正常新增。同批多个同标准名文件返回1005，不选择“后一个自动覆盖”。

未引用且失败/取消的上传文件只能由后台按保留期处理；被任意发布版本、快照或视频版本引用的文件不自动清理。删除后的同名新视频获得新永久ID，不自动接管历史步骤绑定。

视频播放不可用码：0可播放，1资源禁止播放，2文件待解析，3文件失败，4资源已删除；播放器本身加载失败由前端展示重试，不写成生产任务状态。

```json
{
  "categoryId": "205",
  "replaceMode": 1,
  "files": [{"fileId": "901", "name": "拧紧机.mp4"}],
  "duplicates": [{"videoId": "301", "expectedVersion": 2}]
}
```

### 4.4 工艺导入、编辑与资源引用

| 方法 路径 | 权限 | 请求/筛选 | data返回 |
|---|---|---|---|
| GET /processes | U | page/pageSize、modelId、workstationId、keyword | 当前发布工艺实体，model/stepCount/importantStepCount/stationStepCount/sourceWorkbook/sourceDocument/revision |
| GET /processes/metrics | A | selectedProcessId（本用户偏好） | 有效modelCount/processCount、当前importantStepCount、selectedProcessId |
| GET /processes/{id} | U | revisionId可选；默认当前 | 工艺入口实体、版本实体、steps实体（参数/候选项/工位/视频/投影），所有子实体带公共字段 |
| POST /process-imports/precheck | A | fileId、autoFixType(0/1) | 导入job实体、issues实体、preview、fallbackStationCount、videoMatchedCount |
| GET /process-imports/{id} | A | 无 | 本用户job/preview/issues |
| POST /process-imports/{id}/confirm | A | expectedVersion（已有工艺版本，否则null） | 发布后的工艺详情及revision；重复确认返回已发布结果 |
| PATCH /processes/{id}/steps/{stepId} | A | title、workstationId、importanceCode、videoId、parameters、expectedVersion | 新发布版本详情、旧到新步骤/参数ID映射 |
| PUT /processes/{id}/steps/{stepId}/projection | A | sourceTemplateId（套用）或完整elements、expectedVersion；两者二选一 | 新发布版本详情和步骤投影实体 |
| GET /processes/{id}/steps/{stepId}/projection-preview | A | 无 | 预览步骤/参数、elements、resolvedText、dataSource=1 |
| GET /processes/{id}/references | A | 无 | currentConfigCount/runningTaskCount/historyTaskCount、blockCode |
| DELETE /processes/{id} | A | expectedVersion | null；可逻辑下架，已开始任务继续快照，用户选中已删除工艺时回退有效工艺 |

工艺导入服务端读取首工作表、识别“减振器型号/标题/步骤”表头；一个文件一个型号；步骤号正整数且不重复；重要等级0/1；缺工位列、工位空/未配置回退默认1号并返回来源行提示。来源文档读首行说明，无说明回落文件名。型号未存在时，同一确认事务创建启用型号，并纳入型号候选。

参数类型中文“手动输入/单选/范围值”和数值0/1/2只作为Excel输入兼容；内部和JSON使用数字。新范围参考必须是四段逗号格式：下限排除、下限包含、上限包含、上限排除，空边界为 `()`；只允许示例中的边界组合，至少有一个边界且不能形成空区间。旧两行有效十进制继续按闭区间兼容，单选至少两项不同文本，第一项合格。autoFixType=1只修正“缺少参数类型且名称/参考齐全”为type=0，不修复非法范围或缺字段。

视频匹配先精确资源名、再标准名唯一匹配；空或NONE映射null。未找到/多个候选是阻塞错误。通过预检也不写正式工艺；确认时重新验证资源、工位、参数、当前工艺版本和默认模板是否变化，不能只相信旧预览。job24小时失效，原文件内容不修改。

同型号确认覆盖“当前版本指针”，不覆盖旧发布行；步骤/参数/投影更新统一克隆整个版本后应用修改再发布。新步骤、参数和投影行有新ID，客户端使用响应新ID，旧ID不能继续写新版本。原型只有一个全局“当前工艺”，后端改为每用户工艺偏好，以免管理页面查看某工艺改变其它工位任务。

```json
{
  "title": "拧紧检查",
  "workstationId": "4",
  "importanceCode": 1,
  "videoId": "301",
  "expectedVersion": 3,
  "parameters": [
    {"parameterIndex":1,"name":"扭矩","parameterType":2,"referenceValue":"17\n23"},
    {"parameterIndex":2,"name":"外观","parameterType":1,"options":["合格","不合格"]},
    {"parameterIndex":3,"name":"编号核对","parameterType":0,"referenceValue":"已核对"}
  ]
}
```

返回参数实体同时提供referenceMode/minimumValue/maximumValue；选项由数组生成候选项实体。没有参数用parameters=[]；没有视频用videoId=null。

### 4.5 投影素材、模板及编辑器

| 方法 路径 | 权限 | 请求 | data返回 |
|---|---|---|---|
| GET /projector/icons | U | page/pageSize、keyword | 素材分页实体、文件预览URL |
| POST /projector/icons | A | name、fileId | 素材实体，sourceCode=1 |
| PATCH /projector/icons/{id} | A | name/fileId、expectedVersion | 更新实体；旧任务仍取冻结iconFileId |
| GET /projector/icons/{id}/references | A | 无 | 模板/工艺/在制/历史引用数量、blockCode |
| DELETE /projector/icons/{id} | A | expectedVersion | null；当前配置/在制引用阻塞 |
| GET /projector/templates | A/O | page/pageSize、keyword | 模板实体、elementCount |
| POST /projector/templates | A | name、description | 新模板实体，画布固定1920×1080 |
| GET /projector/templates/{id} | A | 无 | 模板实体加元素实体数组 |
| PUT /projector/templates/{id} | A | name、description、elements、expectedVersion | 更新模板实体及元素；同时维护元素五字段 |
| PATCH /projector/templates/{id} | A | name/description、expectedVersion | 模板实体，供列表重命名 |
| GET /projector/templates/{id}/preview | A | processId、stepNo | 模板/预览步骤及resolvedText，dataSource=1 |
| GET /projector/templates/{id}/references | A | 无 | 来源模板引用数量及默认保护说明 |
| DELETE /projector/templates/{id} | A | expectedVersion | null；默认模板禁止，已复制的步骤配置仍完整保留 |

模板编辑PUT对带id元素保留createdBy/createdAt，修改更新时间；新增元素写新审计字段；移除元素软删除。套用模板复制配置和元素；后续模板修改不影响已经发布的步骤投影。模板删除只下架来源模板，已复制配置不依赖其是否有效。

```json
{
  "name":"工序引导",
  "description":"检查参数",
  "expectedVersion":2,
  "elements":[
    {"id":"501","elementType":0,"iconId":"410","bindCode":0,"x":190,"y":300,"scale":1,"rotation":0,"blinkFlag":0,"zIndex":1},
    {"elementType":1,"iconId":null,"textContent":"{{stepTitle}}","bindCode":1,"x":460,"y":410,"scale":1,"rotation":0,"fontSize":60,"color":"#EFF2F0","blinkFlag":0,"zIndex":2}
  ]
}
```

### 4.6 任务作业、汇总、交接及投影

| 方法 路径 | 权限 | 请求/查询 | data返回 |
|---|---|---|---|
| GET /bindings/by-product-code | O | productCode | 有效binding实体、型号/任务摘要；无绑定返回null |
| GET /bindings/by-steel-no | O | steelNo | 有效binding实体或null |
| POST /tasks/start | O | processId、modelId、steelNo、productCode、workstationId | task实体、startMode(0新建/1恢复)、cursor实体、workstation及流程权限 |
| GET /tasks | A/O | page/pageSize、statusCode、steelNo、productCode、modelId、from/to（可选） | task分页实体；O只返回有本站快照的任务；状态0按startedAt，状态1按endedAt，未传状态不应用时间过滤 |
| GET /tasks/{id} | A/O | 无 | 任务实体、工位与步骤冻结定义、当前结果/参数、本站/最终summary |
| GET /tasks/{id}/work | O | cursorId可选；无则查本会话对应游标 | 第2.2节所有作业展示数据和后端权限；GET不创建/更新游标 |
| PUT /tasks/{id}/cursor | O | taskStepId、expectedVersion（游标版本） | cursor实体；只查看，不写结果、不跳步 |
| POST /tasks/{id}/jumps | O | toTaskStepId、expectedVersion（任务版本） | 新游标、补写的skip事件/步骤结果、task新版本 |
| PUT /tasks/{id}/steps/{stepId}/result | O | parameters:[{parameterSnapshotId,actualValue}]、expectedVersion（任务版本） | result实体、参数实体、nextStepId、stationComplete(0/1)、task新版本 |
| POST /tasks/{id}/summary-entry | O | expectedVersion（任务版本） | 未处理步骤补跳过后返回本站summary、最后一步cursor、task新版本 |
| GET /tasks/{id}/summary | O/A | stationId可选（永久ID；O必须本站）；默认按任务状态决定本站/最终 | scopeCode、editable、task、station、steps/parameters、unfilledCount、summary实体或null、blockCode |
| PUT /tasks/{id}/summary | O | scopeCode、parameters、expectedVersion（任务版本） | summary草稿实体、参数值、unfilledCount、task新版本 |
| POST /tasks/{id}/stations/{stationId}/confirm | O | parameters、expectedVersion（任务版本） | 已确认本站summary实体、nextStation、finalSummaryPending(0/1)、task新版本 |
| POST /tasks/{id}/steel-no | O | steelNo、expectedVersion（任务版本） | task和binding更新实体、审计event实体 |
| POST /tasks/{id}/complete | O最终工位 | parameters（全任务范围）、expectedVersion（任务版本） | 已完成task实体、最终summary实体、bindingState=1 |
| GET /tasks/{id}/projection | O | cursorId | task/step快照、冻结elements、resolvedText、progress、revisionToken、asOf |
| GET /tasks/{id}/operation-details | A | page/pageSize | 每步骤一行加每次钢印号修改一行的分页展示视图 |
| GET /tasks/{id}/events | A | page/pageSize、eventCode、stationId | 全部事件及参数修改前后分页实体 |

work.permissions包含数字canEditStep/canEnterSummary/canConfirmStation/canCompleteTask、blockCode与blockMessage。流程阻塞码：0可用，1前序未确认，2已向下站交接，3任务完成，4无本站步骤，5本站尚有未处理步骤，6参数未填；与HTTP错误码分别使用不同字段。

汇总schema：scopeCode、editable(0/1)、unfilledCount、stepCount、summary；steps包含taskStepId、stepNo、title、stationNo/stationName、importanceCode、result及parameters。参数实体含actualValue、isFilled、deviationFlag、type/reference/minimum/maximum/options。所有snapshot实体和真实summary实体带公共字段；衍生计数不带虚构公共字段。

operation-details：rowType、taskStepId或eventId、stepNo（修改编号行null）、title、stationNo/stationName、resultCode、operatorId/operatorName、operatedAt、reasonCode/reasonText、previousSteelNo/steelNo。步骤行关联snapshot/result对象；编号行关联event对象，每个对象返回自身公共字段。当前结果可展示最近一次操作，全部旧结果从events追溯，不能重复把所有历史保存事件当作多个工序。

投影cursorId必须归属当前用户、当前任务、操作工位。投影窗口与作业页共享本用户游标，其他操作员查看同任务不改变该投影。GET轮询使用ETag/If-None-Match，默认250ms与原型一致，未变化返回304；响应中接口实体/值必须来自同一数据库一致性读，权限每次校验。后续可以添加推送，但本设计不要求WebSocket服务。

```json
{
  "processId":"1001",
  "modelId":"4",
  "steelNo":"GY-20260930-001",
  "productCode":"PC-20260930-001",
  "workstationId":"1"
}
```

```json
{
  "expectedVersion":5,
  "parameters":[
    {"parameterSnapshotId":"6201","actualValue":"23"},
    {"parameterSnapshotId":"6202","actualValue":"不合格"}
  ]
}
```

上例可以保存异常选择，范围23边界合格；最终交接必须全部填写，但不会因为已填写值偏离而拦截。单选新请求只能提交冻结候选项，非法选项返回1001；历史导入的非法选项值保留并记偏离。

## 5. 流程、事务与冻结规则

### 5.1 开始/恢复任务

1. 验证登录工位、有效工艺、启用型号、型号一致、所选版本有本站步骤。产品编码及钢印号trim后非空。
2. 先按钢印号找在制任务，不以产品编码创建第二条任务；锁定existing task/binding。型号不同返回1012，不覆盖。
3. 已存在则返回原taskId、原startedAt、原工艺快照及原进度；产品编码变化时同事务记录eventCode=7和修改前后值，原载体不再返回有效绑定。如果新产品编码已属于另一在制任务，返回1005。
4. 新建锁定当前工艺入口版本，写task和全部快照、binding、新建事件；先实际完成的工位仍按流程保护。任务编号由服务端唯一ID生成器或完整自增序列格式化，不能使用原型截断时间戳。
5. 绑定/任务有效钢印号双重唯一键防止并发重复；冲突后读出既有在制任务并恢复，不能吞掉型号不一致。
6. 创建或恢复时创建/更新本站用户task_view_cursor至第一条未处理步骤；无未处理则本站末步。同一事务提交幂等响应，更新人和更新时间来自实际登录用户。

### 5.2 跳转、汇总入口与结果

- 查看的跨工位步骤只修改游标，不获取写权限。实际跳步必须本站、前序已确认、本站未交接，任务未完成。
- 向后跳到某步时，只为本站目标前方尚无结果的步骤补普通/重要跳过结果及独立审计事件；已实际完成或已跳过不重复写跳过事件。跳过无参数值。
- 进入参数汇总前先POST summary-entry：首站无需前序确认；有前序未确认返回1008及“请先完成前序工位的全部步骤，并等待前序工位确认交接。”，不能偷偷补跳步。
- 前序均确认时，尚未处理的本站步骤（含末步）以reasonCode=2补跳过，再把游标定位本站末步并开放汇总编辑。直接GET汇总遇未处理步骤返回editable=0、blockCode=5，不能在GET里跳步。
- 正常保存步骤设置actionCode=0，更新最新operator/time及实际值，追加事件及每个发生变化参数的history。回改更新最新结果，保留原事件和旧值。
- 汇总修改只更新实际参数值和来源，追加eventCode=3，不把跳过步骤改为“实际完成”，不重写operated_at；这与当前原型一致。因此跳过后在汇总补填的参数不计入“实际完成步骤”的偏离排行或耗时扇区。

### 5.3 汇总保存、本站确认与最终提交

- 本站范围是任务冻结工位的全部步骤，最终范围是全任务全部步骤。写参数ID必须属于当前可编辑范围；参数被删改过的当前工艺不影响冻结参数。
- PUT汇总允许暂存不完整草稿；更新task_parameter_value、summary_item和审计，服务端返回缺失数量。本站确认或最终提交必须全范围填写，trim空、null为缺失，数值“0”合法；异常值允许保留。
- 普通本站确认：前序确认、本站已处理全部步骤、全部参数填写后，冻结本站summary/items并追加eventCode=4；后序据此解锁。非最终站确认后本人不能修改其参数或钢印号。
- 最终工位确认本站后，task仍statusCode=0，finalSummaryPending=1；退出重入直接GET最终汇总，可编辑全任务所有参数，包括此前交接站的值。本站历史确认表单仍保留当时值；最终修改记录操作者、最终工位和各参数归属工位。
- 最终提交锁定task及绑定，确认全部任务工位已确认、参数全填，写最终summary/items、eventCode=5，设置task.statusCode=1、endedAt、durationMs=max(1000,结束-开始)，binding_state=1。原子提交后所有参数只读。
- 归档任务仍从相同task/快照/结果表查询，不再另复制一套task_history表；historical list是statusCode=1查询。重复提交通过幂等返回同一归档结果。

### 5.4 钢印编号与产品编码

- 修改钢印号仅在当前可编辑本站汇总或待最终汇总允许，任务完成后拒绝。新号trim非空；与原号相同则返回原实体、无需新增“修改”事件。
- 若新钢印号属于其它在制任务，唯一约束与业务校验拒绝。允许与已完成历史任务钢印号相同，以支持后续返工任务统计。
- task.steel_no、task_binding.steel_no和有效扫码查询同事务修改，事件code=6保存before_value/after_value、操作者ID及姓名、冻结工位ID/编号/名称、服务端时间；task_snapshot参数定义不改。
- 最终统计按该任务归档时钢印号计数；旧编号仅在审计显示，不当成第二件。产品编码替换事件code=7供追溯，不计钢印号修改次数。

### 5.5 删除、工位重建与资源替换

- 工位1不可删；所有真实在制任务快照引用永久工位ID时不可删。允许删除时，将当前发布工艺中相关步骤改绑默认工位，通过发布新工艺版本生效；旧版本和历史快照不改。
- 新工位编号为当前有效工位编号的最小空缺正整数。删除8、再次新增得到编号8，但永久id不同；过去任务仍展示原station_no/station_name。
- 视频分类删除不级联，一级有子项、任意级有有效视频均阻塞。视频全库标准名唯一，并发替换用rowVersion；显式替换只改变当前媒体版本，不重新绑定工艺，不改任务videoId。
- 任务冻结步骤、参数、选项、工位名、投影元素及图标文件；视频绑定ID冻结，但有确认的资源替换时播放新媒体，这一例外必须在资源文件版本和公共审计中可追溯。
- 工艺下架只禁止开始新任务，已有任务完整快照继续执行；当前配置/在制引用的图标和视频删除先解绑或完成任务，历史文件保留。
- 资源references返回全库准确数量，不使用原型只检查state.task的简化算法。显示引用数后，DELETE仍重新验证，以防并发新增引用。

### 5.6 事务和并发边界

所有业务写事务先锁task或对应根资源，再检查expectedVersion，修改子行、追加审计、更新根版本，最后提交。工位确认、编号修改、最终归档、导入发布、批量视频替换各自是一个事务。

只改投影游标使用cursor版本，不修改task版本。单次涉及多个步骤/参数时按永久ID升序锁定，降低死锁；发生死锁服务器有限重试并保留同幂等key。录入actualValue不能提交reference/type/工位/偏离结果等服务端字段。

## 6. 生产统计：数据、公式和接口

### 6.1 筛选及统一统计响应

`GET /stats/overview?from=2026-09-24&to=2026-09-30&presetCode=1`；其余前两图使用同一日期与各自modelId。日期始终起止包含；后端转换为UTC半开区间 `[from当天00:00,to次日00:00)`，不得用23:59:59遗漏毫秒记录。

近7日含当天与前6日，近30日含当天与前29日。令N=两日历日期差+1，上周期为 `[from-N日,from-1日]`。统计结果以asOf为截点，未来日期部分没有数据；不允许from>to。

数值来源是is_deleted=0的真实任务；不接受dataSource=1混入真实聚合。生产GET不回退到演示样本。文档预览或专门测试环境可以标记dataSource=1，明确说明生成方式；演示不写任务表，不出现在任务记录，也不与真实样本混合。

每个统计data统一包含 `asOf、dataSource:0、filters:{from,to,presetCode,modelId}、previousPeriod（适用时）、sourceTables、items/metrics`。源表名称是来源说明，不是类型编码。所有聚合值不是实体，无虚构的createdBy/createdAt。

### 6.2 六张经营指标卡

| 卡片 | 原始数据与公式 | 单位/为空 |
|---|---|---|
| 完成件数 | status=1且ended_at落入范围，COUNT(DISTINCT TRIM(steel_no)) | 件；无记录0 |
| 在修积压 | status=0且started_at落入范围的任务行数 | 件；无记录0 |
| 一次合格率 | 筛选期已完成且钢印号非空记录中，该钢印号在本期只出现1次的记录数 / 这些记录总数 ×100 | %，保留1位；无记录null |
| 总平均耗时 | 本期已完成且有效duration_ms>0的任务AVG(duration_ms) | 返回原始avgDurationSec；卡片兼容原型先四舍五入为分钟，再转换小时保留1位，h；无记录null |
| 平均最长耗时型号 | 本期按modelId/modelCode求AVG(duration_ms)，取最大平均值型号 | 主值h，底部modelCode；无记录null |
| 平均最佳耗时型号 | 同上，取最小平均值型号 | 主值h，底部modelCode；无记录null |

一次合格率是当前已确认的“期内非重复钢印记录比例”，不是专业质量系统中“首检通过率”；不增加没有依据的质检结果字段。重复2条任务同号时两条都不进入分子；同号在筛选期外出现不影响本期判定。接口同时返回分母completedRecordCount和分子firstPassRecordCount，以及上期对应数量，便于页面和后端核对计算。型号最长/最佳按原始平均耗时选取，不按单件极值；相同平均值按modelCode升序、modelId升序固定取首个。

前四卡比较：件数及平均耗时 `(当前-上期)/上期×100%`；一次合格率 `当前%-上期%` 用百分点，与已恢复的当前页面一致。上期没有该类样本，comparisonState=0、value=null、显示“无同期数据”；上期值0当前非0也不产生无穷值，仍为无同期数据。均0且存在可比较样本时持平；展示差异绝对值小于0.05时持平，避免0.0箭头。一次合格率本值为%，比较单位为comparisonUnit=1，不擅自改变已有口径。

在修积压的比较严格沿用页面：统计“查询时仍在制、开始时间落入上周期”的任务数量，不是恢复“上周期末的历史在制库存”。历史库存如以后需要应另定义事件时点查询，本版本不冒充已有数据。

```sql
-- @from_utc、@to_utc由服务端按北京时间日期计算并参数绑定。
WITH completed AS (
  SELECT id,model_id,model_code,TRIM(steel_no) AS steel_no,duration_ms
  FROM task
  WHERE is_deleted=0 AND status_code=1
    AND ended_at>=@from_utc AND ended_at<@to_utc
),
steel_counts AS (
  SELECT steel_no,COUNT(*) AS n FROM completed
  WHERE steel_no<>'' GROUP BY steel_no
)
SELECT
  (SELECT COUNT(*) FROM steel_counts) AS completed_pieces,
  (SELECT COUNT(*) FROM task
     WHERE is_deleted=0 AND status_code=0
       AND started_at>=@from_utc AND started_at<@to_utc) AS backlog_pieces,
  (SELECT SUM(IF(n=1,1,0)) / NULLIF(SUM(n),0) * 100 FROM steel_counts) AS first_pass_rate,
  (SELECT AVG(duration_ms)/1000 FROM completed WHERE duration_ms>0) AS avg_duration_sec;

SELECT model_id,model_code,COUNT(*) AS sample_count,AVG(duration_ms)/1000 AS avg_duration_sec
FROM task
WHERE is_deleted=0 AND status_code=1
  AND ended_at>=@from_utc AND ended_at<@to_utc AND duration_ms>0
GROUP BY model_id,model_code
ORDER BY avg_duration_sec DESC,model_code ASC,model_id ASC;
-- 最大取第一条；最小用ASC单独查询，保持同值并列规则。
```

示例overview.data（此处仅给聚合对象，不存在实体审计字段）：

```json
{
  "asOf":"2026-09-30T08:00:00.000Z",
  "dataSource":0,
  "filters":{"from":"2026-09-24","to":"2026-09-30","presetCode":1,"modelId":null},
  "previousPeriod":{"from":"2026-09-17","to":"2026-09-23"},
  "sourceTables":["task"],
  "metrics":{
    "completedPieces":{"value":20,"previousValue":16,"comparisonState":2,"comparisonUnit":0,"comparisonValue":25.0},
    "backlogPieces":{"value":4,"previousValue":5,"comparisonState":3,"comparisonUnit":0,"comparisonValue":20.0},
    "firstPassRate":{"value":90.5,"previousValue":77.8,"completedRecordCount":21,"firstPassRecordCount":19,"previousCompletedRecordCount":18,"previousFirstPassRecordCount":14,"comparisonState":2,"comparisonUnit":1,"comparisonValue":12.7},
    "avgDuration":{"avgDurationSec":3600,"displayHours":1.0,"previousDurationSec":4000,"comparisonState":3,"comparisonUnit":0,"comparisonValue":10.4},
    "longestModel":{"modelId":"4","modelCode":"DISPEN9243-40","avgDurationSec":4800,"displayHours":1.3,"sampleCount":8},
    "bestModel":{"modelId":"1","modelCode":"DISPEN9148-40","avgDurationSec":2400,"displayHours":0.7,"sampleCount":5}
  }
}
```

上例当前21条完成记录中，一个钢印号有2条、其余19个各1条，因此完成20件、一次合格率19/21=90.5%；上期18条记录中两个钢印号各2条、其余14个各1条，因此完成16件、一次合格率14/18=77.8%。平均耗时比较按页面兼容的分钟值计算：当前60分钟，上期round(4000/60)=67分钟，下降10.4%。服务端同时给原始秒值，前端不再自行聚合。

### 6.3 工位实时状态和悬停明细

接口：`GET /stats/stations`，`GET /stats/stations/{workstationId}/tasks?page=&pageSize=`。权限A。与日期筛选无关，固定返回stationNo=1–11；数据库工位12及以上正常业务存在但此状态图不显示。

任务归属：在task_station_snapshot的sequence_no顺序找第一个未确认本站summary的工位；全部确认但task.status=0仍归属最终工位。以永久工位ID分组；返回冻结名称与当前工位实体。0件空闲、1件正常、2–3繁忙、>=4拥堵；没有有效配置的编号heatCode=4、workstation=null，不能生成假的工位实体。

```sql
WITH stationed AS (
  SELECT ts.task_id,ts.workstation_id,ts.sequence_no,
         CASE WHEN sm.summary_state=1 AND sm.is_deleted=0 THEN 1 ELSE 0 END AS confirmed
  FROM task_station_snapshot ts
  JOIN task t ON t.id=ts.task_id AND t.status_code=0 AND t.is_deleted=0
  LEFT JOIN task_summary sm
    ON sm.task_id=ts.task_id AND sm.task_station_id=ts.id AND sm.scope_code=0
  WHERE ts.is_deleted=0
), chosen AS (
  SELECT task_id,
    COALESCE(MIN(CASE WHEN confirmed=0 THEN sequence_no END),MAX(sequence_no)) AS seq
  FROM stationed GROUP BY task_id
)
SELECT s.workstation_id,COUNT(*) AS task_count
FROM stationed s JOIN chosen c ON c.task_id=s.task_id AND c.seq=s.sequence_no
GROUP BY s.workstation_id;
```

data.items每格包括stationNo/workstation实体或null/count/heatCode；悬停tasks返回taskId/taskNo/modelId/modelCode/steelNo/startedAt及任务公共字段。按modelCode、steelNo、taskId升序；一条在制任务一行，不把同型号合并掉。

### 6.4 参数偏离排行

接口：`GET /stats/parameter-deviations?from=&to=&modelId=`；独立型号选择器，modelId必填。型号候选 `GET /stats/models?from=&to=`：本期有真实记录型号优先，其后有效已导入工艺型号，再加入历史任务型号；按组去重，各组内按modelCode升序，停用/已删历史型号仍允许只读统计。

仅统计本期已完成任务，冻结步骤最新结果action_code=0，参数实际值is_filled=1。一项偏离参数贡献1次，同一步3个偏离贡献3次；不按事件保存次数重复加计。按任务冻结工位永久ID、冻结编号、冻结名称分组，按次数降序，再按编号、永久ID排序。历史同一工位改名时分组保留各冻结名称，不被当前工位名称覆盖；相同编号重建后的永久ID不同。

判定：
- type=2/referenceMode=1：actualValue严格解析为十进制，minimum<=值<=maximum为不偏离，边界合格；超限或非数值为偏离。
- 新type=0/referenceMode=0：仅去首尾空白后与参考文本精确相等；不删除中间空格，不忽略大小写。
- type=1/referenceMode=2：仅optionNo=1对应文本不偏离；其余合法候选项偏离，历史非法候选值也偏离。
- 历史type=0内联 `17-23`、`17~23`、`17至23` 迁移为referenceMode=1，保留原文本和type=0；数值按闭区间判定，非数值记偏离。新导入不自动把手动文本解释为范围，必须使用type=2。
- actualValue未填不计入，不把投影回落的参考值当实际填写。值改变时服务端按冻结定义更新deviationFlag，同时写history；统计不信任前端传来的标记。

```sql
SELECT ts.workstation_id,ts.station_no,ts.station_name,
       COUNT(*) AS deviation_count
FROM task t
JOIN task_step_snapshot st ON st.task_id=t.id AND st.is_deleted=0
JOIN task_station_snapshot ts ON ts.id=st.task_station_id AND ts.is_deleted=0
JOIN task_step_result r ON r.task_step_id=st.id AND r.action_code=0 AND r.is_deleted=0
JOIN task_parameter_snapshot p ON p.task_step_id=st.id AND p.is_deleted=0
JOIN task_parameter_value v ON v.parameter_snapshot_id=p.id
  AND v.is_deleted=0 AND v.is_filled=1 AND v.deviation_flag=1
WHERE t.is_deleted=0 AND t.status_code=1 AND t.model_id=@model_id
  AND t.ended_at>=@from_utc AND t.ended_at<@to_utc
GROUP BY ts.workstation_id,ts.station_no,ts.station_name
ORDER BY deviation_count DESC,ts.station_no ASC,ts.workstation_id ASC,ts.station_name ASC;
```

data.items={workstationId,stationNo,stationName,deviationCount}；无偏离返回[]，即使有正常录入也不造偏离柱。所有工位均可出现，没有“只统计工位1”的过滤。

### 6.5 工序耗时比重

接口：`GET /stats/step-duration-share?from=&to=&modelId=`；独立于偏离图的型号选择。

每条本期已完成任务取最新action=0的步骤结果，按operated_at升序、task_step_snapshot.step_no升序、result.id升序排列。同任务首个实际完成步骤耗时=其operated_at-started_at；后续耗时=本次operated_at-上一实际完成operated_at。跳过不生成扇区；差值<=0不纳入。时间差包括等待，不是纯工时；后续汇总改参数不改变步骤时间。

按modelId+stepNo汇总，兼容同型号不同版本的步骤编号；步骤显示标题采用本期最后完成任务该步骤的冻结标题，按ended_at/taskId确定，不从现工艺读改名结果。接口返回所有有效步骤、总耗时及sampleTaskCount；前端按总耗时倒序，前4项独立，余项合成“其他工序”；环中心总时长=全部有效步骤秒数之和，不等于AVG任务总耗时。

```sql
WITH ordered_results AS (
  SELECT t.id AS task_id,st.step_no,st.title,t.ended_at,r.operated_at,
    COALESCE(LAG(r.operated_at) OVER (
      PARTITION BY t.id ORDER BY r.operated_at,st.step_no,r.id
    ),t.started_at) AS previous_at
  FROM task t
  JOIN task_step_snapshot st ON st.task_id=t.id AND st.is_deleted=0
  JOIN task_step_result r ON r.task_step_id=st.id AND r.action_code=0 AND r.is_deleted=0
  WHERE t.is_deleted=0 AND t.status_code=1 AND t.model_id=@model_id
    AND t.ended_at>=@from_utc AND t.ended_at<@to_utc
), durations AS (
  SELECT *,TIMESTAMPDIFF(MICROSECOND,previous_at,operated_at)/1000000 AS duration_sec
  FROM ordered_results
)
SELECT step_no,SUM(duration_sec) AS total_duration_sec,COUNT(*) AS sample_count
FROM durations WHERE duration_sec>0
GROUP BY step_no ORDER BY total_duration_sec DESC,step_no ASC;
-- 标题按上文最后完成任务规则另查，不从GROUP BY任意抽取title。
```

data={modelId,modelCode,totalDurationSec,sampleTaskCount,items:[{stepNo,title,totalDurationSec,percentage,sampleCount}],...统计公共查询字段}。百分比按原始秒数/总秒数×100，返回1位精度；前端可展示整数%，因四舍五入显示百分比之和可能不是100%，不伪造耗时凑整。无有效数据返回totalDurationSec=0、items=[]。没有单独“合计”明细行。

### 6.6 月度产能趋势

接口：`GET /stats/capacity-monthly?to=2026-09-30&months=12`；months固定12。不使用from，仅根据to确定连续12个自然月，从to所在月向前11个月，最后一月只计算到to当日及asOf。返回items恰好12项，没任务的月补0；折线直线连接（smooth=false，属于前端视觉配置）。

每月以北京时间ended_at归属月，COUNT(DISTINCT钢印号)。同号同月多次完成只算1件，同号不同月份各算1件；任务状态完成、未删除；不限制近7/30日起点。

```sql
SELECT DATE_FORMAT(CONVERT_TZ(ended_at,'+00:00','+08:00'),'%Y-%m') AS month_key,
       COUNT(DISTINCT TRIM(steel_no)) AS piece_count
FROM task
WHERE is_deleted=0 AND status_code=1
  AND ended_at>=@first_month_utc AND ended_at<@cutoff_utc
  AND TRIM(steel_no)<>''
GROUP BY month_key ORDER BY month_key;
-- @cutoff_utc=min(to次日北京00:00对应UTC,asOf)。
-- 服务端生成连续12个月后LEFT合并以上结果，把缺失月补0。
```

data.items={month:"2026-09",label:"26-09",completedPieces:20}，另外提供to/months/asOf/dataSource/sourceTables。本项目已删除每日趋势、操作员完成量和操作员平均时长图，不为不存在的页面再设计必须实现的统计接口。

### 6.7 统计接口目录

| 接口（A权限） | 数据范围与返回 |
|---|---|
| GET /stats/overview?from=&to=&presetCode= | 六指标；前四比较；含sourceTables和上周期 |
| GET /stats/models?from=&to= | 两个分析型号选择器共用候选，返回型号实体或历史型号来源对象 |
| GET /stats/stations | 当前全部在制任务，固定11格 |
| GET /stats/stations/{workstationId}/tasks?page=&pageSize= | 对应永久工位的当前任务明细 |
| GET /stats/parameter-deviations?from=&to=&modelId= | 型号+完成时间筛选，偏离次数降序 |
| GET /stats/step-duration-share?from=&to=&modelId= | 型号+完成时间筛选，全部步骤耗时 |
| GET /stats/capacity-monthly?to=&months=12 | 近12自然月的完成件数，结束日截断 |

## 7. 原型对接与兼容说明

本次仅新增设计文档，以下是实施后端时的明确对接工作，不声称已经改动页面：

| 当前原型 | 生产数据设计/前端适配 |
|---|---|
| localStorage中的people/password、演示角色切换 | sys_user哈希口令及auth_session；数值角色，保留真实用户身份 |
| 全局processRecord/processSteps与当前工艺 | 本用户偏好+process.currentRevision；查看工艺不改别人任务 |
| 工位id就是可复用编号 | permanent id加stationNo；所有绑定按永久ID，UI按stationNo显示 |
| 固定4个型号候选 | product_model初始化4个后由API返回，导入新型号加入 |
| 同型号直接替换steps、已启动任务JSON快照 | 工艺发布版本+正规任务快照表；历史发布行不覆盖 |
| step.videoName/NONE/运行时名称匹配 | 导入一次解析稳定videoId/null，来源名保存追溯；标准名唯一 |
| 上传blob地址，刷新丢失播放媒体 | 文件对象+不可变视频版本+短期授权URL；刷新可重新取播放地址 |
| state.task.currentStepIndex共享当前查看步 | 用户/工位/任务独立task_view_cursor，投影明确传cursorId |
| logs覆盖之前完成值、跳过行保留 | task_step_result最新结果+不可变event/value_history |
| stationConfirmations与completionForm JSON | task_summary与task_summary_item；本站确认和最终确认分别冻结 |
| steelBindings、productBindings对象；完成时删除 | task_binding生效/归档状态，改号与映射事务处理 |
| 判断前序使用当前全部已配置低编号工位 | 按任务实际工位快照sequence_no判断，排除无本站步骤的工位，避免无法交接 |
| 历史缺快照时回退当前工艺 | 导入旧数据前一次性冻结可恢复的历史定义，标注无法恢复项；新任务必须完整快照，不动态回退 |
| 有些页面类型使用字符串或true/false | store.api把数字编码映射当前界面属性；后端接口始终数字 |
| 统计前端临时模拟数据 | 生产API真实为空即空；演示独立标记且不写业务库 |
| 手动旧内联区间/不同文档的合格判断 | 新定义严格按type0文本/type1首项/type2四段边界；旧区间按referenceMode兼容 |
| 月图目前完整查询to所在月份可能纳入to之后记录 | 服务端月图严格截到to当日，保证自定义历史区间依据一致 |
| docs/02旧文案“最终允许参数为空”、旧统计API | 以本文和现页面“交接/最终提交必填”、六指标和现有三分析图为准 |
| 页面独立临时日期/分页/折叠/全屏/副屏偏好 | 页面状态或本机浏览器偏好，不建没有业务意义的生产实体表 |

已有原型数据迁移是单独实施任务，不能把文档当作已自动迁移。迁移顺序：人员和工位编号→永久ID映射→型号及文件资源→视频/模板→工艺发布版→任务快照→结果/参数→确认/事件→有效绑定。旧数据时间按真实含义转换，未知历史创建人标记系统0并保留导入说明，不伪造人工操作者；没有可信历史工艺定义的任务列入迁移异常报告。

已知历史视频同名数据必须在迁移报告中人工确认保留资源/绑定，再建立有效唯一键；不能直接选第一个候选悄悄绑定。所有迁移录入行含公共字段，既有操作时间保留在业务时间字段，迁移创建时间不能代替历史完成时间。

## 8. 验收与一致性检查清单

| 场景 | 验收结果 |
|---|---|
| 每张表、每个从属实体 | 36张表均有created_by/created_at/updated_by/updated_at/is_deleted；接口实体也返回；密码/令牌摘要不泄露 |
| 数字编码 | SQL无ENUM；JSON状态/类型/标记均数字；传非法编码或字符串状态被拒绝 |
| 登录、角色、个人信息 | O/A/S权限对应页面，停用人员不可登录；普通管理员无法人员管理；改密/退出撤销会话 |
| 工艺导入视频 | “拧紧机”匹配“拧紧机.mp4”，绑定稳定ID；未找到及历史重名报来源行；预检通过后删除资源，确认再次阻塞 |
| 视频重名替换/取消 | 取消整批不创建资源；替换保留ID、递增文件版本、原绑定有效；同批重名拦截；并发替换不丢更新 |
| 参数类型与偏离 | 范围17/23/20合格，16/24/非数值偏离；手动精确参考合格，单选仅首项合格；实际0视为已填写 |
| 范围配置精度 | 缺上下限、NaN/Infinity/上下限颠倒或配置精度超限报错；实际异常保留但记偏离 |
| 在制唯一与恢复 | 两个工位同时开始同钢印号只一个task；不同钢印号任务并存；同号新载体恢复原快照和进度 |
| 前序与跳步 | 前序未确认不能步骤写入/跳步/交接；首站参数汇总补跳过及审计，直接GET不改变数据 |
| 参数汇总必填 | 缺任意参数不能交接或提交；PUT允许暂存；0值有效；填写异常值可确认 |
| 交接冻结和最终恢复 | 非最终工位已交接不可改；最终本站确认后重入显示全任务汇总可编辑；确认完成后全只读 |
| 历史冻结 | 新工艺、参数、工位名、图标/模板变更不改既有快照；视频显式替换按有审计的媒体版本更新 |
| 工位编号复用 | 新增8→删除→新增仍编号8但ID不同；5/8缺失优先5；默认1与在制引用不可删 |
| 钢印号修改审计 | 独立事件记录操作者/当时工位/前后号/时间；映射同步；任务明细每步一行另加每次改号行 |
| 文件与素材 | 文件长期可查，临时URL过期可续签；软删除主数据不移除历史引用的文件 |
| 六指标及比较 | 唯一钢印件数、在制记录数、非重复期内比例、平均耗时及按型号均值极值分别正确；上期为空/0不无穷 |
| 工位实时状态 | 0/1/2–3/4件对应0/1/2/3；缺配置4；确认迁移至下一本站；同型号多钢印号都展示 |
| 参数偏离筛选 | 各永久工位均能统计，型号/日期过滤生效；重复修改不重复加计，跳过步骤不计入 |
| 工序耗时 | 相邻实际完成时间差，跳过无扇区，汇总编辑不改变时间，前4项+其他总数不变 |
| 月产能 | 连续12项、按北京月份去重，to之后任务不纳入，没记录月份补0 |
| 并发与幂等 | 相同key重放不重复保存/交接/归档/替换；不同请求同key冲突；过期版本不覆盖新数据 |
| 真实与演示 | 无真实数据返回0/null/[]，不写假任务；演示预览dataSource=1，任务记录不混入 |
| 全路由覆盖 | 第2节涵盖登录、所有op/admin实际路由、预览、公共个人弹窗；字典路由是重定向 |

### 8.1 文档交付验证边界

本文SQL应在空MySQL 8.0.17+数据库中按顺序执行，然后执行初始化SQL；SQL审计字段/外键/有效唯一键可通过information_schema核对。未连接真实MySQL进行执行验证时，只能标注“静态一致性检查通过”，不能声称“建表已运行成功”。

真正后端上线前需执行SQL建库、初始化和上述事务/接口场景，并将接口schema及OpenAPI与本文对齐。本次文档交付不启动数据库、不改前端业务流程、不连接或修改已有生产数据。

## 9. MySQL 官方语法依据

- [CREATE TABLE](https://dev.mysql.com/doc/refman/8.0/en/create-table.html)：表、索引、约束和唯一索引NULL值规则。
- [生成列](https://dev.mysql.com/doc/refman/8.0/en/create-table-generated-columns.html)：有效记录生成键及索引。
- [MySQL 8.0.17 发布说明](https://dev.mysql.com/doc/relnotes/mysql/8.0/en/news-8-0-17.html)：本文采用的 `utf8mb4_0900_bin` 校对规则从该版本提供，因此最低版本限定为8.0.17。
