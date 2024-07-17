package com.michael.limit.management.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedBy;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "apic_t_reserve_customer_limit",
        uniqueConstraints = {@UniqueConstraint(name = "Customer_limit_table_constr_01", columnNames = {"cif_id", "status", "transfer_type", "product_type"})})
public class CustomerLimitModel {

    @Id
    @SequenceGenerator(
            name = "apic_t_reserve_customer_limit_sequence",
            sequenceName = "apic_t_reserve_customer_limit_sequence",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "apic_t_reserve_customer_limit_sequence"
    )
    @Column(name = "ID")
    private Long id;

    @Column(name = "cif_id")
    private String cifId;

    @Column(name = "cif_type")
    private String cifType;

    @Column(name = "status")
    private String status = "A";

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

    @OneToOne(mappedBy = "customerLimitModel", cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name="ID")
    @ToString.Exclude
    private GlobalLimit globalLimit;

    @OneToMany(mappedBy = "customerLimitModel", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @ToString.Exclude
    private List<ChannelCode> channelCode = new ArrayList<>();

    @OneToOne(mappedBy = "customerLimitModel", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @JoinColumn(name="ID")
    private NipLimit nipLimit;
}
