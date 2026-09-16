package com.port.inspection.service;

import com.port.inspection.dto.Dtos;
import com.port.inspection.exception.BizException;
import com.port.inspection.model.*;
import com.port.inspection.model.enums.*;
import com.port.inspection.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 海关查验：仓库人员按指令执行查验动作，海关/报关员提交查验结论 */
@Service
@RequiredArgsConstructor
public class InspectionService {

    private final InspectionOrderRepository orderRepository;
    private final InspectionActionRepository actionRepository;
    private final DeclarationRepository declarationRepository;
    private final ParcelRepository parcelRepository;
    private final MerchantRepository merchantRepository;
    private final CompensationRepository compensationRepository;
    private final ReturnOrderRepository returnOrderRepository;
    private final DeclarationService declarationService;
    private final ParcelEventService eventService;

    // ---------------- 仓库执行查验动作 ----------------

    @Transactional
    public InspectionAction executeAction(Long orderId, Dtos.InspectionActionRequest req, User actor) {
        InspectionOrder order = getOrder(orderId);
        if (order.getStatus() == InspectionOrderStatus.COMPLETED) {
            throw new BizException("查验指令已完成，无法继续操作");
        }
        InspectionActionType type = InspectionActionType.valueOf(req.actionType());
        if (type == InspectionActionType.OPEN_PHOTO && (req.photoUrl() == null || req.photoUrl().isBlank())) {
            throw new BizException("开箱拍照必须提供照片地址");
        }
        if (order.getStatus() == InspectionOrderStatus.PENDING) {
            order.setStatus(InspectionOrderStatus.IN_PROGRESS);
            orderRepository.save(order);
        }
        InspectionAction a = new InspectionAction();
        a.setOrderId(orderId);
        a.setActionType(type);
        a.setNotes(req.notes());
        a.setPhotoUrl(req.photoUrl());
        a.setOperator(actor.getDisplayName());
        actionRepository.save(a);

        Declaration d = declarationRepository.findById(order.getDeclarationId()).orElseThrow();
        if (d.getStatus() == DeclarationStatus.INSPECTION_REQUIRED) {
            d.setStatus(DeclarationStatus.INSPECTING);
            d.setUpdatedAt(LocalDateTime.now());
            declarationRepository.save(d);
        }
        eventService.record(order.getParcelId(), PackageStatus.INSPECTION, PackageStatus.INSPECTION,
                "海关查验", actor, actionName(type) + (req.notes() != null ? "：" + req.notes() : ""));
        return a;
    }

    // ---------------- 提交查验结论 ----------------

