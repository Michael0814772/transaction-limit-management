package com.michael.limit.management.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "apic_t_product_type_table",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {
                        "product"})})
public class ProductType {

    @Id
    @SequenceGenerator(
            name = "apic_t_product_type_table_sequence",
            sequenceName = "apic_t_product_type_table_sequence",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "apic_t_product_type_table_sequence"
    )
    @Column(name = "ID")
    private Long id;

    @Column(name = "transfer")
    private String transfer;

    @Column(name = "product")
    private String product;
}
