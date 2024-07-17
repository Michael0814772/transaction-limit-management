package com.michael.limit.management.service;

import com.michael.limit.management.dto.serviceTypeDto.ServiceTypeRequest;

import java.util.Map;

public interface ServiceTypeService {

    Map<String, Object> serviceType(ServiceTypeRequest serviceTypeRequest);
}
