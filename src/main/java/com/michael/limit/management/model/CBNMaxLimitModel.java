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
@Table(name = "apic_t_cbn_max_limit",
        uniqueConstraints = {
                @UniqueConstraint(name = "cbn_limit_table_constr_01",
                        columnNames = {"kyc_level", "cif_type", "transfer_type", "product_type"})})
public class CBNMaxLimitModel {

    @Id
    @SequenceGenerator(
            name = "apic_t_cbn_max_limit_sequence",
            sequenceName = "apic_t_cbn_max_limit_sequence",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "apic_t_cbn_max_limit_sequence"
    )
    @Column(name = "ID")
    private Long id;

    @Column(name = "kyc_level")
    private int kycLevel;

    @Column(name = "cif_type")
    private String cifType;

    @Column(name = "transfer_type")
    private String transferType;

    @Column(name = "product_type")
    private String productType;

    @Column(name = "global_daily_limit")
    private BigDecimal globalDailyLimit;

    @Column(name = "global_per_limit")
    private BigDecimal globalPerTransaction;

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

    @Column(name = "hash")
    public String hash;
}
