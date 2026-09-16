# 口岸跨境电商仓包裹查验与退运申报服务

基于 **Java 17 + Spring Boot 3 + PostgreSQL 16** 的口岸跨境电商仓包裹查验与退运申报服务。覆盖包裹入仓、申报前检查、六方协同申报、**申报价格异常复核（同品牌历史成交价比对）**、海关查验、补材料/扣留/退运/销毁、放行派送、税费赔付、批次处理、商家风控与消费者进度查询全链路。

## 原始需求（本次缺陷修复 Prompt，一字不漏）

> 修复高风险商家同品牌同类申报在单票价格复核通过后失去严格预审的问题。商家此前因低报已被提升为 HIGH 并纳入兰蔻同品类重点复核，随后另一票凭证齐全被判 PASS 后，系统把该商家的重点复核标记清除；下一票申报价210元、历史均价275.56元时仅提示预警，不生成复核单，补传普通发票即可创建申报，使风险仍为 HIGH 的商家绕过采购凭证、促销说明、付款记录与报关员复核。单票 PASS 只能证明该票价格合理，不能覆盖既有风险处置；后续同品牌同类是否严格预审应持续由当前风险等级和未解除的风险记录决定。验收：M001 经补税或人工查验升为 HIGH 后，即使再完成一票 PASS，后续同品类报价处于普通60%阈值之上但严格80%阈值之下时仍须立案并阻断申报；只有明确降风险或解除重点名单后才恢复普通预检。

## 严格预审不被单票 PASS 解除（本次修复）

- **根因**：旧逻辑把“单票复核 PASS”当作风险解除——`applyReviewOutcome` 用 PASS 覆盖了品类 `review_flag=false` 并把商家重点名单 `stricter_review` 置 false；且严格预审只看名单/品类标记，不看商家**当前风险等级**。于是 HIGH 商家下一票落在 60%~80% 区间只预警、不立案。
- **修复**：
  1. PASS **仅**把该票申报价滚动计入历史均价，**不修改** `review_flag`、不解除任何重点名单；只有 `SUPPLEMENT_TAX`/`MANUAL_INSPECTION` 才置重点。
  2. 是否走严格 80% 阻断线，持续由“**当前风险等级 = HIGH** 或 **该商家×品牌品类重点名单未解除**”决定；品类级 `review_flag` 只对其他商家产生预警，不再阻断已解除处置的商家。
  3. 补税与转人工查验均直接把商家升为 **HIGH**（抽检 ≥50%、须提前传票、违规 +1）并写入重点名单。
  4. 解除途径只有两种（显式）：管理员/海关 `PUT /api/merchants/{id}/risk` 把风险**调离 HIGH**（自动解除该商家全部重点名单），或 `POST /api/brand-price-rules/watches/{id}/release` 显式解除单条；解除后同品类才恢复普通 60% 阈值预审。

## 原始需求（价格异常复核 Prompt，一字不漏）

> 增加申报价格异常复核。系统发现某品牌商品申报价远低于历史成交价时，服务要求商家上传采购凭证、促销说明和付款记录。报关员复核后，包裹可继续申报、补税或转人工查验，处理结果会影响商家风险等级。价格复核结论会沉淀到同品牌同类商品规则，后续申报会提前提示商家。风险等级提高后，商家后续同类商品会进入更严格预审。

## 申报价格异常复核（本次新增）

围绕“**某品牌 + HS 品类**”的历史成交价做闭环管理：

1. **自动比对立案**：申报前检查新增第 7 项 `BRAND_PRICE`。包裹带品牌时，与 `brand_price_rules` 同品牌同类历史均价比对——申报单价低于均价 ×60%（远低阈值）自动立案；在重点复核名单/品类已存疑的商家，低于均价 ×80%（预警线）即立案。未完成复核的包裹**不能创建申报单**。
2. **三证齐备**：商家在复核单上传 **采购凭证 `PURCHASE_PROOF` / 促销说明 `PROMO_EXPLANATION` / 付款记录 `PAYMENT_RECORD`**，三证齐备自动由 `AWAITING_EVIDENCE` 转 `UNDER_REVIEW`。
3. **报关员三结论**（影响商家风险等级）：
   - **继续申报 PASS**：价格合理，风险不加重，以申报价沉淀为可信成交价；**单票 PASS 只证明该票合理，不会清除既有的高风险等级或重点复核名单**。
   - **补税 SUPPLEMENT_TAX**：按认定计税单价（缺省取历史均价）写入包裹 `taxable_price`，重算申报单与未缴税费金额；低报价格属申报不实，商家风险**直接升至 HIGH**（抽检比例 ≥50%、要求提前传票），违规 +1，并纳入该品牌品类重点复核名单。
   - **转人工查验 MANUAL_INSPECTION**：商家直接置高风险并纳入重点名单；已建申报单立即布控查验，未建的在创建申报单时自动补开人工查验指令。
