package com.port.inspection.model.enums;

/** 税费状态 */
public enum TaxStatus {
    PENDING,
    PAID,
    REFUNDED,
    /** 作废（退运/销毁处置完成后，未缴税费转为不可缴纳的取消状态） */
    VOID
}
