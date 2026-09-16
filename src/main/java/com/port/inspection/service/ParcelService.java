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
import java.util.*;
import java.util.stream.Collectors;

/** 包裹：入仓登记、合包、贸易模式转换、派送、档案、消费者视图 */
@Service
@RequiredArgsConstructor
public class ParcelService {

    private final ParcelRepository parcelRepository;
    private final ParcelOrderRepository parcelOrderRepository;
    private final ParcelEventRepository eventRepository;
    private final PrecheckResultRepository precheckRepository;
    private final DeclarationRepository declarationRepository;
    private final DeclarationParticipantRepository participantRepository;
    private final MaterialRepository materialRepository;
    private final TaxRecordRepository taxRecordRepository;
    private final InspectionOrderRepository inspectionOrderRepository;
    private final InspectionActionRepository inspectionActionRepository;
    private final CompensationRepository compensationRepository;
    private final ConsumerUrgeRepository urgeRepository;
    private final ReturnOrderRepository returnOrderRepository;
    private final PriceReviewOrderRepository priceReviewOrderRepository;
    private final BrandPriceRuleRepository brandPriceRuleRepository;
    private final MerchantRepository merchantRepository;
    private final ParcelEventService eventService;

    // ---------------- 入仓登记 ----------------

    @Transactional
    public Parcel createParcel(Dtos.ParcelCreateRequest req, User merchant) {
        if (parcelRepository.findByWaybillNo(req.waybillNo()).isPresent()) {
            throw new BizException("运单号已存在: " + req.waybillNo());
        }
        Parcel p = new Parcel();
        p.setWaybillNo(req.waybillNo());
        p.setMerchantId(merchant.getMerchantId());
        p.setHsCode(req.hsCode());
        p.setBrand(req.brand());
        p.setGoodsName(req.goodsName());
        p.setDeclaredPrice(req.declaredPrice());
        p.setQuantity(req.quantity());
        p.setRecipientName(req.recipientName());
        p.setRecipientIdCard(req.recipientIdCard());
        p.setRecipientPhone(req.recipientPhone());
        p.setBatchNo(req.batchNo());
        p.setWarehouseLocation(req.warehouseLocation());
        p.setLogisticsChannel(req.logisticsChannel());
        if (req.tradeMode() != null && !req.tradeMode().isBlank()) {
            p.setTradeMode(TradeMode.valueOf(req.tradeMode()));
        }
        parcelRepository.save(p);
        eventService.record(p.getId(), null, PackageStatus.RECEIVED, "入仓登记", merchant,
                "包裹入仓，仓位 " + (req.warehouseLocation() == null ? "待分配" : req.warehouseLocation())
                        + "，物流渠道 " + req.logisticsChannel());
        return p;
    }

    // ---------------- 多平台订单合包 ----------------

    @Transactional
    public Parcel consolidate(Dtos.ConsolidateRequest req, User merchant) {
        Dtos.ParcelCreateRequest base = new Dtos.ParcelCreateRequest(
                req.waybillNo(), req.hsCode(), req.brand(), req.goodsName(), req.declaredPrice(), req.quantity(),
                req.recipientName(), req.recipientIdCard(), req.recipientPhone(), req.batchNo(),
                req.warehouseLocation(), req.logisticsChannel(), req.tradeMode());
        Parcel p = createParcel(base, merchant);
        p.setPackageType(PackageType.CONSOLIDATED);
        parcelRepository.save(p);
        for (Dtos.OrderItem item : req.orders()) {
            ParcelOrder o = new ParcelOrder();
            o.setParcelId(p.getId());
            o.setPlatform(item.platform());
            o.setOrderNo(item.orderNo());
            parcelOrderRepository.save(o);
        }
        eventService.record(p.getId(), PackageStatus.RECEIVED, PackageStatus.RECEIVED, "多平台合包", merchant,
                "合并 " + req.orders().size() + " 个平台订单："
                        + req.orders().stream().map(i -> i.platform() + "/" + i.orderNo())
                        .collect(Collectors.joining("，")));
        return p;
    }

    // ---------------- 保税仓转一般贸易 ----------------

    @Transactional
    public Parcel convertTradeMode(Long parcelId, User actor) {
        Parcel p = getAndCheckOwner(parcelId, actor);
        if (p.getTradeMode() != TradeMode.BONDED) {
            throw new BizException("仅保税仓模式包裹可转一般贸易");
        }
        EnumSet<PackageStatus> allowed = EnumSet.of(PackageStatus.RECEIVED, PackageStatus.PRECHECK_PASSED,
                PackageStatus.PRECHECK_FAILED, PackageStatus.SUPPLEMENT_REQUIRED);
        if (!allowed.contains(p.getStatus())) {
            throw new BizException("当前状态不允许转一般贸易: " + p.getStatus());
        }
        p.setTradeMode(TradeMode.GENERAL);
        p.setUpdatedAt(LocalDateTime.now());
        parcelRepository.save(p);
        eventService.record(p.getId(), p.getStatus(), p.getStatus(), "保税转一般贸易", actor,
                "贸易模式由保税仓(B2B2C)转为一般贸易，将按一般贸易税率重新计税");
        return p;
    }

