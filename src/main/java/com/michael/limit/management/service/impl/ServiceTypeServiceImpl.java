package com.michael.limit.management.service.impl;

import com.michael.limit.management.custom.CustomResponse;
import com.michael.limit.management.dto.serviceTypeDto.ServiceTypeRequest;
import com.michael.limit.management.exception.exceptionMethod.MyCustomException;
import com.michael.limit.management.model.ServiceTypeModel;
import com.michael.limit.management.repository.CustomerLimitRepository;
import com.michael.limit.management.repository.ServiceTypeRepository;
import com.michael.limit.management.service.ServiceTypeService;
import com.michael.limit.management.utils.LastModifiedBy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceTypeServiceImpl implements ServiceTypeService {

    private final CustomerLimitRepository customerLimitRepository;

    private final LastModifiedBy lastModifiedBy;

    private final ServiceTypeRepository serviceTypeRepository;


    @Override
    public Map<String, Object> serviceType(ServiceTypeRequest serviceTypeRequest) {
        log.info("running service type creation");
        log.info("{}", ServiceTypeServiceImpl.class);

        if (serviceTypeRequest.getServiceTypeId() < 1) {
            String message = String.format("service type id cannot be less than 1 but supplied %s", serviceTypeRequest.getServiceTypeId());
            throw new MyCustomException(message);
        }

        Optional<ServiceTypeModel> findByServiceTypeId = serviceTypeRepository.findByServiceTypeId(serviceTypeRequest.getServiceTypeId());

        if (findByServiceTypeId.isPresent()) {
            String message = String.format("service type id %s already exist", serviceTypeRequest.getServiceTypeId());
            throw new MyCustomException(message);
        }

        ServiceTypeModel serviceTypeModel = new ServiceTypeModel();
        serviceTypeModel.setServiceTypeId(serviceTypeRequest.getServiceTypeId());
        serviceTypeModel.setServiceType(serviceTypeRequest.getServiceType());
        serviceTypeModel.setCreatedDate(customerLimitRepository.createDate());
        serviceTypeModel.setCreatedTime(customerLimitRepository.createTime());
        serviceTypeModel.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
        serviceTypeModel.setLastModifiedDate(customerLimitRepository.createDate());
        serviceTypeModel.setLastModifiedDateTime(customerLimitRepository.createTime());

        try {
            serviceTypeRepository.save(serviceTypeModel);
            log.info("saved");
        } catch (Exception e) {
            log.info(e.toString());
            throw new MyCustomException("kindly try again later");
        }
        return CustomResponse.response("success", "00", serviceTypeRequest);
    }
}
