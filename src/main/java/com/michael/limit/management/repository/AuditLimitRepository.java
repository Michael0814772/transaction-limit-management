package com.michael.limit.management.repository;

import com.michael.limit.management.model.AuditLimitModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLimitRepository extends JpaRepository<AuditLimitModel, Long> {

    @Query(value = "SELECT TO_CHAR(SYSTIMESTAMP,'DD-MON-YYYY HH24:MI:SSFF2') AS TimeNow FROM Dual", nativeQuery = true)
    String createTime();

    @Query(value = "SELECT TO_CHAR(sysdate,'DD-MON-YYYY') AS Create_date FROM Dual", nativeQuery = true)
    String createDate();

    @Query(value = "select s from AuditLimitModel s where s.auditId = :auditId")
    AuditLimitModel findByAuditId(@Param("auditId") String auditId);
}
