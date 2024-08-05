package com.michael.limit.management.service.impl;

import com.michael.limit.management.model.ProductType;
import com.michael.limit.management.repository.ProductTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class ProductTypeServiceImplTest {

    @Autowired
    ProductTypeRepository productTypeRepository;

    @Autowired
    ProductType productType;

    @BeforeEach
    void setUp() {
        productType = new ProductType(productTypeRepository);
    }

    @Test
    void productType() {
    }
}