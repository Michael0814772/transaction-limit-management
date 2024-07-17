package com.michael.limit.management.service.impl;

import com.michael.limit.management.dto.authentication.ResponseMessage;
import com.michael.limit.management.dto.databaseDto.AccountDetailsDto;
import com.michael.limit.management.dto.response.ResponseDto;
import com.michael.limit.management.dto.updateCustomerHash.UpdateCustomerHash;
import com.michael.limit.management.exception.exceptionMethod.InternalServerException;
import com.michael.limit.management.exception.exceptionMethod.MyCustomException;
import com.michael.limit.management.exception.exceptionMethod.MyCustomizedException;
import com.michael.limit.management.httpCall.HttpCall;
import com.michael.limit.management.model.*;
import com.michael.limit.management.repository.*;
import com.michael.limit.management.service.UpdateCustomerHashService;
import com.michael.limit.management.utils.HelperUtils;
import com.michael.limit.management.utils.LastModifiedBy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
@Slf4j
@RequiredArgsConstructor
public class UpdateCustomerHashServiceImpl implements UpdateCustomerHashService {

    private final CustomerLimitRepository customerLimitRepository;

    private final GlobalLimitRepository globalLimitRepository;

    private final NipLimitRepository nipLimitRepository;

    private final ChannelLimitRepository channelLimitRepository;

    private final ProductTypeRepository productTypeRepository;

    private final HelperUtils helperUtils;

    private final LastModifiedBy lastModifiedBy;

    private final HttpCall httpCall;

    @Override
    public ResponseDto updateHash(UpdateCustomerHash updateCustomerHash, String serviceToken, String serviceIpAddress) throws MyCustomException {
        log.info("updating customer hash...");

        ResponseMessage responseMessage = authenticationValidation(serviceToken, serviceIpAddress);

        if (!responseMessage.getResponseCode().equalsIgnoreCase("00")) {
            throw new MyCustomizedException("header authentication failed");
        }

        if (responseMessage.getServiceAccessLevel() < 9) {
            throw new MyCustomizedException("unauthorized access");
        }

        ProductType productType = checkProductType(updateCustomerHash);

        AccountDetailsDto getCif = helperUtils.getWithCifOrAccount(updateCustomerHash.getAccountNumber());

        CustomerLimitModel customerLimitModel = customerLimitRepository.findByCifId(getCif.getCifId(), productType.getTransfer(), productType.getProduct());

        if (customerLimitModel == null) {
            String message = String.format("customer with cif id %s does not exist", getCif.getCifId());
            throw new MyCustomizedException(message);
        }

        CompletableFuture<Void> globalChannel = CompletableFuture.runAsync(() ->
                updateGlobalChannel(customerLimitModel, getCif.getCifId()));

        CompletableFuture<Void> nipChannel = CompletableFuture.runAsync(() ->
                updateNipChannel(customerLimitModel, getCif.getCifId()));

        CompletableFuture<Void> channelCode = CompletableFuture.runAsync(() ->
                updateChannelCode(customerLimitModel, getCif.getCifId()));

        globalChannel.join();
        nipChannel.join();
        channelCode.join();

        try {
            customerLimitRepository.save(customerLimitModel);
        } catch (Exception e) {
            log.info("error: " + e);
            throw new MyCustomizedException("kindly try again later...");
        }

        ResponseDto responseDto = new ResponseDto();
        responseDto.setResponseCode("00");
        responseDto.setResponseMsg("updated successfully");
        return responseDto;
    }

    private ProductType checkProductType(UpdateCustomerHash updateCustomerHash) {

        ProductType productType = productTypeRepository.findByProduct(updateCustomerHash.getProductType(), updateCustomerHash.getTransferType());

        if (productType == null) {
            String message = String.format("Transfer of transferType %s and product type %s has not been configured", updateCustomerHash.getTransferType(), updateCustomerHash.getProductType());
            throw new MyCustomException(message);
        }
        return productType;
    }

