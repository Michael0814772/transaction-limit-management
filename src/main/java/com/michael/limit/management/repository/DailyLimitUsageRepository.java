package com.michael.limit.management.repository;

import com.michael.limit.management.model.DailyLimitUsageModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface DailyLimitUsageRepository extends JpaRepository<DailyLimitUsageModel, Long> {

    @Query(value = "select MIGADM.apictranbalrequest@red_link(:account,'100') from dual", nativeQuery = true)
    String getCIfIdAndType(@Param("account") String accountNumber);

    @Query(value = "select MIGADM.omnichannelcustomerdetails@red_link(:account) from dual", nativeQuery = true)
    String fetchAccountDetailsWithAccount(@Param("account") String accountNumber);

    @Query(value = "select s from DailyLimitUsageModel s where s.transactionRequest = :request " +
            "and s.requestId = 1 and s.requestAmount = :amount and s.tranDate = :tranDate " +
            "and s.channelCode = :channel " +
            "and (CASE WHEN s.transferType IS NULL THEN 'INSTANT' ELSE s.transferType END) = :transferType " +
            "and (CASE WHEN s.productType IS NULL THEN 'NIPS' ELSE s.productType END) = :productType")
    DailyLimitUsageModel findByTransactionRequest(@Param("request") String request, @Param("amount") BigDecimal amount, @Param("tranDate") String tranDate, @Param("channel") int channel, @Param("transferType") String transferType, @Param("productType") String productType);

    @Query(value = "select s from DailyLimitUsageModel s where s.transactionRequest = :request" +
            " and s.requestId = 1 and s.tranDate = :tranDate and s.channelCode = :channel" +
            " and (CASE WHEN s.transferType IS NULL THEN 'INSTANT' ELSE s.transferType END) = :transferType " +
            "and (CASE WHEN s.productType IS NULL THEN 'NIPS' ELSE s.productType END) = :productType")
    DailyLimitUsageModel findByTransactionRequestBulk(@Param("request") String request, @Param("tranDate") String tranDate, @Param("channel") int channel, @Param("transferType") String transferType, @Param("productType") String productType);

    @Query(value = "SELECT SUM(REQUEST_AMOUNT) FROM apic_t_daily_limit_usage_details WHERE CIF_ID = :cifId" +
            " and TRAN_DATE = :tDate and channel_code = :code " +
            "and (CASE WHEN transfer_Type IS NULL THEN 'INSTANT' ELSE transfer_Type END) = :transferType" +
            " and (CASE WHEN product_Type IS NULL THEN 'NIPS' ELSE product_Type END) = :productType ", nativeQuery = true)
    BigDecimal amount(@Param("cifId") String cifId, @Param("tDate") String date, @Param("code") int code, @Param("transferType") String transferType, @Param("productType") String productType);

    @Query(value = "select * from (select * from apic_t_daily_limit_usage_details " +
            "where cif_id = :cifId and tran_date = :sDate " +
            "and (CASE WHEN transfer_Type IS NULL THEN 'INSTANT' ELSE transfer_Type END) = :transferType" +
            " and (CASE WHEN product_Type IS NULL THEN 'NIPS' ELSE product_Type END) = :productType order by TRAN_TIME desc) where rownum < 2", nativeQuery = true)
    DailyLimitUsageModel getRecentTrans(@Param("cifId") String cifId, @Param("sDate") String sDate, @Param("transferType") String transferType, @Param("productType") String productType);

    @Query(value = "select * from (select * from apic_t_daily_limit_usage_details " +
            "where cif_id = :cifId and channel_code = :channelCode and tran_date = :sDate" +
            " and (CASE WHEN transfer_Type IS NULL THEN 'INSTANT' ELSE transfer_Type END) = :transferType " +
            "and (CASE WHEN product_Type IS NULL THEN 'NIPS' ELSE product_Type END) = :productType order by TRAN_TIME desc) where rownum < 2", nativeQuery = true)
    DailyLimitUsageModel getRecentTransByChannel(@Param("cifId") String cifId, @Param("channelCode") int code, @Param("sDate") String sDate, @Param("transferType") String transferType, @Param("productType") String productType);

    @Query(value = "select s from DailyLimitUsageModel s where s.transactionRequest = :request" +
            " and s.requestId = :requestId and s.tranDate = :tranDate " +
            "and (CASE WHEN s.transferType IS NULL THEN 'INSTANT' ELSE s.transferType END) = :transferType" +
            " and (CASE WHEN s.productType IS NULL THEN 'NIPS' ELSE s.productType END) = :productType")
    Optional<DailyLimitUsageModel> isRequestIdUnique(@Param("request") String request, @Param("requestId") int requestId, @Param("tranDate") String tranDate, @Param("transferType") String transferType, @Param("productType") String productType);

}
