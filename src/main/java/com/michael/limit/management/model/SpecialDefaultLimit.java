package com.michael.limit.management.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "apic_t_special_default_limit_details",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"cif"})})
public class SpecialDefaultLimit {

    @Id
    @SequenceGenerator(
            name = "apic_t_special_default_limit_details_sequence",
            sequenceName = "apic_t_special_default_limit_details_sequence",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "apic_t_special_default_limit_details_sequence"
    )
    @Column(name = "ID")
    private Long id;

    @Column(name = "cif")
    private String cif;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "transfer_type")
    private String transferType;

    @Column(name = "product_type")
    private String productType;
}