    @Override
    public ResponseDto updateAllHash(String serviceToken, String serviceIpAddress) throws MyCustomException, ExecutionException, InterruptedException {
        log.info("updating all customer hash...");

        ResponseMessage responseMessage = authenticationValidation(serviceToken, serviceIpAddress);

        if (!responseMessage.getResponseCode().equalsIgnoreCase("00")) {
            throw new MyCustomizedException("header authentication failed");
        }

        if (responseMessage.getServiceAccessLevel() < 9) {
            throw new MyCustomizedException("unauthorized access");
        }

        List<GlobalLimit> globalLimits = globalLimitRepository.findAll();

        if (!globalLimits.isEmpty()) {

            for (GlobalLimit globalLimit : globalLimits) {
                log.info("updating for global limit with cif id: " + globalLimit.getCifId());

                String hash = helperUtils.globalHashMethod(globalLimit);

                if (!globalLimit.getHash().equals(hash)) {
                    log.info("updating for global limit with cif id: " + globalLimit.getCifId());
                    globalLimit.setHash(hash);
                    globalLimit.setLastModifiedDate(customerLimitRepository.createDate());
                    globalLimit.setLastModifiedDateTime(customerLimitRepository.createTime());
                    globalLimit.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
                    globalLimit = globalLimitRepository.save(globalLimit);
                    log.info("successfully saved global limit for cif id: " + globalLimit.getCifId());
                }
            }
        }

        List<NipLimit> nipLimitList = nipLimitRepository.findAll();

        if (!nipLimitList.isEmpty()) {
            for (NipLimit nipLimit : nipLimitList) {
                String hash = helperUtils.nipHashMethod(nipLimit);

                if (!nipLimit.getHash().equals(hash)) {
                    log.info("updating for nip limit with cif id: " + nipLimit.getCifId());
                    nipLimit.setHash(hash);
                    nipLimit.setLastModifiedDate(customerLimitRepository.createDate());
                    nipLimit.setLastModifiedDateTime(customerLimitRepository.createTime());
                    nipLimit.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
                    nipLimit = nipLimitRepository.save(nipLimit);
                    log.info("successfully saved nip limit for cif id: " + nipLimit.getCifId());
                }
            }
        }

        List<ChannelCode> channelCodeList = channelLimitRepository.findAll();

        if (!channelCodeList.isEmpty()) {

            for (ChannelCode check : channelCodeList) {
                String hash = helperUtils.channelHashMethod(check);

                if (!check.getHash().equals(hash)) {
                    log.info("updated for channel " + check.getChannelId() + " limit with cif id: " + check.getCifId());
                    check.setHash(hash);
                    check.setLastModifiedDate(customerLimitRepository.createDate());
                    check.setLastModifiedDateTime(customerLimitRepository.createTime());
                    check.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
                    check = channelLimitRepository.saveAndFlush(check);
                    log.info("successfully saved for channel " + check.getChannelId() + " limit with cif id: " + check.getCifId());
                }
            }
        }

        ResponseDto responseDto = new ResponseDto();
        responseDto.setResponseMsg("updated successfully");
        responseDto.setResponseCode("00");
        return responseDto;
    }

    private ResponseMessage authenticationValidation(String serviceToken, String serviceIpAddress) {

        ResponseMessage responseMessage = new ResponseMessage();

        try {
            Map<String, Object> getResponse = httpCall.serviceToken(serviceToken, serviceIpAddress);
            log.info("response: " + getResponse);

            if (getResponse.get("responseCode") != null) {
                responseMessage.setResponseCode((String) getResponse.get("responseCode"));
            } else {
                responseMessage.setResponseCode("99");
            }

            if (getResponse.get("accessLevel") != null) {
                responseMessage.setServiceAccessLevel(Integer.valueOf((String) getResponse.get("accessLevel")));
            }
        } catch (Exception e) {
            log.info(e.toString());
            throw new InternalServerException("Err - Technical Error with dependency");
        }

        return responseMessage;
    }

    private void updateChannelCode(CustomerLimitModel customerLimitModel, String getCif) {

        List<ChannelCode> updateChannelLimit = customerLimitModel.getChannelCode();

        if (!updateChannelLimit.isEmpty()) {

            for (ChannelCode check : updateChannelLimit) {
                String hash = helperUtils.channelHashMethod(check);

                if (!check.getHash().equals(hash)) {
                    log.info("updated for channel " + check.getChannelId() + " limit with cif id: " + getCif);
                    check.setHash(hash);
                    check.setLastModifiedDate(customerLimitRepository.createDate());
                    check.setLastModifiedDateTime(customerLimitRepository.createTime());
                    check.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
                    customerLimitModel.setChannelCode(updateChannelLimit);
                }
            }
        }
    }

    private void updateNipChannel(CustomerLimitModel customerLimitModel, String getCif) {

        NipLimit updateNipLimit = customerLimitModel.getNipLimit();

        if (updateNipLimit != null) {
            String hash = helperUtils.nipHashMethod(updateNipLimit);

            if (!updateNipLimit.getHash().equals(hash)) {
                log.info("updated for nip limit with cif id: " + getCif);
                updateNipLimit.setHash(hash);
                updateNipLimit.setLastModifiedDate(customerLimitRepository.createDate());
                updateNipLimit.setLastModifiedDateTime(customerLimitRepository.createTime());
                updateNipLimit.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
                customerLimitModel.setNipLimit(updateNipLimit);
            }
        }
    }

    private void updateGlobalChannel(CustomerLimitModel customerLimitModel, String getCif) {

        GlobalLimit updateGlobalHash = customerLimitModel.getGlobalLimit();

        if (updateGlobalHash != null) {
            String hash = helperUtils.globalHashMethod(updateGlobalHash);

            if (!updateGlobalHash.getHash().equals(hash)) {
                log.info("updated for global limit with cif id: " + getCif);
                updateGlobalHash.setHash(hash);
                updateGlobalHash.setLastModifiedDate(customerLimitRepository.createDate());
                updateGlobalHash.setLastModifiedDateTime(customerLimitRepository.createTime());
                updateGlobalHash.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
                customerLimitModel.setGlobalLimit(updateGlobalHash);
            }
        }
    }
}
