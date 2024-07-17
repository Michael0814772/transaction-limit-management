package com.michael.limit.management.repository;

import com.michael.limit.management.model.NipLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NipLimitRepository extends JpaRepository<NipLimit, Long> {

    @Override
    List<NipLimit> findAll();

    @Query(value = "select s from NipLimit s where s.cifId = :cifId " +
            "and (CASE WHEN s.transferType IS NULL THEN 'INSTANT' ELSE s.transferType END) = :transferType" +
            " and (CASE WHEN s.productType IS NULL THEN 'NIPS' ELSE s.productType END) = :productType and s.status = 'A'")
    NipLimit findNipLimitByCifId(@Param("cifId") String cifId, @Param("transferType") String transferType, @Param("productType") String productType);
}
