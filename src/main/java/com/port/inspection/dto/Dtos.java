package com.port.inspection.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/** 请求 DTO 集合 */
public final class Dtos {

    private Dtos() {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record ParcelCreateRequest(
            @NotBlank String waybillNo,
            @NotBlank String hsCode,
            String brand,
            @NotBlank String goodsName,
            @NotNull @DecimalMin("0.01") BigDecimal declaredPrice,
            @NotNull @Min(1) Integer quantity,
            @NotBlank String recipientName,
            @NotBlank String recipientIdCard,
            @NotBlank String recipientPhone,
            String batchNo,
            String warehouseLocation,
            @NotBlank String logisticsChannel,
            String tradeMode
    ) {}

    public record OrderItem(@NotBlank String platform, @NotBlank String orderNo) {}

    public record ConsolidateRequest(
            @NotBlank String waybillNo,
            @NotBlank String hsCode,
            String brand,
            @NotBlank String goodsName,
            @NotNull @DecimalMin("0.01") BigDecimal declaredPrice,
            @NotNull @Min(1) Integer quantity,
            @NotBlank String recipientName,
            @NotBlank String recipientIdCard,
            @NotBlank String recipientPhone,
            String batchNo,
            String warehouseLocation,
            @NotBlank String logisticsChannel,
            String tradeMode,
            @NotNull @Size(min = 2, message = "合包至少需要两个平台订单") List<OrderItem> orders
    ) {}

    public record MaterialUploadRequest(
            @NotBlank String materialType,
            @NotBlank String fileName,
            String fileUrl
    ) {}

    public record InspectionActionRequest(
            @NotBlank String actionType,
            String notes,
            String photoUrl
    ) {}

    public record InspectionResultRequest(
            @NotNull Boolean pass,
            String failReason,
            String failAction,
            String note
    ) {}

    public record ReturnApplyRequest(
            @NotBlank String type,
            @NotBlank String reason
    ) {}

    public record UrgeRequest(@NotBlank String message) {}

    public record HandleUrgeRequest(@NotBlank String note) {}

    public record BatchCreateRequest(@NotBlank String batchNo) {}

    public record MerchantRiskRequest(
            @NotBlank String riskLevel,
            @NotNull @Min(0) @Max(100) Integer inspectionRatio,
            @NotNull @Min(1) Integer batchLimit,
            @NotNull Boolean requireAdvanceDocs
    ) {}

    public record CompensationCreateRequest(
            @NotNull Long parcelId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank String reason,
            @NotBlank String responsibleParty
    ) {}

    public record SupplementRequest(String note) {}

    public record SimulateDelayRequest(@NotNull @Min(0) Integer seconds) {}

    /** 价格复核凭证上传复用 MaterialUploadRequest（materialType 限定三证） */

    /** 报关员价格复核结论 */
    public record PriceReviewDecisionRequest(
            @NotBlank String decision,
            java.math.BigDecimal revisedUnitPrice,
            String note
    ) {}
}
