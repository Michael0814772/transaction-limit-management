package com.michael.limit.management.repository;

import com.michael.limit.management.model.GlobalLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GlobalLimitRepository extends JpaRepository<GlobalLimit, Long> {

    @Override
    List<GlobalLimit> findAll();

    @Query(value = "select s from GlobalLimit s where s.cifId = :cifId " +
            "and (CASE WHEN s.transferType IS NULL THEN 'INSTANT' ELSE s.transferType END) = :transferType" +
            " and (CASE WHEN s.productType IS NULL THEN 'NIPS' ELSE s.productType END) = :productType and s.status = 'A'")
    GlobalLimit findGlobalLimitByCifId(@Param("cifId") String cifId, @Param("transferType") String transferType, @Param("productType") String productType);
}
