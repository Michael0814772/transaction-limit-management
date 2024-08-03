package com.michael.limit.management.model;

import com.michael.limit.management.repository.ProductTypeRepository;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
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

    @Column(name = "transfer", nullable = false)
    private String transfer;

    @Column(name = "product", nullable = false)
    private String product;

    public ProductType(String transfer, String product) {
        this.transfer = transfer;
        this.product = product;
    }

    public ProductType(ProductTypeRepository productTypeRepository) {
    }
}
