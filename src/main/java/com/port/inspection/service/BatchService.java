package com.port.inspection.service;

import com.port.inspection.exception.BizException;
import com.port.inspection.model.*;
import com.port.inspection.model.enums.*;
import com.port.inspection.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 批次处理：口岸仓每日批量处理包裹，异常包裹与正常放行队列分离 */
@Service
@RequiredArgsConstructor
public class BatchService {

    private final BatchRepository batchRepository;
    private final ParcelRepository parcelRepository;
    private final MerchantRepository merchantRepository;
    private final PrecheckService precheckService;
    private final ParcelEventService eventService;

    @Transactional
    public Batch create(String batchNo, User merchant) {
        if (batchRepository.findByBatchNo(batchNo).isPresent()) {
            throw new BizException("批次号已存在: " + batchNo);
        }
        Batch b = new Batch();
        b.setBatchNo(batchNo);
        b.setMerchantId(merchant.getMerchantId());
        return batchRepository.save(b);
    }

    /**
     * 批量处理：对批次内所有待处理包裹执行申报前检查，
     * 通过的进入正常放行队列，异常的隔离到异常队列（HOLD），互不影响时效。
     */
    @Transactional
    public Map<String, Object> process(Long batchId, User actor) {
        Batch b = batchRepository.findById(batchId).orElseThrow(() -> BizException.notFound("批次"));
        if (b.getStatus() == BatchStatus.PROCESSED) {
            throw new BizException("批次已处理完毕");
        }
        Merchant m = merchantRepository.findById(b.getMerchantId()).orElseThrow(() -> BizException.notFound("商家"));
        List<Parcel> parcels = parcelRepository.findByBatchNo(b.getBatchNo());

        // 高风险商家：限制批量申报规模
        if (parcels.size() > m.getBatchLimit()) {
            throw new BizException("批次含 " + parcels.size() + " 单，超出该商家批量申报限额 "
                    + m.getBatchLimit() + "（高风险商家管控）");
        }

        b.setStatus(BatchStatus.PROCESSING);
        batchRepository.save(b);

        List<Map<String, Object>> normalQueue = new ArrayList<>();
        List<Map<String, Object>> abnormalQueue = new ArrayList<>();
        for (Parcel p : parcels) {
            if (p.getStatus() != PackageStatus.RECEIVED && p.getStatus() != PackageStatus.HOLD) {
                continue; // 只处理待处理包裹，已进入后续环节的不动
            }
            List<PrecheckResult> results = precheckService.runPrecheck(p.getId(), actor);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("parcelId", p.getId());
            item.put("waybillNo", p.getWaybillNo());
            item.put("goodsName", p.getGoodsName());
            if (p.getStatus() == PackageStatus.PRECHECK_PASSED) {
                item.put("queue", "NORMAL");
                normalQueue.add(item);
            } else {
                // 异常包裹隔离，不进入正常放行队列
                Parcel fresh = parcelRepository.findById(p.getId()).orElseThrow();
                fresh.setStatus(PackageStatus.HOLD);
                fresh.setUpdatedAt(LocalDateTime.now());
                parcelRepository.save(fresh);
                item.put("queue", "ABNORMAL");
                item.put("reasons", results.stream()
                        .filter(r -> r.getLevel() == PrecheckLevel.FAIL)
                        .map(r -> r.getCheckType() + ": " + r.getMessage()).toList());
                abnormalQueue.add(item);
                eventService.record(p.getId(), PackageStatus.PRECHECK_FAILED, PackageStatus.HOLD,
                        "异常隔离", actor, "批次处理发现异常，包裹隔离至异常队列，不影响整批时效");
            }
        }
        b.setTotalCount(parcels.size());
        b.setNormalCount(normalQueue.size());
        b.setAbnormalCount(abnormalQueue.size());
        b.setStatus(BatchStatus.PROCESSED);
        b.setProcessedAt(LocalDateTime.now());
        batchRepository.save(b);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batch", b);
        result.put("normalQueue", normalQueue);
        result.put("abnormalQueue", abnormalQueue);
        return result;
    }

    public Map<String, Object> detail(Long batchId) {
        Batch b = batchRepository.findById(batchId).orElseThrow(() -> BizException.notFound("批次"));
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("batch", b);
        v.put("parcels", parcelRepository.findByBatchNo(b.getBatchNo()));
        return v;
    }

    public List<Batch> list() {
        return batchRepository.findAllByOrderByCreatedAtDesc();
    }
}