4. **结论沉淀**：写回同品牌同类规则（滚动成交均价、最近结论；品类低报标记仅在补税/转人工时置真，PASS 不清除），并对“商家×品牌×品类”建立重点复核名单 `merchant_price_watches`。
5. **后续提前提示 + 更严格预审（不被单票 PASS 解除）**：只要商家当前为 **HIGH** 或重点名单未解除，后续同品牌同类申报落在普通 60% 立案线之上、严格 80% 线之下时，仍会**立案并阻断申报**（必须传三证 + 报关员复核）；其他商家对已存疑品类只收到预警。**仅当管理员明确降风险（HIGH→非HIGH）或显式解除某条重点名单后，才恢复普通 60% 阈值预审**；正常放行的计税价会滚动沉淀为可信成交价。

| 模块 | 端点 |
|---|---|
| 价格复核 | `GET /api/price-reviews`、`GET /api/price-reviews/{id}`、`POST /api/price-reviews/{id}/evidence`（传三证）、`POST /api/price-reviews/{id}/decision`（PASS/SUPPLEMENT_TAX/MANUAL_INSPECTION） |
| 品牌规则 | `GET /api/brand-price-rules`、`GET /api/brand-price-rules/watches`（重点名单）、`POST /api/brand-price-rules/watches/{id}/release`（显式解除）、`GET /api/brand-price-rules/my-watches`（商家查看重点复核名单） |


## 原始需求（本次缺陷修复 Prompt，一字不漏）

> 修复退运/销毁终态与税费放行状态互相覆盖。已审单通过但税费仍待缴的包裹申请退运或销毁时，仓库执行完成后系统当前只对已缴税费标记退款，未缴税记录仍为 PENDING；财务随后缴税会在 FinanceService.payTax 调用 DeclarationService.tryRelease，把已 RETURNED/DESTROYED 的包裹和申报单重新写为 RELEASED，导致退运档案、税费责任和国内派送结论互相矛盾。应围绕同一申报单统一终态处理：退运/销毁执行时，已缴税费仅生成一次退款，未缴税费转为不可缴纳的作废/取消状态并保留原因；tryRelease 只能在包裹及申报单仍处于可放行链路、且不存在已完成退运/销毁处置时执行，不能覆盖终态。档案、税费列表、消费者进度和内部事件时间线需共同展示退运/销毁及税费清算结论。验收：海关已 ACCEPTED、税费 PENDING 的包裹完成退运后，财务缴税返回4xx或不可操作，包裹和申报单仍为 RETURNED、税费为作废且无放行事件；已缴税包裹退运后只产生一次退款；销毁分支同样不会被后续缴税改回 RELEASED；正常 ACCEPTED+缴税的包裹仍可放行。

## 退运/销毁终态与税费清算一致性（本次修复）

围绕**同一申报单**统一终态，杜绝“退运/销毁终态被后续缴税覆盖回 RELEASED”：

| 触发点 | 修复后行为 |
|---|---|
| 仓库执行退运/销毁 `ReturnService.execute` | 包裹 → `RETURNED`/`DESTROYED`，申报单同步进入同名终态（不再保留可放行的 `ACCEPTED`） |
| 已缴税费 `PAID` | 仅生成**一次**退款 → `REFUNDED`，事件“税费退还”留痕；重复执行处置被 4xx 拒绝，杜绝二次退款 |
| 未缴税费 `PENDING` | 转为**不可缴纳**的作废状态 `VOID`，并在 `void_reason` 保留作废原因（处置单号、原因、结论） |
| 财务缴税 `FinanceService.payTax` | 税费 `VOID`/`REFUNDED` 或包裹已处终态时返回 **409**；不会再把终态改回 `RELEASED` |
| 放行 `DeclarationService.tryRelease` | 四重守卫：存在已完成处置 / 已落终态（409）；申报单须可放行；包裹须仍在放行链路（`CUSTOMS_REVIEW`/`INSPECTION`）；存在已核准待执行处置则挂起——均不得覆盖终态 |
| 展示面 | 包裹档案（处置单 + 税费清算 + 作废原因 + 全链路事件）、财务税费列表（作废徽标与原因）、申报单详情、消费者进度（脱敏的“税费已取消/已结清”结论与退运销毁节点）共同呈现一致结论 |

