package com.port.inspection.repository;

import com.port.inspection.model.BrandPriceRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandPriceRuleRepository extends JpaRepository<BrandPriceRule, Long> {
    Optional<BrandPriceRule> findByBrandAndHsCode(String brand, String hsCode);
    List<BrandPriceRule> findByReviewFlagTrue();
    List<BrandPriceRule> findAllByOrderByUpdatedAtDesc();
}
