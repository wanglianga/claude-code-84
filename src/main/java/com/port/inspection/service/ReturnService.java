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
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/** 退运/销毁处置：申请 → 海关核准 → 仓库执行 */
@Service
@RequiredArgsConstructor
public class ReturnService {

    private final ReturnOrderRepository returnOrderRepository;
    private final ParcelRepository parcelRepository;
    private final DeclarationRepository declarationRepository;
    private final TaxRecordRepository taxRecordRepository;
    private final ParcelEventService eventService;

    /** 商家/客服发起退运申请 */
    @Transactional
    public ReturnOrder request(Long parcelId, Dtos.ReturnApplyRequest req, User actor) {
        Parcel p = parcelRepository.findById(parcelId).orElseThrow(() -> BizException.notFound("包裹"));
        if (actor.getRole() == Role.MERCHANT && !Objects.equals(p.getMerchantId(), actor.getMerchantId())) {
            throw BizException.forbidden("只能操作本商家的包裹");
        }
        EnumSet<PackageStatus> allowed = EnumSet.of(PackageStatus.RECEIVED, PackageStatus.PRECHECK_PASSED,
                PackageStatus.PRECHECK_FAILED, PackageStatus.HOLD, PackageStatus.DECLARED,
                PackageStatus.CUSTOMS_REVIEW, PackageStatus.SUPPLEMENT_REQUIRED, PackageStatus.DETAINED);
        if (!allowed.contains(p.getStatus())) {
            throw new BizException("当前状态不允许申请退运/销毁: " + p.getStatus());
        }
        ReturnType type = ReturnType.valueOf(req.type());
        ReturnOrder ro = new ReturnOrder();
        ro.setReturnNo(DeclarationService.genNo(type == ReturnType.RETURN ? "RTN" : "DST"));
        ro.setParcelId(parcelId);
        ro.setType(type);
        ro.setReason(req.reason());
        ro.setRequestedBy(actor.getDisplayName());
        declarationRepository.findByParcelId(parcelId).stream().findFirst()
                .ifPresent(d -> ro.setDeclarationId(d.getId()));
        returnOrderRepository.save(ro);
        eventService.record(parcelId, p.getStatus(), p.getStatus(),
                type == ReturnType.RETURN ? "退运申请" : "销毁申请", actor,
                "原因：" + req.reason() + "，处置单 " + ro.getReturnNo() + " 待海关核准");
        return ro;
    }

    /** 海关核准 */
    @Transactional
    public ReturnOrder approve(Long id, User actor) {
        ReturnOrder ro = getOrder(id);
        if (ro.getStatus() != ReturnStatus.REQUESTED) {
            throw new BizException("该处置单不在待核准状态");
        }
        ro.setStatus(ReturnStatus.APPROVED);
        ro.setApprovedBy(actor.getDisplayName());
        returnOrderRepository.save(ro);
        Parcel p = parcelRepository.findById(ro.getParcelId()).orElseThrow();
        if (ro.getType() == ReturnType.RETURN) {
            PackageStatus old = p.getStatus();
            p.setStatus(PackageStatus.RETURNING);
            p.setUpdatedAt(LocalDateTime.now());
            parcelRepository.save(p);
            eventService.record(p.getId(), old, p.getStatus(), "退运核准", actor,
                    "海关核准退运，处置单 " + ro.getReturnNo());
        } else {
            eventService.record(p.getId(), p.getStatus(), p.getStatus(), "销毁核准", actor,
                    "海关核准销毁，处置单 " + ro.getReturnNo());
        }
        return ro;
    }

    /** 海关驳回 */
    @Transactional
    public ReturnOrder reject(Long id, User actor) {
        ReturnOrder ro = getOrder(id);
        if (ro.getStatus() != ReturnStatus.REQUESTED) {
            throw new BizException("该处置单不在待核准状态");
        }
        ro.setStatus(ReturnStatus.REJECTED);
        ro.setApprovedBy(actor.getDisplayName());
        returnOrderRepository.save(ro);
        eventService.record(ro.getParcelId(), null, parcelRepository.findById(ro.getParcelId()).orElseThrow().getStatus(),
                "处置驳回", actor, "海关驳回处置单 " + ro.getReturnNo());
        return ro;
    }

    /** 仓库执行退运/销毁 */
    @Transactional
    public ReturnOrder execute(Long id, User actor) {
        ReturnOrder ro = getOrder(id);
        if (ro.getStatus() != ReturnStatus.APPROVED) {
            throw new BizException("处置单须先经海关核准");
        }
        ro.setStatus(ReturnStatus.COMPLETED);
        ro.setCompletedAt(LocalDateTime.now());
        returnOrderRepository.save(ro);

        Parcel p = parcelRepository.findById(ro.getParcelId()).orElseThrow();
        PackageStatus old = p.getStatus();
        PackageStatus done = ro.getType() == ReturnType.RETURN ? PackageStatus.RETURNED : PackageStatus.DESTROYED;
        p.setStatus(done);
        p.setUpdatedAt(LocalDateTime.now());
        parcelRepository.save(p);

        // 已缴税费退还
        for (TaxRecord t : taxRecordRepository.findByParcelId(p.getId())) {
            if (t.getStatus() == TaxStatus.PAID) {
                t.setStatus(TaxStatus.REFUNDED);
                taxRecordRepository.save(t);
                eventService.record(p.getId(), done, done, "税费退还", actor,
                        "处置完成，已缴税费 ¥" + t.getAmount() + " 退回");
            }
        }
        eventService.record(p.getId(), old, done,
                ro.getType() == ReturnType.RETURN ? "退运执行" : "销毁执行", actor,
                (ro.getType() == ReturnType.RETURN ? "包裹已退运出境" : "包裹已按海关要求销毁")
                        + "，处置单 " + ro.getReturnNo());
        return ro;
    }

    public ReturnOrder getOrder(Long id) {
        return returnOrderRepository.findById(id).orElseThrow(() -> BizException.notFound("处置单"));
    }

    public List<ReturnOrder> list(String status) {
        if (status != null && !status.isBlank()) {
            return returnOrderRepository.findByStatus(ReturnStatus.valueOf(status));
        }
        return returnOrderRepository.findAllByOrderByCreatedAtDesc();
    }
}
