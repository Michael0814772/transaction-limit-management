package com.michael.limit.management.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedBy;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "apic_t_Internal_default_limit",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {
                        "kyc_Level", "cif_type", "transfer_type", "product_type"})})
public class InternalLimitModel {

    @Id
    @SequenceGenerator(
            name = "apic_t_Internal_default_limit_sequence",
            sequenceName = "apic_t_Internal_default_limit_sequence",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "apic_t_Internal_default_limit_sequence"
    )
    @Column(name = "ID")
    private Long id;

    @Column(name = "kyc_Level")
    private int kycLevel;

    @Column(name = "cif_type")
    private String cifType;

    @Column(name = "transfer_type")
    private String transferType;

    @Column(name = "product_type")
    private String productType;

    @Column(name = "global_per_Transaction_limit")
    private BigDecimal globalPerTransaction;

    @Column(name = "global_daily_Transaction_limit")
    private BigDecimal globalDailyTransaction;

    @Column(name = "nip_per_transaction_limit")
    private BigDecimal nipPerTransaction;

    @Column(name = "nip_daily_transaction_limit")
    private BigDecimal nipDailyTransaction;

    @Column(name = "ussd_per_transaction_limit")
    private BigDecimal ussdPerTransaction;

    @Column(name = "ussd_daily_transaction_limit")
    private BigDecimal ussdDailyTransaction;

    @Column(name = "bank_per_transaction_limit")
    private BigDecimal bankPerTransaction;

    @Column(name = "bank_daily_transaction_limit")
    private BigDecimal bankDailyTransaction;

    @Column(name = "internet_per_transaction_limit")
    private BigDecimal internetPerTransaction;

    @Column(name = "internet_daily_transaction_limit")
    private BigDecimal internetDailyTransaction;

    @Column(name = "mobile_per_transaction_limit")
    private BigDecimal mobilePerTransaction;

    @Column(name = "mobile_daily_transaction_limit")
    private BigDecimal mobileDailyTransaction;

    @Column(name = "pos_per_transaction_limit")
    private BigDecimal posPerTransaction;

    @Column(name = "pos_daily_transaction_limit")
    private BigDecimal posDailyTransaction;

    @Column(name = "atm_per_transaction_limit")
    private BigDecimal atmPerTransaction;

    @Column(name = "atm_daily_transaction_limit")
    private BigDecimal atmDailyTransaction;

    @Column(name = "vendor_per_transaction_limit")
    private BigDecimal vendorPerTransaction;

    @Column(name = "vendor_daily_transaction_limit")
    private BigDecimal vendorDailyTransaction;

    @Column(name = "third_per_transaction_limit")
    private BigDecimal thirdPerTransaction;

    @Column(name = "third_daily_transaction_limit")
    private BigDecimal thirdDailyTransaction;

    @Column(name = "others_per_transaction_limit")
    private BigDecimal othersPerTransaction;

    @Column(name = "others_daily_transaction_limit")
    private BigDecimal othersDailyTransaction;

    @Column(name = "hash")
    private String hash;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "created_date")
    private String createdDate;

    @Column(name = "created_time")
    private String createdTime;

    @LastModifiedBy
    @Column(name = "last_modified_by")
    private String lastModifiedBy;

    @Column(name = "last_Modified_date")
    private String lastModifiedDate;

    @Column(name = "last_Modified_Date_Time")
    private String lastModifiedDateTime;

    public void setCifType(String cifType) {
        this.cifType = cifType.toUpperCase();
    }
}