    // ---------------- 派送 ----------------

    @Transactional
    public Parcel dispatch(Long parcelId, User actor) {
        Parcel p = parcelRepository.findById(parcelId).orElseThrow(() -> BizException.notFound("包裹"));
        if (p.getStatus() != PackageStatus.RELEASED) {
            throw new BizException("仅已放行包裹可安排派送，当前状态: " + p.getStatus());
        }
        p.setStatus(PackageStatus.DELIVERING);
        p.setUpdatedAt(LocalDateTime.now());
        parcelRepository.save(p);
        eventService.record(p.getId(), PackageStatus.RELEASED, PackageStatus.DELIVERING, "放行派送", actor,
                "海关放行，转国内物流派送，渠道 " + p.getLogisticsChannel());
        return p;
    }

    @Transactional
    public Parcel deliver(Long parcelId, User actor) {
        Parcel p = parcelRepository.findById(parcelId).orElseThrow(() -> BizException.notFound("包裹"));
        if (p.getStatus() != PackageStatus.DELIVERING) {
            throw new BizException("包裹不在派送中，当前状态: " + p.getStatus());
        }
        p.setStatus(PackageStatus.DELIVERED);
        p.setUpdatedAt(LocalDateTime.now());
        parcelRepository.save(p);
        eventService.record(p.getId(), PackageStatus.DELIVERING, PackageStatus.DELIVERED, "签收", actor,
                "收件人已签收");
        return p;
    }

    // ---------------- 查询 ----------------

    public List<Parcel> listForUser(User user, String status) {
        List<Parcel> list;
        if (user.getRole() == Role.MERCHANT) {
            list = parcelRepository.findByMerchantIdOrderByCreatedAtDesc(user.getMerchantId());
        } else if (user.getRole() == Role.CONSUMER) {
            list = parcelRepository.findByRecipientIdCardOrderByCreatedAtDesc(user.getIdCard());
        } else {
            list = parcelRepository.findAllByOrderByCreatedAtDesc();
        }
        if (status != null && !status.isBlank()) {
            PackageStatus ps = PackageStatus.valueOf(status);
            list = list.stream().filter(p -> p.getStatus() == ps).collect(Collectors.toList());
        }
        return list;
    }

    public Parcel getAndCheckOwner(Long parcelId, User user) {
        // 消费者端只展示必要进度，内部详情/档案不对消费者开放
        if (user.getRole() == Role.CONSUMER) {
            throw BizException.forbidden("消费者请使用进度查询接口");
        }
        Parcel p = parcelRepository.findById(parcelId).orElseThrow(() -> BizException.notFound("包裹"));
        if (user.getRole() == Role.MERCHANT && !Objects.equals(p.getMerchantId(), user.getMerchantId())) {
            throw BizException.forbidden("只能操作本商家的包裹");
        }
        return p;
    }

    // ---------------- 包裹档案（内部全量视图） ----------------

    public Map<String, Object> archive(Long parcelId, User user) {
        Parcel p = getAndCheckOwner(parcelId, user);
        Map<String, Object> archive = new LinkedHashMap<>();
        archive.put("parcel", p);
        archive.put("merchant", merchantRepository.findById(p.getMerchantId()).orElse(null));
        archive.put("orders", parcelOrderRepository.findByParcelId(parcelId));
        archive.put("events", eventRepository.findByParcelIdOrderByCreatedAtAsc(parcelId));
        archive.put("precheckResults", precheckRepository.findByParcelId(parcelId));
        archive.put("materials", materialRepository.findByParcelId(parcelId));
        archive.put("taxes", taxRecordRepository.findByParcelId(parcelId));
        archive.put("compensations", compensationRepository.findByParcelId(parcelId));
        archive.put("urges", urgeRepository.findByParcelId(parcelId));
        archive.put("returnOrders", returnOrderRepository.findByParcelId(parcelId));
        archive.put("priceReviews", priceReviewOrderRepository.findByParcelIdOrderByCreatedAtDesc(parcelId));
        if (p.getBrand() != null && !p.getBrand().isBlank()) {
            archive.put("brandPriceRule",
                    brandPriceRuleRepository.findByBrandAndHsCode(p.getBrand().trim(), p.getHsCode()).orElse(null));
        }

        List<Map<String, Object>> declarations = new ArrayList<>();
        for (Declaration d : declarationRepository.findByParcelId(parcelId)) {
            Map<String, Object> dv = new LinkedHashMap<>();
            dv.put("declaration", d);
            dv.put("participants", participantRepository.findByDeclarationId(d.getId()));
            List<Map<String, Object>> inspections = new ArrayList<>();
            for (InspectionOrder o : inspectionOrderRepository.findByDeclarationId(d.getId())) {
                Map<String, Object> ov = new LinkedHashMap<>();
                ov.put("order", o);
                ov.put("actions", inspectionActionRepository.findByOrderIdOrderByCreatedAtAsc(o.getId()));
                inspections.add(ov);
            }
            dv.put("inspections", inspections);
            declarations.add(dv);
        }
        archive.put("declarations", declarations);
        return archive;
    }

