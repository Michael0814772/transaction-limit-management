package com.michael.limit.management.service;

import com.michael.limit.management.dto.productType.ProductTypeDto;

import java.util.Map;

public interface ProductTypeService {

    Map<String, Object> productType(ProductTypeDto productTypeDto);
}
