package com.port.inspection.repository;

import com.port.inspection.model.RestrictedGoods;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface RestrictedGoodsRepository extends JpaRepository<RestrictedGoods, Long> {

}