    // ---------------- 消费者视图（只展示必要进度） ----------------

    public Map<String, Object> consumerTrack(String waybillNo) {
        Parcel p = parcelRepository.findByWaybillNo(waybillNo)
                .orElseThrow(() -> BizException.notFound("运单"));
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("waybillNo", p.getWaybillNo());
        v.put("goodsName", p.getGoodsName());
        v.put("quantity", p.getQuantity());
        v.put("stage", consumerStage(p.getStatus()));
        v.put("stageDesc", consumerStageDesc(p.getStatus()));
        v.put("updatedAt", p.getUpdatedAt());
        // 消费者端时间线：只保留面向消费者的节点，隐藏海关查验/商家责任/仓库操作细节
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (ParcelEvent e : eventRepository.findByParcelIdOrderByCreatedAtAsc(parcelIdOrThrow(waybillNo))) {
            String consumerNode = consumerNodeOf(e.getNode(), e.getToStatus());
            if (consumerNode != null) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("time", e.getCreatedAt());
                t.put("text", consumerNode);
                timeline.add(t);
            }
        }
        v.put("timeline", timeline);
        return v;
    }

    private Long parcelIdOrThrow(String waybillNo) {
        return parcelRepository.findByWaybillNo(waybillNo).orElseThrow().getId();
    }

    public String consumerStage(PackageStatus s) {
        return switch (s) {
            case RECEIVED -> "已入仓";
            case PRECHECK_PASSED, PRECHECK_FAILED, HOLD -> "申报准备中";
            case DECLARED, CUSTOMS_REVIEW -> "海关申报中";
            case INSPECTION -> "海关查验中";
            case RELEASED -> "海关已放行";
            case DELIVERING -> "国内派送中";
            case DELIVERED -> "已签收";
            case SUPPLEMENT_REQUIRED -> "申报资料补充中";
            case DETAINED -> "海关处理中";
            case RETURNING -> "包裹退运中";
            case RETURNED -> "包裹已退运";
            case DESTROYED -> "包裹已按海关要求处置";
        };
    }

    public String consumerStageDesc(PackageStatus s) {
        return switch (s) {
            case RECEIVED -> "包裹已到达口岸仓，等待申报";
            case PRECHECK_PASSED, PRECHECK_FAILED, HOLD -> "包裹资料整理中";
            case DECLARED, CUSTOMS_REVIEW -> "已向海关申报，请耐心等待";
            case INSPECTION -> "海关正在查验包裹";
            case RELEASED -> "海关已放行，即将安排国内派送";
            case DELIVERING -> "包裹正在派送途中";
            case DELIVERED -> "包裹已签收，感谢使用";
            case SUPPLEMENT_REQUIRED -> "申报资料补充中，可能略有延迟";
            case DETAINED -> "海关正在处理包裹，如有疑问请联系客服";
            case RETURNING -> "包裹办理退运中，退款事宜请联系商家";
            case RETURNED -> "包裹已退运，退款事宜请联系商家";
            case DESTROYED -> "包裹已按海关要求处置，赔付事宜请联系商家";
        };
    }

    /** 内部节点 → 消费者可见文案（过滤内部细节） */
    private String consumerNodeOf(String node, PackageStatus toStatus) {
        return switch (node) {
            case "入仓登记" -> "包裹已到达口岸仓";
            case "申报提交" -> "已向海关申报";
            case "海关放行" -> "海关已放行";
            case "放行派送" -> "包裹开始国内派送";
            case "签收" -> "包裹已签收";
            case "退运执行" -> "包裹已退运出境";
            case "销毁执行" -> "包裹已按海关要求处置";
            // 税费清算结论（退运/销毁后的作废/退款，以脱敏文案告知消费者，无需再缴税）
            case "税费作废" -> "相关税费已取消，无需缴纳";
            case "税费退还" -> "相关税费已结清，无需另行缴纳";
            case "消费者催件" -> "已收到您的催件，客服将尽快处理";
            case "催件处理" -> "客服已处理您的催件";
            default -> null;
        };
    }
}
