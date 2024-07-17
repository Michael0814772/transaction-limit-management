package com.michael.limit.management.service;

import com.michael.limit.management.dto.DailyLimitUsageDto.DailyLimitUsageRequestDto;
import com.michael.limit.management.dto.enquiry.EnquiryRequestDto;
import com.michael.limit.management.exception.exceptionMethod.MyCustomException;

import java.util.Map;

public interface DailyLimitUsageService {

    Map<String, Object> dailyLimit(DailyLimitUsageRequestDto dailyLimitUsageRequestDto, String url, String serviceToken, String serviceIpAddress) throws MyCustomException;

    Map<String, Object> enquiryLimit(EnquiryRequestDto enquiryRequestDto) throws MyCustomException;
}