> 说明：海关异步回执即使在退运核准（包裹 `RETURNING`）之后才到达，`tryRelease` 也会因“包裹不在放行链路”而拒绝放行；销毁核准则不改变包裹状态，同样由“已核准待执行处置挂起放行”守卫拦截。

## 系统原始需求（产品）

> 开发口岸跨境电商仓包裹查验与退运申报服务，可采用 Java、Spring Boot 和 PostgreSQL。商家把跨境包裹送入口岸仓后，服务记录运单、商品编码、申报价格、收件人、身份证、批次、仓位和物流渠道。申报前，服务检查商品禁限售、价格异常、身份证重复、税费规则、同收件人频次和商家历史风险。海关查验时，仓库人员根据指令开箱拍照、核对商品、补充票据或提交说明；若商品与申报不符、缺少认证、价格明显偏低、收件人资料错误、包裹破损或商家要求退运，服务要把商家、仓库、报关员、客服、海关接口和财务放在同一申报单中处理。查验通过后进入放行和国内派送，查验不通过则进入补材料、扣留、退运或销毁。每个节点的材料、税费、时效、责任和赔付都留在包裹档案里，便于商家和消费者查询。服务还要处理多平台订单合包、保税仓转一般贸易、消费者催件、商家拒绝补材料和海关系统延迟。口岸仓每天批量处理大量包裹，异常包裹必须和正常放行队列分开，否则会影响整批时效。对高风险商家，服务可以提高抽检比例、限制批量申报或要求提前上传完整票据。消费者端只展示必要进度，内部端保留海关查验、商家责任和仓库操作细节。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 3.3（Web / Data JPA / Security / Validation / Actuator） |
| 数据库 | PostgreSQL 16（Flyway 迁移） |
| 认证 | JWT（jjwt 0.12）+ BCrypt 密码散列 |
| 前端 | 静态单页（原生 HTML/JS/CSS，由 Spring Boot 托管） |
| 部署 | 多阶段 Dockerfile（非 root 用户 + HEALTHCHECK）、docker compose |

## 快速开始（Docker 一键部署）

```bash
cp .env.example .env        # 可选：按需修改端口/密钥/海关延迟
docker compose up -d --build
```

- 应用端口通过环境变量 `CC_PUBLISH_PORT` 发布到宿主（compose 写法 `${CC_PUBLISH_PORT}:8080`）。
- PostgreSQL **不发布到宿主端口**，仅在 compose 内部网络以服务名 `db` 访问。
- 查看实际映射端口：`docker compose port app 8080`，然后访问 `http://localhost:<端口>/`。
- 健康检查：`curl http://localhost:<端口>/actuator/health`（容器内 HEALTHCHECK 亦使用该端点）。
- 停止并释放资源：`docker compose down`（加 `-v` 同时清空数据卷）。

### 环境变量（.env）

| 变量 | 默认值 | 说明 |
|---|---|---|
| `CC_PUBLISH_PORT` | `3084` | 宿主机发布端口 |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `portdb` / `portuser` / `portpass123` | 数据库（仅内部网络） |
| `JWT_SECRET` | 演示密钥 | JWT 签名密钥，生产必换 |
| `CUSTOMS_DELAY_SECONDS` | `3` | 模拟海关系统回执延迟（秒），调大可演示“海关系统延迟” |
| `SEED_DEMO_DATA` | `true` | 首次启动是否写入演示数据 |

## 测试账号（逐角色）

