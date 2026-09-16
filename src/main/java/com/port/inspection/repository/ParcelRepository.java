package com.port.inspection.repository;

import com.port.inspection.model.Parcel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface ParcelRepository extends JpaRepository<Parcel, Long> {
    Optional<Parcel> findByWaybillNo(String waybillNo);
    List<Parcel> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);
    List<Parcel> findByRecipientIdCardOrderByCreatedAtDesc(String recipientIdCard);
    List<Parcel> findByBatchNo(String batchNo);
    List<Parcel> findByStatusOrderByCreatedAtDesc(com.port.inspection.model.enums.PackageStatus status);
    List<Parcel> findAllByOrderByCreatedAtDesc();
    long countByRecipientIdCardAndCreatedAtAfter(String recipientIdCard, LocalDateTime after);
    List<Parcel> findByRecipientIdCard(String recipientIdCard);
}
