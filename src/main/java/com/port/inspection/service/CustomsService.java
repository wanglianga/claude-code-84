package com.port.inspection.service;

import com.port.inspection.model.*;
import com.port.inspection.model.enums.*;
import com.port.inspection.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/**
 * 海关接口模拟：异步审单回执、布控查验、系统延迟。
 * 预检含预警的包裹必查验；其余按商家抽检比例随机布控。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomsService {

    private final CustomsTaskRepository customsTaskRepository;
    private final DeclarationRepository declarationRepository;
    private final ParcelRepository parcelRepository;
    private final MerchantRepository merchantRepository;
    private final PrecheckResultRepository precheckRepository;
    private final InspectionOrderRepository inspectionOrderRepository;
    private final DeclarationService declarationService;
    private final ParcelEventService eventService;

    @Value("${app.customs-sla-seconds:60}")
    private long customsSlaSeconds;

    private final Random random = new Random();

    /** 每 3 秒处理一批到期的海关任务 */
    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void processDueTasks() {
        List<CustomsTask> due = customsTaskRepository.findByStatusAndExecuteAfterBefore(
                CustomsTaskStatus.PENDING, LocalDateTime.now());
        for (CustomsTask task : due) {
            try {
                processTask(task);
            } catch (Exception e) {
                log.error("海关任务处理失败 task={}: {}", task.getId(), e.getMessage());
            }
        }
    }

    /** 海关延迟巡检：超过 SLA 未回执的申报单标记延迟 */
    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void markDelayed() {
        for (Declaration d : declarationRepository.findByStatus(DeclarationStatus.SUBMITTED)) {
            if (d.getSubmittedAt() != null
                    && d.getSubmittedAt().plusSeconds(customsSlaSeconds).isBefore(LocalDateTime.now())) {
                Parcel p = parcelRepository.findById(d.getParcelId()).orElse(null);
                if (p != null && !Boolean.TRUE.equals(p.getCustomsDelayed())) {
                    p.setCustomsDelayed(true);
                    parcelRepository.save(p);
                    eventService.record(p.getId(), p.getStatus(), p.getStatus(), "海关系统延迟", null,
                            "海关系统处理延迟，申报单 " + d.getDeclarationNo() + " 等待回执");
                }
            }
        }
    }

    private void processTask(CustomsTask task) {
        Declaration d = declarationRepository.findById(task.getDeclarationId()).orElse(null);
        if (d == null || d.getStatus() != DeclarationStatus.SUBMITTED) {
            task.setStatus(CustomsTaskStatus.DONE);
            task.setProcessedAt(LocalDateTime.now());
            customsTaskRepository.save(task);
            return;
        }
        Parcel p = parcelRepository.findById(d.getParcelId()).orElseThrow();
        Merchant m = merchantRepository.findById(d.getMerchantId()).orElseThrow();

        // 预检含预警必查验；否则按商家抽检比例布控（高风险商家比例更高）
        boolean hasWarn = precheckRepository.findByParcelId(p.getId()).stream()
                .anyMatch(r -> r.getLevel() == PrecheckLevel.WARN);
        boolean needInspection = hasWarn || random.nextInt(100) < m.getInspectionRatio();

        if (needInspection) {
            d.setStatus(DeclarationStatus.INSPECTION_REQUIRED);
            d.setUpdatedAt(LocalDateTime.now());
            declarationRepository.save(d);

            InspectionOrder order = new InspectionOrder();
            order.setOrderNo(DeclarationService.genNo("INS"));
            order.setDeclarationId(d.getId());
            order.setParcelId(p.getId());
            order.setInstruction("海关布控查验：开箱核对商品与申报信息，拍照留证");
            order.setIssuedBy("海关审单系统");
            inspectionOrderRepository.save(order);

            PackageStatus old = p.getStatus();
            p.setStatus(PackageStatus.INSPECTION);
            p.setCustomsDelayed(false);
            p.setUpdatedAt(LocalDateTime.now());
            parcelRepository.save(p);
            eventService.record(p.getId(), old, PackageStatus.INSPECTION, "海关布控", null,
                    "海关下达查验指令 " + order.getOrderNo() + "，仓库人员按指令开箱查验");
            task.setResult("INSPECTION_REQUIRED");
        } else {
            d.setStatus(DeclarationStatus.ACCEPTED);
            d.setUpdatedAt(LocalDateTime.now());
            declarationRepository.save(d);
            p.setCustomsDelayed(false);
            parcelRepository.save(p);
            eventService.record(p.getId(), p.getStatus(), p.getStatus(), "海关受理", null,
                    "海关审单通过，等待税费缴清后放行");
            task.setResult("ACCEPTED");
            // 税费已缴清则直接放行
            declarationService.tryRelease(d.getId(), null);
        }
        task.setStatus(CustomsTaskStatus.DONE);
        task.setProcessedAt(LocalDateTime.now());
        customsTaskRepository.save(task);
    }

    /** 手动触发处理（演示/测试用） */
    @Transactional
    public int processNow() {
        List<CustomsTask> due = customsTaskRepository.findByStatusAndExecuteAfterBefore(
                CustomsTaskStatus.PENDING, LocalDateTime.now().plusYears(1));
        due.forEach(this::processTask);
        return due.size();
    }

    /** 模拟海关系统延迟：将所有待处理任务延后指定秒数 */
    @Transactional
    public int simulateDelay(int seconds) {
        List<CustomsTask> pending = customsTaskRepository.findByStatusAndExecuteAfterBefore(
                CustomsTaskStatus.PENDING, LocalDateTime.now().plusYears(1));
        for (CustomsTask t : pending) {
            t.setExecuteAfter(LocalDateTime.now().plusSeconds(seconds));
            customsTaskRepository.save(t);
        }
        return pending.size();
    }
}
