package com.michael.limit.management.repository;

import com.michael.limit.management.model.CustomerLimitModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerLimitRepository extends JpaRepository<CustomerLimitModel, Long> {

    @Query(value = "SELECT TO_CHAR(SYSTIMESTAMP,'DD-MON-YYYY HH24:MI:SSFF2') AS TimeNow FROM Dual", nativeQuery = true)
    String createTime();

    @Override
    List<CustomerLimitModel> findAll();

    @Query(value = "SELECT TO_CHAR(sysdate,'DD-MON-YYYY') AS Create_date FROM Dual", nativeQuery = true)
    String createDate();

    @Query(value = "select s from CustomerLimitModel s where s.cifId = :cifId " +
            "and (CASE WHEN s.transferType IS NULL THEN 'INSTANT' ELSE s.transferType END) = :transferType " +
            "and (CASE WHEN s.productType IS NULL THEN 'NIPS' ELSE s.productType END) = :productType and s.status = 'A'")
    CustomerLimitModel findByCifId(@Param("cifId") String cifId, @Param("transferType") String transferType, @Param("productType") String productType);
}
