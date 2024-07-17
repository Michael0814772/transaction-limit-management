package com.michael.limit.management.repository;

import com.michael.limit.management.model.SummaryDetailModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SummaryDetailRepository extends JpaRepository<SummaryDetailModel, Long> {

    @Query(value = "select s from SummaryDetailModel s where s.cifId = :cifId " +
            "and s.tranDate = :sDate " +
            "and (CASE WHEN s.transferType IS NULL THEN 'INSTANT' ELSE s.transferType END) = :transferType" +
            " and (CASE WHEN s.productType IS NULL THEN 'NIPS' ELSE s.productType END) = :productType")
    SummaryDetailModel findByCifId(@Param("cifId") String cifId, @Param("sDate") String date, @Param("transferType") String transferType, @Param("productType") String productType);
}
