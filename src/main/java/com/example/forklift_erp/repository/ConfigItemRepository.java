package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.ConfigItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConfigItemRepository extends JpaRepository<ConfigItem, Long> {
    Optional<ConfigItem> findByItemCode(String itemCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConfigItem c where c.id = :id")
    Optional<ConfigItem> findByIdForUpdate(@Param("id") Long id);
    List<ConfigItem> findByCategoryOrderBySortOrderAsc(String category);
    List<ConfigItem> findAllByOrderBySortOrderAsc();
}
