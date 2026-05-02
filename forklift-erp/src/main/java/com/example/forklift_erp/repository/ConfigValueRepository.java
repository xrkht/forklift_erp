package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.ConfigValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * 配置项可选值数据访问层
 */
@Repository
public interface ConfigValueRepository extends JpaRepository<ConfigValue, Long> {

    /**
     * 根据配置项ID查询该配置项的所有可选值
     * 按排序号升序排列
     */
    List<ConfigValue> findByConfigItemIdOrderBySortOrderAsc(Long configItemId);

    /**
     * 根据配置项ID查询默认值
     */
    ConfigValue findByConfigItemIdAndIsDefaultTrue(Long configItemId);
    /**
     * 根据配置项ID删除该配置项的所有可选值
     */
    void deleteByConfigItemId(Long configItemId);
}