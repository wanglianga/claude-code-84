package com.port.inspection.repository;

import com.port.inspection.model.MerchantPriceWatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MerchantPriceWatchRepository extends JpaRepository<MerchantPriceWatch, Long> {
    Optional<MerchantPriceWatch> findByMerchantIdAndBrandAndHsCode(Long merchantId, String brand, String hsCode);
    List<MerchantPriceWatch> findByMerchantIdOrderByUpdatedAtDesc(Long merchantId);
}
