package com.michael.limit.management.service.impl;

import com.michael.limit.management.config.ExternalConfig;
import com.michael.limit.management.custom.CustomResponse;
import com.michael.limit.management.dto.productType.ProductTypeDto;
import com.michael.limit.management.exception.exceptionMethod.MyCustomizedException;
import com.michael.limit.management.model.ProductType;
import com.michael.limit.management.repository.ProductTypeRepository;
import com.michael.limit.management.service.ProductTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductTypeServiceImpl implements ProductTypeService {

    private final ProductTypeRepository productTypeRepository;

    private final ExternalConfig externalConfig;

    @Override
    public Map<String, Object> productType(ProductTypeDto productTypeDto) {
        log.info("creating product type...");

        log.info("productTypeDto: " + productTypeDto);

        if (!productTypeDto.getTransfer().equalsIgnoreCase("INSTANT")) {
            if (!productTypeDto.getTransfer().equalsIgnoreCase("NON-INSTANT")) {
                throw new MyCustomizedException("transfer should be instant or non-instant");
            }
        }

        if (productTypeDto.getProduct().equalsIgnoreCase("nip")) {
            if (!productTypeDto.getTransfer().equalsIgnoreCase("INSTANT")) {
                throw new MyCustomizedException("nip can only be an instant transfer");
            }
        }

        ProductType findByProduct = productTypeRepository.findByProduct(productTypeDto.getProduct().toUpperCase(), productTypeDto.getTransfer().toUpperCase());

        if (findByProduct != null) {
            throw new MyCustomizedException("product already exist");
        }

        ProductType productType = new ProductType();
        productType.setProduct(productTypeDto.getProduct());
        productType.setTransfer(productTypeDto.getTransfer());

        ProductType savedModel;

        try {
            savedModel = productTypeRepository.save(productType);
        } catch (Exception e) {
            log.info(e.toString());
            throw new MyCustomizedException("try again later");
        }
        log.info("savedModel: " + savedModel);

        return CustomResponse.response("success", "00", savedModel);
    }
}
