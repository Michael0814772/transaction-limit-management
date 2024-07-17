package com.michael.limit.management.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "apic_t_audit_limit_table",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"audit_id"})})
public class AuditLimitModel {

    @Id
    @SequenceGenerator(
            name = "apic_t_audit_limit_table_sequence",
            sequenceName = "apic_t_audit_limit_table_sequence",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "apic_t_audit_limit_table_sequence"
    )
    @Column(name = "ID")
    private Long id;

    @Column(name = "cif_id")
    private String cifId;

    @Column(name = "global_daily_Limit_Cf")
    private BigDecimal globalDailyLimitCf;

    @Column(name = "global_daily_Limit_Bf")
    private BigDecimal globalDailyLimitBf;

    @Column(name = "nip_daily_Limit_Cf")
    private BigDecimal nipDailyLimitCf;

    @Column(name = "nip_daily_Limit_Bf")
    private BigDecimal nipDailyLimitBf;

    @Column(name = "ussd_daily_Limit_Cf")
    private BigDecimal ussdDailyLimitCf;

    @Column(name = "ussd_daily_Limit_Bf")
    private BigDecimal ussdDailyLimitBf;

    @Column(name = "teller_daily_Limit_Cf")
    private BigDecimal tellerDailyLimitCf;

    @Column(name = "teller_daily_Limit_Bf")
    private BigDecimal tellerDailyLimitBf;

    @Column(name = "internet_daily_Limit_Cf")
    private BigDecimal internetDailyLimitCf;

    @Column(name = "internet_daily_Limit_Bf")
    private BigDecimal internetDailyLimitBf;

    @Column(name = "mobile_daily_Limit_Cf")
    private BigDecimal mobileDailyLimitCf;

    @Column(name = "mobile_daily_Limit_Bf")
    private BigDecimal mobileDailyLimitBf;

    @Column(name = "pos_daily_Limit_Cf")
    private BigDecimal posDailyLimitCf;

    @Column(name = "pos_daily_Limit_Bf")
    private BigDecimal posDailyLimitBf;

    @Column(name = "atm_daily_Limit_Cf")
    private BigDecimal atmDailyLimitCf;

    @Column(name = "atm_daily_Limit_Bf")
    private BigDecimal atmDailyLimitBf;

    @Column(name = "portal_daily_Limit_Cf")
    private BigDecimal portalDailyLimitCf;

    @Column(name = "portal_daily_Limit_Bf")
    private BigDecimal portalDailyLimitBf;

    @Column(name = "third_party_daily_Limit_Cf")
    private BigDecimal thirdPartyDailyLimitCf;

    @Column(name = "third_party_daily_Limit_Bf")
    private BigDecimal thirdPartyDailyLimitBf;

    @Column(name = "others_daily_Limit_Cf")
    private BigDecimal othersDailyLimitCf;

    @Column(name = "others_daily_Limit_Bf")
    private BigDecimal othersDailyLimitBf;

    @Column(name = "transfer_type")
    private String transferType;

    @Column(name = "product_type")
    private String productType;

    @Column(name = "audit_id")
    private String auditId;

    @Column(name = "update_date")
    private String updateDate;

    @Column(name = "update_time")
    private String updateTime;

    @Column(name = "modified_by")
    private String modifiedBy;

    @Column(name = "hash")
    private String hash;
}
