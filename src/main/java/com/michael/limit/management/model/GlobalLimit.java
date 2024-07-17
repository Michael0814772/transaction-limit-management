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
@Table(name = "apic_t_reserve_customer_global_limit",
        uniqueConstraints = {
                @UniqueConstraint(name = "customer_global_limit_table_constr_03", columnNames = {
                        "cif_id","status","transfer_type","product_type"})})
public class GlobalLimit {

    @Id
    @SequenceGenerator(
            name = "apic_t_reserve_customer_global_limit_sequence",
            sequenceName = "apic_t_reserve_customer_global_limit_sequence",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "apic_t_reserve_customer_global_limit_sequence"
    )
    @Column(name = "ID")
    private Long id;

    @Column(name = "cif_id")
    private String cifId;

    @Column(name = "status")
    private String status = "A";

    @Column(name = "total_daily_limit")
    private BigDecimal totalDailyLimit;

    @Column(name = "per_transaction_limit")
    private BigDecimal perTransactionLimit;

    @Column(name = "transfer_type")
    private String transferType;

    @Column(name = "product_type")
    private String productType;

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

    @OneToOne
    @JoinColumn(name = "account_limit_model_id")
    private CustomerLimitModel customerLimitModel;

    @Column(name = "hash")
    public String hash;
}
