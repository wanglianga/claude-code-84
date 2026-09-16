package com.port.inspection.model.enums;

/** 申报前检查类型 */
public enum CheckType {
    RESTRICTED_GOODS, PRICE_ANOMALY, ID_CARD_DUPLICATE, TAX_RULE, RECIPIENT_FREQUENCY, MERCHANT_RISK,
    /** 同品牌同类商品历史成交价复核（价格异常复核） */
    BRAND_PRICE
}
