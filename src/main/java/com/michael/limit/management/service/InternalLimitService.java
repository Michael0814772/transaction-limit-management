package com.michael.limit.management.service;

import com.michael.limit.management.dto.defaultLimitDto.DefaultLimitRequestDto;
import com.michael.limit.management.exception.exceptionMethod.MyCustomException;

import java.util.Map;

public interface InternalLimitService {

    Map<String, Object> defaultLimit(DefaultLimitRequestDto defaultLimitRequestDto, String serviceToken, String serviceIpAddress) throws MyCustomException;

    Map<String, Object> updateDefaultLimit(DefaultLimitRequestDto defaultLimitRequestDto, String serviceToken, String serviceIpAddress) throws MyCustomException;
}
