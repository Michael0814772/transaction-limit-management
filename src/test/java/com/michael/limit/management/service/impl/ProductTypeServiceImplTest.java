package com.michael.limit.management.service.impl;

import com.michael.limit.management.dto.productType.ProductTypeDto;
import com.michael.limit.management.exception.exceptionMethod.MyCustomizedException;
import com.michael.limit.management.model.ProductType;
import com.michael.limit.management.repository.ProductTypeRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class ProductTypeServiceImplTest {

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

    @Test
    public void testValidTransferInstant() {
        ProductTypeDto productTypeDto = new ProductTypeDto();
        productTypeDto.setTransfer("INSTANT");

        // No exception should be thrown for a valid value
        assertDoesNotThrow(() -> validateTransfer(productTypeDto));
    }

    @Test
    public void testValidTransferNonInstant() {
        ProductTypeDto productTypeDto = new ProductTypeDto();
        productTypeDto.setTransfer("NON-INSTANT");

        // No exception should be thrown for a valid value
        assertDoesNotThrow(() -> validateTransfer(productTypeDto));
    }

    @Test
    public void testInvalidTransfer() {
        ProductTypeDto productTypeDto = new ProductTypeDto();
        productTypeDto.setTransfer("INVALID");

        // MyCustomizedException should be thrown for an invalid value
        assertThrows(MyCustomizedException.class, () -> validateTransfer(productTypeDto));
    }

    private void validateTransfer(ProductTypeDto productTypeDto) throws MyCustomizedException {
        if (!productTypeDto.getTransfer().equalsIgnoreCase("INSTANT")) {
            if (!productTypeDto.getTransfer().equalsIgnoreCase("NON-INSTANT")) {
                throw new MyCustomizedException("transfer should be instant or non-instant");
            }
        }
    }
}