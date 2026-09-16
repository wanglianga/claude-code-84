# 口岸跨境电商仓包裹查验与退运申报服务

基于 **Java 17 + Spring Boot 3 + PostgreSQL 16** 的口岸跨境电商仓包裹查验与退运申报服务。覆盖包裹入仓、申报前检查、六方协同申报、海关查验、补材料/扣留/退运/销毁、放行派送、税费赔付、批次处理、商家风控与消费者进度查询全链路。

## 原始需求

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
- **包裹**：13 个运单覆盖全状态机 —— `WB20260001` 待预检、`WB20260002` 预检通过、`WB20260003` 海关审单中（启动后约 3 秒自动回执）、`WB20260004` 查验中（含待执行查验指令）、`WB20260005` 已放行、`WB20260006` 派送中（张三，含未处理催件）、`WB20260007` 待补材料（缺认证）、`WB20260008` 预检失败（象牙禁售）、`WB20260009` 高风险商家包裹、`WB20260010` 已签收（张三）、`WB20260011~13` 批次 BATCH001（其中 `WB20260013` 象牙手镯会在批次处理时被隔离）。
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

- **申报前检查**：禁限售、价格异常（对照税则参考价）、身份证重复（一证多人）、税费规则（超 5000 元个人限值提示转一般贸易）、同收件人 7 天频次、商家历史风险。
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
        ├── db/migration/      # V1 表结构、V2 税则与禁限售参考数据
        └── static/            # 演示前端（index.html/app.js/style.css）
```
