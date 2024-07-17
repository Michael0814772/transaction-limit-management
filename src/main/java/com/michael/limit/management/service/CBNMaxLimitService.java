package com.michael.limit.management.service;

import com.michael.limit.management.dto.cbnMaxLimit.RegisterCbnLimitDto;
import com.michael.limit.management.dto.cbnMaxLimit.UpdateCbnLimitDto;

import java.util.Map;

public interface CBNMaxLimitService {
    Map<String, Object> registerCbnLimit(RegisterCbnLimitDto registerCbnLimitDto, String serviceToken, String serviceIpAddress);

    Map<String, Object> updateCbnLimit(UpdateCbnLimitDto updateCbnLimitDto, String serviceToken, String serviceIpAddress);

}
