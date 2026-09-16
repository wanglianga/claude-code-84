package com.port.inspection.repository;

import com.port.inspection.model.PriceReviewOrder;
import com.port.inspection.model.enums.PriceReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceReviewOrderRepository extends JpaRepository<PriceReviewOrder, Long> {
    List<PriceReviewOrder> findByParcelIdOrderByCreatedAtDesc(Long parcelId);
    List<PriceReviewOrder> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);
    List<PriceReviewOrder> findByStatus(PriceReviewStatus status);
    List<PriceReviewOrder> findAllByOrderByCreatedAtDesc();
    boolean existsByParcelIdAndStatusIn(Long parcelId, List<PriceReviewStatus> statuses);
}
