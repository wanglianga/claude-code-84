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

/** 财务：税费缴纳、赔付审批与支付 */
@Service
@RequiredArgsConstructor
public class FinanceService {

    private final TaxRecordRepository taxRecordRepository;
    private final CompensationRepository compensationRepository;
    private final ParcelRepository parcelRepository;
    private final DeclarationService declarationService;
    private final ParcelEventService eventService;

    // ---------------- 税费 ----------------

    @Transactional
    public TaxRecord payTax(Long taxId, User actor) {
        TaxRecord t = taxRecordRepository.findById(taxId).orElseThrow(() -> BizException.notFound("税费记录"));
        if (t.getStatus() != TaxStatus.PENDING) {
            throw new BizException("该税费记录状态为 " + t.getStatus() + "，无法缴纳");
        }
        t.setStatus(TaxStatus.PAID);
        t.setPaidBy(actor.getDisplayName());
        t.setPaidAt(LocalDateTime.now());
        taxRecordRepository.save(t);
        eventService.record(t.getParcelId(), null,
                parcelRepository.findById(t.getParcelId()).orElseThrow().getStatus(),
                "税费缴纳", actor, "缴纳" + t.getTaxType() + " ¥" + t.getAmount());
        // 海关已审结且税费缴清 → 放行
        declarationService.tryRelease(t.getDeclarationId(), actor);
        return t;
    }

    public List<TaxRecord> listTaxes(String status) {
        if (status != null && !status.isBlank()) {
            return taxRecordRepository.findByStatus(TaxStatus.valueOf(status));
        }
        return taxRecordRepository.findAllByOrderByCreatedAtDesc();
    }

    // ---------------- 赔付 ----------------

    @Transactional
    public Compensation createCompensation(Dtos.CompensationCreateRequest req, User actor) {
        Parcel p = parcelRepository.findById(req.parcelId()).orElseThrow(() -> BizException.notFound("包裹"));
        Compensation c = new Compensation();
        c.setParcelId(p.getId());
        c.setAmount(req.amount());
        c.setReason(req.reason());
        c.setResponsibleParty(req.responsibleParty());
        c.setCreatedBy(actor.getDisplayName());
        compensationRepository.save(c);
        eventService.record(p.getId(), p.getStatus(), p.getStatus(), "赔付登记", actor,
                "登记赔付 ¥" + req.amount() + "，责任方：" + partyName(req.responsibleParty())
                        + "，原因：" + req.reason());
        return c;
    }

    @Transactional
    public Compensation approve(Long id, User actor) {
        Compensation c = compensationRepository.findById(id).orElseThrow(() -> BizException.notFound("赔付单"));
        if (c.getStatus() != CompensationStatus.PENDING) {
            throw new BizException("赔付单状态为 " + c.getStatus() + "，无法审批");
        }
        c.setStatus(CompensationStatus.APPROVED);
        compensationRepository.save(c);
        eventService.record(c.getParcelId(), null,
                parcelRepository.findById(c.getParcelId()).orElseThrow().getStatus(),
                "赔付审批", actor, "赔付 ¥" + c.getAmount() + " 审批通过");
        return c;
    }

    @Transactional
    public Compensation pay(Long id, User actor) {
        Compensation c = compensationRepository.findById(id).orElseThrow(() -> BizException.notFound("赔付单"));
        if (c.getStatus() != CompensationStatus.APPROVED) {
            throw new BizException("赔付单须先审批通过");
        }
        c.setStatus(CompensationStatus.PAID);
        c.setPaidAt(LocalDateTime.now());
        compensationRepository.save(c);
        eventService.record(c.getParcelId(), null,
                parcelRepository.findById(c.getParcelId()).orElseThrow().getStatus(),
                "赔付支付", actor, "赔付 ¥" + c.getAmount() + " 已支付");
        return c;
    }

    public List<Compensation> listCompensations(String status) {
        if (status != null && !status.isBlank()) {
            return compensationRepository.findByStatus(CompensationStatus.valueOf(status));
        }
        return compensationRepository.findAllByOrderByCreatedAtDesc();
    }

    public static String partyName(String party) {
        return switch (party) {
            case "MERCHANT" -> "商家";
            case "WAREHOUSE" -> "仓库";
            case "LOGISTICS" -> "物流";
            case "PLATFORM" -> "平台";
            default -> party;
        };
    }
}
