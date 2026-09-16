package com.port.inspection.model.enums;

/** 申报单状态 */
public enum DeclarationStatus {
    DRAFT, SUBMITTED, ACCEPTED, INSPECTION_REQUIRED, INSPECTING, INSPECTION_PASSED, RELEASED, SUPPLEMENT_REQUIRED, FAILED_DETAINED, FAILED_RETURN, FAILED_DESTROY,
    /** 终态：处置单执行完成，包裹已退运，申报单随同一包裹终态结案，不再放行 */
    RETURNED,
    /** 终态：处置单执行完成，包裹已销毁，申报单随同一包裹终态结案，不再放行 */
    DESTROYED,
    CLOSED
}
