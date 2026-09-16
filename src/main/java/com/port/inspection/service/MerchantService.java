package com.port.inspection.service;

import com.port.inspection.dto.Dtos;
import com.port.inspection.exception.BizException;
import com.port.inspection.model.Merchant;
import com.port.inspection.model.enums.RiskLevel;
import com.port.inspection.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 商家风控：风险等级、抽检比例、批量申报限额、提前上传票据要求 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final BrandPriceRuleService brandPriceRuleService;

    public List<Merchant> list() {
        return merchantRepository.findAll();
    }

    @Transactional
    public Merchant updateRisk(Long id, Dtos.MerchantRiskRequest req) {
        Merchant m = merchantRepository.findById(id).orElseThrow(() -> new BizException("商家不存在", HttpStatus.NOT_FOUND));
        RiskLevel old = m.getRiskLevel();
        m.setRiskLevel(RiskLevel.valueOf(req.riskLevel()));
        m.setInspectionRatio(req.inspectionRatio());
        m.setBatchLimit(req.batchLimit());
        m.setRequireAdvanceDocs(req.requireAdvanceDocs());
        merchantRepository.save(m);

        // 明确降风险（调离 HIGH）才解除该商家的价格重点复核名单，恢复普通预审；
        // 单票价格复核 PASS 不会触发解除。
        if (old == RiskLevel.HIGH && m.getRiskLevel() != RiskLevel.HIGH) {
            int n = brandPriceRuleService.releaseMerchantWatches(id);
            log.info("商家 {} 由 HIGH 降为 {}，解除 {} 条价格重点复核记录", m.getCode(), m.getRiskLevel(), n);
        }
        return m;
    }
}