| 用户名 | 密码 | 角色 | 权限/可见范围 |
|---|---|---|---|
| `admin` | `admin123` | 管理员 | 全部数据、商家风控调整、海关任务触发 |
| `merchant1` | `merchant123` | 商家（低风险 M001） | 本商家包裹：入仓登记、合包、预检、申报、补材料、退运申请、批次处理 |
| `merchant2` | `merchant123` | 商家（高风险 M002） | 同上；受风控约束：抽检 50%、批次限 20 单、须提前上传发票 |
| `warehouse1` | `warehouse123` | 仓库人员 | 执行查验动作（开箱拍照/核对商品/补充票据/提交说明）、派送、签收、执行退运销毁 |
| `broker1` | `broker123` | 报关员 | 创建/提交申报单、上传材料、提交查验结论 |
| `cs1` | `cs123` | 客服 | 处理消费者催件、登记赔付、代发起退运 |
| `customs1` | `customs123` | 海关接口 | 查验结论、退运/销毁核准、商家风控、模拟海关延迟/触发回执 |
| `finance1` | `finance123` | 财务 | 税费缴纳、赔付审批与支付 |
| `consumer1` | `consumer123` | 消费者（张三） | 只读本人包裹必要进度、催件 |

## 演示数据

首次启动自动写入（`SEED_DEMO_DATA=true`）：

- **商家**：M001 低风险；M002 高风险（抽检 50%、批次限 20、须提前传票、违规 3 次）。
- **包裹**：19 个运单覆盖全状态机 —— `WB20260001` 待预检、`WB20260002` 预检通过、`WB20260003` 海关审单中（启动后约 3 秒自动回执）、`WB20260004` 查验中（含待执行查验指令）、`WB20260005` 已放行、`WB20260006` 派送中（张三，含未处理催件）、`WB20260007` 待补材料（缺认证）、`WB20260008` 预检失败（象牙禁售）、`WB20260009` 高风险商家包裹、`WB20260010` 已签收（张三）、`WB20260011~13` 批次 BATCH001（其中 `WB20260013` 象牙手镯会在批次处理时被隔离）、`WB20260014` **已退运（未缴税随终态作废 VOID）**、`WB20260015` **已销毁（已缴税已退 REFUNDED）**、`WB20260016` **已退运（张三，已缴税已退，消费者可见税费结清结论）**、`WB20260017` **价格复核待传三证（兰蔻面霜低报）**、`WB20260018` **价格复核三证齐备待结论**、`WB20260019` **价格复核“补税”已完成（按 ¥300 重算税费 ¥69.30）**。
- **价格规则**：3 条同品牌同类成交价规则（A2至初奶粉、兰蔻化妆品[重点复核]、Apple 手机），高风险商家 M002 在兰蔻品类列入重点复核名单。
- **参考数据**：7 条税则（跨境综合税率/一般贸易税率/参考价）、7 条禁限售规则。
- **财务**：多条税费记录（待缴/已缴）、1 条待审批赔付。

## 核心业务流程

```
入仓登记 → 申报前检查(6项) → 创建申报单(六方协同) → 提交海关(异步回执)
  → 审单通过 ──→ 税费缴清 → 放行 → 国内派送 → 签收
  → 布控查验 → 仓库执行查验动作 → 海关结论
       ├─ 通过 → 放行 → 派送 → 签收
       └─ 不通过 → 补材料(可重报/商家拒绝→扣留) / 扣留 / 退运 / 销毁
```

- **申报前检查**：禁限售、价格异常（对照税则参考价）、身份证重复（一证多人）、税费规则（超 5000 元个人限值提示转一般贸易）、同收件人 7 天频次、商家历史风险、**同品牌同类历史成交价复核（远低于自动立案价格复核）**，共 7 项。
- **六方协同申报单**：创建时自动纳入商家、仓库、报关员、客服、海关接口、财务；材料、税费、查验、事件全部挂在同一申报单下。
- **高风险商家管控**：提高抽检比例（海关布控概率）、限制批量申报单数、要求申报前上传发票，三者均可在线调整。
- **批次处理**：批次内包裹逐单预检，通过进正常放行队列，失败置 `HOLD` 隔离，互不影响整批时效。
- **海关系统延迟**：`CUSTOMS_DELAY_SECONDS` 控制回执时延；超 SLA（60 秒）未回执的包裹自动标记“海关延迟”；`POST /api/customs/simulate-delay` 可主动注入延迟。
- **消费者视图**：只展示阶段化进度与脱敏时间线；内部端保留查验、责任、赔付全部细节（包裹档案 `GET /api/packages/{id}/archive`）。

