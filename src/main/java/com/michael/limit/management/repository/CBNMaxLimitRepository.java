package com.michael.limit.management.repository;

import com.michael.limit.management.model.CBNMaxLimitModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CBNMaxLimitRepository extends JpaRepository<CBNMaxLimitModel, Long> {

    @Query("select s from CBNMaxLimitModel s where s.kycLevel = :id and s.cifType = :cifType " +
            "and (CASE WHEN s.transferType IS NULL THEN 'INSTANT' ELSE s.transferType END) = :transfer " +
            "and (CASE WHEN s.productType IS NULL THEN 'NIPS' ELSE s.productType END) = :productType")
    CBNMaxLimitModel findByKycId(@Param("id") int id, @Param("cifType") String cifType, @Param("transfer") String transferType, @Param("productType") String productType);
}
