package com.michael.limit.management.service.impl;

import com.michael.limit.management.custom.CustomResponse;
import com.michael.limit.management.dto.databaseDto.AccountDetailsDto;
import com.michael.limit.management.dto.specialLimit.SpecialLimitRequestDto;
import com.michael.limit.management.exception.exceptionMethod.DuplicateException;
import com.michael.limit.management.exception.exceptionMethod.MyCustomException;
import com.michael.limit.management.model.ProductType;
import com.michael.limit.management.model.SpecialDefaultLimit;
import com.michael.limit.management.repository.ProductTypeRepository;
import com.michael.limit.management.repository.SpecialDefaultLimitRepository;
import com.michael.limit.management.service.SpecialDefaultLimitService;
import com.michael.limit.management.utils.HelperUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SpecialDefaultLimitServiceImpl implements SpecialDefaultLimitService {

    private final HelperUtils helperUtils;

    private final SpecialDefaultLimitRepository specialDefaultLimitRepository;

    private final ProductTypeRepository productTypeRepository;

    @Override
    public Map<String, Object> add(SpecialLimitRequestDto requestDto, String serviceToken, String serviceIpAddress, int i, String userToken) {
        log.info("{}", SpecialDefaultLimitServiceImpl.class);

        if (i == 1) {
            helperUtils.serviceAuth(serviceToken, serviceIpAddress);
        } else {
            helperUtils.userAuth(userToken, serviceToken, serviceIpAddress, "", requestDto.getChannelCode(), requestDto.getCifId());
        }

        AccountDetailsDto getCIfId = helperUtils.getWithCifOrAccount(requestDto.getCifId());

        ProductType productType = checkProductType(requestDto);

        List<SpecialDefaultLimit> findByCif = specialDefaultLimitRepository.findByCif(requestDto.getCifId(), productType.getTransfer(), productType.getProduct());

        if (!findByCif.isEmpty()) {
            throw new DuplicateException("cif id already exist");
        }

        SpecialDefaultLimit specialDefaultLimit = new SpecialDefaultLimit();
        specialDefaultLimit.setCif(getCIfId.getCifId());
        specialDefaultLimit.setCustomerName(requestDto.getCustomerName());
        specialDefaultLimit.setProductType(productType.getProduct());
        specialDefaultLimit.setTransferType(productType.getTransfer());

        try {
            specialDefaultLimitRepository.save(specialDefaultLimit);
        } catch (Exception e) {
            throw new MyCustomException("Err: kindly try again later");
        }

        return CustomResponse.response("registered successfully", "00", requestDto);
    }

    private ProductType checkProductType(SpecialLimitRequestDto requestDto) {

        ProductType productType = productTypeRepository.findByProduct(requestDto.getProductType(), requestDto.getTransferType());

        if (productType == null) {
            String message = String.format("Transfer of transferType %s and product type %s has not been configured", requestDto.getTransferType(), requestDto.getProductType());
            throw new MyCustomException(message);
        }
        return productType;
    }
}
