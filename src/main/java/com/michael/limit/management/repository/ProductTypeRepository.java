package com.michael.limit.management.repository;

import com.michael.limit.management.model.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductTypeRepository extends JpaRepository<ProductType, Long> {

    @Query("select s from ProductType s where s.product = :product and s.transfer = :transfer")
    ProductType findByProduct(@Param("product") String product, @Param("transfer") String transfer);

    @Query("select case when count(s) > 0 then true else false end from ProductType s where s.product = :product and s.transfer = :transfer")
    Boolean findIfProductAndTransferExist(@Param("product") String product, @Param("transfer") String transfer);
}
