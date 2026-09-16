package com.port.inspection.model.enums;

/** 价格复核结论 */
public enum PriceReviewDecision {
    /** 价格合理，继续申报 */
    PASS,
    /** 价格偏低，按认定成交价补税 */
    SUPPLEMENT_TAX,
    /** 转人工查验 */
    MANUAL_INSPECTION
}
