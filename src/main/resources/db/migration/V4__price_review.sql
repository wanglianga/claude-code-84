-- V4 申报价格异常复核：品牌历史成交价规则、价格复核单、商家品牌重点复核、包裹品牌与计税价
ALTER TABLE packages ADD COLUMN brand VARCHAR(64);
ALTER TABLE packages ADD COLUMN taxable_price NUMERIC(12, 2);

-- 同品牌同类商品（品牌 + HS 编码）历史成交价规则，由价格复核结论持续沉淀
CREATE TABLE brand_price_rules (
    id                     BIGSERIAL PRIMARY KEY,
    brand                  VARCHAR(64)  NOT NULL,
    hs_code                VARCHAR(20)  NOT NULL,
    category               VARCHAR(64),
    avg_deal_price         NUMERIC(12,2) NOT NULL,
    deal_count             INT          NOT NULL DEFAULT 1,
    -- 申报单价低于历史均价的该比例即判定“远低于”（默认 60%）
    low_report_threshold   NUMERIC(4,2) NOT NULL DEFAULT 0.60,
    -- 低于该比例给出预警（默认 80%）
    warn_threshold         NUMERIC(4,2) NOT NULL DEFAULT 0.80,
    -- 经复核存疑（补税/转人工）后置真，后续同类申报更严格
    review_flag            BOOLEAN      NOT NULL DEFAULT FALSE,
    last_review_decision   VARCHAR(20),
    last_merchant_id       BIGINT,
    created_at             TIMESTAMP NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_brand_hs UNIQUE (brand, hs_code)
);

-- 价格复核单：立案 → 商家上传采购凭证/促销说明/付款记录 → 报关员复核结论
CREATE TABLE price_review_orders (
    id                    BIGSERIAL PRIMARY KEY,
    review_no             VARCHAR(64)  NOT NULL UNIQUE,
    parcel_id             BIGINT       NOT NULL REFERENCES packages(id),
    declaration_id        BIGINT,
    merchant_id           BIGINT       NOT NULL,
    brand                 VARCHAR(64)  NOT NULL,
    hs_code               VARCHAR(20)  NOT NULL,
    declared_price        NUMERIC(12,2) NOT NULL,
    reference_avg_price   NUMERIC(12,2),
    status                VARCHAR(20)  NOT NULL DEFAULT 'AWAITING_EVIDENCE',
    decision              VARCHAR(20),
    revised_unit_price    NUMERIC(12,2),
    decision_note         VARCHAR(512),
    requested_by          VARCHAR(64),
    decided_by            VARCHAR(64),
    created_at            TIMESTAMP NOT NULL DEFAULT now(),
    decided_at            TIMESTAMP
);
CREATE INDEX idx_price_review_parcel ON price_review_orders(parcel_id);
CREATE INDEX idx_price_review_status ON price_review_orders(status);

-- 商家 × 品牌品类 重点复核名单（复核为补税/转人工后纳入，后续同类商品更严格预审）
CREATE TABLE merchant_price_watches (
    id               BIGSERIAL PRIMARY KEY,
    merchant_id      BIGINT      NOT NULL,
    brand            VARCHAR(64) NOT NULL,
    hs_code          VARCHAR(20) NOT NULL,
    stricter_review  BOOLEAN     NOT NULL DEFAULT TRUE,
    last_decision    VARCHAR(20),
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_merchant_brand_hs UNIQUE (merchant_id, brand, hs_code)
);
