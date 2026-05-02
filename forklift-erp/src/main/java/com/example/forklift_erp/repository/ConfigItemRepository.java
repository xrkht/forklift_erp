package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.ConfigItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConfigItemRepository extends JpaRepository<ConfigItem, Long> {
    Optional<ConfigItem> findByItemCode(String itemCode);
    List<ConfigItem> findByCategoryOrderBySortOrderAsc(String category);
    List<ConfigItem> findAllByOrderBySortOrderAsc();
}