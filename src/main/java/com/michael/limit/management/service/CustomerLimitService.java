package com.michael.limit.management.service;

import com.michael.limit.management.dto.createAccountLimit.DeleteLimitRequest;
import com.michael.limit.management.dto.createAccountLimit.UpdateLimitRequest;
import com.michael.limit.management.dto.response.ResponseDto;
import com.michael.limit.management.exception.exceptionMethod.MyCustomException;

import java.util.Map;
import java.util.concurrent.ExecutionException;

public interface CustomerLimitService {

    Map<String, Object> update(UpdateLimitRequest createLimitRequest, String serviceToken, String serviceIpAddress, String userToken, int internal) throws MyCustomException, ExecutionException, InterruptedException;

    ResponseDto delete(DeleteLimitRequest deleteLimitRequest, String serviceToken, String serviceIpAddress, String userToken) throws MyCustomException, ExecutionException, InterruptedException;
}
