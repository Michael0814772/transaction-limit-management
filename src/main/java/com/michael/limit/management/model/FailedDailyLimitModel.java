package com.michael.limit.management.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "apic_t_failed_daily_limit_details")
public class FailedDailyLimitModel {

    @Id
    @SequenceGenerator(
            name = "apic_t_failed_daily_limit_details_sequence",
            sequenceName = "apic_t_failed_daily_limit_details_sequence",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "apic_t_failed_daily_limit_details_sequence"
    )
    @Column(name = "ID")
    private Long id;

    @Column(name = "cif_id")
    private String cifId;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "Request_amount")
    private BigDecimal requestAmount = BigDecimal.ZERO;

    @Column(name = "request_id")
    private int requestId;

    @Column(name = "destination_request_id")
    private int destinationRequestId; //0 => Internal, 1 => external, 2 => both

    @Column(name = "destination")
    private String destination; //=>either STO(Internal to other) or STS(Internal to Internal)

    @Column(name = "service_type_id")
    private int serviceTypeId;

    @Column(name = "service_type")
    private String serviceType;

    @Column(name = "transfer_type")
    private String transferType;

    @Column(name = "product_type")
    private String productType;

    @Column(name = "transaction_request")
    private String transactionRequest;

    @Column(name = "global_daily_Limit_Cf")
    private BigDecimal globalDailyLimitCf = BigDecimal.ZERO;

    @Column(name = "global_daily_Limit_Bf")
    private BigDecimal globalDailyLimitBf = BigDecimal.ZERO;

    @Column(name = "nip_daily_Limit_Cf")
    private BigDecimal nipDailyLimitCf = BigDecimal.ZERO;

    @Column(name = "nip_daily_Limit_Bf")
    private BigDecimal nipDailyLimitBf = BigDecimal.ZERO;

    @Column(name = "ussd_daily_Limit_Cf")
    private BigDecimal ussdDailyLimitCf = BigDecimal.ZERO;

    @Column(name = "ussd_daily_Limit_Bf")
    private BigDecimal ussdDailyLimitBf = BigDecimal.ZERO;

    @Column(name = "teller_daily_Limit_Cf")
    private BigDecimal tellerDailyLimitCf = BigDecimal.ZERO;

    @Column(name = "teller_daily_Limit_Bf")
    private BigDecimal tellerDailyLimitBf = BigDecimal.ZERO;

    @Column(name = "internet_daily_Limit_Cf")
    private BigDecimal internetDailyLimitCf = BigDecimal.ZERO;

    @Column(name = "internet_daily_Limit_Bf")
    private BigDecimal internetDailyLimitBf = BigDecimal.ZERO;

    @Column(name = "mobile_daily_Limit_Cf")
    private BigDecimal mobileDailyLimitCf = BigDecimal.ZERO;

    @Column(name = "mobile_daily_Limit_Bf")
    private BigDecimal mobileDailyLimitBf = BigDecimal.ZERO;

    @Column(name = "pos_daily_Limit_Cf")
    private BigDecimal posDailyLimitCf = BigDecimal.ZERO;

    @Column(name = "pos_daily_Limit_Bf")
    private BigDecimal posDailyLimitBf = BigDecimal.ZERO;

    @Column(name = "atm_daily_Limit_Cf")
    private BigDecimal atmDailyLimitCf = BigDecimal.ZERO;

    @Column(name = "atm_daily_Limit_Bf")
    private BigDecimal atmDailyLimitBf = BigDecimal.ZERO;

    @Column(name = "portal_daily_Limit_Cf")
    private BigDecimal portalDailyLimitCf = BigDecimal.ZERO;

    @Column(name = "portal_daily_Limit_Bf")
    private BigDecimal portalDailyLimitBf = BigDecimal.ZERO;

    @Column(name = "third_party_daily_Limit_Cf")
    private BigDecimal thirdPartyDailyLimitCf = BigDecimal.ZERO;

    @Column(name = "third_party_daily_Limit_Bf")
    private BigDecimal thirdPartyDailyLimitBf = BigDecimal.ZERO;

    @Column(name = "others_daily_Limit_Cf")
    private BigDecimal othersDailyLimitCf = BigDecimal.ZERO;

    @Column(name = "others_daily_Limit_Bf")
    private BigDecimal othersDailyLimitBf = BigDecimal.ZERO;

    @Column(name = "channel_code")
    private int channelCode;

    @Column(name = "nip_amount")
    private BigDecimal nipAmount = BigDecimal.ZERO;

    @Column(name = "limit_type")
    private String limitType;

    @Column(name = "Limit_request_Status")
    private String limitRequestStatus;

    @CreatedDate
    @Column(name = "tran_date")
    private String tranDate;

    @Column(name = "tran_time")
    private String tranTime;

    @LastModifiedBy
    @Column(name = "last_modified_by")
    private String lastModifiedBy;

    @Column(name = "last_Modified_date")
    private String lastModifiedDate;

    @Column(name = "last_Modified_Date_Time")
    private String lastModifiedDateTime;

    @Column(name = "hash")
    private String hash;

    @Column(name = "checksum")
    private String checksum;

    public void setLimitRequestStatus(String limitRequestStatus) {
        this.limitRequestStatus = limitRequestStatus.toUpperCase();
    }

    public void setLimitType(String limitType) {
        this.limitType = limitType.toUpperCase();
    }

    public void setDestination(String destination) {
        this.destination = destination.toUpperCase();
    }
}
