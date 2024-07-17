package com.michael.limit.management.repository;

import com.michael.limit.management.model.SpecialDefaultLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpecialDefaultLimitRepository extends JpaRepository<SpecialDefaultLimit, Long> {

    @Query(value = "select s from SpecialDefaultLimit s where s.cif = :cif " +
            "and (CASE WHEN s.transferType IS NULL THEN 'INSTANT' ELSE s.transferType END) = :transferType" +
            " and (CASE WHEN s.productType IS NULL THEN 'NIPS' ELSE s.productType END) = :productType")
    List<SpecialDefaultLimit> findByCif(@Param("cif") String cifId, @Param("transferType") String transferType, @Param("productType") String productType);
}
