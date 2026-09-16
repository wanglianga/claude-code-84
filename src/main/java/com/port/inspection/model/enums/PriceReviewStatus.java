package com.port.inspection.model.enums;

/** 价格复核单状态 */
public enum PriceReviewStatus {
    /** 待商家上传采购凭证/促销说明/付款记录 */
    AWAITING_EVIDENCE,
    /** 材料齐备，待报关员复核 */
    UNDER_REVIEW,
    /** 复核完成（继续申报/补税/转人工查验） */
    COMPLETED
}
