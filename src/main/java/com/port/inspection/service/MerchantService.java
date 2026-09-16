package com.port.inspection.service;

import com.port.inspection.dto.Dtos;
import com.port.inspection.exception.BizException;
import com.port.inspection.model.Merchant;
import com.port.inspection.model.enums.RiskLevel;
import com.port.inspection.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 商家风控：风险等级、抽检比例、批量申报限额、提前上传票据要求 */
@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;

    public List<Merchant> list() {
        return merchantRepository.findAll();
    }

    @Transactional
    public Merchant updateRisk(Long id, Dtos.MerchantRiskRequest req) {
        Merchant m = merchantRepository.findById(id).orElseThrow(() -> BizException.notFound("商家"));
        m.setRiskLevel(RiskLevel.valueOf(req.riskLevel()));
        m.setInspectionRatio(req.inspectionRatio());
        m.setBatchLimit(req.batchLimit());
        m.setRequireAdvanceDocs(req.requireAdvanceDocs());
        return merchantRepository.save(m);
    }
}