## 验证方式（宿主 docker compose up）

```bash
docker compose up -d --build
PORT=$(docker compose port app 8080 | cut -d: -f2)
curl http://localhost:$PORT/actuator/health          # {"status":"UP"}

# 登录拿 token（商家）
TOKEN=$(curl -s -X POST http://localhost:$PORT/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"merchant1","password":"merchant123"}' | jq -r .token)

# 关键业务流：预检 → 申报 → 提交海关 → 回执 → 缴税 → 放行
curl -X POST http://localhost:$PORT/api/packages/1/precheck -H "Authorization: Bearer $TOKEN"
curl -X POST "http://localhost:$PORT/api/declarations?parcelId=1" -H "Authorization: Bearer $TOKEN"
curl -X POST http://localhost:$PORT/api/declarations/<id>/submit -H "Authorization: Bearer $TOKEN"

# 消费者公开查询（无需登录）
curl http://localhost:$PORT/api/public/track/WB20260006
```

浏览器打开 `http://localhost:<端口>/` 可使用完整演示界面（登录后按角色呈现不同工作台）。

## API 一览

| 模块 | 端点 |
|---|---|
| 认证 | `POST /api/auth/login`、`GET /api/auth/me` |
| 包裹 | `POST /api/packages`、`POST /api/packages/consolidate`、`GET /api/packages`、`GET /api/packages/{id}`、`GET /api/packages/{id}/archive`、`POST /api/packages/{id}/precheck`、`POST /api/packages/{id}/convert-trade-mode`、`POST /api/packages/{id}/dispatch`、`POST /api/packages/{id}/deliver` |
| 申报 | `POST /api/declarations?parcelId=`、`GET /api/declarations`、`GET /api/declarations/{id}`、`POST /api/declarations/{id}/submit`、`POST /api/declarations/{id}/materials`、`POST /api/declarations/{id}/refuse-supplement` |
| 查验 | `GET /api/inspections/orders`、`POST /api/inspections/orders/{id}/actions`、`POST /api/inspections/orders/{id}/result` |
| 价格复核 | `GET /api/price-reviews`、`GET /api/price-reviews/{id}`、`POST /api/price-reviews/{id}/evidence`、`POST /api/price-reviews/{id}/decision`、`GET /api/brand-price-rules`、`GET /api/brand-price-rules/my-watches` |
| 退运/销毁 | `POST /api/returns?parcelId=`、`POST /api/returns/{id}/approve|reject|execute` |
| 批次 | `POST /api/batches`、`GET /api/batches/{id}`、`POST /api/batches/{id}/process` |
| 财务 | `GET /api/finance/taxes`、`POST /api/finance/taxes/{id}/pay`、`GET|POST /api/finance/compensations`、`POST /api/finance/compensations/{id}/approve|pay` |
| 风控 | `GET /api/merchants`、`PUT /api/merchants/{id}/risk` |
| 海关模拟 | `POST /api/customs/process-tasks`、`POST /api/customs/simulate-delay` |
| 客服 | `GET /api/urges`、`POST /api/urges/{id}/handle` |
| 消费者 | `GET /api/public/track/{waybill}`（公开）、`GET /api/consumer/packages`、`POST /api/consumer/packages/{waybill}/urge` |

## 项目结构

```
├── Dockerfile                 # 多阶段构建：maven 构建 → jre-alpine 运行（非 root + HEALTHCHECK）
├── docker-compose.yml         # app + db（db 不发布宿主端口）
├── .env.example
├── pom.xml
└── src/main
    ├── java/com/port/inspection
    │   ├── config/            # Security/JWT/数据播种
    │   ├── controller/        # 11 个 REST 控制器
    │   ├── dto/               # 请求 DTO
    │   ├── exception/         # 统一异常
    │   ├── model/             # 19 个实体 + 21 个枚举
    │   ├── repository/        # Spring Data JPA
    │   └── service/           # 预检/申报/海关/查验/退运/批次/财务/风控/催件
    └── resources
        ├── application.yml
        ├── db/migration/      # V1 表结构、V2 税则与禁限售、V3 税费 void_reason、V4 价格异常复核（品牌规则/复核单/重点名单）
        └── static/            # 演示前端（index.html/app.js/style.css）
```
