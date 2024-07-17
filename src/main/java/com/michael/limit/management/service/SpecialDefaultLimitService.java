package com.michael.limit.management.service;

import com.michael.limit.management.dto.specialLimit.SpecialLimitRequestDto;

import java.util.Map;

public interface SpecialDefaultLimitService {

    Map<String, Object> add(SpecialLimitRequestDto specialLimitRequestDto, String serviceToken, String serviceIpAddress, int i, String userToken);
}
