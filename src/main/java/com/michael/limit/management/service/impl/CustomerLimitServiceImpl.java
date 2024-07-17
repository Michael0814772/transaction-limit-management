package com.michael.limit.management.service.impl;

import com.michael.limit.management.custom.CustomResponse;
import com.michael.limit.management.dto.auditLimit.UpdateChannelAudit;
import com.michael.limit.management.dto.createAccountLimit.DeleteLimitRequest;
import com.michael.limit.management.dto.createAccountLimit.UpdateChannelLimit;
import com.michael.limit.management.dto.createAccountLimit.UpdateGlobalLimit;
import com.michael.limit.management.dto.createAccountLimit.UpdateLimitRequest;
import com.michael.limit.management.dto.databaseDto.AccountDetailsDto;
import com.michael.limit.management.dto.response.ResponseDto;
import com.michael.limit.management.exception.exceptionMethod.InternalServerException;
import com.michael.limit.management.exception.exceptionMethod.MyCustomException;
import com.michael.limit.management.exception.exceptionMethod.MyCustomizedException;
import com.michael.limit.management.model.*;
import com.michael.limit.management.repository.*;
import com.michael.limit.management.service.CustomerLimitService;
import com.michael.limit.management.utils.HelperUtils;
import com.michael.limit.management.utils.LastModifiedBy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerLimitServiceImpl implements CustomerLimitService {

    private final CustomerLimitRepository customerLimitRepository;

    private final SummaryDetailRepository summaryDetailRepository;

    private final GlobalLimitRepository globalLimitRepository;

    private final NipLimitRepository nipLimitRepository;

    private final ChannelLimitRepository channelLimitRepository;

    private final AuditLimitRepository auditLimitRepository;

    private final LastModifiedBy lastModifiedBy;

    private final DailyLimitUsageRepository dailyLimitUsageRepository;

    private final HelperUtils helperUtils;

    private final CBNMaxLimitRepository cbnMaxLimitRepository;

    private final InternalLimitRepository InternalLimitRepository;

    private final SpecialDefaultLimitRepository specialDefaultLimitRepository;

    private final ProductTypeRepository productTypeRepository;

    @Transactional(rollbackFor = {DataIntegrityViolationException.class, Exception.class})
    @Override
    public Map<String, Object> update(UpdateLimitRequest updateLimitRequest, String serviceToken, String serviceIpAddress, String userToken, int internal) throws MyCustomException, ExecutionException, InterruptedException {
        //log.info("update customer limit...");

        String accountNumber = helperUtils.accountNumber(updateLimitRequest.getAccountNumber(), updateLimitRequest.getCifId());

        if (internal == 2) {
            helperUtils.userAuth(userToken, serviceToken, serviceIpAddress, updateLimitRequest.getAccountNumber(), updateLimitRequest.getChannelCode(), updateLimitRequest.getCifId());
        } else {
            helperUtils.serviceAuth(serviceToken, serviceIpAddress);
        }

        String createTime = customerLimitRepository.createTime();
        ProductType productType = productTypeRepository.findByProduct(updateLimitRequest.getProductType(), updateLimitRequest.getTransferType());

        if (productType == null) {
            String message = String.format("Transfer of transferType %s and product type %s has not been configured", updateLimitRequest.getTransferType(), updateLimitRequest.getProductType());
            throw new MyCustomException(message);
        }

        AuditLimitModel auditLimitModel = new AuditLimitModel();
        String presentDate = customerLimitRepository.createDate(); //present date

        UpdateChannelAudit updateChannelAudit = new UpdateChannelAudit();

        //random letter
        AtomicReference<String> randomLetters = new AtomicReference<>("");

        CompletableFuture<Void> randomLetter = CompletableFuture.runAsync(() ->
                randomLetters.set(generateRandomLetters()));

        AccountDetailsDto findCifId = helperUtils.getWithCifOrAccount(accountNumber);

        //log.info("findCifId: " + findCifId);

        comparePerAndDailyLimitMethod(updateLimitRequest);

        updateLimitRequest.setCifType(findCifId.getCifType());

        validateFields(updateLimitRequest, findCifId.getKycLevel());

        CustomerLimitModel customerLimitModel = customerLimitRepository.findByCifId(findCifId.getCifId(), productType.getTransfer(), productType.getProduct());

        GlobalLimit globalLimit1;

        int cbnGlobalLimit = findCifId.getKycLevel();

        randomLetter.join();

        customerLimitModel = setCustomerModel(customerLimitModel, findCifId.getCifId(), updateLimitRequest.getCifType(), productType, createTime);
        //log.info("customerLimitModel: " + customerLimitModel);

        InternalLimitModel getInternalLimit = InternalLimitRepository.findByKycLevel(findCifId.getKycLevel(), findCifId.getCifType(), productType.getTransfer(), productType.getProduct());

        CBNMaxLimitModel findCbnLimit = cbnMaxLimitRepository.findByKycId(cbnGlobalLimit, updateLimitRequest.getCifType(), productType.getTransfer(), productType.getProduct());

        SummaryDetailModel getRecentSummaryTrans = summaryDetailRepository.findByCifId(findCifId.getCifId(), presentDate, productType.getTransfer(), productType.getProduct());

        if (getInternalLimit == null) {
            String message = String.format("Internal max limit has not be configured for kyc level %s", cbnGlobalLimit);
            throw new MyCustomizedException(message);
        }

        //log.info("getInternalLimit: " + getInternalLimit);

        if (findCbnLimit == null) {
            String message = String.format("Cbn max limit has not be configured for kyc level %s", cbnGlobalLimit);
            throw new MyCustomizedException(message);
        }

        //log.info("findCbnLimit: " + findCbnLimit);

        List<SpecialDefaultLimit> findByCif = specialDefaultLimitRepository.findByCif(findCifId.getCifId(), productType.getTransfer(), productType.getProduct());

        if (updateLimitRequest.getChannelLimits() != null) {
            ChannelCode channelCode1 = channelLimitRepository.findUssdLimitById(String.valueOf(updateLimitRequest.getChannelLimits().getChannelCode()), findCifId.getCifId(), productType.getTransfer(), productType.getProduct());

            globalLimit1 = globalLimitRepository.findGlobalLimitByCifId(findCifId.getCifId(), productType.getTransfer(), productType.getProduct());

            BigDecimal globalDailyAmt = globalLimit1 != null ? globalLimit1.getTotalDailyLimit() : getInternalLimit.getGlobalDailyTransaction();
            BigDecimal globalPerAmt = globalLimit1 != null ? globalLimit1.getPerTransactionLimit() : getInternalLimit.getGlobalPerTransaction();

            compareChannelAmountToZero(updateLimitRequest);

            if (channelCode1 == null) {
                channelCode1 = new ChannelCode();
                channelCode1.setCreatedDate(customerLimitRepository.createDate());
                channelCode1.setCreatedTime(createTime);
                channelCode1.setTransferType(productType.getTransfer());
                channelCode1.setProductType(productType.getProduct());
            }

            channelCode1.setTotalDailyLimit(updateLimitRequest.getChannelLimits().getTotalDailyLimit());
            channelCode1.setPerTransactionLimit(updateLimitRequest.getChannelLimits().getPerTransactionLimit());

            channelCode1 = channelLimitToSave(findCifId.getCifId(), customerLimitModel, channelCode1, updateLimitRequest, productType, createTime);
            //log.info("channel saved: " + channelCode1);

            channelAuditLimit(channelCode1, findCifId.getCifId(), updateLimitRequest.getChannelLimits(), auditLimitModel);

//            setChannelAudit(updateChannelAudit, auditLimitModel);

            if (globalLimit1 == null) {
                globalLimit1 = new GlobalLimit();
                globalLimit1.setCreatedDate(customerLimitRepository.createDate());
                globalLimit1.setCreatedTime(createTime);
                globalLimit1.setTransferType(productType.getTransfer());
                globalLimit1.setProductType(productType.getProduct());
            }

            if (channelCode1.getPerTransactionLimit().compareTo(globalPerAmt) > 0) {
                if (findByCif.isEmpty()) {
                    if (channelCode1.getPerTransactionLimit().compareTo(findCbnLimit.getGlobalPerTransaction()) > 0) {
                        throw new MyCustomizedException("global limit per transaction cannot be greater than cbn max limit");
                    }
                }
                globalLimit1.setPerTransactionLimit(channelCode1.getPerTransactionLimit());
            } else {
                globalLimit1.setPerTransactionLimit(globalPerAmt);
            }

            if (channelCode1.getTotalDailyLimit().compareTo(globalDailyAmt) > 0) {
                if (findByCif.isEmpty()) {
                    if (channelCode1.getTotalDailyLimit().compareTo(findCbnLimit.getGlobalDailyLimit()) > 0) {
                        throw new MyCustomizedException("global limit daily transaction cannot be greater than cbn max limit");
                    }
                }
                globalLimit1.setTotalDailyLimit(channelCode1.getTotalDailyLimit());
                auditLimitModel.setGlobalDailyLimitBf(globalDailyAmt);
                auditLimitModel.setGlobalDailyLimitCf(channelCode1.getTotalDailyLimit());
            } else {
                globalLimit1.setTotalDailyLimit(globalDailyAmt);
                auditLimitModel.setGlobalDailyLimitBf(globalDailyAmt);
                auditLimitModel.setGlobalDailyLimitCf(globalDailyAmt);
            }

            globalLimit1 = saveGlobalLimitSet(customerLimitModel, globalLimit1, findCifId.getCifId(), productType, createTime);
            //log.info("globalLimit: " + globalLimit1);
        }

        //set nip limit
        if (updateLimitRequest.getNipLimit() != null) {
            NipLimit nipLimit1 = nipLimitRepository.findNipLimitByCifId(findCifId.getCifId(), productType.getTransfer(), productType.getProduct());

            if (nipLimit1 == null) {
                nipLimit1 = new NipLimit();
                nipLimit1.setCreatedDate(customerLimitRepository.createDate());
                nipLimit1.setCreatedTime(createTime);
                nipLimit1.setTransferType(productType.getTransfer());
                nipLimit1.setProductType(productType.getProduct());
            }

            compareNipAmountToZero(updateLimitRequest);
            nipLimit1.setPerTransactionLimit(updateLimitRequest.getNipLimit().getPerTransactionLimit());

            nipLimit1.setTotalDailyLimit(updateLimitRequest.getNipLimit().getTotalDailyLimit());
            auditLimitModel.setNipDailyLimitBf(getRecentSummaryTrans != null ? getRecentSummaryTrans.getNipDailyLimitCf() : getInternalLimit.getNipDailyTransaction());
            auditLimitModel.setNipDailyLimitCf(nipLimit1.getTotalDailyLimit());

            nipLimit1 = setNipLimitToSave(nipLimit1, findCifId.getCifId(), customerLimitModel, productType, createTime);
            //log.info("nipLimit saved: " + nipLimit1);

            globalLimit1 = globalLimitRepository.findGlobalLimitByCifId(findCifId.getCifId(), productType.getTransfer(), productType.getProduct());

            BigDecimal globalDailyAmt = globalLimit1 != null ? globalLimit1.getTotalDailyLimit() : getInternalLimit.getGlobalDailyTransaction();
            BigDecimal globalPerAmt = globalLimit1 != null ? globalLimit1.getPerTransactionLimit() : getInternalLimit.getGlobalPerTransaction();

            if (globalLimit1 == null) {
                globalLimit1 = new GlobalLimit();
                globalLimit1.setCreatedDate(customerLimitRepository.createDate());
                globalLimit1.setCreatedTime(createTime);
                globalLimit1.setTransferType(productType.getTransfer());
                globalLimit1.setProductType(productType.getProduct());
            }

            if (nipLimit1.getPerTransactionLimit().compareTo(globalPerAmt) > 0) {
                if (findByCif.isEmpty()) {
                    if (nipLimit1.getPerTransactionLimit().compareTo(findCbnLimit.getGlobalPerTransaction()) > 0) {
                        throw new MyCustomizedException("global limit per transaction cannot be greater than cbn max limit");
                    }
                }
                globalLimit1.setPerTransactionLimit(nipLimit1.getPerTransactionLimit());
            } else {
                globalLimit1.setPerTransactionLimit(globalPerAmt);
            }

            if (nipLimit1.getTotalDailyLimit().compareTo(globalDailyAmt) > 0) {
                if (findByCif.isEmpty()) {
                    if (nipLimit1.getTotalDailyLimit().compareTo(findCbnLimit.getGlobalDailyLimit()) > 0) {
                        throw new MyCustomizedException("global limit daily transaction cannot be greater than cbn max limit");
                    }
                }
                globalLimit1.setTotalDailyLimit(nipLimit1.getTotalDailyLimit());
                auditLimitModel.setGlobalDailyLimitBf(getRecentSummaryTrans != null ? getRecentSummaryTrans.getGlobalDailyLimitCf() : globalDailyAmt);
                auditLimitModel.setGlobalDailyLimitCf(nipLimit1.getTotalDailyLimit());
            } else {
                globalLimit1.setTotalDailyLimit(globalDailyAmt);
                auditLimitModel.setGlobalDailyLimitBf(getRecentSummaryTrans != null ? getRecentSummaryTrans.getGlobalDailyLimitCf() : globalDailyAmt);
                auditLimitModel.setGlobalDailyLimitCf(globalDailyAmt);
            }

            globalLimit1 = saveGlobalLimitSet(customerLimitModel, globalLimit1, findCifId.getCifId(), productType, createTime);
            //log.info("globalLimit: " + globalLimit1);
        }

        //set global limit
        if (updateLimitRequest.getGlobalLimit() != null) {

            globalLimit1 = globalLimitRepository.findGlobalLimitByCifId(findCifId.getCifId(), productType.getTransfer(), productType.getProduct());

            if (globalLimit1 == null) {
                globalLimit1 = new GlobalLimit();
                globalLimit1.setCreatedDate(customerLimitRepository.createDate());
                globalLimit1.setCreatedTime(createTime);
                globalLimit1.setTransferType(productType.getTransfer());
                globalLimit1.setProductType(productType.getProduct());
            }

            compareGlobalAmountZero(updateLimitRequest);

            if (updateLimitRequest.getGlobalLimit().getPerTransactionLimit() != null) {
                if (findByCif.isEmpty()) {
                    if (updateLimitRequest.getGlobalLimit().getPerTransactionLimit().compareTo(findCbnLimit.getGlobalPerTransaction()) > 0) {
                        throw new MyCustomizedException("global limit per transaction cannot be greater than cbn max limit");
                    }
                }
                globalLimit1.setPerTransactionLimit(updateLimitRequest.getGlobalLimit().getPerTransactionLimit());
            }

            if (updateLimitRequest.getGlobalLimit().getTotalDailyLimit() != null) {
                if (findByCif.isEmpty()) {
                    if (updateLimitRequest.getGlobalLimit().getTotalDailyLimit().compareTo(findCbnLimit.getGlobalDailyLimit()) > 0) {
                        throw new MyCustomizedException("global daily limit transaction cannot be greater than cbn max limit");
                    }
                }
                auditLimitModel.setGlobalDailyLimitBf(getRecentSummaryTrans != null ? getRecentSummaryTrans.getGlobalDailyLimitCf() : getInternalLimit.getGlobalDailyTransaction());
                auditLimitModel.setGlobalDailyLimitCf(updateLimitRequest.getGlobalLimit().getTotalDailyLimit());

                globalLimit1.setTotalDailyLimit(updateLimitRequest.getGlobalLimit().getTotalDailyLimit());
            }

            globalLimit1 = saveGlobalLimitSet(customerLimitModel, globalLimit1, findCifId.getCifId(), productType, createTime);
            //log.info("globalLimit: " + globalLimit1);

            //Remove once in mobile 3.0
            //update the channel where the update is coming from
            ChannelCode channelCode1 = channelLimitRepository.findUssdLimitById(String.valueOf(2), findCifId.getCifId(), productType.getTransfer(), productType.getProduct());
            BigDecimal amt = channelCode1 != null ? channelCode1.getTotalDailyLimit() : getInternalLimit.getNipDailyTransaction();

            if (channelCode1 == null) {
                channelCode1 = new ChannelCode();
                channelCode1.setCreatedDate(customerLimitRepository.createDate());
                channelCode1.setCreatedTime(createTime);
                channelCode1.setTransferType(productType.getTransfer());
                channelCode1.setProductType(productType.getProduct());
            }

            channelCode1.setTotalDailyLimit(updateLimitRequest.getGlobalLimit().getTotalDailyLimit());
            channelCode1.setPerTransactionLimit(updateLimitRequest.getGlobalLimit().getPerTransactionLimit());
            channelCode1 = channelLimitToSaveGlobal(findCifId.getCifId(), customerLimitModel, channelCode1, updateLimitRequest, 2, productType, createTime);
            //log.info("channel saved: " + channelCode1);
            auditLimitModel.setInternetDailyLimitBf(getRecentSummaryTrans != null ? getRecentSummaryTrans.getInternetDailyLimitCf() : amt);
            auditLimitModel.setInternetDailyLimitCf(updateLimitRequest.getGlobalLimit().getTotalDailyLimit());

            ChannelCode channelCode = channelLimitRepository.findUssdLimitById(String.valueOf(3), findCifId.getCifId(), productType.getTransfer(), productType.getProduct());
            amt = channelCode != null ? channelCode.getTotalDailyLimit() : getInternalLimit.getNipDailyTransaction();

            if (channelCode == null) {
                channelCode = new ChannelCode();
                channelCode.setCreatedDate(customerLimitRepository.createDate());
                channelCode.setCreatedTime(createTime);
                channelCode.setTransferType(productType.getTransfer());
                channelCode.setProductType(productType.getProduct());
            }

            channelCode.setTotalDailyLimit(updateLimitRequest.getGlobalLimit().getTotalDailyLimit());
            channelCode.setPerTransactionLimit(updateLimitRequest.getGlobalLimit().getPerTransactionLimit());
            channelCode = channelLimitToSaveGlobal(findCifId.getCifId(), customerLimitModel, channelCode, updateLimitRequest, 3, productType, createTime);
            //log.info("channel saved: " + channelCode);
            auditLimitModel.setMobileDailyLimitBf(getRecentSummaryTrans != null ? getRecentSummaryTrans.getInternetDailyLimitCf() : amt);
            auditLimitModel.setMobileDailyLimitCf(updateLimitRequest.getGlobalLimit().getTotalDailyLimit());

            NipLimit nipLimit1 = nipLimitRepository.findNipLimitByCifId(findCifId.getCifId(), productType.getTransfer(), productType.getProduct());

            if (nipLimit1 == null) {
                nipLimit1 = new NipLimit();
                nipLimit1.setCreatedDate(customerLimitRepository.createDate());
                nipLimit1.setCreatedTime(createTime);
                nipLimit1.setTransferType(productType.getTransfer());
                nipLimit1.setProductType(productType.getProduct());
            }

            nipLimit1.setPerTransactionLimit(updateLimitRequest.getGlobalLimit().getPerTransactionLimit());
            auditLimitModel.setNipDailyLimitBf(getRecentSummaryTrans != null ? getRecentSummaryTrans.getNipDailyLimitCf() : getInternalLimit.getNipDailyTransaction());
            auditLimitModel.setNipDailyLimitCf(nipLimit1.getTotalDailyLimit());
            nipLimit1.setTotalDailyLimit(updateLimitRequest.getGlobalLimit().getTotalDailyLimit());
            nipLimit1 = setNipLimitToSave(nipLimit1, findCifId.getCifId(), customerLimitModel, productType, createTime);
            //log.info("nip saved: " + nipLimit1);

            updateChannelAudit = channelAuditLimitWithGlobal(channelCode1, findCifId.getCifId(), updateLimitRequest.getGlobalLimit(), updateLimitRequest.getChannelCode());

//            setChannelAudit(updateChannelAudit, auditLimitModel);


            //compare amount of global limit set to other limit available if lesser, will reduce other limits
            compareGlobalAmountWithNipAndChannel(globalLimit1, findCifId.getCifId(), customerLimitModel, auditLimitModel, updateChannelAudit, productType, createTime);
        }

        //set other audit
        auditLimitModel.setCifId(findCifId.getCifId());
        auditLimitModel.setModifiedBy(lastModifiedBy.lastModifiedBy());
        auditLimitModel.setUpdateDate(auditLimitRepository.createDate());
        auditLimitModel.setUpdateTime(auditLimitRepository.createTime());
        auditLimitModel.setAuditId(randomLetters.get());
        auditLimitModel.setTransferType(productType.getTransfer());
        auditLimitModel.setProductType(productType.getProduct());

        String hash = helperUtils.auditLimitToHash(auditLimitModel);
        auditLimitModel.setHash(hash);

        try {
            auditLimitModel = auditLimitRepository.save(auditLimitModel);
            //log.info("audit saved: " + auditLimitModel);
        } catch (Exception e) {
            //log.info("failed to save audit limit");
            //log.info(e.toString());
            throw new MyCustomizedException("kindly try again later...");
        }

        //updating daily and summary table
        String saved = updateDailyAndSummaryTable(auditLimitModel, customerLimitModel, presentDate, updateLimitRequest, findCifId, productType, createTime);
        //log.info("saved: " + saved);

        return CustomResponse.response("Successful", "00", updateLimitRequest);
    }

    private void compareGlobalAmountZero(UpdateLimitRequest updateLimitRequest) {

        if (updateLimitRequest.getGlobalLimit().getTotalDailyLimit().compareTo(BigDecimal.valueOf(5000)) < 0) {
            throw new MyCustomizedException("Transaction Limit cannot be less than 5000");
        }
        if (updateLimitRequest.getGlobalLimit().getPerTransactionLimit().compareTo(BigDecimal.valueOf(5000)) < 0) {
            throw new MyCustomizedException("Transaction Limit cannot be less than 5000");
        }
    }

    private CustomerLimitModel setCustomerModel(CustomerLimitModel customerLimitModel, String cifId, String cifType, ProductType productType, String createTime) {

        if (customerLimitModel == null) {
            customerLimitModel = new CustomerLimitModel();
            customerLimitModel.setCreatedDate(customerLimitRepository.createDate());
            customerLimitModel.setCreatedTime(createTime);
            customerLimitModel.setTransferType(productType.getTransfer());
            customerLimitModel.setProductType(productType.getProduct());
        }

        customerLimitModel.setCifId(cifId);
        customerLimitModel.setCifType(cifType);
        customerLimitModel.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
        customerLimitModel.setLastModifiedDate(customerLimitRepository.createDate());
        customerLimitModel.setLastModifiedDateTime(createTime);
        customerLimitModel.setTransferType(customerLimitModel.getTransferType() == null ? productType.getTransfer() : customerLimitModel.getTransferType());
        customerLimitModel.setProductType(customerLimitModel.getProductType() == null ? productType.getProduct() : customerLimitModel.getProductType());

        try {
            //log.info("saving account limit");
            customerLimitModel = customerLimitRepository.save(customerLimitModel);
        } catch (DataIntegrityViolationException e) {
            //log.info(e.toString());
            throw new MyCustomizedException("kindly try again later...");
        }
        return customerLimitModel;
    }

    private void compareGlobalAmountWithNipAndChannel(GlobalLimit globalLimit1, String cifId, CustomerLimitModel customerLimitModel, AuditLimitModel auditLimitModel, UpdateChannelAudit updateChannelAudit, ProductType productType, String createTime) {
        //log.info("compareGlobalAmountWithNipAndChannel");

        BigDecimal globalDailyAmt = globalLimit1.getTotalDailyLimit();
        BigDecimal globalPerAmt = globalLimit1.getPerTransactionLimit();

        List<ChannelCode> channelCode = channelLimitRepository.findAllChannel(cifId, productType.getTransfer(), productType.getProduct());

        if (!channelCode.isEmpty()) {

            for (ChannelCode code : channelCode) {
                if (code.getPerTransactionLimit().compareTo(globalPerAmt) > 0) {
                    code.setPerTransactionLimit(globalPerAmt);
                }

                if (code.getTotalDailyLimit().compareTo(globalDailyAmt) > 0) {
                    channelAuditLimitWithOutRequest(code, code.getChannelId(), globalDailyAmt, auditLimitModel);
                    code.setTotalDailyLimit(globalDailyAmt);
                }

                code = channelLimitToSaveWithOutRequest(cifId, customerLimitModel, code, createTime);
                //log.info("channel saved: " + code);

            }
//            setChannelAudit(updateChannelAudit, auditLimitModel);
        }

        NipLimit nipLimit1 = nipLimitRepository.findNipLimitByCifId(cifId, productType.getTransfer(), productType.getProduct());

        if (nipLimit1 != null) {
            if (nipLimit1.getPerTransactionLimit().compareTo(globalPerAmt) > 0) {
                nipLimit1.setPerTransactionLimit(globalPerAmt);
            } else {
                nipLimit1.setPerTransactionLimit(nipLimit1.getPerTransactionLimit());
            }

            if (nipLimit1.getTotalDailyLimit().compareTo(globalDailyAmt) > 0) {
                auditLimitModel.setNipDailyLimitBf(nipLimit1.getTotalDailyLimit());
                nipLimit1.setTotalDailyLimit(globalDailyAmt);
            } else {
                nipLimit1.setTotalDailyLimit(nipLimit1.getTotalDailyLimit());
            }

            auditLimitModel.setNipDailyLimitCf(nipLimit1.getTotalDailyLimit());

            nipLimit1 = setNipLimitToSave(nipLimit1, cifId, customerLimitModel, productType, createTime);
            //log.info("nipLimit saved: " + nipLimit1);
        }
    }

    private void compareChannelAmountToZero(UpdateLimitRequest updateLimitRequest) {

        if (updateLimitRequest.getChannelLimits().getTotalDailyLimit().compareTo(BigDecimal.valueOf(5000)) < 0) {
            throw new MyCustomizedException("Transaction Limit cannot be less than 5000");
        }
        if (updateLimitRequest.getChannelLimits().getPerTransactionLimit().compareTo(BigDecimal.valueOf(5000)) < 0) {
            throw new MyCustomizedException("Transaction Limit cannot be less than 5000");
        }
    }

    private void compareNipAmountToZero(UpdateLimitRequest updateLimitRequest) {

        if (updateLimitRequest.getNipLimit().getPerTransactionLimit().compareTo(BigDecimal.valueOf(5000)) < 0) {
            throw new MyCustomizedException("Transaction Limit cannot be less than 5000");
        }
        if (updateLimitRequest.getNipLimit().getTotalDailyLimit().compareTo(BigDecimal.valueOf(5000)) < 0) {
            throw new MyCustomizedException("Transaction Limit cannot be less than 5000");
        }
    }

    private void comparePerAndDailyLimitMethod(UpdateLimitRequest updateLimitRequest) {

        if (updateLimitRequest.getGlobalLimit() != null) {
            CompletableFuture<Boolean> compareGlobalLimit = CompletableFuture.supplyAsync(() ->
                    compareGlobalLimitPerDaily(updateLimitRequest));

            boolean compareGlo = compareGlobalLimit.join();

            if (compareGlo) {
                throw new MyCustomException("Global per transaction cannot be greater than daily transaction");
            }
        }

        if (updateLimitRequest.getNipLimit() != null) {
            CompletableFuture<Boolean> compareNipLimit = CompletableFuture.supplyAsync(() ->
                    compareNipLimitPerDaily(updateLimitRequest));

            boolean compareNip = compareNipLimit.join();

            if (compareNip) {
                throw new MyCustomException("Nip per transaction cannot be greater than daily transaction");
            }
        }

        if (updateLimitRequest.getChannelLimits() != null) {
            CompletableFuture<Boolean> compareChannelLimit = CompletableFuture.supplyAsync(() ->
                    compareChannelLimitPerDaily(updateLimitRequest));

            boolean compareChannel = compareChannelLimit.join();

            if (compareChannel) {
                throw new MyCustomException("Channel per transaction cannot be greater than daily transaction");
            }
        }
    }

    private boolean compareChannelLimitPerDaily(UpdateLimitRequest updateLimitRequest) {

        boolean status = false;

        if (updateLimitRequest.getChannelLimits().getPerTransactionLimit().compareTo(updateLimitRequest.getChannelLimits().getTotalDailyLimit()) == 1) {
            status = true;
        }
        return status;
    }

    private boolean compareNipLimitPerDaily(UpdateLimitRequest updateLimitRequest) {

        boolean status = false;

        if (updateLimitRequest.getNipLimit().getPerTransactionLimit().compareTo(updateLimitRequest.getNipLimit().getTotalDailyLimit()) == 1) {
            status = true;
        }
        return status;
    }

    private boolean compareGlobalLimitPerDaily(UpdateLimitRequest updateLimitRequest) {

        boolean status = false;

        if (updateLimitRequest.getGlobalLimit().getPerTransactionLimit().compareTo(updateLimitRequest.getGlobalLimit().getTotalDailyLimit()) == 1) {
            status = true;
        }
        return status;
    }

    @Override
    public ResponseDto delete(DeleteLimitRequest deleteLimitRequest, String serviceToken, String serviceIpAddress, String userToken) throws MyCustomException, ExecutionException, InterruptedException {
        //log.info("deleting customer limit...");

        String accountNumber = helperUtils.accountNumber(deleteLimitRequest.getAccountNumber(), deleteLimitRequest.getCifId());

        ProductType productType = productTypeRepository.findByProduct(deleteLimitRequest.getProductType(), deleteLimitRequest.getTransferType());

        if (productType == null) {
            String message = String.format("Transfer of transferType %s and product type %s has not been configured", deleteLimitRequest.getTransferType(), deleteLimitRequest.getProductType());
            throw new MyCustomException(message);
        }

        if (deleteLimitRequest.getChannelId() <= 0) {
            throw new MyCustomizedException("channel id cannot be less than 1");
        }

        helperUtils.userAuth(userToken, serviceToken, serviceIpAddress, deleteLimitRequest.getAccountNumber(), deleteLimitRequest.getChannelId(), deleteLimitRequest.getCifId());

        AccountDetailsDto findCifId = helperUtils.getWithCifOrAccount(accountNumber);
        //log.info("findCifId: " + findCifId);

        CompletableFuture<Boolean> deleteCustomerExist = CompletableFuture.supplyAsync(() ->
                deleteCustomer(findCifId.getCifId(), productType));

        CompletableFuture<Boolean> deleteCustomerChannel = CompletableFuture.supplyAsync(() ->
                deleteCustomerCha(findCifId.getCifId(), productType));

        CompletableFuture<Boolean> deleteCustomerGlobal = CompletableFuture.supplyAsync(() ->
                deleteCustomerGlo(findCifId.getCifId(), productType));

        CompletableFuture<Boolean> deleteCustomerNip = CompletableFuture.supplyAsync(() ->
                deleteCustomerNipLimit(findCifId.getCifId(), productType));

        ResponseDto responseDto = new ResponseDto();

        boolean deleteCustomerLimit = deleteCustomerExist.get();
        boolean deleteCustomerLimitCha = deleteCustomerChannel.get();
        boolean deleteCustomerLimitGlo = deleteCustomerGlobal.get();
        boolean deleteCustomerLimitNip = deleteCustomerNip.get();

        if (!deleteCustomerLimit) {
            responseDto.setResponseCode("99");
            responseDto.setResponseMsg("account does not exist");

            return responseDto;
        }

        if (!deleteCustomerLimitCha) {
            //log.info("no default channel exist");
        }

        if (!deleteCustomerLimitGlo) {
            //log.info("no default global exist");
        }

        if (!deleteCustomerLimitNip) {
            //log.info("no default nip exist");
        }

        responseDto.setResponseCode("00");
        responseDto.setResponseMsg("deleted successfully");

        return responseDto;
    }

    private boolean deleteCustomerNipLimit(String cifId, ProductType productType) {

        boolean status = false;

        NipLimit findNipLimitByCifId = nipLimitRepository.findNipLimitByCifId(cifId, productType.getTransfer(), productType.getProduct());

        if (findNipLimitByCifId != null) {
            findNipLimitByCifId.setStatus("N");

            String hash = helperUtils.nipHashMethod(findNipLimitByCifId);
            findNipLimitByCifId.setHash(hash);

            try {
                status = true;
                nipLimitRepository.save(findNipLimitByCifId);
            } catch (Exception e) {
                //log.info("error saving delete nip limit");
                status = false;
                throw new InternalServerException("Err: kindly try again...");
            }
        }
        return status;
    }

    private boolean deleteCustomerGlo(String cifId, ProductType productType) {

        boolean status = false;

        GlobalLimit findGlobalLimitByCifId = globalLimitRepository.findGlobalLimitByCifId(cifId, productType.getTransfer(), productType.getProduct());

        if (findGlobalLimitByCifId != null) {
            findGlobalLimitByCifId.setStatus("N");

            String hash = helperUtils.globalHashMethod(findGlobalLimitByCifId);
            findGlobalLimitByCifId.setHash(hash);

            try {
                status = true;
                globalLimitRepository.save(findGlobalLimitByCifId);
            } catch (Exception e) {
                //log.info("error saving delete custom global");
                status = false;
                throw new InternalServerException("Err: kindly try again...");
            }
        }
        return status;
    }

    private boolean deleteCustomerCha(String cifId, ProductType productType) {

        boolean status = false;

        List<ChannelCode> findAllChannel = channelLimitRepository.findAllChannel(cifId, productType.getTransfer(), productType.getProduct());

        if (!findAllChannel.isEmpty()) {
            for (ChannelCode channelCode : findAllChannel) {
                channelCode.setStatus("N");

                String hash = helperUtils.channelHashMethod(channelCode);
                channelCode.setHash(hash);

                try {
                    status = true;
                    channelLimitRepository.save(channelCode);
                } catch (Exception e) {
                    //log.info("error saving delete channel: " + e);
                    status = false;
                    throw new InternalServerException("Err: kindly try again...");
                }
            }
        }
        return status;
    }

    private boolean deleteCustomer(String cifId, ProductType productType) {

        boolean status = false;

        CustomerLimitModel deleteCustomerLimit = customerLimitRepository.findByCifId(cifId, productType.getTransfer(), productType.getProduct());

        if (deleteCustomerLimit != null) {
            deleteCustomerLimit.setStatus("N");

            try {
                status = true;
                customerLimitRepository.save(deleteCustomerLimit);
            } catch (Exception e) {
                //log.info("error saving delete: " + e);
                status = false;
                throw new InternalServerException("Err: kindly try again...");
            }
        }

        return status;
    }

    private void validateFields(UpdateLimitRequest updateLimitRequest, int kycLevel) {

        if (updateLimitRequest.getCifType().equalsIgnoreCase("RET")) {
            if (!updateLimitRequest.getTransferType().equalsIgnoreCase("instant")) {
                throw new MyCustomizedException("cif type retail can only have a transfer type of instant");
            }
        }

        if (kycLevel != 3) {
            if (!updateLimitRequest.getTransferType().equalsIgnoreCase("instant")) {
                throw new MyCustomizedException("kyc level below 3 cannot not have a non-instant transfer type");
            }
        }

        if (updateLimitRequest.getChannelLimits() != null && updateLimitRequest.getChannelLimits().getChannelCode() == 8) {
            throw new MyCustomizedException("cannot update ussd");
        }

        if (updateLimitRequest.getGlobalLimit() != null) {
            if (updateLimitRequest.getChannelLimits() != null) {
                if (updateLimitRequest.getGlobalLimit().getTotalDailyLimit().compareTo(updateLimitRequest.getChannelLimits().getTotalDailyLimit()) < 0) {
                    throw new MyCustomizedException("global limit cannot be less than other limit passed");
                }
            }

            if (updateLimitRequest.getNipLimit() != null) {
                if (updateLimitRequest.getGlobalLimit().getTotalDailyLimit().compareTo(updateLimitRequest.getNipLimit().getTotalDailyLimit()) < 0) {
                    throw new MyCustomizedException("global limit cannot be less than nip limit passed");
                }
            }
        }
    }

    private ChannelCode channelLimitToSave(String cifId, CustomerLimitModel savedCustomerLimitModel, ChannelCode channelCode, UpdateLimitRequest createLimitRequest, ProductType productType, String createTime) {

        channelCode.setCifId(cifId);
        channelCode.setChannelId(createLimitRequest.getChannelLimits().getChannelCode());
        channelCode.setCustomerLimitModel(savedCustomerLimitModel);
        channelCode.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
        channelCode.setLastModifiedDate(customerLimitRepository.createDate());
        channelCode.setLastModifiedDateTime(createTime);
        channelCode.setTransferType(channelCode.getTransferType() == null ? productType.getTransfer() : channelCode.getTransferType());
        channelCode.setProductType(channelCode.getProductType() == null ? productType.getProduct() : channelCode.getProductType());

        String hash = helperUtils.channelHashMethod(channelCode);

        channelCode.setHash(hash);

        try {
            channelCode = channelLimitRepository.save(channelCode);
        } catch (Exception e) {
            //log.info("failed to save channel limit");
            //log.info(e.toString());
            throw new MyCustomizedException("kindly try again later...");
        }
        return channelCode;
    }

    private ChannelCode channelLimitToSaveGlobal(String cifId, CustomerLimitModel savedCustomerLimitModel, ChannelCode channelCode, UpdateLimitRequest createLimitRequest, int i, ProductType productType, String createTime) {

        channelCode.setCifId(cifId);
        channelCode.setChannelId(i);
        channelCode.setCustomerLimitModel(savedCustomerLimitModel);
        channelCode.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
        channelCode.setLastModifiedDate(customerLimitRepository.createDate());
        channelCode.setLastModifiedDateTime(createTime);
        channelCode.setTransferType(productType.getTransfer());
        channelCode.setProductType(productType.getProduct());

        String hash = helperUtils.channelHashMethodForJustUpdate(channelCode);

        channelCode.setHash(hash);

        try {
            channelCode = channelLimitRepository.save(channelCode);
        } catch (Exception e) {
            //log.info("failed to save channel limit");
            //log.info(e.toString());
            throw new MyCustomizedException("kindly try again later...");
        }
        return channelCode;
    }

    private ChannelCode channelLimitToSaveWithOutRequest(String cifId, CustomerLimitModel savedCustomerLimitModel, ChannelCode channelCode, String createTime) {

        channelCode.setCifId(cifId);
        channelCode.setCustomerLimitModel(savedCustomerLimitModel);
        channelCode.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
        channelCode.setLastModifiedDate(customerLimitRepository.createDate());
        channelCode.setLastModifiedDateTime(createTime);

        String hash = helperUtils.channelHashMethod(channelCode);

        channelCode.setHash(hash);

        try {
            channelCode = channelLimitRepository.save(channelCode);
        } catch (Exception e) {
            //log.info("failed to save channel limit");
            //log.info(e.toString());
            throw new MyCustomizedException("kindly try again later...");
        }
        return channelCode;
    }

    private NipLimit setNipLimitToSave(NipLimit nipLimit, String cifId, CustomerLimitModel savedCustomerLimitModel, ProductType productType, String createTime) {

        nipLimit.setCifId(cifId);
        nipLimit.setCustomerLimitModel(savedCustomerLimitModel);
        nipLimit.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
        nipLimit.setLastModifiedDate(customerLimitRepository.createDate());
        nipLimit.setLastModifiedDateTime(createTime);
        nipLimit.setTransferType(productType.getTransfer());
        nipLimit.setProductType(productType.getProduct());

        String hash = helperUtils.nipHashMethodJustChecksum(nipLimit);

        nipLimit.setHash(hash);

        try {
            nipLimit = nipLimitRepository.save(nipLimit);
        } catch (Exception e) {
            //log.info("failed to save nip limit");
            //log.info(e.toString());
            throw new MyCustomizedException("kindly try again later...");
        }
        return nipLimit;
    }

    private GlobalLimit saveGlobalLimitSet(CustomerLimitModel savedCustomerLimitModel, GlobalLimit globalLimit, String cifId, ProductType productType, String createTime) {

        globalLimit.setCustomerLimitModel(savedCustomerLimitModel);
        globalLimit.setCifId(cifId);
        globalLimit.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
        globalLimit.setLastModifiedDate(customerLimitRepository.createDate());
        globalLimit.setLastModifiedDateTime(createTime);
        globalLimit.setTransferType(globalLimit.getTransferType() == null ? productType.getTransfer() : globalLimit.getTransferType());
        globalLimit.setProductType(globalLimit.getProductType() == null ? productType.getProduct() : globalLimit.getProductType());

        String hash = helperUtils.globalHashMethod(globalLimit);
        globalLimit.setHash(hash);

        if (globalLimit.getTotalDailyLimit() != null) {

            try {
                //log.info("saving new create limit request");
                globalLimit = globalLimitRepository.save(globalLimit);
            } catch (Exception e) {
                //log.info(e.toString());
                throw new MyCustomizedException("kindly try again later...");
            }
        }
        return globalLimit;
    }

    private String updateDailyAndSummaryTable(AuditLimitModel savedAuditLimit
            , CustomerLimitModel findByCifId, String presentDate, UpdateLimitRequest updateLimitRequest,
                                              AccountDetailsDto findCifId, ProductType productType, String createTime) throws MyCustomException {
        //log.info("method that stores the daily and summary table");

        //random letter
        AtomicReference<BigDecimal> globalLastAmt = new AtomicReference<>(BigDecimal.ZERO);
        AtomicReference<BigDecimal> nipLastAmt = new AtomicReference<>(BigDecimal.ZERO);
        AtomicReference<BigDecimal> tellerLastAmt = new AtomicReference<>(BigDecimal.ZERO);
        AtomicReference<BigDecimal> internetLastAmt = new AtomicReference<>(BigDecimal.ZERO);
        AtomicReference<BigDecimal> mobileLastAmt = new AtomicReference<>(BigDecimal.ZERO);
        AtomicReference<BigDecimal> posLastAmt = new AtomicReference<>(BigDecimal.ZERO);
        AtomicReference<BigDecimal> atmLastAmt = new AtomicReference<>(BigDecimal.ZERO);
        AtomicReference<BigDecimal> portalLastAmt = new AtomicReference<>(BigDecimal.ZERO);
        AtomicReference<BigDecimal> thirdLastAmt = new AtomicReference<>(BigDecimal.ZERO);
        AtomicReference<BigDecimal> ussdLastAmt = new AtomicReference<>(BigDecimal.ZERO);
        AtomicReference<BigDecimal> othersLastAmt = new AtomicReference<>(BigDecimal.ZERO);

        CompletableFuture<Void> globalAmt = CompletableFuture.runAsync(() ->
                globalLastAmt.set(globalLastAmt(savedAuditLimit.getCifId(), updateLimitRequest, findCifId, productType)));

        CompletableFuture<Void> nipAmt = CompletableFuture.runAsync(() ->
                nipLastAmt.set(nipLastAmt(savedAuditLimit.getCifId(), updateLimitRequest, findCifId, productType)));

        CompletableFuture<Void> tellerAmt = CompletableFuture.runAsync(() ->
                tellerLastAmt.set(tellerLastAmt(savedAuditLimit.getCifId(), updateLimitRequest, findCifId, productType)));

        CompletableFuture<Void> internetAmt = CompletableFuture.runAsync(() ->
                internetLastAmt.set(internetLastAmt(savedAuditLimit.getCifId(), updateLimitRequest, findCifId, productType)));

        CompletableFuture<Void> mobileAmt = CompletableFuture.runAsync(() ->
                mobileLastAmt.set(mobileLastAmt(savedAuditLimit.getCifId(), updateLimitRequest, findCifId, productType)));

        CompletableFuture<Void> posAmt = CompletableFuture.runAsync(() ->
                posLastAmt.set(posLastAmt(savedAuditLimit.getCifId(), updateLimitRequest, findCifId, productType)));

        CompletableFuture<Void> atmAmt = CompletableFuture.runAsync(() ->
                atmLastAmt.set(atmLastAmt(savedAuditLimit.getCifId(), updateLimitRequest, findCifId, productType)));

        CompletableFuture<Void> portalAmt = CompletableFuture.runAsync(() ->
                portalLastAmt.set(portalLastAmt(savedAuditLimit.getCifId(), updateLimitRequest, findCifId, productType)));

        CompletableFuture<Void> thirdAmt = CompletableFuture.runAsync(() ->
                thirdLastAmt.set(thirdLastAmt(savedAuditLimit.getCifId(), updateLimitRequest, findCifId, productType)));

        CompletableFuture<Void> ussdAmt = CompletableFuture.runAsync(() ->
                ussdLastAmt.set(ussdLastAmt(savedAuditLimit.getCifId(), updateLimitRequest, findCifId, productType)));

        CompletableFuture<Void> othersAmt = CompletableFuture.runAsync(() ->
                othersLastAmt.set(othersLastAmt(savedAuditLimit.getCifId(), updateLimitRequest, findCifId, productType)));

        globalAmt.join();
        nipAmt.join();
        tellerAmt.join();
        internetAmt.join();
        mobileAmt.join();
        posAmt.join();
        atmAmt.join();
        portalAmt.join();
        thirdAmt.join();
        ussdAmt.join();
        othersAmt.join();

        //log.info("savedAuditLimit: " + savedAuditLimit);

        SummaryDetailModel getRecentSummaryTrans = summaryDetailRepository.findByCifId(savedAuditLimit.getCifId(), presentDate, productType.getTransfer(), productType.getProduct());

        if (getRecentSummaryTrans != null) {

            if (savedAuditLimit.getGlobalDailyLimitCf() != null) {

                BigDecimal oldNew = savedAuditLimit.getGlobalDailyLimitCf().subtract(globalLastAmt.get());
                BigDecimal total = oldNew.add(getRecentSummaryTrans.getGlobalDailyLimitCf());

                boolean globalCheck = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (globalCheck) {
                    getRecentSummaryTrans.setGlobalDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentSummaryTrans.setGlobalDailyLimitCf(total);
                }
            }

            if (savedAuditLimit.getNipDailyLimitCf() != null) {
                BigDecimal oldNew = savedAuditLimit.getNipDailyLimitCf().subtract(nipLastAmt.get());
                BigDecimal total = oldNew.add(getRecentSummaryTrans.getNipDailyLimitCf());

                boolean check = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (check) {
                    getRecentSummaryTrans.setNipDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentSummaryTrans.setNipDailyLimitCf(total);
                }
            }

            if (savedAuditLimit.getTellerDailyLimitCf() != null) {
                BigDecimal oldNew = savedAuditLimit.getTellerDailyLimitCf().subtract(tellerLastAmt.get());
                BigDecimal total = oldNew.add(getRecentSummaryTrans.getTellerDailyLimitCf());

                boolean check = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (check) {
                    getRecentSummaryTrans.setTellerDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentSummaryTrans.setTellerDailyLimitCf(total);
                }
            }

            if (savedAuditLimit.getInternetDailyLimitCf() != null) {
                BigDecimal oldNew = savedAuditLimit.getInternetDailyLimitCf().subtract(internetLastAmt.get());
                BigDecimal total = oldNew.add(getRecentSummaryTrans.getInternetDailyLimitCf());

                boolean check = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (check) {
                    getRecentSummaryTrans.setInternetDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentSummaryTrans.setInternetDailyLimitCf(total);
                }
            }

            if (savedAuditLimit.getMobileDailyLimitCf() != null) {
                BigDecimal oldNew = savedAuditLimit.getMobileDailyLimitCf().subtract(mobileLastAmt.get());
                BigDecimal total = oldNew.add(getRecentSummaryTrans.getMobileDailyLimitCf());

                boolean check = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (check) {
                    getRecentSummaryTrans.setMobileDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentSummaryTrans.setMobileDailyLimitCf(total);
                }
            }

            if (savedAuditLimit.getPosDailyLimitCf() != null) {
                BigDecimal oldNew = savedAuditLimit.getPosDailyLimitCf().subtract(posLastAmt.get());
                BigDecimal total = oldNew.add(getRecentSummaryTrans.getPosDailyLimitCf());

                boolean check = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (check) {
                    getRecentSummaryTrans.setPosDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentSummaryTrans.setPosDailyLimitCf(total);
                }
            }

            if (savedAuditLimit.getAtmDailyLimitCf() != null) {
                BigDecimal oldNew = savedAuditLimit.getAtmDailyLimitCf().subtract(atmLastAmt.get());
                BigDecimal total = oldNew.add(getRecentSummaryTrans.getAtmDailyLimitCf());

                boolean check = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (check) {
                    getRecentSummaryTrans.setAtmDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentSummaryTrans.setAtmDailyLimitCf(total);
                }
            }

            if (savedAuditLimit.getPortalDailyLimitCf() != null) {
                BigDecimal oldNew = savedAuditLimit.getPortalDailyLimitCf().subtract(portalLastAmt.get());
                BigDecimal total = oldNew.add(getRecentSummaryTrans.getPortalDailyLimitCf());

                boolean check = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (check) {
                    getRecentSummaryTrans.setPortalDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentSummaryTrans.setPortalDailyLimitCf(total);
                }
            }

            if (savedAuditLimit.getThirdPartyDailyLimitCf() != null) {
                BigDecimal oldNew = savedAuditLimit.getThirdPartyDailyLimitCf().subtract(thirdLastAmt.get());
                BigDecimal total = oldNew.add(getRecentSummaryTrans.getThirdPartyDailyLimitCf());

                boolean check = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (check) {
                    getRecentSummaryTrans.setThirdPartyDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentSummaryTrans.setThirdPartyDailyLimitCf(total);
                }
            }

            if (savedAuditLimit.getUssdDailyLimitCf() != null) {
                BigDecimal oldNew = savedAuditLimit.getUssdDailyLimitCf().subtract(ussdLastAmt.get());
                BigDecimal total = oldNew.add(getRecentSummaryTrans.getUssdDailyLimitCf());

                boolean check = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (check) {
                    getRecentSummaryTrans.setUssdDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentSummaryTrans.setUssdDailyLimitCf(total);
                }
            }

            if (savedAuditLimit.getOthersDailyLimitCf() != null) {
                BigDecimal oldNew = savedAuditLimit.getOthersDailyLimitCf().subtract(othersLastAmt.get());
                BigDecimal total = oldNew.add(getRecentSummaryTrans.getOthersDailyLimitCf());

                boolean check = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (check) {
                    getRecentSummaryTrans.setOthersDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentSummaryTrans.setOthersDailyLimitCf(total);
                }
            }


            //updating modified date for summary table
            getRecentSummaryTrans.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
            getRecentSummaryTrans.setLastModifiedDate(customerLimitRepository.createDate());
            getRecentSummaryTrans.setLastModifiedDateTime(createTime);
            getRecentSummaryTrans.setAuditId(savedAuditLimit.getAuditId());

            //setting hash and checksum for summary table
//            String summaryChecksum = helperUtils.summaryChecksum(getRecentSummaryTrans);
//            getRecentSummaryTrans.setChecksum(summaryChecksum);

            try {
                getRecentSummaryTrans = summaryDetailRepository.save(getRecentSummaryTrans);
                //log.info("getRecentSummaryTrans: " + getRecentSummaryTrans);
            } catch (Exception e) {
                //log.info("error saving summary");
                //log.info(e.toString());
                throw new MyCustomizedException("please try again later...");
            }
        }

        //log.info("savedAuditLimit: " + savedAuditLimit);

        DailyLimitUsageModel getRecentDailyTrans = dailyLimitUsageRepository.getRecentTrans(findByCifId.getCifId(), presentDate, productType.getTransfer(), productType.getProduct());

        if (getRecentDailyTrans != null) {

            if (savedAuditLimit.getGlobalDailyLimitCf() != null) {
                BigDecimal oldNew = savedAuditLimit.getGlobalDailyLimitCf().subtract(globalLastAmt.get());
                BigDecimal total = oldNew.add(getRecentDailyTrans.getGlobalDailyLimitCf());

                boolean check = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (check) {
                    getRecentDailyTrans.setGlobalDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentDailyTrans.setGlobalDailyLimitCf(total);
                }
            }

            if (savedAuditLimit.getNipDailyLimitCf() != null) {
                BigDecimal oldNew = savedAuditLimit.getNipDailyLimitCf().subtract(nipLastAmt.get());
                BigDecimal total = oldNew.add(getRecentDailyTrans.getNipDailyLimitCf());

                boolean check = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (check) {
                    getRecentDailyTrans.setNipDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentDailyTrans.setNipDailyLimitCf(total);
                }
            }

            if (savedAuditLimit.getTellerDailyLimitCf() != null) {
                BigDecimal oldNew = savedAuditLimit.getTellerDailyLimitCf().subtract(tellerLastAmt.get());
                BigDecimal total = oldNew.add(getRecentDailyTrans.getTellerDailyLimitCf());

                boolean check = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (check) {
                    getRecentDailyTrans.setTellerDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentDailyTrans.setTellerDailyLimitCf(total);
                }
            }

            if (savedAuditLimit.getInternetDailyLimitCf() != null) {
                BigDecimal oldNew = savedAuditLimit.getInternetDailyLimitCf().subtract(internetLastAmt.get());
                BigDecimal total = oldNew.add(getRecentDailyTrans.getInternetDailyLimitCf());

                boolean check = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (check) {
                    getRecentDailyTrans.setInternetDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentDailyTrans.setInternetDailyLimitCf(total);
                }
            }

            if (savedAuditLimit.getMobileDailyLimitCf() != null) {
                BigDecimal oldNew = savedAuditLimit.getMobileDailyLimitCf().subtract(mobileLastAmt.get());
                BigDecimal total = oldNew.add(getRecentDailyTrans.getMobileDailyLimitCf());

                boolean check = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (check) {
                    getRecentDailyTrans.setMobileDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentDailyTrans.setMobileDailyLimitCf(total);
                }
            }

            if (savedAuditLimit.getPosDailyLimitCf() != null) {
                BigDecimal oldNew = savedAuditLimit.getPosDailyLimitCf().subtract(posLastAmt.get());
                BigDecimal total = oldNew.add(getRecentDailyTrans.getPosDailyLimitCf());

                boolean check = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (check) {
                    getRecentDailyTrans.setPosDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentDailyTrans.setPosDailyLimitCf(total);
                }
            }

            if (savedAuditLimit.getAtmDailyLimitCf() != null) {
                BigDecimal oldNew = savedAuditLimit.getAtmDailyLimitCf().subtract(atmLastAmt.get());
                BigDecimal total = oldNew.add(getRecentDailyTrans.getAtmDailyLimitCf());

                boolean check = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (check) {
                    getRecentDailyTrans.setAtmDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentDailyTrans.setAtmDailyLimitCf(total);
                }
            }

            if (savedAuditLimit.getPortalDailyLimitCf() != null) {
                BigDecimal oldNew = savedAuditLimit.getPortalDailyLimitCf().subtract(portalLastAmt.get());
                BigDecimal total = oldNew.add(getRecentDailyTrans.getPortalDailyLimitCf());

                boolean check = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (check) {
                    getRecentDailyTrans.setPortalDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentDailyTrans.setPortalDailyLimitCf(total);
                }
            }

            if (savedAuditLimit.getThirdPartyDailyLimitCf() != null) {
                BigDecimal oldNew = savedAuditLimit.getThirdPartyDailyLimitCf().subtract(thirdLastAmt.get());
                BigDecimal total = oldNew.add(getRecentDailyTrans.getThirdPartyDailyLimitCf());

                boolean check = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (check) {
                    getRecentDailyTrans.setThirdPartyDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentDailyTrans.setThirdPartyDailyLimitCf(total);
                }
            }

            if (savedAuditLimit.getUssdDailyLimitCf() != null) {
                BigDecimal oldNew = savedAuditLimit.getUssdDailyLimitCf().subtract(ussdLastAmt.get());
                BigDecimal total = oldNew.add(getRecentDailyTrans.getUssdDailyLimitCf());

                boolean check = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (check) {
                    getRecentDailyTrans.setUssdDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentDailyTrans.setUssdDailyLimitCf(total);
                }
            }

            if (savedAuditLimit.getOthersDailyLimitCf() != null) {
                BigDecimal oldNew = savedAuditLimit.getOthersDailyLimitCf().subtract(othersLastAmt.get());
                BigDecimal total = oldNew.add(getRecentDailyTrans.getOthersDailyLimitCf());

                boolean check = total.compareTo(BigDecimal.valueOf(0)) < 0;

                if (check) {
                    getRecentDailyTrans.setOthersDailyLimitCf(BigDecimal.ZERO);
                } else {
                    getRecentDailyTrans.setOthersDailyLimitCf(total);
                }
            }

            //updating modified date for daily table
            getRecentDailyTrans.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
            getRecentDailyTrans.setLastModifiedDate(customerLimitRepository.createDate());
            getRecentDailyTrans.setLastModifiedDateTime(createTime);
            getRecentDailyTrans.setAuditId(savedAuditLimit.getAuditId());

            //setting hash and checksum for daily table
//            String dailyHash = helperUtils.dailyValuesToHash(getRecentDailyTrans);
//            String dailyChecksum = helperUtils.DailyValuesToChecksum(getRecentDailyTrans);
//            getRecentDailyTrans.setHash(dailyHash);
//            getRecentDailyTrans.setChecksum(dailyChecksum);

            try {
                getRecentDailyTrans = dailyLimitUsageRepository.save(getRecentDailyTrans);
                //log.info("getRecentDailyTrans: " + getRecentDailyTrans);
            } catch (Exception e) {
                //log.info("error saving daily and summary");
                //log.info(e.toString());
                throw new MyCustomizedException("please try again later...");
            }
        }
        //log.info("savedAuditLimit: " + savedAuditLimit);

        return "success";
    }

    private BigDecimal othersLastAmt(String cifId, UpdateLimitRequest updateLimitRequest, AccountDetailsDto findCifId, ProductType productType) {

        BigDecimal amount = BigDecimal.ZERO;

        ChannelCode getCustomerLimit = channelLimitRepository.findUssdLimitById(String.valueOf(9), cifId, productType.getTransfer(), productType.getProduct());

        InternalLimitModel getStbLimit = InternalLimitRepository.findByKycLevel(findCifId.getKycLevel(), findCifId.getCifType(), productType.getTransfer(), productType.getProduct());

        if (getCustomerLimit != null) {
            amount = getCustomerLimit.getTotalDailyLimit();
        } else {
            amount = getStbLimit.getOthersDailyTransaction();
        }
        return amount;
    }

    private BigDecimal ussdLastAmt(String cifId, UpdateLimitRequest updateLimitRequest, AccountDetailsDto findCifId, ProductType productType) {

        BigDecimal amount = BigDecimal.ZERO;

        ChannelCode getCustomerLimit = channelLimitRepository.findUssdLimitById(String.valueOf(8), cifId, productType.getTransfer(), productType.getProduct());

        InternalLimitModel getStbLimit = InternalLimitRepository.findByKycLevel(findCifId.getKycLevel(), findCifId.getCifType(), productType.getTransfer(), productType.getProduct());

        if (getCustomerLimit != null) {
            amount = getCustomerLimit.getTotalDailyLimit();
        } else {
            amount = getStbLimit.getUssdDailyTransaction();
        }
        return amount;
    }

    private BigDecimal thirdLastAmt(String cifId, UpdateLimitRequest updateLimitRequest, AccountDetailsDto findCifId, ProductType productType) {

        BigDecimal amount = BigDecimal.ZERO;

        ChannelCode getCustomerLimit = channelLimitRepository.findUssdLimitById(String.valueOf(7), cifId, productType.getTransfer(), productType.getProduct());

        InternalLimitModel getStbLimit = InternalLimitRepository.findByKycLevel(findCifId.getKycLevel(), findCifId.getCifType(), productType.getTransfer(), productType.getProduct());

        if (getCustomerLimit != null) {
            amount = getCustomerLimit.getTotalDailyLimit();
        } else {
            amount = getStbLimit.getThirdDailyTransaction();
        }

        return amount;
    }

    private BigDecimal portalLastAmt(String cifId, UpdateLimitRequest updateLimitRequest, AccountDetailsDto findCifId, ProductType productType) {

        BigDecimal amount = BigDecimal.ZERO;

        ChannelCode getCustomerLimit = channelLimitRepository.findUssdLimitById(String.valueOf(6), cifId, productType.getTransfer(), productType.getProduct());

        InternalLimitModel getStbLimit = InternalLimitRepository.findByKycLevel(findCifId.getKycLevel(), findCifId.getCifType(), productType.getTransfer(), productType.getProduct());

        if (getCustomerLimit != null) {
            amount = getCustomerLimit.getTotalDailyLimit();
        } else {
            amount = getStbLimit.getVendorDailyTransaction();
        }
        return amount;
    }

    private BigDecimal atmLastAmt(String cifId, UpdateLimitRequest updateLimitRequest, AccountDetailsDto findCifId, ProductType productType) {

        BigDecimal amount = BigDecimal.ZERO;

        ChannelCode getCustomerLimit = channelLimitRepository.findUssdLimitById(String.valueOf(5), cifId, productType.getTransfer(), productType.getProduct());

        InternalLimitModel getStbLimit = InternalLimitRepository.findByKycLevel(findCifId.getKycLevel(), findCifId.getCifType(), productType.getTransfer(), productType.getProduct());

        if (getCustomerLimit != null) {
            amount = getCustomerLimit.getTotalDailyLimit();
        } else {
            amount = getStbLimit.getAtmDailyTransaction();
        }
        return amount;
    }

    private BigDecimal posLastAmt(String cifId, UpdateLimitRequest updateLimitRequest, AccountDetailsDto findCifId, ProductType productType) {

        BigDecimal amount = BigDecimal.ZERO;

        ChannelCode getCustomerLimit = channelLimitRepository.findUssdLimitById(String.valueOf(4), cifId, productType.getTransfer(), productType.getProduct());

        InternalLimitModel getStbLimit = InternalLimitRepository.findByKycLevel(findCifId.getKycLevel(), findCifId.getCifType(), productType.getTransfer(), productType.getProduct());

        if (getCustomerLimit != null) {
            amount = getCustomerLimit.getTotalDailyLimit();
        } else {
            amount = getStbLimit.getPosDailyTransaction();
        }
        return amount;
    }

    private BigDecimal mobileLastAmt(String cifId, UpdateLimitRequest updateLimitRequest, AccountDetailsDto findCifId, ProductType productType) {

        BigDecimal amount = BigDecimal.ZERO;

        ChannelCode getCustomerLimit = channelLimitRepository.findUssdLimitById(String.valueOf(3), cifId, productType.getTransfer(), productType.getProduct());

        InternalLimitModel getStbLimit = InternalLimitRepository.findByKycLevel(findCifId.getKycLevel(), findCifId.getCifType(), productType.getTransfer(), productType.getProduct());

        if (getCustomerLimit != null) {
            amount = getCustomerLimit.getTotalDailyLimit();
        } else {
            amount = getStbLimit.getMobileDailyTransaction();
        }
        return amount;
    }

    private BigDecimal internetLastAmt(String cifId, UpdateLimitRequest updateLimitRequest, AccountDetailsDto findCifId, ProductType productType) {

        BigDecimal amount = BigDecimal.ZERO;

        ChannelCode getCustomerLimit = channelLimitRepository.findUssdLimitById(String.valueOf(2), cifId, productType.getTransfer(), productType.getProduct());

        InternalLimitModel getStbLimit = InternalLimitRepository.findByKycLevel(findCifId.getKycLevel(), findCifId.getCifType(), productType.getTransfer(), productType.getProduct());

        if (getCustomerLimit != null) {
            amount = getCustomerLimit.getTotalDailyLimit();
        } else {
            amount = getStbLimit.getInternetDailyTransaction();
        }
        return amount;
    }

    private BigDecimal tellerLastAmt(String cifId, UpdateLimitRequest updateLimitRequest, AccountDetailsDto findCifId, ProductType productType) {

        BigDecimal amount = BigDecimal.ZERO;

        ChannelCode getCustomerLimit = channelLimitRepository.findUssdLimitById(String.valueOf(1), cifId, productType.getTransfer(), productType.getProduct());

        InternalLimitModel getStbLimit = InternalLimitRepository.findByKycLevel(findCifId.getKycLevel(), findCifId.getCifType(), productType.getTransfer(), productType.getProduct());

        if (getCustomerLimit != null) {
            amount = getCustomerLimit.getTotalDailyLimit();
        } else {
            amount = getStbLimit.getBankDailyTransaction();
        }
        return amount;
    }

    private BigDecimal nipLastAmt(String cifId, UpdateLimitRequest updateLimitRequest, AccountDetailsDto findCifId, ProductType productType) {

        BigDecimal amount = BigDecimal.ZERO;

        NipLimit getCustomerNipLimit = nipLimitRepository.findNipLimitByCifId(cifId, productType.getTransfer(), productType.getProduct());

        InternalLimitModel getStbGlobalLimit = InternalLimitRepository.findByKycLevel(findCifId.getKycLevel(), findCifId.getCifType(), productType.getTransfer(), productType.getProduct());

        if (getCustomerNipLimit != null) {
            amount = getCustomerNipLimit.getTotalDailyLimit();
        } else {
            amount = getStbGlobalLimit.getNipDailyTransaction();
        }
        return amount;
    }

    private BigDecimal globalLastAmt(String cifId, UpdateLimitRequest updateLimitRequest, AccountDetailsDto findCifId, ProductType productType) {

        BigDecimal amount = BigDecimal.ZERO;

        GlobalLimit getCustomerGlobalLimit = globalLimitRepository.findGlobalLimitByCifId(cifId, productType.getTransfer(), productType.getProduct());

        InternalLimitModel getStbGlobalLimit = InternalLimitRepository.findByKycLevel(findCifId.getKycLevel(), findCifId.getCifType(), productType.getTransfer(), productType.getProduct());

        if (getCustomerGlobalLimit != null) {
            amount = getCustomerGlobalLimit.getTotalDailyLimit();
        } else {
            amount = getStbGlobalLimit.getGlobalDailyTransaction();
        }
        return amount;
    }

    private String generateRandomLetters() {
        StringBuilder sb = new StringBuilder();
        String addedNum = "";

        Random random = new Random();

        boolean check = true;

        while (check) {

            for (int i = 0; i < 3; i++) {
                //generate a random ASCII value between 65 (A) 90 (Z)
                int asciiValue = random.nextInt(26) + 65;
                char letter = (char) asciiValue;
                sb.append(letter);
            }

            int firstNumber = random.nextInt(88888);
            int secondNumber = random.nextInt(88888);
            addedNum = sb.toString().toUpperCase() + "-" + firstNumber + secondNumber;

            AuditLimitModel findByAuditId = auditLimitRepository.findByAuditId(addedNum);

            if (findByAuditId == null) {
                check = false;
            }
            //log.info("regenerating random num & letter");
        }
        return addedNum;
    }

    private AuditLimitModel channelAuditLimit(ChannelCode model, String cifId, UpdateChannelLimit limitRequest, AuditLimitModel updateChannelAudit) {

        switch (limitRequest.getChannelCode()) {
            case 1 -> {
                updateChannelAudit.setTellerDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setTellerDailyLimitCf(limitRequest.getTotalDailyLimit());
            }
            case 2 -> {
                updateChannelAudit.setInternetDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setInternetDailyLimitCf(limitRequest.getTotalDailyLimit());
            }
            case 3 -> {
                updateChannelAudit.setMobileDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setMobileDailyLimitCf(limitRequest.getTotalDailyLimit());
            }
            case 4 -> {
                updateChannelAudit.setPosDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setPosDailyLimitCf(limitRequest.getTotalDailyLimit());
            }
            case 5 -> {
                updateChannelAudit.setAtmDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setAtmDailyLimitCf(limitRequest.getTotalDailyLimit());
            }
            case 6 -> {
                updateChannelAudit.setPortalDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setPortalDailyLimitCf(limitRequest.getTotalDailyLimit());
            }
            case 7 -> {
                updateChannelAudit.setThirdPartyDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setThirdPartyDailyLimitCf(limitRequest.getTotalDailyLimit());
            }
            case 8 -> {
                updateChannelAudit.setUssdDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setUssdDailyLimitCf(limitRequest.getTotalDailyLimit());
            }
            case 9 -> {
                updateChannelAudit.setOthersDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setOthersDailyLimitCf(limitRequest.getTotalDailyLimit());
            }
            default -> throw new MyCustomizedException("channel id does not exist");
        }
        return updateChannelAudit;
    }

    private UpdateChannelAudit channelAuditLimitWithGlobal(ChannelCode model, String cifId, UpdateGlobalLimit limitRequest, int channelCode) {

        UpdateChannelAudit updateChannelAudit = new UpdateChannelAudit();

        switch (channelCode) {
            case 1 -> {
                updateChannelAudit.setTellerDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setTellerDailyLimitCf(limitRequest.getTotalDailyLimit());
            }
            case 2 -> {
                updateChannelAudit.setInternetDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setInternetDailyLimitCf(limitRequest.getTotalDailyLimit());
            }
            case 3 -> {
                updateChannelAudit.setMobileDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setMobileDailyLimitCf(limitRequest.getTotalDailyLimit());
            }
            case 4 -> {
                updateChannelAudit.setPosDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setPosDailyLimitCf(limitRequest.getTotalDailyLimit());
            }
            case 5 -> {
                updateChannelAudit.setAtmDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setAtmDailyLimitCf(limitRequest.getTotalDailyLimit());
            }
            case 6 -> {
                updateChannelAudit.setPortalDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setPortalDailyLimitCf(limitRequest.getTotalDailyLimit());
            }
            case 7 -> {
                updateChannelAudit.setThirdPartyDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setThirdPartyDailyLimitCf(limitRequest.getTotalDailyLimit());
            }
            case 8 -> {
                updateChannelAudit.setUssdDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setUssdDailyLimitCf(limitRequest.getTotalDailyLimit());
            }
            case 9 -> {
                updateChannelAudit.setOthersDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setOthersDailyLimitCf(limitRequest.getTotalDailyLimit());
            }
            default -> throw new MyCustomizedException("channel id does not exist");
        }
        return updateChannelAudit;
    }

    private AuditLimitModel channelAuditLimitWithOutRequest(ChannelCode model, int channelCode, BigDecimal totalDailyLimit, AuditLimitModel updateChannelAudit) {

        switch (channelCode) {
            case 1 -> {
                updateChannelAudit.setTellerDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setTellerDailyLimitCf(totalDailyLimit);
            }
            case 2 -> {
                updateChannelAudit.setInternetDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setInternetDailyLimitCf(totalDailyLimit);
            }
            case 3 -> {
                updateChannelAudit.setMobileDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setMobileDailyLimitCf(totalDailyLimit);
            }
            case 4 -> {
                updateChannelAudit.setPosDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setPosDailyLimitCf(totalDailyLimit);
            }
            case 5 -> {
                updateChannelAudit.setAtmDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setAtmDailyLimitCf(totalDailyLimit);
            }
            case 6 -> {
                updateChannelAudit.setPortalDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setPortalDailyLimitCf(totalDailyLimit);
            }
            case 7 -> {
                updateChannelAudit.setThirdPartyDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setThirdPartyDailyLimitCf(totalDailyLimit);
            }
            case 8 -> {
                updateChannelAudit.setUssdDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setUssdDailyLimitCf(totalDailyLimit);
            }
            case 9 -> {
                updateChannelAudit.setOthersDailyLimitBf(model.getTotalDailyLimit());
                updateChannelAudit.setOthersDailyLimitCf(totalDailyLimit);
            }
            default -> throw new MyCustomizedException("channel id does not exist");
        }
        return updateChannelAudit;
    }
}