    @Transactional
    public InspectionOrder submitResult(Long orderId, Dtos.InspectionResultRequest req, User actor) {
        InspectionOrder order = getOrder(orderId);
        if (order.getStatus() == InspectionOrderStatus.COMPLETED) {
            throw new BizException("查验指令已完成");
        }
        Declaration d = declarationRepository.findById(order.getDeclarationId()).orElseThrow();
        Parcel p = parcelRepository.findById(order.getParcelId()).orElseThrow();
        Merchant m = merchantRepository.findById(d.getMerchantId()).orElseThrow();

        order.setStatus(InspectionOrderStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now());

        if (Boolean.TRUE.equals(req.pass())) {
            order.setVerdict(InspectionVerdict.PASS);
            orderRepository.save(order);
            d.setStatus(DeclarationStatus.INSPECTION_PASSED);
            d.setUpdatedAt(LocalDateTime.now());
            declarationRepository.save(d);
            eventService.record(p.getId(), PackageStatus.INSPECTION, PackageStatus.INSPECTION,
                    "查验通过", actor, "海关查验通过" + (req.note() != null ? "：" + req.note() : ""));
            declarationService.tryRelease(d.getId(), actor);
            return order;
        }

        // 查验不通过
        order.setVerdict(InspectionVerdict.FAIL);
        FailReason reason = req.failReason() != null ? FailReason.valueOf(req.failReason()) : FailReason.OTHER;
        FailAction action = req.failAction() != null ? FailAction.valueOf(req.failAction()) : FailAction.SUPPLEMENT;
        order.setFailReason(reason);
        order.setFailAction(action);
        orderRepository.save(order);

        m.setViolationCount(m.getViolationCount() + 1);
        merchantRepository.save(m);

        d.setFailReason(reason.name());
        PackageStatus old = p.getStatus();
        String reasonText = reasonName(reason) + "（商家违规累计 " + m.getViolationCount() + " 次）";

        switch (action) {
            case SUPPLEMENT -> {
                d.setStatus(DeclarationStatus.SUPPLEMENT_REQUIRED);
                d.setSupplementNote(req.note() != null ? req.note() : "请补充相关材料后重新提交");
                p.setStatus(PackageStatus.SUPPLEMENT_REQUIRED);
                eventService.record(p.getId(), old, p.getStatus(), "查验不通过", actor,
                        reasonText + "；处置：补充材料");
            }
            case DETAIN -> {
                d.setStatus(DeclarationStatus.FAILED_DETAINED);
                p.setStatus(PackageStatus.DETAINED);
                eventService.record(p.getId(), old, p.getStatus(), "查验不通过", actor,
                        reasonText + "；处置：海关扣留");
            }
            case RETURN, DESTROY -> {
                d.setStatus(action == FailAction.RETURN ? DeclarationStatus.FAILED_RETURN : DeclarationStatus.FAILED_DESTROY);
                p.setStatus(PackageStatus.DETAINED);
                ReturnOrder ro = new ReturnOrder();
                ro.setReturnNo(DeclarationService.genNo(action == FailAction.RETURN ? "RTN" : "DST"));
                ro.setParcelId(p.getId());
                ro.setDeclarationId(d.getId());
                ro.setType(action == FailAction.RETURN ? ReturnType.RETURN : ReturnType.DESTROY);
                ro.setReason(reasonName(reason));
                ro.setRequestedBy("海关查验");
                returnOrderRepository.save(ro);
                eventService.record(p.getId(), old, p.getStatus(), "查验不通过", actor,
                        reasonText + "；处置：" + (action == FailAction.RETURN ? "退运" : "销毁")
                                + "，处置单 " + ro.getReturnNo() + " 待海关核准");
            }
        }
        d.setUpdatedAt(LocalDateTime.now());
        declarationRepository.save(d);
        p.setUpdatedAt(LocalDateTime.now());
        parcelRepository.save(p);

        // 包裹破损 → 自动生成赔付单（责任方：物流）
        if (reason == FailReason.PACKAGE_DAMAGED) {
            Compensation c = new Compensation();
            c.setParcelId(p.getId());
            c.setDeclarationId(d.getId());
            c.setAmount(p.getDeclaredPrice());
            c.setReason("包裹破损赔付（查验发现）");
            c.setResponsibleParty("LOGISTICS");
            c.setCreatedBy(actor.getDisplayName());
            compensationRepository.save(c);
            eventService.record(p.getId(), p.getStatus(), p.getStatus(), "赔付登记", actor,
                    "包裹破损，登记赔付 ¥" + c.getAmount() + "，责任方：物流");
        }
        return order;
    }

    public InspectionOrder getOrder(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> BizException.notFound("查验指令"));
    }

    public List<InspectionOrder> listOrders(String status) {
        if (status != null && !status.isBlank()) {
            return orderRepository.findByStatus(InspectionOrderStatus.valueOf(status));
        }
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<InspectionAction> listActions(Long orderId) {
        return actionRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
    }

    public static String actionName(InspectionActionType t) {
        return switch (t) {
            case OPEN_PHOTO -> "开箱拍照";
            case VERIFY_GOODS -> "核对商品";
            case SUPPLEMENT_DOC -> "补充票据";
            case SUBMIT_EXPLANATION -> "提交说明";
        };
    }

    public static String reasonName(FailReason r) {
        return switch (r) {
            case GOODS_MISMATCH -> "商品与申报不符";
            case MISSING_CERT -> "缺少认证";
            case PRICE_TOO_LOW -> "价格明显偏低";
            case RECIPIENT_INFO_ERROR -> "收件人资料错误";
            case PACKAGE_DAMAGED -> "包裹破损";
            case MERCHANT_RETURN_REQUEST -> "商家要求退运";
            case OTHER -> "其他原因";
        };
    }
}
