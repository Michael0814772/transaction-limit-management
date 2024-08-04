package com.michael.limit.management.repository;

import com.michael.limit.management.model.ProductType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class ProductTypeRepositoryTest {

    @Autowired
    ProductTypeRepository productTypeRepository;

    //Given: product & transfer
    String product = "NEFT";
    String transfer = "INSTANT";

    @AfterEach
    void tearDown() {
        productTypeRepository.deleteAll();
    }

    @Test
    void itShouldCheckIfProductExist() {

        //Given
        ProductType productType = new ProductType(
                2L,
                transfer,
                product
        );
        productTypeRepository.save(productType);

        //When
        boolean exist = productTypeRepository.findIfProductAndTransferExist(product, transfer);

        //Then
        Assertions.assertThat(exist).isTrue();
    }

    @Test
    void itShouldCheckIfProductDoesNotExist() {

        //When
        boolean exist = productTypeRepository.findIfProductAndTransferExist(product, transfer);

        //Then
        Assertions.assertThat(exist).isFalse();
    }
}