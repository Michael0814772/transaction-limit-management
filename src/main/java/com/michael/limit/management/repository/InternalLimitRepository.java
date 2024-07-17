package com.michael.limit.management.repository;

import com.michael.limit.management.model.InternalLimitModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InternalLimitRepository extends JpaRepository<InternalLimitModel, Long> {

    @Query(value = "select s from InternalLimitModel s where s.kycLevel = :kycLevel " +
            "and s.cifType = :type " +
            "and (CASE WHEN s.transferType IS NULL THEN 'INSTANT' ELSE s.transferType END) = :transfer and" +
            " (CASE WHEN s.productType IS NULL THEN 'NIPS' ELSE s.productType END) = :productType")
    InternalLimitModel findByKycLevel(@Param("kycLevel") int kycLevel, @Param("type") String cifType, @Param("transfer") String transferType, @Param("productType") String productType);
}
