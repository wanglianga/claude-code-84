package com.port.inspection.model.enums;

/** 材料类型：发票/认证/照片/说明/价格复核三证/其他 */
public enum MaterialType {
    INVOICE, CERT, PHOTO, EXPLANATION, OTHER,
    /** 采购凭证 */
    PURCHASE_PROOF,
    /** 促销说明 */
    PROMO_EXPLANATION,
    /** 付款记录 */
    PAYMENT_RECORD
}
