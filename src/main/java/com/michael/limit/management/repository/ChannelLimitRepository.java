package com.michael.limit.management.repository;

import com.michael.limit.management.model.ChannelCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChannelLimitRepository extends JpaRepository<ChannelCode, Long> {

    @Override
    List<ChannelCode> findAll();

    @Query(value = "select s from ChannelCode s where s.channelId = :channelId " +
            "and s.cifId = :cifId " +
            "and (CASE WHEN s.transferType IS NULL THEN 'INSTANT' ELSE s.transferType END) = :transferType " +
            "and (CASE WHEN s.productType IS NULL THEN 'NIPS' ELSE s.productType END) = :productType and s.status = 'A'")
    ChannelCode findUssdLimitById(@Param("channelId") String channelId, @Param("cifId") String cifId, @Param("transferType") String transferType, @Param("productType") String productType);


    @Query(value = "select s from ChannelCode s where s.cifId = :cifId " +
            "and s.transferType = :transferType " +
            "and (CASE WHEN s.productType IS NULL THEN 'NIPS' ELSE s.productType END) = :productType and s.status = 'A'")
    List<ChannelCode> findAllChannel(@Param("cifId") String cifId, @Param("transferType") String transferType, @Param("productType") String productType);
}
