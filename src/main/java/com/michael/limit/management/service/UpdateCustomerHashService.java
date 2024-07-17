package com.michael.limit.management.service;

import com.michael.limit.management.dto.response.ResponseDto;
import com.michael.limit.management.dto.updateCustomerHash.UpdateCustomerHash;
import com.michael.limit.management.exception.exceptionMethod.MyCustomException;

import java.util.concurrent.ExecutionException;

public interface UpdateCustomerHashService {
    ResponseDto updateHash(UpdateCustomerHash updateCustomerHash, String serviceToken, String serviceIpAddress) throws MyCustomException;

    ResponseDto updateAllHash(String serviceToken, String serviceIpAddress) throws MyCustomException, ExecutionException, InterruptedException;
}
