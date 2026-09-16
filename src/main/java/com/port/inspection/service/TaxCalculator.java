package com.port.inspection.service;

import com.port.inspection.model.Parcel;
import com.port.inspection.model.TaxRule;
import com.port.inspection.model.enums.TradeMode;
import com.port.inspection.repository.TaxRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * 税费计算：计税单价取包裹认定计税价（价格复核补税后可能高于申报价），缺税则时按默认综合税率。
 * 集中一处，保证预检预估、申报计税、补税重算口径一致。
 */
@Component
@RequiredArgsConstructor
public class TaxCalculator {

    /** 缺税则时的默认跨境综合税率，与 PrecheckService 历史默认值保持一致 */
    public static final BigDecimal DEFAULT_RATE = new BigDecimal("0.0910");

    private final TaxRuleRepository taxRuleRepository;

    /** 计税单价：价格复核补税认定价优先，否则用申报价 */
    public BigDecimal taxableUnitPrice(Parcel p) {
        return p.getTaxablePrice() != null ? p.getTaxablePrice() : p.getDeclaredPrice();
    }

    public BigDecimal rateOf(Parcel p) {
        Optional<TaxRule> rule = taxRuleRepository.findByHsCode(p.getHsCode());
        return rule.map(t -> p.getTradeMode() == TradeMode.BONDED ? t.getTaxRate() : t.getGeneralTaxRate())
                .orElse(DEFAULT_RATE);
    }

    public BigDecimal computeTax(Parcel p) {
        return taxableUnitPrice(p)
                .multiply(BigDecimal.valueOf(p.getQuantity()))
                .multiply(rateOf(p))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
