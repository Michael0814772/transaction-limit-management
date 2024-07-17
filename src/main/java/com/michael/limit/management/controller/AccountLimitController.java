package com.michael.limit.management.controller;

import com.michael.limit.management.dto.DailyLimitUsageDto.DailyLimitUsageRequestDto;
import com.michael.limit.management.dto.cbnMaxLimit.RegisterCbnLimitDto;
import com.michael.limit.management.dto.cbnMaxLimit.UpdateCbnLimitDto;
import com.michael.limit.management.dto.createAccountLimit.DeleteLimitRequest;
import com.michael.limit.management.dto.createAccountLimit.UpdateLimitRequest;
import com.michael.limit.management.dto.defaultLimitDto.DefaultLimitRequestDto;
import com.michael.limit.management.dto.enquiry.EnquiryRequestDto;
import com.michael.limit.management.dto.health.HealthDto;
import com.michael.limit.management.dto.productType.ProductTypeDto;
import com.michael.limit.management.dto.response.ResponseDto;
import com.michael.limit.management.dto.serviceTypeDto.ServiceTypeRequest;
import com.michael.limit.management.dto.specialLimit.SpecialLimitRequestDto;
import com.michael.limit.management.dto.updateCustomerHash.UpdateCustomerHash;
import com.michael.limit.management.exception.exceptionMethod.MyCustomException;
import com.michael.limit.management.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(path = "/limit-management")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AccountLimitController {

    private final CustomerLimitService customerLimitService;

    private final DailyLimitUsageService dailyLimitUsageService;

    private final ServiceTypeService serviceTypeService;

    private final InternalLimitService InternalLimitService;

    private final HttpServletRequest request;

    private final CBNMaxLimitService cbnMaxLimitService;

    private final ProductTypeService productTypeService;

    private final UpdateCustomerHashService updateCustomerHashService;

    private final SpecialDefaultLimitService specialDefaultLimitService;

    @GetMapping(path = "/health")
    public HealthDto getHealth() {
        HealthDto healthDto = new HealthDto();
        healthDto.setStatus("Healthy");
        healthDto.setStatusCode(HttpStatus.OK);
        return healthDto;
    }

    @PostMapping(path = "api/v1/update/customer/limit/service")
    public Map<String, Object> updateService(@Valid @RequestBody UpdateLimitRequest createLimitRequest,
                                      @RequestHeader(value = "serviceToken") String serviceToken,
                                      @RequestHeader(value = "requestSourceIp") String serviceIpAddress) throws MyCustomException, ExecutionException, InterruptedException {
        log.info("customer limit: " + createLimitRequest);
        int internal = 1;
        return customerLimitService.update(createLimitRequest, serviceToken, serviceIpAddress, "", internal);
    }

    @PostMapping(path = "api/v1/update/customer/limit")
    public Map<String, Object> update(@Valid @RequestBody UpdateLimitRequest createLimitRequest,
                                      @RequestHeader(value = "userToken") String userToken,
                                      @RequestHeader(value = "serviceToken") String serviceToken,
                                      @RequestHeader(value = "requestSourceIp") String serviceIpAddress) throws MyCustomException, ExecutionException, InterruptedException {
        log.info("customer limit: " + createLimitRequest);
        int internal = 2;
        return customerLimitService.update(createLimitRequest, serviceToken, serviceIpAddress, userToken, internal);
    }

    @PostMapping(path = "api/v1/delete/customer/limit")
    public ResponseDto delete(@Valid @RequestBody DeleteLimitRequest deleteLimitRequest,
                              @RequestHeader(value = "userToken") String userToken,
                              @RequestHeader(value = "serviceToken") String serviceToken,
                              @RequestHeader(value = "requestSourceIp") String serviceIpAddress) throws MyCustomException, ExecutionException, InterruptedException {
        log.info("delete customer limit: " + deleteLimitRequest);
        return customerLimitService.delete(deleteLimitRequest, serviceToken, serviceIpAddress, userToken);
    }

    @PostMapping(path = "api/v1/daily/limit")
    public Map<String, Object> dailyLimit(@Valid @RequestBody DailyLimitUsageRequestDto dailyLimitUsageRequestDto,
                                          String serviceToken, String serviceIpAddress) throws MyCustomException {
        log.info("daily limit reserve: " + dailyLimitUsageRequestDto);
        String url = request.getRequestURI();
        log.info("url: " + url);
        return dailyLimitUsageService.dailyLimit(dailyLimitUsageRequestDto, url, serviceToken, serviceIpAddress);
    }

    @PostMapping(path = "api/v1/reverse/daily/limit")
    public Map<String, Object> reverseLimit(@Valid @RequestBody DailyLimitUsageRequestDto dailyLimitUsageRequestDto,
//                                            @RequestHeader(value = "userToken") String userToken,
                                            @RequestHeader(value = "serviceToken") String serviceToken,
                                            @RequestHeader(value = "requestSourceIp") String serviceIpAddress) throws MyCustomException {
        log.info("reverse limit reserve: " + dailyLimitUsageRequestDto);
        String url = request.getRequestURI();
        log.info("url: " + url);
        return dailyLimitUsageService.dailyLimit(dailyLimitUsageRequestDto, url, serviceToken, serviceIpAddress);
    }

    @PostMapping(path = "api/v1/limit/enquiry")
    public Map<String, Object> enquiryLimit(@Valid @RequestBody EnquiryRequestDto enquiryRequestDto) throws MyCustomException {
        log.info("enquiry: " + enquiryRequestDto);
        return dailyLimitUsageService.enquiryLimit(enquiryRequestDto);
    }

    @PostMapping(path = "api/v1/servicetype")
    public Map<String, Object> serviceType(@Valid @RequestBody ServiceTypeRequest serviceTypeRequest) throws MyCustomException {
        return serviceTypeService.serviceType(serviceTypeRequest);
    }

    @PostMapping(path = "api/v1/create/default/limit")
    public Map<String, Object> defaultLimit(@Valid @RequestBody DefaultLimitRequestDto defaultLimitRequestDto,
                                            @RequestHeader(value = "serviceToken") String serviceToken,
                                            @RequestHeader(value = "requestSourceIp") String serviceIpAddress) throws MyCustomException {
        log.info("create Internal limit: " + defaultLimitRequestDto);
        return InternalLimitService.defaultLimit(defaultLimitRequestDto, serviceToken, serviceIpAddress);
    }

    @PostMapping(path = "api/v1/update/default/limit")
    public Map<String, Object> updateDefaultLimit(@Valid @RequestBody DefaultLimitRequestDto defaultLimitRequestDto,
                                                  @RequestHeader(value = "serviceToken") String serviceToken,
                                                  @RequestHeader(value = "requestSourceIp") String serviceIpAddress) throws MyCustomException {
        log.info("update Internal limit: " + defaultLimitRequestDto);
        return InternalLimitService.updateDefaultLimit(defaultLimitRequestDto, serviceToken, serviceIpAddress);
    }

    @PostMapping(path = "api/v1/register/cbn/limit")
    public Map<String, Object> registerCbnLimit(@Valid @RequestBody RegisterCbnLimitDto registerCbnLimitDto,
                                                @RequestHeader(value = "serviceToken") String serviceToken,
                                                @RequestHeader(value = "requestSourceIp") String serviceIpAddress) throws MyCustomException {
        log.info("create cbn limit: " + registerCbnLimitDto);
        return cbnMaxLimitService.registerCbnLimit(registerCbnLimitDto, serviceToken, serviceIpAddress);
    }

    @PostMapping(path = "api/v1/update/cbn/limit")
    public Map<String, Object> updateCbnLimit(@Valid @RequestBody UpdateCbnLimitDto updateCbnLimitDto,
                                              @RequestHeader(value = "serviceToken") String serviceToken,
                                              @RequestHeader(value = "requestSourceIp") String serviceIpAddress) throws MyCustomException {
        log.info("update cbn limit: " + updateCbnLimitDto);
        return cbnMaxLimitService.updateCbnLimit(updateCbnLimitDto, serviceToken, serviceIpAddress);
    }

    @PostMapping(path = "api/v1/create/product/type")
    public Map<String, Object> productType(@Valid @RequestBody ProductTypeDto productTypeDto) throws MyCustomException {
        return productTypeService.productType(productTypeDto);
    }

    @PostMapping(path = "api/v1/update/customer/hash")
    public ResponseDto updateHash(@Valid @RequestBody UpdateCustomerHash updateCustomerHash,
                                  @RequestHeader(value = "serviceToken") String serviceToken,
                                  @RequestHeader(value = "requestSourceIp") String serviceIpAddress) throws MyCustomException {
        return updateCustomerHashService.updateHash(updateCustomerHash, serviceToken, serviceIpAddress);
    }

    @GetMapping(path = "api/v1/update/all-customer/hash")
    public ResponseDto updateAllHash(@Valid @RequestHeader(value = "serviceToken") String serviceToken,
                                     @RequestHeader(value = "requestSourceIp") String serviceIpAddress) throws MyCustomException, ExecutionException, InterruptedException {
        return updateCustomerHashService.updateAllHash(serviceToken, serviceIpAddress);
    }

    @PostMapping(path = "api/v1/add/special/limit/service")
    public Map<String, Object> addSpecialLimitService(@Valid @RequestBody SpecialLimitRequestDto specialLimitRequestDto,
                                               @RequestHeader(value = "serviceToken") String serviceToken,
                                               @RequestHeader(value = "requestSourceIp") String serviceIpAddress) throws MyCustomException {
        return specialDefaultLimitService.add(specialLimitRequestDto, serviceToken, serviceIpAddress, 1, "");
    }

    @PostMapping(path = "api/v1/add/special/limit/user")
    public Map<String, Object> addSpecialLimitUser(@Valid @RequestBody SpecialLimitRequestDto specialLimitRequestDto,
                                                   @RequestHeader(value = "userToken") String userToken,
                                                   @RequestHeader(value = "serviceToken") String serviceToken,
                                                   @RequestHeader(value = "requestSourceIp") String serviceIpAddress) throws MyCustomException {
        return specialDefaultLimitService.add(specialLimitRequestDto, serviceToken, serviceIpAddress, 2, userToken);
    }
}
