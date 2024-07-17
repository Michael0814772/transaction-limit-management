package com.michael.limit.management.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedBy;

import java.math.BigDecimal;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "apic_t_reserve_customer_channel_limit",
        uniqueConstraints = {
                @UniqueConstraint(name = "customer_channel_limit_table_constr_02", columnNames = {
                        "channel_id",
                        "cif_id",
                        "status",
                        "product_type",
                        "transfer_type"})})
public class ChannelCode {

    @Id
    @SequenceGenerator(
            name = "apic_t_reserve_customer_channel_limit_sequence",
            sequenceName = "apic_t_reserve_customer_channel_limit_sequence",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "apic_t_reserve_customer_channel_limit_sequence"
    )
    @Column(name = "ID")
    private Long id;

    @Column(name = "cif_id")
    private String cifId;

    @Column(name = "channel_id")
    private int channelId;

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

    @ManyToOne
    @JoinColumn(name = "account_limit_model_id")
    private CustomerLimitModel customerLimitModel;

    @Column(name = "hash")
    private String hash;
}
