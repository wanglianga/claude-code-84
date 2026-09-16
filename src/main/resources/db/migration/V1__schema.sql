-- V1 数据库结构：口岸跨境电商仓包裹查验与退运申报服务
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password      VARCHAR(100) NOT NULL,
    display_name  VARCHAR(64)  NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    merchant_id   BIGINT,
    id_card       VARCHAR(32),
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE merchants (
    id                   BIGSERIAL PRIMARY KEY,
    code                 VARCHAR(32)  NOT NULL UNIQUE,
    name                 VARCHAR(128) NOT NULL,
    risk_level           VARCHAR(10)  NOT NULL DEFAULT 'LOW',
    inspection_ratio     INT          NOT NULL DEFAULT 10,
    batch_limit          INT          NOT NULL DEFAULT 100,
    require_advance_docs BOOLEAN      NOT NULL DEFAULT FALSE,
    violation_count      INT          NOT NULL DEFAULT 0,
    total_declarations   INT          NOT NULL DEFAULT 0,
    created_at           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE packages (
    id                 BIGSERIAL PRIMARY KEY,
    waybill_no         VARCHAR(64)  NOT NULL UNIQUE,
    merchant_id        BIGINT       NOT NULL REFERENCES merchants(id),
    hs_code            VARCHAR(20)  NOT NULL,
    goods_name         VARCHAR(128) NOT NULL,
    declared_price     NUMERIC(12,2) NOT NULL,
    quantity           INT          NOT NULL DEFAULT 1,
    recipient_name     VARCHAR(64)  NOT NULL,
    recipient_id_card  VARCHAR(32)  NOT NULL,
    recipient_phone    VARCHAR(20)  NOT NULL,
    batch_no           VARCHAR(64),
    warehouse_location VARCHAR(32),
    logistics_channel  VARCHAR(32)  NOT NULL,
    package_type       VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    trade_mode         VARCHAR(20)  NOT NULL DEFAULT 'BONDED',
    status             VARCHAR(32)  NOT NULL DEFAULT 'RECEIVED',
    customs_delayed    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_packages_merchant   ON packages(merchant_id);
CREATE INDEX idx_packages_id_card    ON packages(recipient_id_card);
CREATE INDEX idx_packages_batch      ON packages(batch_no);
CREATE INDEX idx_packages_status     ON packages(status);

CREATE TABLE package_orders (
    id         BIGSERIAL PRIMARY KEY,
    parcel_id  BIGINT      NOT NULL REFERENCES packages(id),
    platform   VARCHAR(32) NOT NULL,
    order_no   VARCHAR(64) NOT NULL
);
CREATE INDEX idx_package_orders_parcel ON package_orders(parcel_id);

CREATE TABLE package_events (
    id          BIGSERIAL PRIMARY KEY,
    parcel_id   BIGINT      NOT NULL REFERENCES packages(id),
    from_status VARCHAR(32),
    to_status   VARCHAR(32) NOT NULL,
    node        VARCHAR(64) NOT NULL,
    actor       VARCHAR(64),
    actor_role  VARCHAR(20),
    remark      VARCHAR(1024),
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_package_events_parcel ON package_events(parcel_id);

CREATE TABLE precheck_results (
    id         BIGSERIAL PRIMARY KEY,
    parcel_id  BIGINT      NOT NULL REFERENCES packages(id),
    check_type VARCHAR(32) NOT NULL,
    level      VARCHAR(10) NOT NULL,
    message    VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_precheck_parcel ON precheck_results(parcel_id);

CREATE TABLE declarations (
    id               BIGSERIAL PRIMARY KEY,
    declaration_no   VARCHAR(64)   NOT NULL UNIQUE,
    parcel_id        BIGINT        NOT NULL REFERENCES packages(id),
    merchant_id      BIGINT        NOT NULL REFERENCES merchants(id),
    status           VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    tax_amount       NUMERIC(12,2),
    fail_reason      VARCHAR(64),
    supplement_note  VARCHAR(512),
    submitted_by     VARCHAR(64),
    submitted_at     TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_declarations_parcel ON declarations(parcel_id);

CREATE TABLE declaration_participants (
    id             BIGSERIAL PRIMARY KEY,
    declaration_id BIGINT      NOT NULL REFERENCES declarations(id),
    user_id        BIGINT      NOT NULL REFERENCES users(id),
    role           VARCHAR(20) NOT NULL,
    display_name   VARCHAR(64),
    CONSTRAINT uk_decl_participant UNIQUE (declaration_id, role)
);

CREATE TABLE materials (
    id             BIGSERIAL PRIMARY KEY,
    parcel_id      BIGINT      NOT NULL REFERENCES packages(id),
    declaration_id BIGINT      REFERENCES declarations(id),
    material_type  VARCHAR(32) NOT NULL,
    file_name      VARCHAR(255) NOT NULL,
    file_url       VARCHAR(512),
    uploaded_by    VARCHAR(64),
    created_at     TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_materials_parcel ON materials(parcel_id);

CREATE TABLE tax_records (
    id             BIGSERIAL PRIMARY KEY,
    declaration_id BIGINT         NOT NULL REFERENCES declarations(id),
    parcel_id      BIGINT         NOT NULL REFERENCES packages(id),
    tax_type       VARCHAR(32)    NOT NULL,
    amount         NUMERIC(12,2)  NOT NULL,
    status         VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    paid_by        VARCHAR(64),
    paid_at        TIMESTAMP,
    created_at     TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_tax_declaration ON tax_records(declaration_id);

CREATE TABLE inspection_orders (
    id             BIGSERIAL PRIMARY KEY,
    order_no       VARCHAR(64) NOT NULL UNIQUE,
    declaration_id BIGINT      NOT NULL REFERENCES declarations(id),
    parcel_id      BIGINT      NOT NULL REFERENCES packages(id),
    instruction    VARCHAR(255) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    verdict        VARCHAR(20),
    fail_reason    VARCHAR(64),
    fail_action    VARCHAR(20),
    issued_by      VARCHAR(64),
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    completed_at   TIMESTAMP
);
CREATE INDEX idx_inspection_parcel ON inspection_orders(parcel_id);

CREATE TABLE inspection_actions (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT      NOT NULL REFERENCES inspection_orders(id),
    action_type VARCHAR(32) NOT NULL,
    notes       VARCHAR(512),
    photo_url   VARCHAR(512),
    operator    VARCHAR(64),
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE compensations (
    id                 BIGSERIAL PRIMARY KEY,
    parcel_id          BIGINT         NOT NULL REFERENCES packages(id),
    declaration_id     BIGINT,
    amount             NUMERIC(12,2)  NOT NULL,
    reason             VARCHAR(255)   NOT NULL,
    responsible_party  VARCHAR(20)    NOT NULL,
    status             VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    created_by         VARCHAR(64),
    created_at         TIMESTAMP NOT NULL DEFAULT now(),
    paid_at            TIMESTAMP
);

CREATE TABLE consumer_urges (
    id            BIGSERIAL PRIMARY KEY,
    parcel_id     BIGINT       NOT NULL REFERENCES packages(id),
    consumer_name VARCHAR(64),
    message       VARCHAR(512),
    status        VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    handled_by    VARCHAR(64),
    handle_note   VARCHAR(512),
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    handled_at    TIMESTAMP
);

CREATE TABLE return_orders (
    id           BIGSERIAL PRIMARY KEY,
    return_no    VARCHAR(64)  NOT NULL UNIQUE,
    parcel_id    BIGINT       NOT NULL REFERENCES packages(id),
    declaration_id BIGINT,
    type         VARCHAR(20)  NOT NULL,
    reason       VARCHAR(255) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED',
    requested_by VARCHAR(64),
    approved_by  VARCHAR(64),
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    completed_at TIMESTAMP
);

CREATE TABLE batches (
    id             BIGSERIAL PRIMARY KEY,
    batch_no       VARCHAR(64) NOT NULL UNIQUE,
    merchant_id    BIGINT      NOT NULL REFERENCES merchants(id),
    status         VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    total_count    INT         NOT NULL DEFAULT 0,
    normal_count   INT         NOT NULL DEFAULT 0,
    abnormal_count INT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    processed_at   TIMESTAMP
);

CREATE TABLE restricted_goods (
    id          BIGSERIAL PRIMARY KEY,
    hs_code     VARCHAR(20),
    keyword     VARCHAR(64),
    rule_type   VARCHAR(20) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE tax_rules (
    id               BIGSERIAL PRIMARY KEY,
    hs_code          VARCHAR(20)    NOT NULL UNIQUE,
    category         VARCHAR(64)    NOT NULL,
    tax_rate         NUMERIC(5,4)   NOT NULL,
    general_tax_rate NUMERIC(5,4)   NOT NULL,
    ref_price        NUMERIC(12,2)  NOT NULL
);

CREATE TABLE customs_tasks (
    id             BIGSERIAL PRIMARY KEY,
    declaration_id BIGINT      NOT NULL REFERENCES declarations(id),
    task_type      VARCHAR(32) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    execute_after  TIMESTAMP   NOT NULL,
    result         VARCHAR(32),
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    processed_at   TIMESTAMP
);
CREATE INDEX idx_customs_tasks_due ON customs_tasks(status, execute_after);
