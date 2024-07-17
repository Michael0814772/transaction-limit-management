package com.michael.limit.management.service.impl;

import com.google.common.base.Strings;
import com.michael.limit.management.custom.CustomResponse;
import com.michael.limit.management.dto.DailyLimitUsageDto.CifDto;
import com.michael.limit.management.dto.DailyLimitUsageDto.DailyLimitUsageRequestDto;
import com.michael.limit.management.dto.DailyLimitUsageDto.TransferDestinationDetailsDto;
import com.michael.limit.management.dto.allLimit.LimitManagement;
import com.michael.limit.management.dto.databaseDto.AccountDetailsDto;
import com.michael.limit.management.dto.enquiry.*;
import com.michael.limit.management.dto.response.ChannelIdResponse;
import com.michael.limit.management.dto.response.GlobalResponse;
import com.michael.limit.management.dto.response.NipResponse;
import com.michael.limit.management.dto.response.TransferMethodResponse;
import com.michael.limit.management.exception.exceptionMethod.DuplicateException;
import com.michael.limit.management.exception.exceptionMethod.MyCustomException;
import com.michael.limit.management.exception.exceptionMethod.MyCustomizedException;
import com.michael.limit.management.model.*;
import com.michael.limit.management.repository.*;
import com.michael.limit.management.service.DailyLimitUsageService;
import com.michael.limit.management.utils.Encryption;
import com.michael.limit.management.utils.HelperUtils;
import com.michael.limit.management.utils.LastModifiedBy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyLimitUsageServiceImpl implements DailyLimitUsageService {

    @Value("${application.transfer.limit}")
    private String transfer;

    @Value("${application.reverse.limit}")
    private String reversal;

    private final CustomerLimitRepository customerLimitRepository;

    private final LastModifiedBy lastModifiedBy;

    private final DailyLimitUsageRepository dailyLimitUsageRepository;

    private final ChannelLimitRepository channelLimitRepository;

    private final ServiceTypeRepository serviceTypeRepository;

    private final GlobalLimitRepository globalLimitRepository;

    private final NipLimitRepository nipLimitRepository;

    private final FailedDailyLimitRepository failedDailyLimitRepository;

    private final SummaryDetailRepository summaryDetailRepository;

    private final InternalLimitRepository InternalLimitRepository;

    private final ProductTypeRepository productTypeRepository;

    private final CBNMaxLimitRepository cbnMaxLimitRepository;

    private final HelperUtils helperUtils;

    private final Encryption encryption;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Map<String, Object> dailyLimit(DailyLimitUsageRequestDto requestDto, String url, String serviceToken, String serviceIpAddress) throws MyCustomException {
        log.info("{}", DailyLimitUsageServiceImpl.class);
        log.info("running daily limit details...");

        TransferMethodResponse transferMethodResponse = new TransferMethodResponse();
        CifDto getCIfId = helperUtils.getCifIdAndType(requestDto.getAccountNumber());
        String transferTypeNonInstant = "NON-INSTANT";
        String productTypeNonInstant = "ALL";
        String createTime = customerLimitRepository.createTime();

        String product = product(requestDto.getPaymentChannel(), requestDto.getChannelId());

        ProductType productType = checkProductType(requestDto, product, getCIfId);

        String instantOrNonInstant = productType.getTransfer();

        CompletableFuture<Boolean> transferChannel = CompletableFuture.supplyAsync(() ->
                validateTransferChannel(requestDto.getChannelId(), instantOrNonInstant));

        CompletableFuture<Boolean> destinationId = CompletableFuture.supplyAsync(() ->
                destinationIdSize(requestDto.getRequestDestinationId()));

        boolean transferChannelCheck = transferChannel.join();
        boolean destinationIdCheck = destinationId.join();

        if (!transferChannelCheck) {
            throw new MyCustomizedException("channel id mismatch for non-instant transfer");
        }

        if (!destinationIdCheck) {
            throw new MyCustomizedException("request destination id cannot be more than 30 characters");
        }

        String presentDate;

        try {
            presentDate = customerLimitRepository.createDate();
        } catch (Exception e) {
            log.info("error fetching date: " + e.getCause());
            throw new MyCustomException("error fetching date");
        }

        String destination = destinationRequestId(requestDto.getDestinationRequestId(), requestDto.getTransferDestinationDetails());
        BigDecimal totalArrayAmount = BigDecimal.ZERO;

        int transferType = transferType(url, requestDto.getRequestDestinationId(), requestDto.getTransferDestinationDetails()
                , presentDate, requestDto.getAccountNumber(), serviceToken, serviceIpAddress, requestDto.getChannelId(), requestDto.getIsBulk(), productType);

        Optional<DailyLimitUsageModel> isRequestIdUnique = dailyLimitUsageRepository.isRequestIdUnique(requestDto.getRequestDestinationId(), transferType, presentDate, productType.getTransfer(), productType.getProduct());

        if (isRequestIdUnique.isPresent()) {
            log.info("request destination id: " + requestDto.getRequestDestinationId() + " exist");
            throw new DuplicateException("duplicate transaction");
        }

        Optional<ServiceTypeModel> findByServiceTypeId = serviceTypeRepository.findByServiceTypeId(requestDto.getServiceTypeId());

        if (findByServiceTypeId.isEmpty()) {
            String message = String.format("service type id %s does not exist", requestDto.getServiceTypeId());
            throw new MyCustomizedException(message);
        }

        LimitManagement limitManagement = new LimitManagement();

        if (findByServiceTypeId.get().getServiceTypeId() == 1) {
            requiredFieldsForTransfer(requestDto);

            String limitType = transOrReverse(transferType);

            log.info("getCIfId: " + getCIfId);

            checkDebitFreeze(getCIfId);

//            validateRetFields(getCIfId, productType, requestDto);

            limitManagement = settingDefaultFieldsFromConfig(requestDto.getChannelId(), presentDate, getCIfId, instantOrNonInstant, productType);

            if (!productType.getProduct().equalsIgnoreCase(productTypeNonInstant) && getCIfId.getCifType().equalsIgnoreCase("CORP")) {
                limitManagement = settingDefaultFieldsFromConfigForNonInstant(requestDto.getChannelId(), presentDate, getCIfId, limitManagement);
            }
            log.info("limitManagement: " + limitManagement);

            //for getting total amount in the array
            totalArrayAmount = totalArrayAmount(requestDto.getTransferDestinationDetails(), transferType);

            //=>getting total daily amount on the table
            BigDecimal totalDailyAmount = totalDailyTransactions(totalArrayAmount, getCIfId.getCifId(), presentDate, requestDto.getChannelId(), productType);

            //=> completable future for global limit validation
            String status = "SUCCESSFUL";
            Object Map;
            BigDecimal finalTotalArrayAmount = totalArrayAmount;

            //=> global completable
            GlobalResponse globalLimit = new GlobalResponse();

            String finalGetCIfId1 = getCIfId.getCifId();
            BigDecimal finalTotalArrayAmount2 = totalArrayAmount;

            globalLimit = globalLimit(finalGetCIfId1, finalTotalArrayAmount2, presentDate, transferType, limitManagement, productType);

            if (!productType.getProduct().equalsIgnoreCase(productTypeNonInstant) && getCIfId.getCifType().equalsIgnoreCase("CORP")) {
                globalLimitNonInstant(finalGetCIfId1, finalTotalArrayAmount2, presentDate, transferType, limitManagement, globalLimit);
            }

            GlobalResponse finalGlobalLimit = globalLimit;
            CompletableFuture<Boolean> globalPerValidationLimit = CompletableFuture.supplyAsync(() ->
                    globalPerValidationLimit(finalGlobalLimit, finalTotalArrayAmount));

            CompletableFuture<Boolean> globalDailyValidationLimit = CompletableFuture.supplyAsync(() ->
                    globalDailyValidationLimit(finalGlobalLimit, totalDailyAmount, finalTotalArrayAmount2));

            if (!productType.getProduct().equalsIgnoreCase(productTypeNonInstant) && getCIfId.getCifType().equalsIgnoreCase("CORP")) {
                CompletableFuture<Boolean> globalPerValidationLimitNonInstant = CompletableFuture.supplyAsync(() ->
                        globalPerValidationLimitNonInstant(finalGlobalLimit, finalTotalArrayAmount));

                CompletableFuture<Boolean> globalDailyValidationLimitNonInstant = CompletableFuture.supplyAsync(() ->
                        globalDailyValidationLimitNonInstant(finalGlobalLimit, totalDailyAmount, finalTotalArrayAmount2));

                boolean globalPerLimitNonInstant = globalPerValidationLimitNonInstant.join();
                boolean globalDailyLimitNonInstant = globalDailyValidationLimitNonInstant.join();

                if (!globalPerLimitNonInstant) {
                    status = "failed global transaction per limit";
                    try {
                        failedAttempts(requestDto, findByServiceTypeId.get().getServiceType(), findByServiceTypeId.get().getServiceTypeId(),
                                requestDto.getDestinationRequestId(), getCIfId, status, finalTotalArrayAmount, transferType, destination,
                                BigDecimal.ZERO, limitManagement, limitType, productType, createTime);
                    } catch (Exception e) {
                        throw new MyCustomizedException(e.toString());
                    }
                    String message = "exceed limit per transaction (Global)";
                    throw new MyCustomizedException(message);
                }

                if (!globalDailyLimitNonInstant) {
                    status = "failed global daily limit";
                    try {
                        failedAttempts(requestDto, findByServiceTypeId.get().getServiceType(), findByServiceTypeId.get().getServiceTypeId(),
                                requestDto.getDestinationRequestId(), getCIfId, status, finalTotalArrayAmount, transferType, destination, BigDecimal.ZERO, limitManagement, limitType, productType, createTime);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    String message = String.format("cif id %s has surpassed it global daily limit transaction", getCIfId.getCifId());
                    log.info(message);
                    throw new MyCustomizedException("You have exceeded your daily transaction limit");
                }
            }

            boolean globalPerLimit = globalPerValidationLimit.join();
            boolean globalDailyLimit = globalDailyValidationLimit.join();

            if (!globalPerLimit) {
                status = "failed global transaction per limit";
                try {
                    failedAttempts(requestDto, findByServiceTypeId.get().getServiceType(), findByServiceTypeId.get().getServiceTypeId(),
                            requestDto.getDestinationRequestId(), getCIfId, status, finalTotalArrayAmount, transferType, destination,
                            BigDecimal.ZERO, limitManagement, limitType, productType, createTime);
                } catch (Exception e) {
                    throw new MyCustomizedException(e.toString());
                }
                String message = "exceed limit per transaction (Global)";
                throw new MyCustomizedException(message);
            }

            if (!globalDailyLimit) {
                status = "failed global daily limit";
                try {
                    failedAttempts(requestDto, findByServiceTypeId.get().getServiceType(), findByServiceTypeId.get().getServiceTypeId(),
                            requestDto.getDestinationRequestId(), getCIfId, status, finalTotalArrayAmount, transferType, destination, BigDecimal.ZERO, limitManagement, limitType, productType, createTime);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                String message = String.format("cif id %s has surpassed it global daily limit transaction", getCIfId.getCifId());
                log.info(message);
                throw new MyCustomizedException("You have exceeded your daily transaction limit");
            }

            transferMethodResponse = transferMethod(requestDto, status, requestDto.getChannelId(), findByServiceTypeId.get().getServiceTypeId()
                    , findByServiceTypeId.get().getServiceType(), getCIfId, requestDto.getTransferDestinationDetails(), requestDto.getDestinationRequestId(),
                    totalArrayAmount, totalDailyAmount, transferType, destination, presentDate, findByServiceTypeId.get().getServiceTypeId(), limitManagement, limitType, productType, createTime);

            summaryDetailsMethod(getCIfId, presentDate, totalArrayAmount, requestDto.getRequestDestinationId()
                    , transferMethodResponse.getNipAmount(), limitType, totalArrayAmount, transferType, findByServiceTypeId,
                    destination, transferMethodResponse, requestDto, limitManagement, productType, createTime);

            if (!productType.getProduct().equalsIgnoreCase(productTypeNonInstant) && getCIfId.getCifType().equalsIgnoreCase("CORP")) {
                summaryDetailsMethodNonInstant(getCIfId, presentDate, totalArrayAmount, requestDto.getRequestDestinationId()
                        , transferMethodResponse.getNipAmount(), limitType, totalArrayAmount, transferType, findByServiceTypeId,
                        destination, transferMethodResponse, requestDto, limitManagement, createTime);
            }
        } else {
            String message = String.format("invalid service type id inserted %s", requestDto.getServiceTypeId());
            throw new MyCustomizedException(message);
        }

        return CustomResponse.response("success", "00", requestDto);
    }

    private void validateRetFields(CifDto getCIfId, ProductType productType, DailyLimitUsageRequestDto requestDto) {

        if (getCIfId.getCifType().equalsIgnoreCase("RET")) {
            if (!requestDto.getTransferType().equalsIgnoreCase("instant")) {
                throw new MyCustomizedException("cif type retail can only have a transfer type of instant");
            }
        }
    }

    private ProductType checkProductType(DailyLimitUsageRequestDto requestDto, String product, CifDto getCIfId) {

        String transferType = requestDto.getTransferType();
        String newProductType = "";
        if (getCIfId.getCifType().equalsIgnoreCase("RET")) {
            newProductType = product;
            requestDto.setPaymentChannel(newProductType);
        } else {
            newProductType = requestDto.getTransferType().equalsIgnoreCase("NON-INSTANT") ? "ALL" : product;
            requestDto.setPaymentChannel(newProductType);
        }

        log.info("newProductType: " + newProductType + ",transferType: " + transferType);

        ProductType productType = productTypeRepository.findByProduct(newProductType, transferType);

        if (productType == null) {
            String message = String.format("Transfer of transferType %s and product type %s has not been configured", requestDto.getTransferType(), product);
            throw new MyCustomException(message);
        }
        return productType;
    }

    private boolean destinationIdSize(String requestDestinationId) {

        return requestDestinationId.length() <= 30;
    }

    private boolean validateTransferChannel(int channelId, String instantOrNonInstant) {

        boolean status = true;

        if (channelId == 8) {
            if (instantOrNonInstant.equalsIgnoreCase("NON-INSTANT")) {
                status = false;
            }
        }
        return status;
    }

    private String product(String productType, int channelId) {
        String product = "";

        if (productType == null || productType.equalsIgnoreCase("") || channelId == 8) {
            product = "NIPS";
        } else {
            product = productType;
        }
        return product;
    }

    private void checkDebitFreeze(CifDto getCIfId) {

        String freeze = getCIfId.getFreezeCode();

        if (freeze.equalsIgnoreCase("D")) {
            throw new MyCustomizedException("account can't be debited (debit freeze)");
        }

        if (freeze.equalsIgnoreCase("T")) {
            throw new MyCustomizedException("account can't be debited (debit freeze)");
        }
    }

    private boolean globalDailyValidationLimit(GlobalResponse globalLimit, BigDecimal totalDailyAmount, BigDecimal finalTotalArrayAmount2) {

        return globalLimit.getDailyTotalTransaction().compareTo(finalTotalArrayAmount2) >= 0;
    }

    private boolean globalDailyValidationLimitNonInstant(GlobalResponse globalLimit, BigDecimal totalDailyAmount, BigDecimal finalTotalArrayAmount2) {

        return globalLimit.getDailyTotalTransactionNonInstant().compareTo(finalTotalArrayAmount2) >= 0;
    }

    private boolean globalPerValidationLimit(GlobalResponse globalLimit, BigDecimal finalTotalArrayAmount) {

        return globalLimit.getPerTransaction().compareTo(finalTotalArrayAmount) >= 0;
    }

    private boolean globalPerValidationLimitNonInstant(GlobalResponse globalLimit, BigDecimal finalTotalArrayAmount) {

        return globalLimit.getPerTransactionNonInstant().compareTo(finalTotalArrayAmount) >= 0;
    }

    public String globalHashMethod(GlobalLimit globalLimit) {

        String hash = globalLimit.getCifId() + globalLimit.getCreatedDate() + globalLimit.getLastModifiedBy() +
                globalLimit.getTotalDailyLimit().intValue() + globalLimit.getPerTransactionLimit().intValue() + globalLimit.getStatus();

        String returnHash = encryption.encrypt(hash);

        if (globalLimit.getHash() == null) {
            log.info("updating for global limit with cif id: " + globalLimit.getCifId());
            globalLimit.setHash(returnHash);
            globalLimit = globalLimitRepository.save(globalLimit);
            log.info("successfully hashed saved global limit for cif id: " + globalLimit);
            return globalLimit.getHash();
        } else {
            return returnHash;
        }
    }

    private LimitManagement settingDefaultFieldsFromConfig(int channelId, String presentDate, CifDto getCIfId, String instantOrNonInstant, ProductType productType) {
        log.info("setting default");

        LimitManagement limitManagement = new LimitManagement();

        ChannelCode channelCode = channelLimitRepository.findUssdLimitById(String.valueOf(channelId), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
        InternalLimitModel InternalModel = InternalLimitRepository.findByKycLevel(getCIfId.getKycLevel(), getCIfId.getCifType(), productType.getTransfer(), productType.getProduct());

        SummaryDetailModel summaryDetailModel = summaryDetailRepository.findByCifId(getCIfId.getCifId(), presentDate, productType.getTransfer(), productType.getProduct());

        NipLimit nipLimit = nipLimitRepository.findNipLimitByCifId(getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());

        GlobalLimit globalLimit = globalLimitRepository.findGlobalLimitByCifId(getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());

        //check channel hash
        if (channelCode != null) {
            log.info("channelCode hash: " + channelCode.getHash());
            String hash = channelHashMethod(channelCode);
            log.info("channelCode encrypted hash: " + hash);

            if (!hash.equals(channelCode.getHash())) {
                throw new MyCustomizedException("Err-02:limit reservation failed");
            }
        }

        if (globalLimit != null) {
            log.info("globalLimit hash: " + globalLimit.getHash());

            String hash = globalHashMethod(globalLimit);
            log.info("globalLimit encrypted hash: " + hash);

            if (!hash.equals(globalLimit.getHash())) {
                throw new MyCustomizedException("Err-01:limit reservation failed");
            }
        }

        if (nipLimit != null) {
            log.info("nipLimit hash: " + nipLimit.getHash());
            String hash = nipHashMethod(nipLimit);
            log.info("nipLimit encrypted hash: " + hash);

            if (!hash.equals(nipLimit.getHash())) {
                throw new MyCustomizedException("Err-03:limit reservation failed");
            }
        }

        if (InternalModel == null) {
            String message = String.format("kyc level %s & cif type %s & transfer type %s & product type %s does not exist on the config table", getCIfId.getKycLevel(), getCIfId.getCifType(), productType.getTransfer(), productType.getProduct());
            log.info(message);
            throw new MyCustomizedException("config error");
        }

        BigDecimal globalAmount = globalLimit != null ? globalLimit.getTotalDailyLimit() : InternalModel.getGlobalDailyTransaction();
        BigDecimal globalPerAmount = globalLimit != null ? globalLimit.getPerTransactionLimit() : InternalModel.getGlobalPerTransaction();


        if (channelId == 1) {
            BigDecimal bankTotalAmt = channelCode != null ? channelCode.getTotalDailyLimit() : InternalModel.getBankDailyTransaction();
            BigDecimal bankTellerDailyTransactionLimit = bankTotalAmt.compareTo(globalAmount) < 0 ? bankTotalAmt : globalAmount;
            limitManagement.setBankTellerDailyTransactionLimit(bankTellerDailyTransactionLimit);

            if (summaryDetailModel == null) {
                ChannelCode internetChannel = channelLimitRepository.findUssdLimitById(String.valueOf(2), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal internetDailyLimitCf = getInternetDailyLimitCf(internetChannel, globalAmount, InternalModel);
                limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(internetDailyLimitCf);
                limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(internetDailyLimitCf);

                ChannelCode mobileChannel = channelLimitRepository.findUssdLimitById(String.valueOf(3), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal mobileDailyLimitCf = getMobileDailyLimitCf(mobileChannel, globalAmount, InternalModel);
                limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(mobileDailyLimitCf);
                limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(mobileDailyLimitCf);

                ChannelCode posChannel = channelLimitRepository.findUssdLimitById(String.valueOf(4), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal posDailyLimitCf = getPosDailyLimitCf(posChannel, globalAmount, InternalModel);
                limitManagement.getPosLimitCarried().setPosDailyLimitCf(posDailyLimitCf);
                limitManagement.getPosLimitCarried().setPosDailyLimitBf(posDailyLimitCf);

                ChannelCode atmChannel = channelLimitRepository.findUssdLimitById(String.valueOf(5), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal atmDailyLimitCf = getAtmDailyLimitCf(atmChannel, globalAmount, InternalModel);
                limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(atmDailyLimitCf);
                limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(atmDailyLimitCf);

                ChannelCode vendorChannel = channelLimitRepository.findUssdLimitById(String.valueOf(6), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal vendorDailyLimitCf = getVendorDailyLimitCf(vendorChannel, globalAmount, InternalModel);
                limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(vendorDailyLimitCf);
                limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(vendorDailyLimitCf);

                ChannelCode thirdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(7), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal thirdDailyLimitCf = getThirdDailyLimitCf(thirdChannel, globalAmount, InternalModel);
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(thirdDailyLimitCf);
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(thirdDailyLimitCf);

                ChannelCode ussdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(8), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal ussdDailyLimitCf = getUssdDailyLimitCf(ussdChannel, globalAmount, InternalModel);
                limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(ussdDailyLimitCf);
                limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(ussdDailyLimitCf);

                ChannelCode otherChannel = channelLimitRepository.findUssdLimitById(String.valueOf(9), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal otherDailyLimitCf = getOtherDailyLimitCf(otherChannel, globalAmount, InternalModel);
                limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(otherDailyLimitCf);
                limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(otherDailyLimitCf);
            } else {
                limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(summaryDetailModel.getTellerDailyLimitCf());
                limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(summaryDetailModel.getTellerDailyLimitBf());

                limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(summaryDetailModel.getInternetDailyLimitCf());
                limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(summaryDetailModel.getInternetDailyLimitBf());

                limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(summaryDetailModel.getMobileDailyLimitCf());
                limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(summaryDetailModel.getMobileDailyLimitBf());

                limitManagement.getPosLimitCarried().setPosDailyLimitCf(summaryDetailModel.getPosDailyLimitCf());
                limitManagement.getPosLimitCarried().setPosDailyLimitBf(summaryDetailModel.getPosDailyLimitBf());

                limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(summaryDetailModel.getAtmDailyLimitCf());
                limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(summaryDetailModel.getAtmDailyLimitBf());

                limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(summaryDetailModel.getPortalDailyLimitCf());
                limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(summaryDetailModel.getPortalDailyLimitBf());

                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(summaryDetailModel.getThirdPartyDailyLimitCf());
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(summaryDetailModel.getThirdPartyDailyLimitBf());

                limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(summaryDetailModel.getUssdDailyLimitCf());
                limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(summaryDetailModel.getUssdDailyLimitBf());

                limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(summaryDetailModel.getOthersDailyLimitCf());
                limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(summaryDetailModel.getOthersDailyLimitBf());
            }
        }

        if (channelId == 2) {
            BigDecimal bankTotalAmt = channelCode != null ? channelCode.getTotalDailyLimit() : InternalModel.getInternetDailyTransaction();
            BigDecimal internetBankingDailyTransactionLimit = bankTotalAmt.compareTo(globalAmount) < 0 ? bankTotalAmt : globalAmount;
            limitManagement.setInternetBankingDailyTransactionLimit(internetBankingDailyTransactionLimit);

            if (summaryDetailModel == null) {
                ChannelCode tellerChannel = channelLimitRepository.findUssdLimitById(String.valueOf(1), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal tellerDailyLimitCf = getTellerDailyLimitCf(tellerChannel, globalAmount, InternalModel);
                limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(tellerDailyLimitCf);
                limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(tellerDailyLimitCf);

                ChannelCode mobileChannel = channelLimitRepository.findUssdLimitById(String.valueOf(3), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal mobileDailyLimitCf = getMobileDailyLimitCf(mobileChannel, globalAmount, InternalModel);
                limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(mobileDailyLimitCf);
                limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(mobileDailyLimitCf);

                ChannelCode posChannel = channelLimitRepository.findUssdLimitById(String.valueOf(4), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal posDailyLimitCf = getPosDailyLimitCf(posChannel, globalAmount, InternalModel);
                limitManagement.getPosLimitCarried().setPosDailyLimitCf(posDailyLimitCf);
                limitManagement.getPosLimitCarried().setPosDailyLimitBf(posDailyLimitCf);

                ChannelCode atmChannel = channelLimitRepository.findUssdLimitById(String.valueOf(5), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal atmDailyLimitCf = getAtmDailyLimitCf(atmChannel, globalAmount, InternalModel);
                limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(atmDailyLimitCf);
                limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(atmDailyLimitCf);

                ChannelCode vendorChannel = channelLimitRepository.findUssdLimitById(String.valueOf(6), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal vendorDailyLimitCf = getVendorDailyLimitCf(vendorChannel, globalAmount, InternalModel);
                limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(vendorDailyLimitCf);
                limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(vendorDailyLimitCf);

                ChannelCode thirdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(7), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal thirdDailyLimitCf = getThirdDailyLimitCf(thirdChannel, globalAmount, InternalModel);
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(thirdDailyLimitCf);
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(thirdDailyLimitCf);

                ChannelCode ussdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(8), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal ussdDailyLimitCf = getUssdDailyLimitCf(ussdChannel, globalAmount, InternalModel);
                limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(ussdDailyLimitCf);
                limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(ussdDailyLimitCf);

                ChannelCode otherChannel = channelLimitRepository.findUssdLimitById(String.valueOf(9), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal otherDailyLimitCf = getOtherDailyLimitCf(otherChannel, globalAmount, InternalModel);
                limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(otherDailyLimitCf);
                limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(otherDailyLimitCf);
            } else {
                limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(summaryDetailModel.getTellerDailyLimitCf());
                limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(summaryDetailModel.getTellerDailyLimitBf());

                limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(summaryDetailModel.getInternetDailyLimitCf());
                limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(summaryDetailModel.getInternetDailyLimitBf());

                limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(summaryDetailModel.getMobileDailyLimitCf());
                limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(summaryDetailModel.getMobileDailyLimitBf());

                limitManagement.getPosLimitCarried().setPosDailyLimitCf(summaryDetailModel.getPosDailyLimitCf());
                limitManagement.getPosLimitCarried().setPosDailyLimitBf(summaryDetailModel.getPosDailyLimitBf());

                limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(summaryDetailModel.getAtmDailyLimitCf());
                limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(summaryDetailModel.getAtmDailyLimitBf());

                limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(summaryDetailModel.getPortalDailyLimitCf());
                limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(summaryDetailModel.getPortalDailyLimitBf());

                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(summaryDetailModel.getThirdPartyDailyLimitCf());
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(summaryDetailModel.getThirdPartyDailyLimitBf());

                limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(summaryDetailModel.getUssdDailyLimitCf());
                limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(summaryDetailModel.getUssdDailyLimitBf());

                limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(summaryDetailModel.getOthersDailyLimitCf());
                limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(summaryDetailModel.getOthersDailyLimitBf());
            }
        }

        if (channelId == 3) {
            BigDecimal mobileTotalAmt = channelCode != null ? channelCode.getTotalDailyLimit() : InternalModel.getMobileDailyTransaction();
            BigDecimal mobileDailyTransactionLimit = mobileTotalAmt.compareTo(globalAmount) < 0 ? mobileTotalAmt : globalAmount;
            limitManagement.setMobileDailyTransactionLimit(mobileDailyTransactionLimit);

            if (summaryDetailModel == null) {
                ChannelCode tellerChannel = channelLimitRepository.findUssdLimitById(String.valueOf(1), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal tellerDailyLimitCf = getTellerDailyLimitCf(tellerChannel, globalAmount, InternalModel);
                limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(tellerDailyLimitCf);
                limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(tellerDailyLimitCf);

                ChannelCode internetChannel = channelLimitRepository.findUssdLimitById(String.valueOf(2), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal internetDailyLimitCf = getInternetDailyLimitCf(internetChannel, globalAmount, InternalModel);
                limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(internetDailyLimitCf);
                limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(internetDailyLimitCf);

                ChannelCode posChannel = channelLimitRepository.findUssdLimitById(String.valueOf(4), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal posDailyLimitCf = getPosDailyLimitCf(posChannel, globalAmount, InternalModel);
                limitManagement.getPosLimitCarried().setPosDailyLimitCf(posDailyLimitCf);
                limitManagement.getPosLimitCarried().setPosDailyLimitBf(posDailyLimitCf);

                ChannelCode atmChannel = channelLimitRepository.findUssdLimitById(String.valueOf(5), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal atmDailyLimitCf = getAtmDailyLimitCf(atmChannel, globalAmount, InternalModel);
                limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(atmDailyLimitCf);
                limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(atmDailyLimitCf);

                ChannelCode vendorChannel = channelLimitRepository.findUssdLimitById(String.valueOf(6), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal vendorDailyLimitCf = getVendorDailyLimitCf(vendorChannel, globalAmount, InternalModel);
                limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(vendorDailyLimitCf);
                limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(vendorDailyLimitCf);

                ChannelCode thirdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(7), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal thirdDailyLimitCf = getThirdDailyLimitCf(thirdChannel, globalAmount, InternalModel);
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(thirdDailyLimitCf);
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(thirdDailyLimitCf);

                ChannelCode ussdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(8), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal ussdDailyLimitCf = getUssdDailyLimitCf(ussdChannel, globalAmount, InternalModel);
                limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(ussdDailyLimitCf);
                limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(ussdDailyLimitCf);

                ChannelCode otherChannel = channelLimitRepository.findUssdLimitById(String.valueOf(9), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal otherDailyLimitCf = getOtherDailyLimitCf(otherChannel, globalAmount, InternalModel);
                limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(otherDailyLimitCf);
                limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(otherDailyLimitCf);
            } else {
                limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(summaryDetailModel.getTellerDailyLimitCf());
                limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(summaryDetailModel.getTellerDailyLimitBf());

                limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(summaryDetailModel.getInternetDailyLimitCf());
                limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(summaryDetailModel.getInternetDailyLimitBf());

                limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(summaryDetailModel.getMobileDailyLimitCf());
                limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(summaryDetailModel.getMobileDailyLimitBf());

                limitManagement.getPosLimitCarried().setPosDailyLimitCf(summaryDetailModel.getPosDailyLimitCf());
                limitManagement.getPosLimitCarried().setPosDailyLimitBf(summaryDetailModel.getPosDailyLimitBf());

                limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(summaryDetailModel.getAtmDailyLimitCf());
                limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(summaryDetailModel.getAtmDailyLimitBf());

                limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(summaryDetailModel.getPortalDailyLimitCf());
                limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(summaryDetailModel.getPortalDailyLimitBf());

                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(summaryDetailModel.getThirdPartyDailyLimitCf());
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(summaryDetailModel.getThirdPartyDailyLimitBf());

                limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(summaryDetailModel.getUssdDailyLimitCf());
                limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(summaryDetailModel.getUssdDailyLimitBf());

                limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(summaryDetailModel.getOthersDailyLimitCf());
                limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(summaryDetailModel.getOthersDailyLimitBf());
            }
        }

        if (channelId == 4) {
            BigDecimal posTotalAmt = channelCode != null ? channelCode.getTotalDailyLimit() : InternalModel.getPosDailyTransaction();
            BigDecimal posDailyTransactionLimit = posTotalAmt.compareTo(globalAmount) < 0 ? posTotalAmt : globalAmount;
            limitManagement.setPosDailyTransactionLimit(posDailyTransactionLimit);

            if (summaryDetailModel == null) {
                ChannelCode tellerChannel = channelLimitRepository.findUssdLimitById(String.valueOf(1), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal tellerDailyLimitCf = getTellerDailyLimitCf(tellerChannel, globalAmount, InternalModel);
                limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(tellerDailyLimitCf);
                limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(tellerDailyLimitCf);

                ChannelCode internetChannel = channelLimitRepository.findUssdLimitById(String.valueOf(2), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal internetDailyLimitCf = getInternetDailyLimitCf(internetChannel, globalAmount, InternalModel);
                limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(internetDailyLimitCf);
                limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(internetDailyLimitCf);

                ChannelCode mobileChannel = channelLimitRepository.findUssdLimitById(String.valueOf(3), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal mobileDailyLimitCf = getMobileDailyLimitCf(mobileChannel, globalAmount, InternalModel);
                limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(mobileDailyLimitCf);
                limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(mobileDailyLimitCf);

                ChannelCode atmChannel = channelLimitRepository.findUssdLimitById(String.valueOf(5), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal atmDailyLimitCf = getAtmDailyLimitCf(atmChannel, globalAmount, InternalModel);
                limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(atmDailyLimitCf);
                limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(atmDailyLimitCf);

                ChannelCode vendorChannel = channelLimitRepository.findUssdLimitById(String.valueOf(6), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal vendorDailyLimitCf = getVendorDailyLimitCf(vendorChannel, globalAmount, InternalModel);
                limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(vendorDailyLimitCf);
                limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(vendorDailyLimitCf);

                ChannelCode thirdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(7), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal thirdDailyLimitCf = getThirdDailyLimitCf(thirdChannel, globalAmount, InternalModel);
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(thirdDailyLimitCf);
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(thirdDailyLimitCf);

                ChannelCode ussdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(8), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal ussdDailyLimitCf = getUssdDailyLimitCf(ussdChannel, globalAmount, InternalModel);
                limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(ussdDailyLimitCf);
                limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(ussdDailyLimitCf);

                ChannelCode otherChannel = channelLimitRepository.findUssdLimitById(String.valueOf(9), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal otherDailyLimitCf = getOtherDailyLimitCf(otherChannel, globalAmount, InternalModel);
                limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(otherDailyLimitCf);
                limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(otherDailyLimitCf);
            } else {
                limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(summaryDetailModel.getTellerDailyLimitCf());
                limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(summaryDetailModel.getTellerDailyLimitBf());

                limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(summaryDetailModel.getInternetDailyLimitCf());
                limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(summaryDetailModel.getInternetDailyLimitBf());

                limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(summaryDetailModel.getMobileDailyLimitCf());
                limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(summaryDetailModel.getMobileDailyLimitBf());

                limitManagement.getPosLimitCarried().setPosDailyLimitCf(summaryDetailModel.getPosDailyLimitCf());
                limitManagement.getPosLimitCarried().setPosDailyLimitBf(summaryDetailModel.getPosDailyLimitBf());

                limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(summaryDetailModel.getAtmDailyLimitCf());
                limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(summaryDetailModel.getAtmDailyLimitBf());

                limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(summaryDetailModel.getPortalDailyLimitCf());
                limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(summaryDetailModel.getPortalDailyLimitBf());

                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(summaryDetailModel.getThirdPartyDailyLimitCf());
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(summaryDetailModel.getThirdPartyDailyLimitBf());

                limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(summaryDetailModel.getUssdDailyLimitCf());
                limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(summaryDetailModel.getUssdDailyLimitBf());

                limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(summaryDetailModel.getOthersDailyLimitCf());
                limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(summaryDetailModel.getOthersDailyLimitBf());
            }
        }

        if (channelId == 5) {
            BigDecimal atmTotalAmt = channelCode != null ? channelCode.getTotalDailyLimit() : InternalModel.getAtmDailyTransaction();
            BigDecimal atmDailyTransactionLimit = atmTotalAmt.compareTo(globalAmount) < 0 ? atmTotalAmt : globalAmount;
            limitManagement.setAtmDailyTransactionLimit(atmDailyTransactionLimit);

            if (summaryDetailModel == null) {
                ChannelCode tellerChannel = channelLimitRepository.findUssdLimitById(String.valueOf(1), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal tellerDailyLimitCf = getTellerDailyLimitCf(tellerChannel, globalAmount, InternalModel);
                limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(tellerDailyLimitCf);
                limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(tellerDailyLimitCf);

                ChannelCode internetChannel = channelLimitRepository.findUssdLimitById(String.valueOf(2), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal internetDailyLimitCf = getInternetDailyLimitCf(internetChannel, globalAmount, InternalModel);
                limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(internetDailyLimitCf);
                limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(internetDailyLimitCf);

                ChannelCode mobileChannel = channelLimitRepository.findUssdLimitById(String.valueOf(3), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal mobileDailyLimitCf = getMobileDailyLimitCf(mobileChannel, globalAmount, InternalModel);
                limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(mobileDailyLimitCf);
                limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(mobileDailyLimitCf);

                ChannelCode posChannel = channelLimitRepository.findUssdLimitById(String.valueOf(4), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal posDailyLimitCf = getPosDailyLimitCf(posChannel, globalAmount, InternalModel);
                limitManagement.getPosLimitCarried().setPosDailyLimitCf(posDailyLimitCf);
                limitManagement.getPosLimitCarried().setPosDailyLimitBf(posDailyLimitCf);

                ChannelCode vendorChannel = channelLimitRepository.findUssdLimitById(String.valueOf(6), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal vendorDailyLimitCf = getVendorDailyLimitCf(vendorChannel, globalAmount, InternalModel);
                limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(vendorDailyLimitCf);
                limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(vendorDailyLimitCf);

                ChannelCode thirdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(7), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal thirdDailyLimitCf = getThirdDailyLimitCf(thirdChannel, globalAmount, InternalModel);
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(thirdDailyLimitCf);
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(thirdDailyLimitCf);

                ChannelCode ussdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(8), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal ussdDailyLimitCf = getUssdDailyLimitCf(ussdChannel, globalAmount, InternalModel);
                limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(ussdDailyLimitCf);
                limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(ussdDailyLimitCf);

                ChannelCode otherChannel = channelLimitRepository.findUssdLimitById(String.valueOf(9), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal otherDailyLimitCf = getOtherDailyLimitCf(otherChannel, globalAmount, InternalModel);
                limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(otherDailyLimitCf);
                limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(otherDailyLimitCf);
            } else {
                limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(summaryDetailModel.getTellerDailyLimitCf());
                limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(summaryDetailModel.getTellerDailyLimitBf());

                limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(summaryDetailModel.getInternetDailyLimitCf());
                limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(summaryDetailModel.getInternetDailyLimitBf());

                limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(summaryDetailModel.getMobileDailyLimitCf());
                limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(summaryDetailModel.getMobileDailyLimitBf());

                limitManagement.getPosLimitCarried().setPosDailyLimitCf(summaryDetailModel.getPosDailyLimitCf());
                limitManagement.getPosLimitCarried().setPosDailyLimitBf(summaryDetailModel.getPosDailyLimitBf());

                limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(summaryDetailModel.getAtmDailyLimitCf());
                limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(summaryDetailModel.getAtmDailyLimitBf());

                limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(summaryDetailModel.getPortalDailyLimitCf());
                limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(summaryDetailModel.getPortalDailyLimitBf());

                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(summaryDetailModel.getThirdPartyDailyLimitCf());
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(summaryDetailModel.getThirdPartyDailyLimitBf());

                limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(summaryDetailModel.getUssdDailyLimitCf());
                limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(summaryDetailModel.getUssdDailyLimitBf());

                limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(summaryDetailModel.getOthersDailyLimitCf());
                limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(summaryDetailModel.getOthersDailyLimitBf());
            }
        }

        if (channelId == 6) {
            BigDecimal vendorTotalAmt = channelCode != null ? channelCode.getTotalDailyLimit() : InternalModel.getVendorDailyTransaction();
            BigDecimal vendorDailyTransactionLimit = vendorTotalAmt.compareTo(globalAmount) < 0 ? vendorTotalAmt : globalAmount;
            limitManagement.setVendorDailyTransactionLimit(vendorDailyTransactionLimit);

            if (summaryDetailModel == null) {
                ChannelCode tellerChannel = channelLimitRepository.findUssdLimitById(String.valueOf(1), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal tellerDailyLimitCf = getTellerDailyLimitCf(tellerChannel, globalAmount, InternalModel);
                limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(tellerDailyLimitCf);
                limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(tellerDailyLimitCf);

                ChannelCode internetChannel = channelLimitRepository.findUssdLimitById(String.valueOf(2), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal internetDailyLimitCf = getInternetDailyLimitCf(internetChannel, globalAmount, InternalModel);
                limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(internetDailyLimitCf);
                limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(internetDailyLimitCf);

                ChannelCode mobileChannel = channelLimitRepository.findUssdLimitById(String.valueOf(3), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal mobileDailyLimitCf = getMobileDailyLimitCf(mobileChannel, globalAmount, InternalModel);
                limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(mobileDailyLimitCf);
                limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(mobileDailyLimitCf);

                ChannelCode posChannel = channelLimitRepository.findUssdLimitById(String.valueOf(4), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal posDailyLimitCf = getPosDailyLimitCf(posChannel, globalAmount, InternalModel);
                limitManagement.getPosLimitCarried().setPosDailyLimitCf(posDailyLimitCf);
                limitManagement.getPosLimitCarried().setPosDailyLimitBf(posDailyLimitCf);

                ChannelCode atmChannel = channelLimitRepository.findUssdLimitById(String.valueOf(5), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal atmDailyLimitCf = getAtmDailyLimitCf(atmChannel, globalAmount, InternalModel);
                limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(atmDailyLimitCf);
                limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(atmDailyLimitCf);

                ChannelCode thirdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(7), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal thirdDailyLimitCf = getThirdDailyLimitCf(thirdChannel, globalAmount, InternalModel);
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(thirdDailyLimitCf);
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(thirdDailyLimitCf);

                ChannelCode ussdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(8), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal ussdDailyLimitCf = getUssdDailyLimitCf(ussdChannel, globalAmount, InternalModel);
                limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(ussdDailyLimitCf);
                limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(ussdDailyLimitCf);

                ChannelCode otherChannel = channelLimitRepository.findUssdLimitById(String.valueOf(9), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal otherDailyLimitCf = getOtherDailyLimitCf(otherChannel, globalAmount, InternalModel);
                limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(otherDailyLimitCf);
                limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(otherDailyLimitCf);
            } else {
                limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(summaryDetailModel.getTellerDailyLimitCf());
                limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(summaryDetailModel.getTellerDailyLimitBf());

                limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(summaryDetailModel.getInternetDailyLimitCf());
                limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(summaryDetailModel.getInternetDailyLimitBf());

                limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(summaryDetailModel.getMobileDailyLimitCf());
                limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(summaryDetailModel.getMobileDailyLimitBf());

                limitManagement.getPosLimitCarried().setPosDailyLimitCf(summaryDetailModel.getPosDailyLimitCf());
                limitManagement.getPosLimitCarried().setPosDailyLimitBf(summaryDetailModel.getPosDailyLimitBf());

                limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(summaryDetailModel.getAtmDailyLimitCf());
                limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(summaryDetailModel.getAtmDailyLimitBf());

                limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(summaryDetailModel.getPortalDailyLimitCf());
                limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(summaryDetailModel.getPortalDailyLimitBf());

                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(summaryDetailModel.getThirdPartyDailyLimitCf());
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(summaryDetailModel.getThirdPartyDailyLimitBf());

                limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(summaryDetailModel.getUssdDailyLimitCf());
                limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(summaryDetailModel.getUssdDailyLimitBf());

                limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(summaryDetailModel.getOthersDailyLimitCf());
                limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(summaryDetailModel.getOthersDailyLimitBf());
            }
        }

        if (channelId == 7) {
            BigDecimal thirdTotalAmt = channelCode != null ? channelCode.getTotalDailyLimit() : InternalModel.getThirdDailyTransaction();
            BigDecimal thirdPartyDailyTransactionLimit = thirdTotalAmt.compareTo(globalAmount) < 0 ? thirdTotalAmt : globalAmount;
            limitManagement.setThirdPartyDailyTransactionLimit(thirdPartyDailyTransactionLimit);

            if (summaryDetailModel == null) {
                ChannelCode tellerChannel = channelLimitRepository.findUssdLimitById(String.valueOf(1), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal tellerDailyLimitCf = getTellerDailyLimitCf(tellerChannel, globalAmount, InternalModel);
                limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(tellerDailyLimitCf);
                limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(tellerDailyLimitCf);

                ChannelCode internetChannel = channelLimitRepository.findUssdLimitById(String.valueOf(2), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal internetDailyLimitCf = getInternetDailyLimitCf(internetChannel, globalAmount, InternalModel);
                limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(internetDailyLimitCf);
                limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(internetDailyLimitCf);

                ChannelCode mobileChannel = channelLimitRepository.findUssdLimitById(String.valueOf(3), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal mobileDailyLimitCf = getMobileDailyLimitCf(mobileChannel, globalAmount, InternalModel);
                limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(mobileDailyLimitCf);
                limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(mobileDailyLimitCf);

                ChannelCode posChannel = channelLimitRepository.findUssdLimitById(String.valueOf(4), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal posDailyLimitCf = getPosDailyLimitCf(posChannel, globalAmount, InternalModel);
                limitManagement.getPosLimitCarried().setPosDailyLimitCf(posDailyLimitCf);
                limitManagement.getPosLimitCarried().setPosDailyLimitBf(posDailyLimitCf);

                ChannelCode atmChannel = channelLimitRepository.findUssdLimitById(String.valueOf(5), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal atmDailyLimitCf = getAtmDailyLimitCf(atmChannel, globalAmount, InternalModel);
                limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(atmDailyLimitCf);
                limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(atmDailyLimitCf);

                ChannelCode vendorChannel = channelLimitRepository.findUssdLimitById(String.valueOf(6), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal vendorDailyLimitCf = getVendorDailyLimitCf(vendorChannel, globalAmount, InternalModel);
                limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(vendorDailyLimitCf);
                limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(vendorDailyLimitCf);

                ChannelCode ussdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(8), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal ussdDailyLimitCf = getUssdDailyLimitCf(ussdChannel, globalAmount, InternalModel);
                limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(ussdDailyLimitCf);
                limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(ussdDailyLimitCf);

                ChannelCode otherChannel = channelLimitRepository.findUssdLimitById(String.valueOf(9), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal otherDailyLimitCf = getOtherDailyLimitCf(otherChannel, globalAmount, InternalModel);
                limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(otherDailyLimitCf);
                limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(otherDailyLimitCf);
            } else {
                limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(summaryDetailModel.getTellerDailyLimitCf());
                limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(summaryDetailModel.getTellerDailyLimitBf());

                limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(summaryDetailModel.getInternetDailyLimitCf());
                limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(summaryDetailModel.getInternetDailyLimitBf());

                limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(summaryDetailModel.getMobileDailyLimitCf());
                limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(summaryDetailModel.getMobileDailyLimitBf());

                limitManagement.getPosLimitCarried().setPosDailyLimitCf(summaryDetailModel.getPosDailyLimitCf());
                limitManagement.getPosLimitCarried().setPosDailyLimitBf(summaryDetailModel.getPosDailyLimitBf());

                limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(summaryDetailModel.getAtmDailyLimitCf());
                limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(summaryDetailModel.getAtmDailyLimitBf());

                limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(summaryDetailModel.getPortalDailyLimitCf());
                limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(summaryDetailModel.getPortalDailyLimitBf());

                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(summaryDetailModel.getThirdPartyDailyLimitCf());
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(summaryDetailModel.getThirdPartyDailyLimitBf());

                limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(summaryDetailModel.getUssdDailyLimitCf());
                limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(summaryDetailModel.getUssdDailyLimitBf());

                limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(summaryDetailModel.getOthersDailyLimitCf());
                limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(summaryDetailModel.getOthersDailyLimitBf());
            }
        }

        if (channelId == 8) {
            BigDecimal ussdTotalAmt = channelCode != null ? channelCode.getTotalDailyLimit() : InternalModel.getUssdDailyTransaction();
            BigDecimal ussdDailyTransactionLimit = ussdTotalAmt.compareTo(globalAmount) < 0 ? ussdTotalAmt : globalAmount;
            limitManagement.setUssdDailyTransactionLimit(ussdDailyTransactionLimit);

            if (summaryDetailModel == null) {
                ChannelCode tellerChannel = channelLimitRepository.findUssdLimitById(String.valueOf(1), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal tellerDailyLimitCf = getTellerDailyLimitCf(tellerChannel, globalAmount, InternalModel);
                limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(tellerDailyLimitCf);
                limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(tellerDailyLimitCf);

                ChannelCode internetChannel = channelLimitRepository.findUssdLimitById(String.valueOf(2), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal internetDailyLimitCf = getInternetDailyLimitCf(internetChannel, globalAmount, InternalModel);
                limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(internetDailyLimitCf);
                limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(internetDailyLimitCf);

                ChannelCode mobileChannel = channelLimitRepository.findUssdLimitById(String.valueOf(3), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal mobileDailyLimitCf = getMobileDailyLimitCf(mobileChannel, globalAmount, InternalModel);
                limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(mobileDailyLimitCf);
                limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(mobileDailyLimitCf);

                ChannelCode posChannel = channelLimitRepository.findUssdLimitById(String.valueOf(4), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal posDailyLimitCf = getPosDailyLimitCf(posChannel, globalAmount, InternalModel);
                limitManagement.getPosLimitCarried().setPosDailyLimitCf(posDailyLimitCf);
                limitManagement.getPosLimitCarried().setPosDailyLimitBf(posDailyLimitCf);

                ChannelCode atmChannel = channelLimitRepository.findUssdLimitById(String.valueOf(5), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal atmDailyLimitCf = getAtmDailyLimitCf(atmChannel, globalAmount, InternalModel);
                limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(atmDailyLimitCf);
                limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(atmDailyLimitCf);

                ChannelCode vendorChannel = channelLimitRepository.findUssdLimitById(String.valueOf(6), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal vendorDailyLimitCf = getVendorDailyLimitCf(vendorChannel, globalAmount, InternalModel);
                limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(vendorDailyLimitCf);
                limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(vendorDailyLimitCf);

                ChannelCode thirdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(7), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal thirdDailyLimitCf = getThirdDailyLimitCf(thirdChannel, globalAmount, InternalModel);
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(thirdDailyLimitCf);
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(thirdDailyLimitCf);

                ChannelCode otherChannel = channelLimitRepository.findUssdLimitById(String.valueOf(9), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal otherDailyLimitCf = getOtherDailyLimitCf(otherChannel, globalAmount, InternalModel);
                limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(otherDailyLimitCf);
                limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(otherDailyLimitCf);
            } else {
                limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(summaryDetailModel.getTellerDailyLimitCf());
                limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(summaryDetailModel.getTellerDailyLimitBf());

                limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(summaryDetailModel.getInternetDailyLimitCf());
                limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(summaryDetailModel.getInternetDailyLimitBf());

                limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(summaryDetailModel.getMobileDailyLimitCf());
                limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(summaryDetailModel.getMobileDailyLimitBf());

                limitManagement.getPosLimitCarried().setPosDailyLimitCf(summaryDetailModel.getPosDailyLimitCf());
                limitManagement.getPosLimitCarried().setPosDailyLimitBf(summaryDetailModel.getPosDailyLimitBf());

                limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(summaryDetailModel.getAtmDailyLimitCf());
                limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(summaryDetailModel.getAtmDailyLimitBf());

                limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(summaryDetailModel.getPortalDailyLimitCf());
                limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(summaryDetailModel.getPortalDailyLimitBf());

                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(summaryDetailModel.getThirdPartyDailyLimitCf());
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(summaryDetailModel.getThirdPartyDailyLimitBf());

                limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(summaryDetailModel.getUssdDailyLimitCf());
                limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(summaryDetailModel.getUssdDailyLimitBf());

                limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(summaryDetailModel.getOthersDailyLimitCf());
                limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(summaryDetailModel.getOthersDailyLimitBf());
            }
        }

        if (channelId == 9) {
            BigDecimal othersTotalAmt = channelCode != null ? channelCode.getTotalDailyLimit() : InternalModel.getOthersDailyTransaction();
            BigDecimal othersDailyTransactionLimit = othersTotalAmt.compareTo(globalAmount) < 0 ? othersTotalAmt : globalAmount;
            limitManagement.setOthersDailyTransactionLimit(othersDailyTransactionLimit);

            if (summaryDetailModel == null) {
                ChannelCode tellerChannel = channelLimitRepository.findUssdLimitById(String.valueOf(1), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal tellerDailyLimitCf = getTellerDailyLimitCf(tellerChannel, globalAmount, InternalModel);
                limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(tellerDailyLimitCf);
                limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(tellerDailyLimitCf);

                ChannelCode internetChannel = channelLimitRepository.findUssdLimitById(String.valueOf(2), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal internetDailyLimitCf = getInternetDailyLimitCf(internetChannel, globalAmount, InternalModel);
                limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(internetDailyLimitCf);
                limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(internetDailyLimitCf);

                ChannelCode mobileChannel = channelLimitRepository.findUssdLimitById(String.valueOf(3), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal mobileDailyLimitCf = getMobileDailyLimitCf(mobileChannel, globalAmount, InternalModel);
                limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(mobileDailyLimitCf);
                limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(mobileDailyLimitCf);

                ChannelCode posChannel = channelLimitRepository.findUssdLimitById(String.valueOf(4), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal posDailyLimitCf = getPosDailyLimitCf(posChannel, globalAmount, InternalModel);
                limitManagement.getPosLimitCarried().setPosDailyLimitCf(posDailyLimitCf);
                limitManagement.getPosLimitCarried().setPosDailyLimitBf(posDailyLimitCf);

                ChannelCode atmChannel = channelLimitRepository.findUssdLimitById(String.valueOf(5), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal atmDailyLimitCf = getAtmDailyLimitCf(atmChannel, globalAmount, InternalModel);
                limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(atmDailyLimitCf);
                limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(atmDailyLimitCf);

                ChannelCode vendorChannel = channelLimitRepository.findUssdLimitById(String.valueOf(6), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal vendorDailyLimitCf = getVendorDailyLimitCf(vendorChannel, globalAmount, InternalModel);
                limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(vendorDailyLimitCf);
                limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(vendorDailyLimitCf);

                ChannelCode thirdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(7), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal thirdDailyLimitCf = getThirdDailyLimitCf(thirdChannel, globalAmount, InternalModel);
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(thirdDailyLimitCf);
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(thirdDailyLimitCf);

                ChannelCode ussdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(8), getCIfId.getCifId(), productType.getTransfer(), productType.getProduct());
                BigDecimal ussdDailyLimitCf = getUssdDailyLimitCf(ussdChannel, globalAmount, InternalModel);
                limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(ussdDailyLimitCf);
                limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(ussdDailyLimitCf);
            } else {
                limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(summaryDetailModel.getTellerDailyLimitCf());
                limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(summaryDetailModel.getTellerDailyLimitBf());

                limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(summaryDetailModel.getInternetDailyLimitCf());
                limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(summaryDetailModel.getInternetDailyLimitBf());

                limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(summaryDetailModel.getMobileDailyLimitCf());
                limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(summaryDetailModel.getMobileDailyLimitBf());

                limitManagement.getPosLimitCarried().setPosDailyLimitCf(summaryDetailModel.getPosDailyLimitCf());
                limitManagement.getPosLimitCarried().setPosDailyLimitBf(summaryDetailModel.getPosDailyLimitBf());

                limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(summaryDetailModel.getAtmDailyLimitCf());
                limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(summaryDetailModel.getAtmDailyLimitBf());

                limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(summaryDetailModel.getPortalDailyLimitCf());
                limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(summaryDetailModel.getPortalDailyLimitBf());

                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(summaryDetailModel.getThirdPartyDailyLimitCf());
                limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(summaryDetailModel.getThirdPartyDailyLimitBf());

                limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(summaryDetailModel.getUssdDailyLimitCf());
                limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(summaryDetailModel.getUssdDailyLimitBf());

                limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(summaryDetailModel.getOthersDailyLimitCf());
                limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(summaryDetailModel.getOthersDailyLimitBf());
            }
        }

        BigDecimal nipLimitAmt;
        BigDecimal nipLimitCf;
        BigDecimal nipLimitBf;
        if (summaryDetailModel != null) {
            nipLimitAmt = summaryDetailModel.getNipDailyLimitCf();
            nipLimitCf = nipLimitAmt;
            nipLimitBf = summaryDetailModel.getNipDailyLimitBf();////
        } else {
            nipLimitAmt = nipLimit != null ? nipLimit.getTotalDailyLimit() : InternalModel.getNipDailyTransaction();
            nipLimitCf = nipLimitAmt.compareTo(globalAmount) < 0 ? nipLimitAmt : globalAmount;
            nipLimitBf = nipLimitCf;
        }
        limitManagement.setNipDailyTransactionLimit(nipLimitCf);
        limitManagement.getNipLimitCarried().setNipDailyLimitCf(nipLimitCf);
        limitManagement.getNipLimitCarried().setNipDailyLimitBf(nipLimitBf);

        if (summaryDetailModel != null) {
            limitManagement.setGlobalDailyTransactionLimit(summaryDetailModel.getGlobalDailyLimitCf());
            limitManagement.getGlobalLimitCarried().setGlobalDailyLimitCf(summaryDetailModel.getGlobalDailyLimitCf());
            limitManagement.getGlobalLimitCarried().setGlobalDailyLimitBf(summaryDetailModel.getGlobalDailyLimitBf());
        } else {
            limitManagement.setGlobalDailyTransactionLimit(globalAmount);
            limitManagement.getGlobalLimitCarried().setGlobalDailyLimitCf(globalAmount);
            limitManagement.getGlobalLimitCarried().setGlobalDailyLimitBf(globalAmount);
        }

        //per amount set
        limitManagement.setGlobalPerTransactionLimit(globalPerAmount);

        limitManagement.setNipPerTransactionLimit(getNipPerAmount(globalPerAmount, nipLimit, InternalModel));

        limitManagement.setUssdPerTransactionLimit(getChannelUssdPerAmount(globalPerAmount, channelCode, InternalModel));

        limitManagement.setBankTellerPerTransactionLimit(getBankTellerPerAmount(globalPerAmount, channelCode, InternalModel));

        limitManagement.setInternetBankingPerTransactionLimit(getInternetPerAmount(globalPerAmount, channelCode, InternalModel));

        limitManagement.setMobilePerTransactionLimit(getMobilePerAmount(globalPerAmount, channelCode, InternalModel));

        limitManagement.setPosPerTransactionLimit(getPosPerAmount(globalPerAmount, channelCode, InternalModel));

        limitManagement.setAtmPerTransactionLimit(getAtmPerAmount(globalPerAmount, channelCode, InternalModel));

        limitManagement.setVendorPerTransactionLimit(getVendorPerAmount(globalPerAmount, channelCode, InternalModel));

        limitManagement.setThirdPartyPerTransactionLimit(getThirdPerAmount(globalPerAmount, channelCode, InternalModel));

        limitManagement.setOthersPerTransactionLimit(getOthersPerAmount(globalPerAmount, channelCode, InternalModel));

        return limitManagement;
    }

    private LimitManagement settingDefaultFieldsFromConfigForNonInstant(int channelId, String presentDate, CifDto getCIfId, LimitManagement limitManagement) {
        log.info("setting default");

        String transferType = "NON-INSTANT";
        String productType = "ALL";

        ChannelCode nonChannelCode = channelLimitRepository.findUssdLimitById(String.valueOf(channelId), getCIfId.getCifId(), transferType, productType);
        InternalLimitModel nonInternalModel = InternalLimitRepository.findByKycLevel(getCIfId.getKycLevel(), getCIfId.getCifType(), transferType, productType);

        SummaryDetailModel nonSummaryDetailModel = summaryDetailRepository.findByCifId(getCIfId.getCifId(), presentDate, transferType, productType);

        NipLimit nonNipLimit = nipLimitRepository.findNipLimitByCifId(getCIfId.getCifId(), transferType, productType);

        GlobalLimit nonGlobalLimit = globalLimitRepository.findGlobalLimitByCifId(getCIfId.getCifId(), transferType, productType);

        //check channel hash
//        if (nonChannelCode != null) {
//            log.info("channelCode hash: " + channelCode.getHash());
//            String hash = channelHashMethod(nonChannelCode);
//            log.info("channelCode encrypted hash: " + hash);
//
//            if (!hash.equals(nonChannelCode.getHash())) {
//                throw new MyCustomizedException("Err-02:limit reservation failed");
//            }
//        }
//
//        if (nonGlobalLimit != null) {
//            log.info("globalLimit hash: " + globalLimit.getHash());
//
//            String hash = globalHashMethod(nonGlobalLimit);
//            log.info("globalLimit encrypted hash: " + hash);
//
//            if (!hash.equals(nonGlobalLimit.getHash())) {
//                throw new MyCustomizedException("Err-01:limit reservation failed");
//            }
//        }
//
//        if (nonNipLimit != null) {
//            log.info("nipLimit hash: " + nipLimit.getHash());
//            String hash = nipHashMethod(nonNipLimit);
//            log.info("nipLimit encrypted hash: " + hash);
//
//            if (!hash.equals(nonNipLimit.getHash())) {
//                throw new MyCustomizedException("Err-03:limit reservation failed");
//            }
//        }

        if (nonInternalModel == null) {
            String message = String.format("kyc level %s & cif type %s & transfer type %s & product type %s does not exist on the config table", getCIfId.getKycLevel(), getCIfId.getCifType(), transferType, productType);
            log.info(message);
            throw new MyCustomizedException("config error");
        }

        BigDecimal globalAmount = nonGlobalLimit != null ? nonGlobalLimit.getTotalDailyLimit() : nonInternalModel.getGlobalDailyTransaction();
        BigDecimal globalPerAmount = nonGlobalLimit != null ? nonGlobalLimit.getPerTransactionLimit() : nonInternalModel.getGlobalPerTransaction();


        if (channelId == 1) {
            BigDecimal bankTotalAmt = nonChannelCode != null ? nonChannelCode.getTotalDailyLimit() : nonInternalModel.getBankDailyTransaction();
            BigDecimal bankTellerDailyTransactionLimit = bankTotalAmt.compareTo(globalAmount) < 0 ? bankTotalAmt : globalAmount;
            limitManagement.setNonInstantBankTellerDailyTransactionLimit(bankTellerDailyTransactionLimit);

            if (nonSummaryDetailModel == null) {
                ChannelCode internetChannel = channelLimitRepository.findUssdLimitById(String.valueOf(2), getCIfId.getCifId(), transferType, productType);
                BigDecimal internetDailyLimitCf = getInternetDailyLimitCf(internetChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(internetDailyLimitCf);
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(internetDailyLimitCf);

                ChannelCode mobileChannel = channelLimitRepository.findUssdLimitById(String.valueOf(3), getCIfId.getCifId(), transferType, productType);
                BigDecimal mobileDailyLimitCf = getMobileDailyLimitCf(mobileChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(mobileDailyLimitCf);
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(mobileDailyLimitCf);

                ChannelCode posChannel = channelLimitRepository.findUssdLimitById(String.valueOf(4), getCIfId.getCifId(), transferType, productType);
                BigDecimal posDailyLimitCf = getPosDailyLimitCf(posChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(posDailyLimitCf);
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(posDailyLimitCf);

                ChannelCode atmChannel = channelLimitRepository.findUssdLimitById(String.valueOf(5), getCIfId.getCifId(), transferType, productType);
                BigDecimal atmDailyLimitCf = getAtmDailyLimitCf(atmChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(atmDailyLimitCf);
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(atmDailyLimitCf);

                ChannelCode vendorChannel = channelLimitRepository.findUssdLimitById(String.valueOf(6), getCIfId.getCifId(), transferType, productType);
                BigDecimal vendorDailyLimitCf = getVendorDailyLimitCf(vendorChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(vendorDailyLimitCf);
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(vendorDailyLimitCf);

                ChannelCode thirdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(7), getCIfId.getCifId(), transferType, productType);
                BigDecimal thirdDailyLimitCf = getThirdDailyLimitCf(thirdChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(thirdDailyLimitCf);
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(thirdDailyLimitCf);

                ChannelCode ussdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(8), getCIfId.getCifId(), transferType, productType);
                BigDecimal ussdDailyLimitCf = getUssdDailyLimitCf(ussdChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(ussdDailyLimitCf);
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(ussdDailyLimitCf);

                ChannelCode otherChannel = channelLimitRepository.findUssdLimitById(String.valueOf(9), getCIfId.getCifId(), transferType, productType);
                BigDecimal otherDailyLimitCf = getOtherDailyLimitCf(otherChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(otherDailyLimitCf);
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(otherDailyLimitCf);
            } else {
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(nonSummaryDetailModel.getTellerDailyLimitCf());
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(nonSummaryDetailModel.getTellerDailyLimitBf());

                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(nonSummaryDetailModel.getInternetDailyLimitCf());
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(nonSummaryDetailModel.getInternetDailyLimitBf());

                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(nonSummaryDetailModel.getMobileDailyLimitCf());
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(nonSummaryDetailModel.getMobileDailyLimitBf());

                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(nonSummaryDetailModel.getPosDailyLimitCf());
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(nonSummaryDetailModel.getPosDailyLimitBf());

                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(nonSummaryDetailModel.getAtmDailyLimitCf());
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(nonSummaryDetailModel.getAtmDailyLimitBf());

                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(nonSummaryDetailModel.getPortalDailyLimitCf());
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(nonSummaryDetailModel.getPortalDailyLimitBf());

                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(nonSummaryDetailModel.getThirdPartyDailyLimitCf());
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(nonSummaryDetailModel.getThirdPartyDailyLimitBf());

                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(nonSummaryDetailModel.getUssdDailyLimitCf());
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(nonSummaryDetailModel.getUssdDailyLimitBf());

                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(nonSummaryDetailModel.getOthersDailyLimitCf());
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(nonSummaryDetailModel.getOthersDailyLimitBf());
            }
        }

        if (channelId == 2) {
            BigDecimal bankTotalAmt = nonChannelCode != null ? nonChannelCode.getTotalDailyLimit() : nonInternalModel.getInternetDailyTransaction();
            BigDecimal internetBankingDailyTransactionLimit = bankTotalAmt.compareTo(globalAmount) < 0 ? bankTotalAmt : globalAmount;
            limitManagement.setNonInstantInternetBankingDailyTransactionLimit(internetBankingDailyTransactionLimit);

            if (nonSummaryDetailModel == null) {
                ChannelCode tellerChannel = channelLimitRepository.findUssdLimitById(String.valueOf(1), getCIfId.getCifId(), transferType, productType);
                BigDecimal tellerDailyLimitCf = getTellerDailyLimitCf(tellerChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(tellerDailyLimitCf);
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(tellerDailyLimitCf);

                ChannelCode mobileChannel = channelLimitRepository.findUssdLimitById(String.valueOf(3), getCIfId.getCifId(), transferType, productType);
                BigDecimal mobileDailyLimitCf = getMobileDailyLimitCf(mobileChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(mobileDailyLimitCf);
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(mobileDailyLimitCf);

                ChannelCode posChannel = channelLimitRepository.findUssdLimitById(String.valueOf(4), getCIfId.getCifId(), transferType, productType);
                BigDecimal posDailyLimitCf = getPosDailyLimitCf(posChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(posDailyLimitCf);
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(posDailyLimitCf);

                ChannelCode atmChannel = channelLimitRepository.findUssdLimitById(String.valueOf(5), getCIfId.getCifId(), transferType, productType);
                BigDecimal atmDailyLimitCf = getAtmDailyLimitCf(atmChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(atmDailyLimitCf);
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(atmDailyLimitCf);

                ChannelCode vendorChannel = channelLimitRepository.findUssdLimitById(String.valueOf(6), getCIfId.getCifId(), transferType, productType);
                BigDecimal vendorDailyLimitCf = getVendorDailyLimitCf(vendorChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(vendorDailyLimitCf);
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(vendorDailyLimitCf);

                ChannelCode thirdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(7), getCIfId.getCifId(), transferType, productType);
                BigDecimal thirdDailyLimitCf = getThirdDailyLimitCf(thirdChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(thirdDailyLimitCf);
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(thirdDailyLimitCf);

                ChannelCode ussdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(8), getCIfId.getCifId(), transferType, productType);
                BigDecimal ussdDailyLimitCf = getUssdDailyLimitCf(ussdChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(ussdDailyLimitCf);
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(ussdDailyLimitCf);

                ChannelCode otherChannel = channelLimitRepository.findUssdLimitById(String.valueOf(9), getCIfId.getCifId(), transferType, productType);
                BigDecimal otherDailyLimitCf = getOtherDailyLimitCf(otherChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(otherDailyLimitCf);
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(otherDailyLimitCf);
            } else {
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(nonSummaryDetailModel.getTellerDailyLimitCf());
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(nonSummaryDetailModel.getTellerDailyLimitBf());

                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(nonSummaryDetailModel.getInternetDailyLimitCf());
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(nonSummaryDetailModel.getInternetDailyLimitBf());

                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(nonSummaryDetailModel.getMobileDailyLimitCf());
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(nonSummaryDetailModel.getMobileDailyLimitBf());

                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(nonSummaryDetailModel.getPosDailyLimitCf());
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(nonSummaryDetailModel.getPosDailyLimitBf());

                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(nonSummaryDetailModel.getAtmDailyLimitCf());
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(nonSummaryDetailModel.getAtmDailyLimitBf());

                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(nonSummaryDetailModel.getPortalDailyLimitCf());
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(nonSummaryDetailModel.getPortalDailyLimitBf());

                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(nonSummaryDetailModel.getThirdPartyDailyLimitCf());
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(nonSummaryDetailModel.getThirdPartyDailyLimitBf());

                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(nonSummaryDetailModel.getUssdDailyLimitCf());
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(nonSummaryDetailModel.getUssdDailyLimitBf());

                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(nonSummaryDetailModel.getOthersDailyLimitCf());
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(nonSummaryDetailModel.getOthersDailyLimitBf());
            }
        }

        if (channelId == 3) {
            BigDecimal mobileTotalAmt = nonChannelCode != null ? nonChannelCode.getTotalDailyLimit() : nonInternalModel.getMobileDailyTransaction();
            BigDecimal mobileDailyTransactionLimit = mobileTotalAmt.compareTo(globalAmount) < 0 ? mobileTotalAmt : globalAmount;
            limitManagement.setNonInstantMobileDailyTransactionLimit(mobileDailyTransactionLimit);

            if (nonSummaryDetailModel == null) {
                ChannelCode tellerChannel = channelLimitRepository.findUssdLimitById(String.valueOf(1), getCIfId.getCifId(), transferType, productType);
                BigDecimal tellerDailyLimitCf = getTellerDailyLimitCf(tellerChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(tellerDailyLimitCf);
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(tellerDailyLimitCf);

                ChannelCode internetChannel = channelLimitRepository.findUssdLimitById(String.valueOf(2), getCIfId.getCifId(), transferType, productType);
                BigDecimal internetDailyLimitCf = getInternetDailyLimitCf(internetChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(internetDailyLimitCf);
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(internetDailyLimitCf);

                ChannelCode posChannel = channelLimitRepository.findUssdLimitById(String.valueOf(4), getCIfId.getCifId(), transferType, productType);
                BigDecimal posDailyLimitCf = getPosDailyLimitCf(posChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(posDailyLimitCf);
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(posDailyLimitCf);

                ChannelCode atmChannel = channelLimitRepository.findUssdLimitById(String.valueOf(5), getCIfId.getCifId(), transferType, productType);
                BigDecimal atmDailyLimitCf = getAtmDailyLimitCf(atmChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(atmDailyLimitCf);
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(atmDailyLimitCf);

                ChannelCode vendorChannel = channelLimitRepository.findUssdLimitById(String.valueOf(6), getCIfId.getCifId(), transferType, productType);
                BigDecimal vendorDailyLimitCf = getVendorDailyLimitCf(vendorChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(vendorDailyLimitCf);
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(vendorDailyLimitCf);

                ChannelCode thirdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(7), getCIfId.getCifId(), transferType, productType);
                BigDecimal thirdDailyLimitCf = getThirdDailyLimitCf(thirdChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(thirdDailyLimitCf);
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(thirdDailyLimitCf);

                ChannelCode ussdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(8), getCIfId.getCifId(), transferType, productType);
                BigDecimal ussdDailyLimitCf = getUssdDailyLimitCf(ussdChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(ussdDailyLimitCf);
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(ussdDailyLimitCf);

                ChannelCode otherChannel = channelLimitRepository.findUssdLimitById(String.valueOf(9), getCIfId.getCifId(), transferType, productType);
                BigDecimal otherDailyLimitCf = getOtherDailyLimitCf(otherChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(otherDailyLimitCf);
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(otherDailyLimitCf);
            } else {
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(nonSummaryDetailModel.getTellerDailyLimitCf());
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(nonSummaryDetailModel.getTellerDailyLimitBf());

                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(nonSummaryDetailModel.getInternetDailyLimitCf());
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(nonSummaryDetailModel.getInternetDailyLimitBf());

                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(nonSummaryDetailModel.getMobileDailyLimitCf());
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(nonSummaryDetailModel.getMobileDailyLimitBf());

                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(nonSummaryDetailModel.getPosDailyLimitCf());
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(nonSummaryDetailModel.getPosDailyLimitBf());

                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(nonSummaryDetailModel.getAtmDailyLimitCf());
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(nonSummaryDetailModel.getAtmDailyLimitBf());

                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(nonSummaryDetailModel.getPortalDailyLimitCf());
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(nonSummaryDetailModel.getPortalDailyLimitBf());

                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(nonSummaryDetailModel.getThirdPartyDailyLimitCf());
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(nonSummaryDetailModel.getThirdPartyDailyLimitBf());

                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(nonSummaryDetailModel.getUssdDailyLimitCf());
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(nonSummaryDetailModel.getUssdDailyLimitBf());

                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(nonSummaryDetailModel.getOthersDailyLimitCf());
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(nonSummaryDetailModel.getOthersDailyLimitBf());
            }
        }

        if (channelId == 4) {
            BigDecimal posTotalAmt = nonChannelCode != null ? nonChannelCode.getTotalDailyLimit() : nonInternalModel.getPosDailyTransaction();
            BigDecimal posDailyTransactionLimit = posTotalAmt.compareTo(globalAmount) < 0 ? posTotalAmt : globalAmount;
            limitManagement.setNonInstantPosDailyTransactionLimit(posDailyTransactionLimit);

            if (nonSummaryDetailModel == null) {
                ChannelCode tellerChannel = channelLimitRepository.findUssdLimitById(String.valueOf(1), getCIfId.getCifId(), transferType, productType);
                BigDecimal tellerDailyLimitCf = getTellerDailyLimitCf(tellerChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(tellerDailyLimitCf);
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(tellerDailyLimitCf);

                ChannelCode internetChannel = channelLimitRepository.findUssdLimitById(String.valueOf(2), getCIfId.getCifId(), transferType, productType);
                BigDecimal internetDailyLimitCf = getInternetDailyLimitCf(internetChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(internetDailyLimitCf);
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(internetDailyLimitCf);

                ChannelCode mobileChannel = channelLimitRepository.findUssdLimitById(String.valueOf(3), getCIfId.getCifId(), transferType, productType);
                BigDecimal mobileDailyLimitCf = getMobileDailyLimitCf(mobileChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(mobileDailyLimitCf);
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(mobileDailyLimitCf);

                ChannelCode atmChannel = channelLimitRepository.findUssdLimitById(String.valueOf(5), getCIfId.getCifId(), transferType, productType);
                BigDecimal atmDailyLimitCf = getAtmDailyLimitCf(atmChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(atmDailyLimitCf);
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(atmDailyLimitCf);

                ChannelCode vendorChannel = channelLimitRepository.findUssdLimitById(String.valueOf(6), getCIfId.getCifId(), transferType, productType);
                BigDecimal vendorDailyLimitCf = getVendorDailyLimitCf(vendorChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(vendorDailyLimitCf);
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(vendorDailyLimitCf);

                ChannelCode thirdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(7), getCIfId.getCifId(), transferType, productType);
                BigDecimal thirdDailyLimitCf = getThirdDailyLimitCf(thirdChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(thirdDailyLimitCf);
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(thirdDailyLimitCf);

                ChannelCode ussdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(8), getCIfId.getCifId(), transferType, productType);
                BigDecimal ussdDailyLimitCf = getUssdDailyLimitCf(ussdChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(ussdDailyLimitCf);
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(ussdDailyLimitCf);

                ChannelCode otherChannel = channelLimitRepository.findUssdLimitById(String.valueOf(9), getCIfId.getCifId(), transferType, productType);
                BigDecimal otherDailyLimitCf = getOtherDailyLimitCf(otherChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(otherDailyLimitCf);
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(otherDailyLimitCf);
            } else {
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(nonSummaryDetailModel.getTellerDailyLimitCf());
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(nonSummaryDetailModel.getTellerDailyLimitBf());

                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(nonSummaryDetailModel.getInternetDailyLimitCf());
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(nonSummaryDetailModel.getInternetDailyLimitBf());

                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(nonSummaryDetailModel.getMobileDailyLimitCf());
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(nonSummaryDetailModel.getMobileDailyLimitBf());

                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(nonSummaryDetailModel.getPosDailyLimitCf());
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(nonSummaryDetailModel.getPosDailyLimitBf());

                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(nonSummaryDetailModel.getAtmDailyLimitCf());
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(nonSummaryDetailModel.getAtmDailyLimitBf());

                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(nonSummaryDetailModel.getPortalDailyLimitCf());
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(nonSummaryDetailModel.getPortalDailyLimitBf());

                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(nonSummaryDetailModel.getThirdPartyDailyLimitCf());
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(nonSummaryDetailModel.getThirdPartyDailyLimitBf());

                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(nonSummaryDetailModel.getUssdDailyLimitCf());
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(nonSummaryDetailModel.getUssdDailyLimitBf());

                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(nonSummaryDetailModel.getOthersDailyLimitCf());
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(nonSummaryDetailModel.getOthersDailyLimitBf());
            }
        }

        if (channelId == 5) {
            BigDecimal atmTotalAmt = nonChannelCode != null ? nonChannelCode.getTotalDailyLimit() : nonInternalModel.getAtmDailyTransaction();
            BigDecimal atmDailyTransactionLimit = atmTotalAmt.compareTo(globalAmount) < 0 ? atmTotalAmt : globalAmount;
            limitManagement.setNonInstantAtmDailyTransactionLimit(atmDailyTransactionLimit);

            if (nonSummaryDetailModel == null) {
                ChannelCode tellerChannel = channelLimitRepository.findUssdLimitById(String.valueOf(1), getCIfId.getCifId(), transferType, productType);
                BigDecimal tellerDailyLimitCf = getTellerDailyLimitCf(tellerChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(tellerDailyLimitCf);
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(tellerDailyLimitCf);

                ChannelCode internetChannel = channelLimitRepository.findUssdLimitById(String.valueOf(2), getCIfId.getCifId(), transferType, productType);
                BigDecimal internetDailyLimitCf = getInternetDailyLimitCf(internetChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(internetDailyLimitCf);
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(internetDailyLimitCf);

                ChannelCode mobileChannel = channelLimitRepository.findUssdLimitById(String.valueOf(3), getCIfId.getCifId(), transferType, productType);
                BigDecimal mobileDailyLimitCf = getMobileDailyLimitCf(mobileChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(mobileDailyLimitCf);
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(mobileDailyLimitCf);

                ChannelCode posChannel = channelLimitRepository.findUssdLimitById(String.valueOf(4), getCIfId.getCifId(), transferType, productType);
                BigDecimal posDailyLimitCf = getPosDailyLimitCf(posChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(posDailyLimitCf);
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(posDailyLimitCf);

                ChannelCode vendorChannel = channelLimitRepository.findUssdLimitById(String.valueOf(6), getCIfId.getCifId(), transferType, productType);
                BigDecimal vendorDailyLimitCf = getVendorDailyLimitCf(vendorChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(vendorDailyLimitCf);
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(vendorDailyLimitCf);

                ChannelCode thirdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(7), getCIfId.getCifId(), transferType, productType);
                BigDecimal thirdDailyLimitCf = getThirdDailyLimitCf(thirdChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(thirdDailyLimitCf);
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(thirdDailyLimitCf);

                ChannelCode ussdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(8), getCIfId.getCifId(), transferType, productType);
                BigDecimal ussdDailyLimitCf = getUssdDailyLimitCf(ussdChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(ussdDailyLimitCf);
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(ussdDailyLimitCf);

                ChannelCode otherChannel = channelLimitRepository.findUssdLimitById(String.valueOf(9), getCIfId.getCifId(), transferType, productType);
                BigDecimal otherDailyLimitCf = getOtherDailyLimitCf(otherChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(otherDailyLimitCf);
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(otherDailyLimitCf);
            } else {
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(nonSummaryDetailModel.getTellerDailyLimitCf());
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(nonSummaryDetailModel.getTellerDailyLimitBf());

                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(nonSummaryDetailModel.getInternetDailyLimitCf());
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(nonSummaryDetailModel.getInternetDailyLimitBf());

                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(nonSummaryDetailModel.getMobileDailyLimitCf());
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(nonSummaryDetailModel.getMobileDailyLimitBf());

                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(nonSummaryDetailModel.getPosDailyLimitCf());
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(nonSummaryDetailModel.getPosDailyLimitBf());

                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(nonSummaryDetailModel.getAtmDailyLimitCf());
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(nonSummaryDetailModel.getAtmDailyLimitBf());

                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(nonSummaryDetailModel.getPortalDailyLimitCf());
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(nonSummaryDetailModel.getPortalDailyLimitBf());

                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(nonSummaryDetailModel.getThirdPartyDailyLimitCf());
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(nonSummaryDetailModel.getThirdPartyDailyLimitBf());

                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(nonSummaryDetailModel.getUssdDailyLimitCf());
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(nonSummaryDetailModel.getUssdDailyLimitBf());

                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(nonSummaryDetailModel.getOthersDailyLimitCf());
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(nonSummaryDetailModel.getOthersDailyLimitBf());
            }
        }

        if (channelId == 6) {
            BigDecimal vendorTotalAmt = nonChannelCode != null ? nonChannelCode.getTotalDailyLimit() : nonInternalModel.getVendorDailyTransaction();
            BigDecimal vendorDailyTransactionLimit = vendorTotalAmt.compareTo(globalAmount) < 0 ? vendorTotalAmt : globalAmount;
            limitManagement.setNonInstantVendorDailyTransactionLimit(vendorDailyTransactionLimit);

            if (nonSummaryDetailModel == null) {
                ChannelCode tellerChannel = channelLimitRepository.findUssdLimitById(String.valueOf(1), getCIfId.getCifId(), transferType, productType);
                BigDecimal tellerDailyLimitCf = getTellerDailyLimitCf(tellerChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(tellerDailyLimitCf);
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(tellerDailyLimitCf);

                ChannelCode internetChannel = channelLimitRepository.findUssdLimitById(String.valueOf(2), getCIfId.getCifId(), transferType, productType);
                BigDecimal internetDailyLimitCf = getInternetDailyLimitCf(internetChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(internetDailyLimitCf);
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(internetDailyLimitCf);

                ChannelCode mobileChannel = channelLimitRepository.findUssdLimitById(String.valueOf(3), getCIfId.getCifId(), transferType, productType);
                BigDecimal mobileDailyLimitCf = getMobileDailyLimitCf(mobileChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(mobileDailyLimitCf);
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(mobileDailyLimitCf);

                ChannelCode posChannel = channelLimitRepository.findUssdLimitById(String.valueOf(4), getCIfId.getCifId(), transferType, productType);
                BigDecimal posDailyLimitCf = getPosDailyLimitCf(posChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(posDailyLimitCf);
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(posDailyLimitCf);

                ChannelCode atmChannel = channelLimitRepository.findUssdLimitById(String.valueOf(5), getCIfId.getCifId(), transferType, productType);
                BigDecimal atmDailyLimitCf = getAtmDailyLimitCf(atmChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(atmDailyLimitCf);
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(atmDailyLimitCf);

                ChannelCode thirdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(7), getCIfId.getCifId(), transferType, productType);
                BigDecimal thirdDailyLimitCf = getThirdDailyLimitCf(thirdChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(thirdDailyLimitCf);
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(thirdDailyLimitCf);

                ChannelCode ussdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(8), getCIfId.getCifId(), transferType, productType);
                BigDecimal ussdDailyLimitCf = getUssdDailyLimitCf(ussdChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(ussdDailyLimitCf);
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(ussdDailyLimitCf);

                ChannelCode otherChannel = channelLimitRepository.findUssdLimitById(String.valueOf(9), getCIfId.getCifId(), transferType, productType);
                BigDecimal otherDailyLimitCf = getOtherDailyLimitCf(otherChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(otherDailyLimitCf);
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(otherDailyLimitCf);
            } else {
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(nonSummaryDetailModel.getTellerDailyLimitCf());
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(nonSummaryDetailModel.getTellerDailyLimitBf());

                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(nonSummaryDetailModel.getInternetDailyLimitCf());
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(nonSummaryDetailModel.getInternetDailyLimitBf());

                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(nonSummaryDetailModel.getMobileDailyLimitCf());
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(nonSummaryDetailModel.getMobileDailyLimitBf());

                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(nonSummaryDetailModel.getPosDailyLimitCf());
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(nonSummaryDetailModel.getPosDailyLimitBf());

                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(nonSummaryDetailModel.getAtmDailyLimitCf());
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(nonSummaryDetailModel.getAtmDailyLimitBf());

                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(nonSummaryDetailModel.getPortalDailyLimitCf());
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(nonSummaryDetailModel.getPortalDailyLimitBf());

                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(nonSummaryDetailModel.getThirdPartyDailyLimitCf());
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(nonSummaryDetailModel.getThirdPartyDailyLimitBf());

                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(nonSummaryDetailModel.getUssdDailyLimitCf());
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(nonSummaryDetailModel.getUssdDailyLimitBf());

                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(nonSummaryDetailModel.getOthersDailyLimitCf());
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(nonSummaryDetailModel.getOthersDailyLimitBf());
            }
        }

        if (channelId == 7) {
            BigDecimal thirdTotalAmt = nonChannelCode != null ? nonChannelCode.getTotalDailyLimit() : nonInternalModel.getThirdDailyTransaction();
            BigDecimal thirdPartyDailyTransactionLimit = thirdTotalAmt.compareTo(globalAmount) < 0 ? thirdTotalAmt : globalAmount;
            limitManagement.setNonInstantThirdPartyDailyTransactionLimit(thirdPartyDailyTransactionLimit);

            if (nonSummaryDetailModel == null) {
                ChannelCode tellerChannel = channelLimitRepository.findUssdLimitById(String.valueOf(1), getCIfId.getCifId(), transferType, productType);
                BigDecimal tellerDailyLimitCf = getTellerDailyLimitCf(tellerChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(tellerDailyLimitCf);
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(tellerDailyLimitCf);

                ChannelCode internetChannel = channelLimitRepository.findUssdLimitById(String.valueOf(2), getCIfId.getCifId(), transferType, productType);
                BigDecimal internetDailyLimitCf = getInternetDailyLimitCf(internetChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(internetDailyLimitCf);
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(internetDailyLimitCf);

                ChannelCode mobileChannel = channelLimitRepository.findUssdLimitById(String.valueOf(3), getCIfId.getCifId(), transferType, productType);
                BigDecimal mobileDailyLimitCf = getMobileDailyLimitCf(mobileChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(mobileDailyLimitCf);
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(mobileDailyLimitCf);

                ChannelCode posChannel = channelLimitRepository.findUssdLimitById(String.valueOf(4), getCIfId.getCifId(), transferType, productType);
                BigDecimal posDailyLimitCf = getPosDailyLimitCf(posChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(posDailyLimitCf);
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(posDailyLimitCf);

                ChannelCode atmChannel = channelLimitRepository.findUssdLimitById(String.valueOf(5), getCIfId.getCifId(), transferType, productType);
                BigDecimal atmDailyLimitCf = getAtmDailyLimitCf(atmChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(atmDailyLimitCf);
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(atmDailyLimitCf);

                ChannelCode vendorChannel = channelLimitRepository.findUssdLimitById(String.valueOf(6), getCIfId.getCifId(), transferType, productType);
                BigDecimal vendorDailyLimitCf = getVendorDailyLimitCf(vendorChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(vendorDailyLimitCf);
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(vendorDailyLimitCf);

                ChannelCode ussdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(8), getCIfId.getCifId(), transferType, productType);
                BigDecimal ussdDailyLimitCf = getUssdDailyLimitCf(ussdChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(ussdDailyLimitCf);
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(ussdDailyLimitCf);

                ChannelCode otherChannel = channelLimitRepository.findUssdLimitById(String.valueOf(9), getCIfId.getCifId(), transferType, productType);
                BigDecimal otherDailyLimitCf = getOtherDailyLimitCf(otherChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(otherDailyLimitCf);
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(otherDailyLimitCf);
            } else {
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(nonSummaryDetailModel.getTellerDailyLimitCf());
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(nonSummaryDetailModel.getTellerDailyLimitBf());

                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(nonSummaryDetailModel.getInternetDailyLimitCf());
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(nonSummaryDetailModel.getInternetDailyLimitBf());

                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(nonSummaryDetailModel.getMobileDailyLimitCf());
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(nonSummaryDetailModel.getMobileDailyLimitBf());

                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(nonSummaryDetailModel.getPosDailyLimitCf());
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(nonSummaryDetailModel.getPosDailyLimitBf());

                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(nonSummaryDetailModel.getAtmDailyLimitCf());
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(nonSummaryDetailModel.getAtmDailyLimitBf());

                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(nonSummaryDetailModel.getPortalDailyLimitCf());
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(nonSummaryDetailModel.getPortalDailyLimitBf());

                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(nonSummaryDetailModel.getThirdPartyDailyLimitCf());
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(nonSummaryDetailModel.getThirdPartyDailyLimitBf());

                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(nonSummaryDetailModel.getUssdDailyLimitCf());
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(nonSummaryDetailModel.getUssdDailyLimitBf());

                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(nonSummaryDetailModel.getOthersDailyLimitCf());
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(nonSummaryDetailModel.getOthersDailyLimitBf());
            }
        }

        if (channelId == 8) {
            BigDecimal ussdTotalAmt = nonChannelCode != null ? nonChannelCode.getTotalDailyLimit() : nonInternalModel.getUssdDailyTransaction();
            BigDecimal ussdDailyTransactionLimit = ussdTotalAmt.compareTo(globalAmount) < 0 ? ussdTotalAmt : globalAmount;
            limitManagement.setNonInstantUssdDailyTransactionLimit(ussdDailyTransactionLimit);

            if (nonSummaryDetailModel == null) {
                ChannelCode tellerChannel = channelLimitRepository.findUssdLimitById(String.valueOf(1), getCIfId.getCifId(), transferType, productType);
                BigDecimal tellerDailyLimitCf = getTellerDailyLimitCf(tellerChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(tellerDailyLimitCf);
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(tellerDailyLimitCf);

                ChannelCode internetChannel = channelLimitRepository.findUssdLimitById(String.valueOf(2), getCIfId.getCifId(), transferType, productType);
                BigDecimal internetDailyLimitCf = getInternetDailyLimitCf(internetChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(internetDailyLimitCf);
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(internetDailyLimitCf);

                ChannelCode mobileChannel = channelLimitRepository.findUssdLimitById(String.valueOf(3), getCIfId.getCifId(), transferType, productType);
                BigDecimal mobileDailyLimitCf = getMobileDailyLimitCf(mobileChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(mobileDailyLimitCf);
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(mobileDailyLimitCf);

                ChannelCode posChannel = channelLimitRepository.findUssdLimitById(String.valueOf(4), getCIfId.getCifId(), transferType, productType);
                BigDecimal posDailyLimitCf = getPosDailyLimitCf(posChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(posDailyLimitCf);
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(posDailyLimitCf);

                ChannelCode atmChannel = channelLimitRepository.findUssdLimitById(String.valueOf(5), getCIfId.getCifId(), transferType, productType);
                BigDecimal atmDailyLimitCf = getAtmDailyLimitCf(atmChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(atmDailyLimitCf);
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(atmDailyLimitCf);

                ChannelCode vendorChannel = channelLimitRepository.findUssdLimitById(String.valueOf(6), getCIfId.getCifId(), transferType, productType);
                BigDecimal vendorDailyLimitCf = getVendorDailyLimitCf(vendorChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(vendorDailyLimitCf);
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(vendorDailyLimitCf);

                ChannelCode thirdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(7), getCIfId.getCifId(), transferType, productType);
                BigDecimal thirdDailyLimitCf = getThirdDailyLimitCf(thirdChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(thirdDailyLimitCf);
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(thirdDailyLimitCf);

                ChannelCode otherChannel = channelLimitRepository.findUssdLimitById(String.valueOf(9), getCIfId.getCifId(), transferType, productType);
                BigDecimal otherDailyLimitCf = getOtherDailyLimitCf(otherChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(otherDailyLimitCf);
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(otherDailyLimitCf);
            } else {
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(nonSummaryDetailModel.getTellerDailyLimitCf());
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(nonSummaryDetailModel.getTellerDailyLimitBf());

                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(nonSummaryDetailModel.getInternetDailyLimitCf());
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(nonSummaryDetailModel.getInternetDailyLimitBf());

                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(nonSummaryDetailModel.getMobileDailyLimitCf());
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(nonSummaryDetailModel.getMobileDailyLimitBf());

                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(nonSummaryDetailModel.getPosDailyLimitCf());
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(nonSummaryDetailModel.getPosDailyLimitBf());

                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(nonSummaryDetailModel.getAtmDailyLimitCf());
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(nonSummaryDetailModel.getAtmDailyLimitBf());

                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(nonSummaryDetailModel.getPortalDailyLimitCf());
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(nonSummaryDetailModel.getPortalDailyLimitBf());

                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(nonSummaryDetailModel.getThirdPartyDailyLimitCf());
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(nonSummaryDetailModel.getThirdPartyDailyLimitBf());

                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(nonSummaryDetailModel.getUssdDailyLimitCf());
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(nonSummaryDetailModel.getUssdDailyLimitBf());

                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(nonSummaryDetailModel.getOthersDailyLimitCf());
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(nonSummaryDetailModel.getOthersDailyLimitBf());
            }
        }

        if (channelId == 9) {
            BigDecimal othersTotalAmt = nonChannelCode != null ? nonChannelCode.getTotalDailyLimit() : nonInternalModel.getOthersDailyTransaction();
            BigDecimal othersDailyTransactionLimit = othersTotalAmt.compareTo(globalAmount) < 0 ? othersTotalAmt : globalAmount;
            limitManagement.setNonInstantOthersDailyTransactionLimit(othersDailyTransactionLimit);

            if (nonSummaryDetailModel == null) {
                ChannelCode tellerChannel = channelLimitRepository.findUssdLimitById(String.valueOf(1), getCIfId.getCifId(), transferType, productType);
                BigDecimal tellerDailyLimitCf = getTellerDailyLimitCf(tellerChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(tellerDailyLimitCf);
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(tellerDailyLimitCf);

                ChannelCode internetChannel = channelLimitRepository.findUssdLimitById(String.valueOf(2), getCIfId.getCifId(), transferType, productType);
                BigDecimal internetDailyLimitCf = getInternetDailyLimitCf(internetChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(internetDailyLimitCf);
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(internetDailyLimitCf);

                ChannelCode mobileChannel = channelLimitRepository.findUssdLimitById(String.valueOf(3), getCIfId.getCifId(), transferType, productType);
                BigDecimal mobileDailyLimitCf = getMobileDailyLimitCf(mobileChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(mobileDailyLimitCf);
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(mobileDailyLimitCf);

                ChannelCode posChannel = channelLimitRepository.findUssdLimitById(String.valueOf(4), getCIfId.getCifId(), transferType, productType);
                BigDecimal posDailyLimitCf = getPosDailyLimitCf(posChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(posDailyLimitCf);
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(posDailyLimitCf);

                ChannelCode atmChannel = channelLimitRepository.findUssdLimitById(String.valueOf(5), getCIfId.getCifId(), transferType, productType);
                BigDecimal atmDailyLimitCf = getAtmDailyLimitCf(atmChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(atmDailyLimitCf);
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(atmDailyLimitCf);

                ChannelCode vendorChannel = channelLimitRepository.findUssdLimitById(String.valueOf(6), getCIfId.getCifId(), transferType, productType);
                BigDecimal vendorDailyLimitCf = getVendorDailyLimitCf(vendorChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(vendorDailyLimitCf);
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(vendorDailyLimitCf);

                ChannelCode thirdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(7), getCIfId.getCifId(), transferType, productType);
                BigDecimal thirdDailyLimitCf = getThirdDailyLimitCf(thirdChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(thirdDailyLimitCf);
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(thirdDailyLimitCf);

                ChannelCode ussdChannel = channelLimitRepository.findUssdLimitById(String.valueOf(8), getCIfId.getCifId(), transferType, productType);
                BigDecimal ussdDailyLimitCf = getUssdDailyLimitCf(ussdChannel, globalAmount, nonInternalModel);
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(ussdDailyLimitCf);
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(ussdDailyLimitCf);
            } else {
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(nonSummaryDetailModel.getTellerDailyLimitCf());
                limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(nonSummaryDetailModel.getTellerDailyLimitBf());

                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(nonSummaryDetailModel.getInternetDailyLimitCf());
                limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(nonSummaryDetailModel.getInternetDailyLimitBf());

                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(nonSummaryDetailModel.getMobileDailyLimitCf());
                limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(nonSummaryDetailModel.getMobileDailyLimitBf());

                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(nonSummaryDetailModel.getPosDailyLimitCf());
                limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(nonSummaryDetailModel.getPosDailyLimitBf());

                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(nonSummaryDetailModel.getAtmDailyLimitCf());
                limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(nonSummaryDetailModel.getAtmDailyLimitBf());

                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(nonSummaryDetailModel.getPortalDailyLimitCf());
                limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(nonSummaryDetailModel.getPortalDailyLimitBf());

                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(nonSummaryDetailModel.getThirdPartyDailyLimitCf());
                limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(nonSummaryDetailModel.getThirdPartyDailyLimitBf());

                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(nonSummaryDetailModel.getUssdDailyLimitCf());
                limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(nonSummaryDetailModel.getUssdDailyLimitBf());

                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(nonSummaryDetailModel.getOthersDailyLimitCf());
                limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(nonSummaryDetailModel.getOthersDailyLimitBf());
            }
        }

        BigDecimal nipLimitAmt;
        BigDecimal nipLimitCf;
        BigDecimal nipLimitBf;
        if (nonSummaryDetailModel != null) {
            nipLimitAmt = nonSummaryDetailModel.getNipDailyLimitCf();
            nipLimitCf = nipLimitAmt;
            nipLimitBf = nonSummaryDetailModel.getNipDailyLimitBf();////
        } else {
            nipLimitAmt = nonNipLimit != null ? nonNipLimit.getTotalDailyLimit() : nonInternalModel.getNipDailyTransaction();
            nipLimitCf = nipLimitAmt.compareTo(globalAmount) < 0 ? nipLimitAmt : globalAmount;
            nipLimitBf = nipLimitCf;
        }
        limitManagement.setNonInstantNipDailyTransactionLimit(nipLimitCf);
        limitManagement.getNonInstantNipLimitCarried().setNipDailyLimitCf(nipLimitCf);
        limitManagement.getNonInstantNipLimitCarried().setNipDailyLimitBf(nipLimitBf);

        if (nonSummaryDetailModel != null) {
            limitManagement.setNonInstantGlobalDailyTransactionLimit(nonSummaryDetailModel.getGlobalDailyLimitCf());
            limitManagement.getNonInstantGlobalLimitCarried().setGlobalDailyLimitCf(nonSummaryDetailModel.getGlobalDailyLimitCf());
            limitManagement.getNonInstantGlobalLimitCarried().setGlobalDailyLimitBf(nonSummaryDetailModel.getGlobalDailyLimitBf());
        } else {
            limitManagement.setNonInstantGlobalDailyTransactionLimit(globalAmount);
            limitManagement.getNonInstantGlobalLimitCarried().setGlobalDailyLimitCf(globalAmount);
            limitManagement.getNonInstantGlobalLimitCarried().setGlobalDailyLimitBf(globalAmount);
        }

        //per amount set
        limitManagement.setNonInstantGlobalPerTransactionLimit(globalPerAmount);

        limitManagement.setNonInstantNipPerTransactionLimit(getNipPerAmount(globalPerAmount, nonNipLimit, nonInternalModel));

        limitManagement.setNonInstantUssdPerTransactionLimit(getChannelUssdPerAmount(globalPerAmount, nonChannelCode, nonInternalModel));

        limitManagement.setNonInstantBankTellerPerTransactionLimit(getBankTellerPerAmount(globalPerAmount, nonChannelCode, nonInternalModel));

        limitManagement.setNonInstantInternetBankingPerTransactionLimit(getInternetPerAmount(globalPerAmount, nonChannelCode, nonInternalModel));

        limitManagement.setNonInstantMobilePerTransactionLimit(getMobilePerAmount(globalPerAmount, nonChannelCode, nonInternalModel));

        limitManagement.setNonInstantPosPerTransactionLimit(getPosPerAmount(globalPerAmount, nonChannelCode, nonInternalModel));

        limitManagement.setNonInstantAtmPerTransactionLimit(getAtmPerAmount(globalPerAmount, nonChannelCode, nonInternalModel));

        limitManagement.setNonInstantVendorPerTransactionLimit(getVendorPerAmount(globalPerAmount, nonChannelCode, nonInternalModel));

        limitManagement.setNonInstantThirdPartyPerTransactionLimit(getThirdPerAmount(globalPerAmount, nonChannelCode, nonInternalModel));

        limitManagement.setNonInstantOthersPerTransactionLimit(getOthersPerAmount(globalPerAmount, nonChannelCode, nonInternalModel));

        return limitManagement;
    }

    public String channelHashMethod(ChannelCode channelCode) {

        String hash = channelCode.getCifId() + channelCode.getCreatedDate() + channelCode.getLastModifiedBy() +
                channelCode.getTotalDailyLimit().intValue() + channelCode.getPerTransactionLimit().intValue() + channelCode.getStatus();
        String returnHash = encryption.encrypt(hash);

        if (channelCode.getHash() == null) {
            log.info("updated for channel " + channelCode.getChannelId() + " limit with cif id: " + channelCode.getCifId());
            channelCode.setHash(returnHash);
            channelCode = channelLimitRepository.save(channelCode);
            log.info("successfully saved for channel " + channelCode.getChannelId() + " limit with cif id: " + channelCode.getCifId());
            return channelCode.getHash();
        } else {
            return returnHash;
        }
    }

    private BigDecimal getOthersPerAmount(BigDecimal globalPerAmount, ChannelCode channelCode, InternalLimitModel InternalModel) {
        BigDecimal channelCodePerAmt = channelCode != null ? channelCode.getPerTransactionLimit() : InternalModel.getOthersPerTransaction();

        return channelCodePerAmt.compareTo(globalPerAmount) < 0 ? channelCodePerAmt : globalPerAmount;
    }

    private BigDecimal getThirdPerAmount(BigDecimal globalPerAmount, ChannelCode channelCode, InternalLimitModel InternalModel) {
        BigDecimal channelCodePerAmt = channelCode != null ? channelCode.getPerTransactionLimit() : InternalModel.getThirdPerTransaction();

        return channelCodePerAmt.compareTo(globalPerAmount) < 0 ? channelCodePerAmt : globalPerAmount;
    }

    private BigDecimal getVendorPerAmount(BigDecimal globalPerAmount, ChannelCode channelCode, InternalLimitModel InternalModel) {
        BigDecimal channelCodePerAmt = channelCode != null ? channelCode.getPerTransactionLimit() : InternalModel.getVendorPerTransaction();

        return channelCodePerAmt.compareTo(globalPerAmount) < 0 ? channelCodePerAmt : globalPerAmount;
    }

    private BigDecimal getAtmPerAmount(BigDecimal globalPerAmount, ChannelCode channelCode, InternalLimitModel InternalModel) {
        BigDecimal channelCodePerAmt = channelCode != null ? channelCode.getPerTransactionLimit() : InternalModel.getAtmPerTransaction();

        return channelCodePerAmt.compareTo(globalPerAmount) < 0 ? channelCodePerAmt : globalPerAmount;
    }

    private BigDecimal getPosPerAmount(BigDecimal globalPerAmount, ChannelCode channelCode, InternalLimitModel InternalModel) {
        BigDecimal channelCodePerAmt = channelCode != null ? channelCode.getPerTransactionLimit() : InternalModel.getPosPerTransaction();

        return channelCodePerAmt.compareTo(globalPerAmount) < 0 ? channelCodePerAmt : globalPerAmount;
    }

    private BigDecimal getMobilePerAmount(BigDecimal globalPerAmount, ChannelCode channelCode, InternalLimitModel InternalModel) {
        BigDecimal channelCodePerAmt = channelCode != null ? channelCode.getPerTransactionLimit() : InternalModel.getMobilePerTransaction();

        return channelCodePerAmt.compareTo(globalPerAmount) < 0 ? channelCodePerAmt : globalPerAmount;
    }

    private BigDecimal getInternetPerAmount(BigDecimal globalPerAmount, ChannelCode channelCode, InternalLimitModel InternalModel) {
        BigDecimal channelCodePerAmt = channelCode != null ? channelCode.getPerTransactionLimit() : InternalModel.getInternetPerTransaction();

        return channelCodePerAmt.compareTo(globalPerAmount) < 0 ? channelCodePerAmt : globalPerAmount;
    }

    private BigDecimal getBankTellerPerAmount(BigDecimal globalPerAmount, ChannelCode channelCode, InternalLimitModel InternalModel) {
        BigDecimal channelCodePerAmt = channelCode != null ? channelCode.getPerTransactionLimit() : InternalModel.getBankPerTransaction();

        return channelCodePerAmt.compareTo(globalPerAmount) < 0 ? channelCodePerAmt : globalPerAmount;
    }

    private BigDecimal getChannelUssdPerAmount(BigDecimal globalPerAmount, ChannelCode channelCode, InternalLimitModel InternalModel) {
        BigDecimal channelCodePerAmt = channelCode != null ? channelCode.getPerTransactionLimit() : InternalModel.getUssdPerTransaction();

        return channelCodePerAmt.compareTo(globalPerAmount) < 0 ? channelCodePerAmt : globalPerAmount;
    }

    private BigDecimal getNipPerAmount(BigDecimal globalPerAmount, NipLimit nipLimit, InternalLimitModel InternalModel) {
        BigDecimal nipPerAmt = nipLimit != null ? nipLimit.getPerTransactionLimit() : InternalModel.getNipPerTransaction();

        return nipPerAmt.compareTo(globalPerAmount) < 0 ? nipPerAmt : globalPerAmount;
    }

    public String nipHashMethod(NipLimit nipLimit) {

        String hash = nipLimit.getCifId() + nipLimit.getCreatedDate() + nipLimit.getLastModifiedBy() +
                nipLimit.getTotalDailyLimit().intValue() + nipLimit.getPerTransactionLimit().intValue() + nipLimit.getStatus();

        String returnHash = encryption.encrypt(hash);

        if (nipLimit.getHash() == null) {
            log.info("updating for nip limit with cif id: " + nipLimit.getCifId());
            nipLimit.setHash(returnHash);
            nipLimit = nipLimitRepository.save(nipLimit);
            log.info("successfully saved nip limit for cif id: " + nipLimit);
            return nipLimit.getHash();
        } else {
            return returnHash;
        }
    }

    private BigDecimal getTellerDailyLimitCf(ChannelCode tellerChannel, BigDecimal globalAmount, InternalLimitModel InternalModel) {
        BigDecimal tellerTotalAmt = tellerChannel != null ? tellerChannel.getTotalDailyLimit() : InternalModel.getBankDailyTransaction();

        return tellerTotalAmt.compareTo(globalAmount) < 0 ? tellerTotalAmt : globalAmount;
    }

    private BigDecimal getInternetDailyLimitCf(ChannelCode internetChannel, BigDecimal globalAmount, InternalLimitModel InternalModel) {
        BigDecimal internetTotalAmt = internetChannel != null ? internetChannel.getTotalDailyLimit() : InternalModel.getInternetDailyTransaction();

        return internetTotalAmt.compareTo(globalAmount) < 0 ? internetTotalAmt : globalAmount;
    }

    private BigDecimal getMobileDailyLimitCf(ChannelCode mobileChannel, BigDecimal globalAmount, InternalLimitModel InternalModel) {
        BigDecimal mobileTotalAmt = mobileChannel != null ? mobileChannel.getTotalDailyLimit() : InternalModel.getMobileDailyTransaction();

        return mobileTotalAmt.compareTo(globalAmount) < 0 ? mobileTotalAmt : globalAmount;
    }

    private BigDecimal getOtherDailyLimitCf(ChannelCode otherChannel, BigDecimal globalAmount, InternalLimitModel InternalModel) {
        BigDecimal otherTotalAmt = otherChannel != null ? otherChannel.getTotalDailyLimit() : InternalModel.getOthersDailyTransaction();

        return otherTotalAmt.compareTo(globalAmount) < 0 ? otherTotalAmt : globalAmount;
    }

    private BigDecimal getUssdDailyLimitCf(ChannelCode ussdChannel, BigDecimal globalAmount, InternalLimitModel InternalModel) {
        BigDecimal ussdTotalAmt = ussdChannel != null ? ussdChannel.getTotalDailyLimit() : InternalModel.getUssdDailyTransaction();

        return ussdTotalAmt.compareTo(globalAmount) < 0 ? ussdTotalAmt : globalAmount;
    }

    private BigDecimal getThirdDailyLimitCf(ChannelCode thirdChannel, BigDecimal globalAmount, InternalLimitModel InternalModel) {
        BigDecimal thirdTotalAmt = thirdChannel != null ? thirdChannel.getTotalDailyLimit() : InternalModel.getThirdDailyTransaction();

        return thirdTotalAmt.compareTo(globalAmount) < 0 ? thirdTotalAmt : globalAmount;
    }

    private BigDecimal getVendorDailyLimitCf(ChannelCode vendorChannel, BigDecimal globalAmount, InternalLimitModel InternalModel) {
        BigDecimal vendorTotalAmt = vendorChannel != null ? vendorChannel.getTotalDailyLimit() : InternalModel.getVendorDailyTransaction();

        return vendorTotalAmt.compareTo(globalAmount) < 0 ? vendorTotalAmt : globalAmount;
    }

    private BigDecimal getAtmDailyLimitCf(ChannelCode atmChannel, BigDecimal globalAmount, InternalLimitModel InternalModel) {
        BigDecimal atmTotalAmt = atmChannel != null ? atmChannel.getTotalDailyLimit() : InternalModel.getAtmDailyTransaction();

        return atmTotalAmt.compareTo(globalAmount) < 0 ? atmTotalAmt : globalAmount;
    }

    private BigDecimal getPosDailyLimitCf(ChannelCode posChannel, BigDecimal globalAmount, InternalLimitModel InternalModel) {
        BigDecimal posTotalAmt = posChannel != null ? posChannel.getTotalDailyLimit() : InternalModel.getPosDailyTransaction();

        return posTotalAmt.compareTo(globalAmount) < 0 ? posTotalAmt : globalAmount;
    }

    private BigDecimal totalArrayAmount(ArrayList<TransferDestinationDetailsDto> transferDestinationDetails, int requestId) {

        BigDecimal amount = BigDecimal.ZERO;

        if (transferDestinationDetails != null) {
            for (TransferDestinationDetailsDto destinationDetailsDto : transferDestinationDetails) {
                amount = amount.add(destinationDetailsDto.getAmount());
            }
        }
        return amount.multiply(BigDecimal.valueOf(requestId));
    }

    private BigDecimal totalAmount(ArrayList<TransferDestinationDetailsDto> transferDestinationDetails) {

        BigDecimal amount = BigDecimal.ZERO;

        if (transferDestinationDetails != null) {
            for (TransferDestinationDetailsDto destinationDetailsDto : transferDestinationDetails) {
                amount = amount.add(destinationDetailsDto.getAmount());
            }
        }
        return amount;
    }

    @Override
    public Map<String, Object> enquiryLimit(EnquiryRequestDto enquiryRequestDto) throws MyCustomException {
        log.info("enquiry limit");
        log.info("{}", DailyLimitUsageServiceImpl.class);

        String status = "00";

        String account = helperUtils.accountNumber(enquiryRequestDto.getAccountNumber(), enquiryRequestDto.getCifId());

        String presentDate = customerLimitRepository.createDate();
        BigDecimal amount = BigDecimal.ZERO;

        AccountDetailsDto getCIfId = helperUtils.getWithCifOrAccount(account);

        if (getCIfId.getCifId() == null || getCIfId.getCifId().equalsIgnoreCase("null")) {
            status = "99";
        }

        SummaryDetailModel findByAccountNumber = summaryDetailRepository.findByCifId(getCIfId.getCifId(), presentDate, enquiryRequestDto.getTransferType(), enquiryRequestDto.getProductType());

        if (findByAccountNumber != null) {
            amount = findByAccountNumber.getRequestAmount();
        }
        GlobalLimit findGlobalLimitByAcctNum = globalLimitRepository.findGlobalLimitByCifId(getCIfId.getCifId(), enquiryRequestDto.getTransferType(), enquiryRequestDto.getProductType());

        InternalLimitModel InternalLimitModel = InternalLimitRepository.findByKycLevel(getCIfId.getKycLevel(), getCIfId.getCifType(), enquiryRequestDto.getTransferType(), enquiryRequestDto.getProductType());

        if (InternalLimitModel == null) {
            throw new MyCustomException("Internal default limit not set");
        }

        BigDecimal globalTotalAmt = findGlobalLimitByAcctNum != null ? findGlobalLimitByAcctNum.getTotalDailyLimit() : InternalLimitModel.getGlobalDailyTransaction();
        BigDecimal globalPerAmt = findGlobalLimitByAcctNum != null ? findGlobalLimitByAcctNum.getPerTransactionLimit() : InternalLimitModel.getGlobalPerTransaction();

        GlobalDetailsDto globalDetailsDto = getGlobalDetails(getCIfId, presentDate, globalTotalAmt, globalPerAmt, enquiryRequestDto);

        NipDetailsDto nipDetailsDto = getNipDetails(getCIfId, presentDate, globalTotalAmt, globalPerAmt, InternalLimitModel, enquiryRequestDto, findByAccountNumber);

        UssdDetailsDto ussdDetailsDto = getUssdDetails(getCIfId, presentDate, globalTotalAmt, globalPerAmt, InternalLimitModel, enquiryRequestDto, findByAccountNumber);

        TellerDetailsDto tellerDetailsDto = getTellerDetails(getCIfId, presentDate, globalTotalAmt, globalPerAmt, InternalLimitModel, enquiryRequestDto, findByAccountNumber);

        InternetDetailsDto internetDetailsDto = getInternetDetails(getCIfId, presentDate, globalTotalAmt, globalPerAmt, InternalLimitModel, enquiryRequestDto, findByAccountNumber);

        MobileDetailsDto mobileDetailsDto = getMobileDetails(getCIfId, presentDate, globalTotalAmt, globalPerAmt, InternalLimitModel, enquiryRequestDto, findByAccountNumber);

        PosDetailsDto posDetailsDto = getPosDetails(getCIfId, presentDate, globalTotalAmt, globalPerAmt, InternalLimitModel, enquiryRequestDto, findByAccountNumber);

        AtmDetailsDto atmDetailsDto = getAtmDetails(getCIfId, presentDate, globalTotalAmt, globalPerAmt, InternalLimitModel, enquiryRequestDto, findByAccountNumber);

        PortalDetailsDto portalDetailsDto = getPortalDetails(getCIfId, presentDate, globalTotalAmt, globalPerAmt, InternalLimitModel, enquiryRequestDto, findByAccountNumber);

        ThirdPartyDetailsDto thirdPartyDetailsDto = getThirdPartyDetails(getCIfId, presentDate, globalTotalAmt, globalPerAmt, InternalLimitModel, enquiryRequestDto, findByAccountNumber);

        OthersDetailsDto othersDetailsDto = getOthersDetails(getCIfId, presentDate, globalTotalAmt, globalPerAmt, InternalLimitModel, enquiryRequestDto, findByAccountNumber);

        CbnDetailsDto cbnDetailsDto = getCbnDetails(getCIfId, enquiryRequestDto);

        EnquiryResponseDto enquiryResponseDto = new EnquiryResponseDto();
        enquiryResponseDto.setCifId(getCIfId.getCifId());
        enquiryResponseDto.setAccountType(getCIfId.getCifType());
        enquiryResponseDto.setAccountNumber(account);
        enquiryResponseDto.setAmount(amount);
        enquiryResponseDto.setCBNMaxLimitRetail(cbnDetailsDto.getCBNMaxLimitRetail());
        enquiryResponseDto.setCBNMaxLimitSME(cbnDetailsDto.getCBNMaxLimitSME());
        enquiryResponseDto.setCBNperTransLimitRetail(cbnDetailsDto.getCBNperTransLimitRetail());
        enquiryResponseDto.setCBNperTransLimitSME(cbnDetailsDto.getCBNperTransLimitSME());
        enquiryResponseDto.setGlobalDailyMaxLimit(globalDetailsDto.getMaxAmount());
        enquiryResponseDto.setAvailableGlobalDailyLimit(globalDetailsDto.getAmountRemaining());
        enquiryResponseDto.setGlobalLimitPerTransaction(globalDetailsDto.getGlobalLimitPerTransaction());
        enquiryResponseDto.setNipDailyMaxLimit(nipDetailsDto.getMaxAmount());
        enquiryResponseDto.setAvailableNipDailyLimit(nipDetailsDto.getAmountRemaining());
        enquiryResponseDto.setNipLimitPerTransaction(nipDetailsDto.getNipLimitPerTransaction());
        enquiryResponseDto.setUssdDailyMaxLimit(ussdDetailsDto.getMaxAmount());
        enquiryResponseDto.setAvailableUssdDailyLimit(ussdDetailsDto.getAmountRemaining());
        enquiryResponseDto.setUssdLimitPerTransaction(ussdDetailsDto.getUssdLimitPerTransaction());
        enquiryResponseDto.setTellerDailyMaxLimit(tellerDetailsDto.getMaxAmount());
        enquiryResponseDto.setAvailableTellerDailyLimit(tellerDetailsDto.getAmountRemaining());
        enquiryResponseDto.setTellerLimitPerTransaction(internetDetailsDto.getInternetLimitPerTransaction());
        enquiryResponseDto.setInternetDailyMaxLimit(internetDetailsDto.getMaxAmount());
        enquiryResponseDto.setAvailableInternetDailyLimit(internetDetailsDto.getAmountRemaining());
        enquiryResponseDto.setInternetLimitPerTransaction(internetDetailsDto.getInternetLimitPerTransaction());
        enquiryResponseDto.setMobileDailyMaxLimit(mobileDetailsDto.getMaxAmount());
        enquiryResponseDto.setAvailableMobileDailyLimit(mobileDetailsDto.getAmountRemaining());
        enquiryResponseDto.setMobileLimitPerTransaction(mobileDetailsDto.getMobileLimitPerTransaction());
        enquiryResponseDto.setPosDailyMaxLimit(posDetailsDto.getMaxAmount());
        enquiryResponseDto.setAvailablePosDailyLimit(posDetailsDto.getAmountRemaining());
        enquiryResponseDto.setPosLimitPerTransaction(posDetailsDto.getPosLimitPerTransaction());
        enquiryResponseDto.setAtmDailyMaxLimit(atmDetailsDto.getMaxAmount());
        enquiryResponseDto.setAvailableAtmDailyLimit(atmDetailsDto.getAmountRemaining());
        enquiryResponseDto.setAtmLimitPerTransaction(atmDetailsDto.getAtmLimitPerTransaction());
        enquiryResponseDto.setPortalDailyMaxLimit(portalDetailsDto.getMaxAmount());
        enquiryResponseDto.setAvailablePortalDailyLimit(portalDetailsDto.getAmountRemaining());
        enquiryResponseDto.setPortalLimitPerTransaction(portalDetailsDto.getPortalLimitPerTransaction());
        enquiryResponseDto.setThirdPartyDailyMaxLimit(thirdPartyDetailsDto.getMaxAmount());
        enquiryResponseDto.setAvailableThirdPartyDailyLimit(thirdPartyDetailsDto.getAmountRemaining());
        enquiryResponseDto.setThirdLimitPerTransaction(thirdPartyDetailsDto.getThirdLimitPerTransaction());
        enquiryResponseDto.setOthersDailyMaxLimit(othersDetailsDto.getMaxAmount());
        enquiryResponseDto.setAvailableOthersDailyLimit(othersDetailsDto.getAmountRemaining());
        enquiryResponseDto.setOthersLimitPerTransaction(othersDetailsDto.getOthersLimitPerTransaction());

        return CustomResponse.response("success", status, enquiryResponseDto);
    }

    private CbnDetailsDto getCbnDetails(AccountDetailsDto getCIfId, EnquiryRequestDto enquiryRequestDto) {

        CbnDetailsDto cbnDetailsDto = new CbnDetailsDto();

        CBNMaxLimitModel findByKycId = cbnMaxLimitRepository.findByKycId(getCIfId.getKycLevel(), getCIfId.getCifType(), enquiryRequestDto.getTransferType(), enquiryRequestDto.getProductType());

        if (findByKycId != null) {
            if (getCIfId.getCifType().equalsIgnoreCase("RET")) {
                cbnDetailsDto.setCBNperTransLimitRetail(findByKycId.getGlobalPerTransaction());
                cbnDetailsDto.setCBNMaxLimitRetail(findByKycId.getGlobalDailyLimit());
            } else {
                cbnDetailsDto.setCBNperTransLimitSME(findByKycId.getGlobalPerTransaction());
                cbnDetailsDto.setCBNMaxLimitSME(findByKycId.getGlobalDailyLimit());
            }
        }
        return cbnDetailsDto;
    }

    private OthersDetailsDto getOthersDetails(AccountDetailsDto getCIfId, String presentDate, BigDecimal globalTotalAmt, BigDecimal globalPerAmt, InternalLimitModel InternalLimitModel, EnquiryRequestDto enquiryRequestDto, SummaryDetailModel findByAccountNumber) {

        OthersDetailsDto othersDetailsDto = new OthersDetailsDto();

        ChannelCode channelCode = channelLimitRepository.findUssdLimitById("9", getCIfId.getCifId(), enquiryRequestDto.getTransferType(), enquiryRequestDto.getProductType());

        BigDecimal channelTotalAmt = channelCode != null ? channelCode.getTotalDailyLimit() : InternalLimitModel.getOthersDailyTransaction();
        BigDecimal channelPerAmt = channelCode != null ? channelCode.getPerTransactionLimit() : InternalLimitModel.getOthersPerTransaction();

        othersDetailsDto.setMaxAmount(channelTotalAmt.compareTo(globalTotalAmt) < 0 ? channelTotalAmt : globalTotalAmt);
        othersDetailsDto.setOthersLimitPerTransaction(channelPerAmt.compareTo(globalPerAmt) < 0 ? channelPerAmt : globalPerAmt);

        //for getting used amount
        if (findByAccountNumber != null) {
            othersDetailsDto.setAmountRemaining(findByAccountNumber.getOthersDailyLimitCf());
        } else {
            othersDetailsDto.setAmountRemaining(othersDetailsDto.getMaxAmount());
        }

        return othersDetailsDto;
    }

    private ThirdPartyDetailsDto getThirdPartyDetails(AccountDetailsDto getCifId, String presentDate, BigDecimal globalTotalAmt, BigDecimal globalPerAmt, InternalLimitModel InternalLimitModel, EnquiryRequestDto enquiryRequestDto, SummaryDetailModel findByAccountNumber) {

        ThirdPartyDetailsDto thirdPartyDetailsDto = new ThirdPartyDetailsDto();

        ChannelCode channelCode = channelLimitRepository.findUssdLimitById("7", getCifId.getCifId(), enquiryRequestDto.getTransferType(), enquiryRequestDto.getProductType());

        BigDecimal channelTotalAmt = channelCode != null ? channelCode.getTotalDailyLimit() : InternalLimitModel.getThirdDailyTransaction();
        BigDecimal channelPerAmt = channelCode != null ? channelCode.getPerTransactionLimit() : InternalLimitModel.getThirdPerTransaction();

        thirdPartyDetailsDto.setMaxAmount(channelTotalAmt.compareTo(globalTotalAmt) < 0 ? channelTotalAmt : globalTotalAmt);
        thirdPartyDetailsDto.setThirdLimitPerTransaction(channelPerAmt.compareTo(globalPerAmt) < 0 ? channelPerAmt : globalPerAmt);

        //for getting used amount
        if (findByAccountNumber != null) {
            thirdPartyDetailsDto.setAmountRemaining(findByAccountNumber.getThirdPartyDailyLimitCf());
        } else {
            thirdPartyDetailsDto.setAmountRemaining(thirdPartyDetailsDto.getMaxAmount());
        }

        return thirdPartyDetailsDto;
    }

    private PortalDetailsDto getPortalDetails(AccountDetailsDto getCifId, String presentDate, BigDecimal globalTotalAmt, BigDecimal globalPerAmt, InternalLimitModel InternalLimitModel, EnquiryRequestDto enquiryRequestDto, SummaryDetailModel findByAccountNumber) {

        PortalDetailsDto portalDetailsDto = new PortalDetailsDto();

        ChannelCode channelCode = channelLimitRepository.findUssdLimitById("6", getCifId.getCifId(), enquiryRequestDto.getTransferType(), enquiryRequestDto.getProductType());

        BigDecimal channelTotalAmt = channelCode != null ? channelCode.getTotalDailyLimit() : InternalLimitModel.getVendorDailyTransaction();
        BigDecimal channelPerAmt = channelCode != null ? channelCode.getPerTransactionLimit() : InternalLimitModel.getVendorPerTransaction();

        portalDetailsDto.setMaxAmount(channelTotalAmt.compareTo(globalTotalAmt) < 0 ? channelTotalAmt : globalTotalAmt);
        portalDetailsDto.setPortalLimitPerTransaction(channelPerAmt.compareTo(globalPerAmt) < 0 ? channelPerAmt : globalPerAmt);

        //for getting used amount
        if (findByAccountNumber != null) {
            portalDetailsDto.setAmountRemaining(findByAccountNumber.getPortalDailyLimitCf());
        } else {
            portalDetailsDto.setAmountRemaining(portalDetailsDto.getMaxAmount());
        }

        return portalDetailsDto;
    }

    private AtmDetailsDto getAtmDetails(AccountDetailsDto getCifId, String presentDate, BigDecimal globalTotalAmt, BigDecimal globalPerAmt, InternalLimitModel InternalLimitModel, EnquiryRequestDto enquiryRequestDto, SummaryDetailModel findByAccountNumber) {

        AtmDetailsDto atmDetailsDto = new AtmDetailsDto();

        ChannelCode channelCode = channelLimitRepository.findUssdLimitById("5", getCifId.getCifId(), enquiryRequestDto.getTransferType(), enquiryRequestDto.getProductType());

        BigDecimal channelTotalAmt = channelCode != null ? channelCode.getTotalDailyLimit() : InternalLimitModel.getAtmDailyTransaction();
        BigDecimal channelPerAmt = channelCode != null ? channelCode.getPerTransactionLimit() : InternalLimitModel.getAtmPerTransaction();

        atmDetailsDto.setMaxAmount(channelTotalAmt.compareTo(globalTotalAmt) < 0 ? channelTotalAmt : globalTotalAmt);
        atmDetailsDto.setAtmLimitPerTransaction(channelPerAmt.compareTo(globalPerAmt) < 0 ? channelPerAmt : globalPerAmt);

        //for getting used amount
        if (findByAccountNumber != null) {
            atmDetailsDto.setAmountRemaining(findByAccountNumber.getAtmDailyLimitCf());
        } else {
            atmDetailsDto.setAmountRemaining(atmDetailsDto.getMaxAmount());
        }
        return atmDetailsDto;
    }

    private PosDetailsDto getPosDetails(AccountDetailsDto getCifId, String presentDate, BigDecimal globalTotalAmt, BigDecimal globalPerAmt, InternalLimitModel InternalLimitModel, EnquiryRequestDto enquiryRequestDto, SummaryDetailModel findByAccountNumber) {

        PosDetailsDto posDetailsDto = new PosDetailsDto();

        ChannelCode channelCode = channelLimitRepository.findUssdLimitById("4", getCifId.getCifId(), enquiryRequestDto.getTransferType(), enquiryRequestDto.getProductType());

        BigDecimal channelTotalAmt = channelCode != null ? channelCode.getTotalDailyLimit() : InternalLimitModel.getPosDailyTransaction();
        BigDecimal channelPerAmt = channelCode != null ? channelCode.getPerTransactionLimit() : InternalLimitModel.getPosPerTransaction();

        posDetailsDto.setMaxAmount(channelTotalAmt.compareTo(globalTotalAmt) < 0 ? channelTotalAmt : globalTotalAmt);
        posDetailsDto.setPosLimitPerTransaction(channelPerAmt.compareTo(globalPerAmt) < 0 ? channelPerAmt : globalPerAmt);

        //for getting used amount
        if (findByAccountNumber != null) {
            posDetailsDto.setAmountRemaining(findByAccountNumber.getPosDailyLimitCf());
        } else {
            posDetailsDto.setAmountRemaining(posDetailsDto.getMaxAmount());
        }
        return posDetailsDto;
    }

    private MobileDetailsDto getMobileDetails(AccountDetailsDto getCidId, String presentDate, BigDecimal globalTotalAmt, BigDecimal globalPerAmt, InternalLimitModel InternalLimitModel, EnquiryRequestDto enquiryRequestDto, SummaryDetailModel findByAccountNumber) {

        MobileDetailsDto mobileDetailsDto = new MobileDetailsDto();

        ChannelCode findNipLimitByAcctNum = channelLimitRepository.findUssdLimitById("3", getCidId.getCifId(), enquiryRequestDto.getTransferType(), enquiryRequestDto.getProductType());

        BigDecimal channelTotalAmt = findNipLimitByAcctNum != null ? findNipLimitByAcctNum.getTotalDailyLimit() : InternalLimitModel.getMobileDailyTransaction();
        BigDecimal channelPerAmt = findNipLimitByAcctNum != null ? findNipLimitByAcctNum.getPerTransactionLimit() : InternalLimitModel.getMobilePerTransaction();

        mobileDetailsDto.setMaxAmount(channelTotalAmt.compareTo(globalTotalAmt) < 0 ? channelTotalAmt : globalTotalAmt);
        mobileDetailsDto.setMobileLimitPerTransaction(channelPerAmt.compareTo(globalPerAmt) < 0 ? channelPerAmt : globalPerAmt);

        //for getting used amount
        if (findByAccountNumber != null) {
            mobileDetailsDto.setAmountRemaining(findByAccountNumber.getMobileDailyLimitCf());
        } else {
            mobileDetailsDto.setAmountRemaining(mobileDetailsDto.getMaxAmount());
        }
        return mobileDetailsDto;
    }

    private InternetDetailsDto getInternetDetails(AccountDetailsDto getCifId, String presentDate, BigDecimal globalTotalAmt, BigDecimal globalPerAmt, InternalLimitModel InternalLimitModel, EnquiryRequestDto enquiryRequestDto, SummaryDetailModel findByAccountNumber) {

        InternetDetailsDto internetDetailsDto = new InternetDetailsDto();

        ChannelCode findNipLimitByAcctNum = channelLimitRepository.findUssdLimitById("2", getCifId.getCifId(), enquiryRequestDto.getTransferType(), enquiryRequestDto.getProductType());

        BigDecimal channelTotalAmt = findNipLimitByAcctNum != null ? findNipLimitByAcctNum.getTotalDailyLimit() : InternalLimitModel.getInternetDailyTransaction();
        BigDecimal channelPerAmt = findNipLimitByAcctNum != null ? findNipLimitByAcctNum.getPerTransactionLimit() : InternalLimitModel.getInternetPerTransaction();

        internetDetailsDto.setMaxAmount(channelTotalAmt.compareTo(globalTotalAmt) < 0 ? channelTotalAmt : globalTotalAmt);
        internetDetailsDto.setInternetLimitPerTransaction(channelPerAmt.compareTo(globalPerAmt) < 0 ? channelPerAmt : globalPerAmt);

        //for getting used amount
        if (findByAccountNumber != null) {
            internetDetailsDto.setAmountRemaining(findByAccountNumber.getInternetDailyLimitCf());
        } else {
            internetDetailsDto.setAmountRemaining(internetDetailsDto.getMaxAmount());
        }
        return internetDetailsDto;
    }

    private TellerDetailsDto getTellerDetails(AccountDetailsDto getCidId, String presentDate, BigDecimal globalTotalAmt, BigDecimal globalPerAmt, InternalLimitModel InternalLimitModel, EnquiryRequestDto enquiryRequestDto, SummaryDetailModel findByAccountNumber) {

        TellerDetailsDto tellerDetailsDto = new TellerDetailsDto();

        ChannelCode findNipLimitByAcctNum = channelLimitRepository.findUssdLimitById("1", getCidId.getCifId(), enquiryRequestDto.getTransferType(), enquiryRequestDto.getProductType());

        BigDecimal channelTotalAmt = findNipLimitByAcctNum != null ? findNipLimitByAcctNum.getTotalDailyLimit() : InternalLimitModel.getBankDailyTransaction();
        BigDecimal channelPerAmt = findNipLimitByAcctNum != null ? findNipLimitByAcctNum.getPerTransactionLimit() : InternalLimitModel.getBankPerTransaction();

        tellerDetailsDto.setMaxAmount(channelTotalAmt.compareTo(globalTotalAmt) < 0 ? channelTotalAmt : globalTotalAmt);
        tellerDetailsDto.setTellerLimitPerTransaction(channelPerAmt.compareTo(globalPerAmt) < 0 ? channelPerAmt : globalPerAmt);

        //for getting used amount
        if (findByAccountNumber != null) {
            tellerDetailsDto.setAmountRemaining(findByAccountNumber.getTellerDailyLimitCf());
        } else {
            tellerDetailsDto.setAmountRemaining(tellerDetailsDto.getMaxAmount());
        }
        return tellerDetailsDto;
    }

    private UssdDetailsDto getUssdDetails(AccountDetailsDto getCidId, String presentDate, BigDecimal globalTotalAmt, BigDecimal globalPerAmt, InternalLimitModel InternalLimitModel, EnquiryRequestDto enquiryRequestDto, SummaryDetailModel findByAccountNumber) {

        UssdDetailsDto ussdDetailsDto = new UssdDetailsDto();

        ChannelCode findNipLimitByAcctNum = channelLimitRepository.findUssdLimitById("8", getCidId.getCifId(), enquiryRequestDto.getTransferType(), enquiryRequestDto.getProductType());

        BigDecimal channelTotalAmt = findNipLimitByAcctNum != null ? findNipLimitByAcctNum.getTotalDailyLimit() : InternalLimitModel.getUssdDailyTransaction();
        BigDecimal channelPerAmt = findNipLimitByAcctNum != null ? findNipLimitByAcctNum.getPerTransactionLimit() : InternalLimitModel.getUssdPerTransaction();

        ussdDetailsDto.setMaxAmount(channelTotalAmt.compareTo(globalTotalAmt) < 0 ? channelTotalAmt : globalTotalAmt);
        ussdDetailsDto.setUssdLimitPerTransaction(channelPerAmt.compareTo(globalPerAmt) < 0 ? channelPerAmt : globalPerAmt);

        //for getting used amount
        if (findByAccountNumber != null) {
            ussdDetailsDto.setAmountRemaining(findByAccountNumber.getUssdDailyLimitCf());
        } else {
            ussdDetailsDto.setAmountRemaining(ussdDetailsDto.getMaxAmount());
        }
        return ussdDetailsDto;
    }

    private NipDetailsDto getNipDetails(AccountDetailsDto getCifId, String presentDate, BigDecimal globalTotalAmt, BigDecimal globalPerAmt, InternalLimitModel InternalLimitModel, EnquiryRequestDto enquiryRequestDto, SummaryDetailModel findByAccountNumber) {

        NipDetailsDto nipDetailsDto = new NipDetailsDto();

        NipLimit findNipLimitByAcctNum = nipLimitRepository.findNipLimitByCifId(getCifId.getCifId(), enquiryRequestDto.getTransferType(), enquiryRequestDto.getProductType());

        BigDecimal nipLimitDaily = findNipLimitByAcctNum != null ? findNipLimitByAcctNum.getTotalDailyLimit() : InternalLimitModel.getNipDailyTransaction();
        BigDecimal nipLimitPer = findNipLimitByAcctNum != null ? findNipLimitByAcctNum.getPerTransactionLimit() : InternalLimitModel.getNipPerTransaction();

        nipDetailsDto.setMaxAmount(nipLimitDaily.compareTo(globalTotalAmt) < 0 ? nipLimitDaily : globalTotalAmt);
        nipDetailsDto.setNipLimitPerTransaction(nipLimitPer.compareTo(globalPerAmt) < 0 ? nipLimitPer : globalPerAmt);

        //for getting used amount
        if (findByAccountNumber != null) {
            nipDetailsDto.setAmountRemaining(findByAccountNumber.getNipDailyLimitCf());
        } else {
            nipDetailsDto.setAmountRemaining(nipDetailsDto.getMaxAmount());
        }
        return nipDetailsDto;
    }

    private GlobalDetailsDto getGlobalDetails(AccountDetailsDto getCifId, String presentDate, BigDecimal globalTotalAmt, BigDecimal globalPerAmt, EnquiryRequestDto enquiryRequestDto) {

        GlobalDetailsDto globalDetailsDto = new GlobalDetailsDto();

        globalDetailsDto.setMaxAmount(globalTotalAmt);
        globalDetailsDto.setGlobalLimitPerTransaction(globalPerAmt);

        //for getting used amount
        SummaryDetailModel findByAccountNumber = summaryDetailRepository.findByCifId(getCifId.getCifId(), presentDate, enquiryRequestDto.getTransferType(), enquiryRequestDto.getProductType());

        if (findByAccountNumber != null) {
            globalDetailsDto.setAmountRemaining(findByAccountNumber.getGlobalDailyLimitCf());
        } else {
            globalDetailsDto.setAmountRemaining(globalDetailsDto.getMaxAmount());
        }
        return globalDetailsDto;
    }

    private void summaryDetailsMethod(CifDto getCIfId, String presentDate, BigDecimal requestAmount,
                                      String transactionRequest, BigDecimal nipAmount, String limitType,
                                      BigDecimal totalArrayAmount, int transferType, Optional<ServiceTypeModel> findByServiceTypeId,
                                      String destination, TransferMethodResponse transferMethodResponse,
                                      DailyLimitUsageRequestDto requestDto, LimitManagement limitManagement, ProductType productType, String createTime) {

        SummaryDetailModel findByCifId = summaryDetailRepository.findByCifId(getCIfId.getCifId(), presentDate, productType.getTransfer(), productType.getProduct());

        if (findByServiceTypeId.isEmpty()) {
            throw new MyCustomizedException("service type id does not exist");
        }

        SummaryDetailModel savedSummaryModel;
        DailyLimitUsageModel savedDailyModel;

        if (findByCifId != null) {
//            String confirmChecksum = helperUtils.summaryChecksum(findByCifId);

//            if (!confirmChecksum.equals(findByCifId.getChecksum())) {
//                throw new MyCustomizedException("Err-00:limit reservation failed");
//            }

            findByCifId.setRequestAmount(findByCifId.getRequestAmount().add(requestAmount));
            findByCifId.setNipAmount(findByCifId.getNipAmount().add(nipAmount));
            findByCifId.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
            findByCifId.setLastModifiedDate(customerLimitRepository.createDate());
            findByCifId.setLastModifiedDateTime(createTime);
            findByCifId.setLimitType(limitType);
            findByCifId.setTransferType(productType.getTransfer());
            findByCifId.setProductType(productType.getProduct());
            findByCifId.setTransactionRequest(transactionRequest);
            findByCifId.setCurrency(getCIfId.getCurrency());

            findByCifId.setGlobalDailyLimitBf(limitManagement.getGlobalLimitCarried().getGlobalDailyLimitBf());
            findByCifId.setGlobalDailyLimitCf(limitManagement.getGlobalLimitCarried().getGlobalDailyLimitCf());

            findByCifId.setNipDailyLimitBf(limitManagement.getNipLimitCarried().getNipDailyLimitBf());
            findByCifId.setNipDailyLimitCf(limitManagement.getNipLimitCarried().getNipDailyLimitCf());

            findByCifId.setUssdDailyLimitBf(limitManagement.getUssdLimitCarried().getUssdDailyLimitBf());
            findByCifId.setUssdDailyLimitCf(limitManagement.getUssdLimitCarried().getUssdDailyLimitCf());

            findByCifId.setThirdPartyDailyLimitBf(limitManagement.getThirdPartyLimitCarried().getThirdPartyDailyLimitBf());
            findByCifId.setThirdPartyDailyLimitCf(limitManagement.getThirdPartyLimitCarried().getThirdPartyDailyLimitCf());

            findByCifId.setTellerDailyLimitBf(limitManagement.getTellerLimitCarried().getTellerDailyLimitBf());
            findByCifId.setTellerDailyLimitCf(limitManagement.getTellerLimitCarried().getTellerDailyLimitCf());

            findByCifId.setPosDailyLimitBf(limitManagement.getPosLimitCarried().getPosDailyLimitBf());
            findByCifId.setPosDailyLimitCf(limitManagement.getPosLimitCarried().getPosDailyLimitCf());

            findByCifId.setPortalDailyLimitBf(limitManagement.getPortalLimitCarried().getPortalDailyLimitBf());
            findByCifId.setPortalDailyLimitCf(limitManagement.getPortalLimitCarried().getPortalDailyLimitCf());

            findByCifId.setOthersDailyLimitBf(limitManagement.getOthersLimitCarried().getOthersDailyLimitBf());
            findByCifId.setOthersDailyLimitCf(limitManagement.getOthersLimitCarried().getOthersDailyLimitCf());

            findByCifId.setMobileDailyLimitBf(limitManagement.getMobileLimitCarried().getMobileDailyLimitBf());
            findByCifId.setMobileDailyLimitCf(limitManagement.getMobileLimitCarried().getMobileDailyLimitCf());

            findByCifId.setInternetDailyLimitBf(limitManagement.getInternetLimitCarried().getInternetDailyLimitBf());
            findByCifId.setInternetDailyLimitCf(limitManagement.getInternetLimitCarried().getInternetDailyLimitCf());

            findByCifId.setAtmDailyLimitBf(limitManagement.getAtmLimitCarried().getAtmDailyLimitBf());
            findByCifId.setAtmDailyLimitCf(limitManagement.getAtmLimitCarried().getAtmDailyLimitCf());

            //setting checksum
//            String summaryChecksum = helperUtils.summaryChecksum(findByCifId);

//            findByCifId.setChecksum(summaryChecksum);

            ////daily
            DailyLimitUsageModel dailyLimitUsageModel = new DailyLimitUsageModel();

            dailyLimitUsageModel.setRequestAmount(totalArrayAmount);

            dailyLimitUsageModel.setCurrency(findByCifId.getCurrency());
            dailyLimitUsageModel.setCifId(getCIfId.getCifId());
            dailyLimitUsageModel.setRequestId(transferType);
            dailyLimitUsageModel.setServiceType(findByServiceTypeId.get().getServiceType());
            dailyLimitUsageModel.setServiceTypeId(findByServiceTypeId.get().getServiceTypeId());
            dailyLimitUsageModel.setDestination(destination);
            dailyLimitUsageModel.setTransferType(findByCifId.getTransferType());
            dailyLimitUsageModel.setProductType(findByCifId.getProductType());

            //=>limit carried after and before
            dailyLimitUsageModel.setGlobalDailyLimitBf(findByCifId.getGlobalDailyLimitBf());
            dailyLimitUsageModel.setGlobalDailyLimitCf(findByCifId.getGlobalDailyLimitCf());
            dailyLimitUsageModel.setNipDailyLimitBf(findByCifId.getNipDailyLimitBf());
            dailyLimitUsageModel.setNipDailyLimitCf(findByCifId.getNipDailyLimitCf());
            dailyLimitUsageModel.setUssdDailyLimitBf(findByCifId.getUssdDailyLimitBf());
            dailyLimitUsageModel.setUssdDailyLimitCf(findByCifId.getUssdDailyLimitCf());
            dailyLimitUsageModel.setThirdPartyDailyLimitBf(findByCifId.getThirdPartyDailyLimitBf());
            dailyLimitUsageModel.setThirdPartyDailyLimitCf(findByCifId.getThirdPartyDailyLimitCf());
            dailyLimitUsageModel.setTellerDailyLimitBf(findByCifId.getTellerDailyLimitBf());
            dailyLimitUsageModel.setTellerDailyLimitCf(findByCifId.getTellerDailyLimitCf());
            dailyLimitUsageModel.setPosDailyLimitBf(findByCifId.getPosDailyLimitBf());
            dailyLimitUsageModel.setPosDailyLimitCf(findByCifId.getPosDailyLimitCf());
            dailyLimitUsageModel.setPortalDailyLimitBf(findByCifId.getPortalDailyLimitBf());
            dailyLimitUsageModel.setPortalDailyLimitCf(findByCifId.getPortalDailyLimitCf());
            dailyLimitUsageModel.setOthersDailyLimitBf(findByCifId.getOthersDailyLimitBf());
            dailyLimitUsageModel.setOthersDailyLimitCf(findByCifId.getOthersDailyLimitCf());
            dailyLimitUsageModel.setMobileDailyLimitBf(findByCifId.getMobileDailyLimitBf());
            dailyLimitUsageModel.setMobileDailyLimitCf(findByCifId.getMobileDailyLimitCf());
            dailyLimitUsageModel.setInternetDailyLimitBf(findByCifId.getInternetDailyLimitBf());
            dailyLimitUsageModel.setInternetDailyLimitCf(findByCifId.getInternetDailyLimitCf());
            dailyLimitUsageModel.setAtmDailyLimitBf(findByCifId.getAtmDailyLimitBf());
            dailyLimitUsageModel.setAtmDailyLimitCf(findByCifId.getAtmDailyLimitCf());
            dailyLimitUsageModel.setLimitType(limitType);
            dailyLimitUsageModel.setDestinationRequestId(transferMethodResponse.getDestinationRequestId());
            dailyLimitUsageModel.setChannelCode(requestDto.getChannelId());
            dailyLimitUsageModel.setTransactionRequest(requestDto.getRequestDestinationId());
            dailyLimitUsageModel.setLimitRequestStatus(transferMethodResponse.getStatus());
            dailyLimitUsageModel.setTranDate(customerLimitRepository.createDate());
            dailyLimitUsageModel.setTranTime(createTime);
            dailyLimitUsageModel.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
            dailyLimitUsageModel.setLastModifiedDate(customerLimitRepository.createDate());
            dailyLimitUsageModel.setLastModifiedDateTime(createTime);

            //setting hash
//            String hash = helperUtils.dailyValuesToHash(dailyLimitUsageModel);

//            String checksum = helperUtils.DailyValuesToChecksum(dailyLimitUsageModel);

//            dailyLimitUsageModel.setHash(hash);
//            dailyLimitUsageModel.setChecksum(checksum);

            try {
                savedSummaryModel = summaryDetailRepository.save(findByCifId);
                savedDailyModel = dailyLimitUsageRepository.save(dailyLimitUsageModel);
            } catch (Exception e) {
                log.info(e.toString());
                throw new MyCustomizedException("kindly try again later...");
            }
//            log.info("savedSummaryModel: " + savedSummaryModel);
//            log.info("savedDailyModel: " + savedDailyModel);

        } else {
            ////SummaryDetailModel
            SummaryDetailModel summaryDetailModel = new SummaryDetailModel();

            summaryDetailModel.setCurrency(getCIfId.getCurrency());
            summaryDetailModel.setCifId(getCIfId.getCifId());
            summaryDetailModel.setRequestAmount(requestAmount);
            summaryDetailModel.setTransactionRequest(transactionRequest);
            summaryDetailModel.setNipAmount(nipAmount);
            summaryDetailModel.setLimitType(limitType);
            summaryDetailModel.setTransferType(productType.getTransfer());
            summaryDetailModel.setProductType(productType.getProduct());

            //=>limit carried after and before
            summaryDetailModel.setGlobalDailyLimitBf(limitManagement.getGlobalLimitCarried().getGlobalDailyLimitBf());
            summaryDetailModel.setGlobalDailyLimitCf(limitManagement.getGlobalLimitCarried().getGlobalDailyLimitCf());
            summaryDetailModel.setNipDailyLimitBf(limitManagement.getNipLimitCarried().getNipDailyLimitBf());
            summaryDetailModel.setNipDailyLimitCf(limitManagement.getNipLimitCarried().getNipDailyLimitCf());
            summaryDetailModel.setUssdDailyLimitBf(limitManagement.getUssdLimitCarried().getUssdDailyLimitBf());
            summaryDetailModel.setUssdDailyLimitCf(limitManagement.getUssdLimitCarried().getUssdDailyLimitCf());
            summaryDetailModel.setThirdPartyDailyLimitBf(limitManagement.getThirdPartyLimitCarried().getThirdPartyDailyLimitBf());
            summaryDetailModel.setThirdPartyDailyLimitCf(limitManagement.getThirdPartyLimitCarried().getThirdPartyDailyLimitCf());
            summaryDetailModel.setTellerDailyLimitBf(limitManagement.getTellerLimitCarried().getTellerDailyLimitBf());
            summaryDetailModel.setTellerDailyLimitCf(limitManagement.getTellerLimitCarried().getTellerDailyLimitCf());
            summaryDetailModel.setPosDailyLimitBf(limitManagement.getPosLimitCarried().getPosDailyLimitBf());
            summaryDetailModel.setPosDailyLimitCf(limitManagement.getPosLimitCarried().getPosDailyLimitCf());
            summaryDetailModel.setPortalDailyLimitBf(limitManagement.getPortalLimitCarried().getPortalDailyLimitBf());
            summaryDetailModel.setPortalDailyLimitCf(limitManagement.getPortalLimitCarried().getPortalDailyLimitCf());
            summaryDetailModel.setOthersDailyLimitBf(limitManagement.getOthersLimitCarried().getOthersDailyLimitBf());
            summaryDetailModel.setOthersDailyLimitCf(limitManagement.getOthersLimitCarried().getOthersDailyLimitCf());
            summaryDetailModel.setMobileDailyLimitBf(limitManagement.getMobileLimitCarried().getMobileDailyLimitBf());
            summaryDetailModel.setMobileDailyLimitCf(limitManagement.getMobileLimitCarried().getMobileDailyLimitCf());
            summaryDetailModel.setInternetDailyLimitBf(limitManagement.getInternetLimitCarried().getInternetDailyLimitBf());
            summaryDetailModel.setInternetDailyLimitCf(limitManagement.getInternetLimitCarried().getInternetDailyLimitCf());
            summaryDetailModel.setAtmDailyLimitBf(limitManagement.getAtmLimitCarried().getAtmDailyLimitBf());
            summaryDetailModel.setAtmDailyLimitCf(limitManagement.getAtmLimitCarried().getAtmDailyLimitCf());
            summaryDetailModel.setLimitType(limitType);
            summaryDetailModel.setTranDate(customerLimitRepository.createDate());
            summaryDetailModel.setTranTime(createTime);
            summaryDetailModel.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
            summaryDetailModel.setLastModifiedDate(customerLimitRepository.createDate());
            summaryDetailModel.setLastModifiedDateTime(createTime);

            //setting checksum
//            String summaryChecksum = helperUtils.summaryChecksum(summaryDetailModel);

//            summaryDetailModel.setChecksum(summaryChecksum);


            ////daily
            DailyLimitUsageModel dailyLimitUsageModel = new DailyLimitUsageModel();

            dailyLimitUsageModel.setRequestAmount(totalArrayAmount);
            dailyLimitUsageModel.setCurrency(getCIfId.getCurrency());
            dailyLimitUsageModel.setCifId(getCIfId.getCifId());
            dailyLimitUsageModel.setRequestId(transferType);
            dailyLimitUsageModel.setServiceType(findByServiceTypeId.get().getServiceType());
            dailyLimitUsageModel.setServiceTypeId(findByServiceTypeId.get().getServiceTypeId());
            dailyLimitUsageModel.setDestination(destination.trim());
            dailyLimitUsageModel.setTransferType(summaryDetailModel.getTransferType());
            dailyLimitUsageModel.setProductType(summaryDetailModel.getProductType());

            //=>limit carried after and before
            dailyLimitUsageModel.setGlobalDailyLimitBf(limitManagement.getGlobalLimitCarried().getGlobalDailyLimitBf());
            dailyLimitUsageModel.setGlobalDailyLimitCf(limitManagement.getGlobalLimitCarried().getGlobalDailyLimitCf());
            dailyLimitUsageModel.setNipDailyLimitBf(limitManagement.getNipLimitCarried().getNipDailyLimitBf());
            dailyLimitUsageModel.setNipDailyLimitCf(limitManagement.getNipLimitCarried().getNipDailyLimitCf());
            dailyLimitUsageModel.setUssdDailyLimitBf(limitManagement.getUssdLimitCarried().getUssdDailyLimitBf());
            dailyLimitUsageModel.setUssdDailyLimitCf(limitManagement.getUssdLimitCarried().getUssdDailyLimitCf());
            dailyLimitUsageModel.setThirdPartyDailyLimitBf(limitManagement.getThirdPartyLimitCarried().getThirdPartyDailyLimitBf());
            dailyLimitUsageModel.setThirdPartyDailyLimitCf(limitManagement.getThirdPartyLimitCarried().getThirdPartyDailyLimitCf());
            dailyLimitUsageModel.setTellerDailyLimitBf(limitManagement.getTellerLimitCarried().getTellerDailyLimitBf());
            dailyLimitUsageModel.setTellerDailyLimitCf(limitManagement.getTellerLimitCarried().getTellerDailyLimitCf());
            dailyLimitUsageModel.setPosDailyLimitBf(limitManagement.getPosLimitCarried().getPosDailyLimitBf());
            dailyLimitUsageModel.setPosDailyLimitCf(limitManagement.getPosLimitCarried().getPosDailyLimitCf());
            dailyLimitUsageModel.setPortalDailyLimitBf(limitManagement.getPortalLimitCarried().getPortalDailyLimitBf());
            dailyLimitUsageModel.setPortalDailyLimitCf(limitManagement.getPortalLimitCarried().getPortalDailyLimitCf());
            dailyLimitUsageModel.setOthersDailyLimitBf(limitManagement.getOthersLimitCarried().getOthersDailyLimitBf());
            dailyLimitUsageModel.setOthersDailyLimitCf(limitManagement.getOthersLimitCarried().getOthersDailyLimitCf());
            dailyLimitUsageModel.setMobileDailyLimitBf(limitManagement.getMobileLimitCarried().getMobileDailyLimitBf());
            dailyLimitUsageModel.setMobileDailyLimitCf(limitManagement.getMobileLimitCarried().getMobileDailyLimitCf());
            dailyLimitUsageModel.setInternetDailyLimitBf(limitManagement.getInternetLimitCarried().getInternetDailyLimitBf());
            dailyLimitUsageModel.setInternetDailyLimitCf(limitManagement.getInternetLimitCarried().getInternetDailyLimitCf());
            dailyLimitUsageModel.setAtmDailyLimitBf(limitManagement.getAtmLimitCarried().getAtmDailyLimitBf());
            dailyLimitUsageModel.setAtmDailyLimitCf(limitManagement.getAtmLimitCarried().getAtmDailyLimitCf());
            dailyLimitUsageModel.setLimitType(limitType);
            dailyLimitUsageModel.setDestinationRequestId(transferMethodResponse.getDestinationRequestId());
            dailyLimitUsageModel.setChannelCode(requestDto.getChannelId());
            dailyLimitUsageModel.setTransactionRequest(requestDto.getRequestDestinationId());
            dailyLimitUsageModel.setLimitRequestStatus(transferMethodResponse.getStatus());
            dailyLimitUsageModel.setTranDate(customerLimitRepository.createDate());
            dailyLimitUsageModel.setTranTime(createTime);
            dailyLimitUsageModel.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
            dailyLimitUsageModel.setLastModifiedDate(customerLimitRepository.createDate());
            dailyLimitUsageModel.setLastModifiedDateTime(createTime);

            //setting hash
//            String hash = helperUtils.dailyValuesToHash(dailyLimitUsageModel);

//            String checksum = helperUtils.DailyValuesToChecksum(dailyLimitUsageModel);

//            dailyLimitUsageModel.setHash(hash);
//            dailyLimitUsageModel.setChecksum(checksum);

            try {
                savedSummaryModel = summaryDetailRepository.save(summaryDetailModel);
                savedDailyModel = dailyLimitUsageRepository.save(dailyLimitUsageModel);
            } catch (Exception e) {
                log.info(e.toString());
                throw new MyCustomizedException("kindly try again later...");
            }
        }
//        log.info("dailyLimitUsageModel: " + savedDailyModel);
//        log.info("summaryLimitUsageModel: " + savedSummaryModel);
    }

    private void summaryDetailsMethodNonInstant(CifDto getCIfId, String presentDate, BigDecimal requestAmount,
                                                String transactionRequest, BigDecimal nipAmount, String limitType,
                                                BigDecimal totalArrayAmount, int transferType, Optional<ServiceTypeModel> findByServiceTypeId,
                                                String destination, TransferMethodResponse transferMethodResponse,
                                                DailyLimitUsageRequestDto requestDto, LimitManagement limitManagement, String createTime) {
        log.info("summaryDetailsMethodNonInstant");

        transactionRequest = (transactionRequest + "-" + createTime).trim();

        String transferTypeNonInstant = "NON-INSTANT";
        String productTypeNonInstant = "ALL";
        ProductType productType = new ProductType();
        productType.setTransfer(transferTypeNonInstant);
        productType.setProduct(productTypeNonInstant);

        SummaryDetailModel findByCifId = summaryDetailRepository.findByCifId(getCIfId.getCifId(), presentDate, productType.getTransfer(), productType.getProduct());

        if (findByServiceTypeId.isEmpty()) {
            throw new MyCustomizedException("service type id does not exist");
        }

        SummaryDetailModel savedSummaryModel;
        DailyLimitUsageModel savedDailyModel;

        if (findByCifId != null) {
//            String confirmChecksum = helperUtils.summaryChecksum(findByCifId);

//            if (!confirmChecksum.equals(findByCifId.getChecksum())) {
//                throw new MyCustomizedException("Err-00:limit reservation failed");
//            }

            findByCifId.setRequestAmount(findByCifId.getRequestAmount().add(requestAmount));
            findByCifId.setNipAmount(findByCifId.getNipAmount().add(nipAmount));
            findByCifId.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
            findByCifId.setLastModifiedDate(customerLimitRepository.createDate());
            findByCifId.setLastModifiedDateTime(createTime);
            findByCifId.setLimitType(limitType);
            findByCifId.setTransferType(productType.getTransfer());
            findByCifId.setProductType(productType.getProduct());
            findByCifId.setTransactionRequest(transactionRequest);
            findByCifId.setCurrency(getCIfId.getCurrency());

            findByCifId.setGlobalDailyLimitBf(limitManagement.getNonInstantGlobalLimitCarried().getGlobalDailyLimitBf());
            findByCifId.setGlobalDailyLimitCf(limitManagement.getNonInstantGlobalLimitCarried().getGlobalDailyLimitCf());

            findByCifId.setNipDailyLimitBf(limitManagement.getNonInstantNipLimitCarried().getNipDailyLimitBf());
            findByCifId.setNipDailyLimitCf(limitManagement.getNonInstantNipLimitCarried().getNipDailyLimitCf());

            findByCifId.setUssdDailyLimitBf(limitManagement.getNonInstantUssdLimitCarried().getUssdDailyLimitBf());
            findByCifId.setUssdDailyLimitCf(limitManagement.getNonInstantUssdLimitCarried().getUssdDailyLimitCf());

            findByCifId.setThirdPartyDailyLimitBf(limitManagement.getNonInstantThirdPartyLimitCarried().getThirdPartyDailyLimitBf());
            findByCifId.setThirdPartyDailyLimitCf(limitManagement.getNonInstantThirdPartyLimitCarried().getThirdPartyDailyLimitCf());

            findByCifId.setTellerDailyLimitBf(limitManagement.getNonInstantTellerLimitCarried().getTellerDailyLimitBf());
            findByCifId.setTellerDailyLimitCf(limitManagement.getNonInstantTellerLimitCarried().getTellerDailyLimitCf());

            findByCifId.setPosDailyLimitBf(limitManagement.getNonInstantPosLimitCarried().getPosDailyLimitBf());
            findByCifId.setPosDailyLimitCf(limitManagement.getNonInstantPosLimitCarried().getPosDailyLimitCf());

            findByCifId.setPortalDailyLimitBf(limitManagement.getNonInstantPortalLimitCarried().getPortalDailyLimitBf());
            findByCifId.setPortalDailyLimitCf(limitManagement.getNonInstantPortalLimitCarried().getPortalDailyLimitCf());

            findByCifId.setOthersDailyLimitBf(limitManagement.getNonInstantOthersLimitCarried().getOthersDailyLimitBf());
            findByCifId.setOthersDailyLimitCf(limitManagement.getNonInstantOthersLimitCarried().getOthersDailyLimitCf());

            findByCifId.setMobileDailyLimitBf(limitManagement.getNonInstantMobileLimitCarried().getMobileDailyLimitBf());
            findByCifId.setMobileDailyLimitCf(limitManagement.getNonInstantMobileLimitCarried().getMobileDailyLimitCf());

            findByCifId.setInternetDailyLimitBf(limitManagement.getNonInstantInternetLimitCarried().getInternetDailyLimitBf());
            findByCifId.setInternetDailyLimitCf(limitManagement.getInternetLimitCarried().getInternetDailyLimitCf());

            findByCifId.setAtmDailyLimitBf(limitManagement.getNonInstantAtmLimitCarried().getAtmDailyLimitBf());
            findByCifId.setAtmDailyLimitCf(limitManagement.getNonInstantAtmLimitCarried().getAtmDailyLimitCf());

            //setting checksum
//            String summaryChecksum = helperUtils.summaryChecksum(findByCifId);

//            findByCifId.setChecksum(summaryChecksum);

            ////daily
            DailyLimitUsageModel dailyLimitUsageModel = new DailyLimitUsageModel();

            dailyLimitUsageModel.setRequestAmount(totalArrayAmount);

            dailyLimitUsageModel.setCurrency(findByCifId.getCurrency());
            dailyLimitUsageModel.setCifId(getCIfId.getCifId());
            dailyLimitUsageModel.setRequestId(transferType);
            dailyLimitUsageModel.setServiceType(findByServiceTypeId.get().getServiceType());
            dailyLimitUsageModel.setServiceTypeId(findByServiceTypeId.get().getServiceTypeId());
            dailyLimitUsageModel.setDestination(destination);
            dailyLimitUsageModel.setTransferType(findByCifId.getTransferType());
            dailyLimitUsageModel.setProductType(findByCifId.getProductType());

            //=>limit carried after and before
            dailyLimitUsageModel.setGlobalDailyLimitBf(findByCifId.getGlobalDailyLimitBf());
            dailyLimitUsageModel.setGlobalDailyLimitCf(findByCifId.getGlobalDailyLimitCf());
            dailyLimitUsageModel.setNipDailyLimitBf(findByCifId.getNipDailyLimitBf());
            dailyLimitUsageModel.setNipDailyLimitCf(findByCifId.getNipDailyLimitCf());
            dailyLimitUsageModel.setUssdDailyLimitBf(findByCifId.getUssdDailyLimitBf());
            dailyLimitUsageModel.setUssdDailyLimitCf(findByCifId.getUssdDailyLimitCf());
            dailyLimitUsageModel.setThirdPartyDailyLimitBf(findByCifId.getThirdPartyDailyLimitBf());
            dailyLimitUsageModel.setThirdPartyDailyLimitCf(findByCifId.getThirdPartyDailyLimitCf());
            dailyLimitUsageModel.setTellerDailyLimitBf(findByCifId.getTellerDailyLimitBf());
            dailyLimitUsageModel.setTellerDailyLimitCf(findByCifId.getTellerDailyLimitCf());
            dailyLimitUsageModel.setPosDailyLimitBf(findByCifId.getPosDailyLimitBf());
            dailyLimitUsageModel.setPosDailyLimitCf(findByCifId.getPosDailyLimitCf());
            dailyLimitUsageModel.setPortalDailyLimitBf(findByCifId.getPortalDailyLimitBf());
            dailyLimitUsageModel.setPortalDailyLimitCf(findByCifId.getPortalDailyLimitCf());
            dailyLimitUsageModel.setOthersDailyLimitBf(findByCifId.getOthersDailyLimitBf());
            dailyLimitUsageModel.setOthersDailyLimitCf(findByCifId.getOthersDailyLimitCf());
            dailyLimitUsageModel.setMobileDailyLimitBf(findByCifId.getMobileDailyLimitBf());
            dailyLimitUsageModel.setMobileDailyLimitCf(findByCifId.getMobileDailyLimitCf());
            dailyLimitUsageModel.setInternetDailyLimitBf(findByCifId.getInternetDailyLimitBf());
            dailyLimitUsageModel.setInternetDailyLimitCf(findByCifId.getInternetDailyLimitCf());
            dailyLimitUsageModel.setAtmDailyLimitBf(findByCifId.getAtmDailyLimitBf());
            dailyLimitUsageModel.setAtmDailyLimitCf(findByCifId.getAtmDailyLimitCf());
            dailyLimitUsageModel.setLimitType(limitType);
            dailyLimitUsageModel.setDestinationRequestId(transferMethodResponse.getDestinationRequestId());
            dailyLimitUsageModel.setChannelCode(requestDto.getChannelId());
            dailyLimitUsageModel.setTransactionRequest(transactionRequest);
            dailyLimitUsageModel.setLimitRequestStatus(transferMethodResponse.getStatus());
            dailyLimitUsageModel.setTranDate(customerLimitRepository.createDate());
            dailyLimitUsageModel.setTranTime(createTime);
            dailyLimitUsageModel.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
            dailyLimitUsageModel.setLastModifiedDate(customerLimitRepository.createDate());
            dailyLimitUsageModel.setLastModifiedDateTime(createTime);

            //setting hash
//            String hash = helperUtils.dailyValuesToHash(dailyLimitUsageModel);

//            String checksum = helperUtils.DailyValuesToChecksum(dailyLimitUsageModel);

//            dailyLimitUsageModel.setHash(hash);
//            dailyLimitUsageModel.setChecksum(checksum);

            try {
                savedSummaryModel = summaryDetailRepository.save(findByCifId);
                savedDailyModel = dailyLimitUsageRepository.save(dailyLimitUsageModel);
            } catch (Exception e) {
                log.info(e.toString());
                throw new MyCustomizedException("kindly try again later...");
            }
//            log.info("savedSummaryModel: " + savedSummaryModel);
//            log.info("savedDailyModel: " + savedDailyModel);
        } else {
            ////SummaryDetailModel
            SummaryDetailModel summaryDetailModel = new SummaryDetailModel();

            summaryDetailModel.setCurrency(getCIfId.getCurrency());
            summaryDetailModel.setCifId(getCIfId.getCifId());
            summaryDetailModel.setRequestAmount(requestAmount);
            summaryDetailModel.setTransactionRequest(transactionRequest);
            summaryDetailModel.setNipAmount(nipAmount);
            summaryDetailModel.setLimitType(limitType);
            summaryDetailModel.setTransferType(productType.getTransfer());
            summaryDetailModel.setProductType(productType.getProduct());

            //=>limit carried after and before
            summaryDetailModel.setGlobalDailyLimitBf(limitManagement.getNonInstantGlobalLimitCarried().getGlobalDailyLimitBf());
            summaryDetailModel.setGlobalDailyLimitCf(limitManagement.getNonInstantGlobalLimitCarried().getGlobalDailyLimitCf());

            summaryDetailModel.setNipDailyLimitBf(limitManagement.getNonInstantNipLimitCarried().getNipDailyLimitBf());
            summaryDetailModel.setNipDailyLimitCf(limitManagement.getNonInstantNipLimitCarried().getNipDailyLimitCf());

            summaryDetailModel.setUssdDailyLimitBf(limitManagement.getNonInstantUssdLimitCarried().getUssdDailyLimitBf());
            summaryDetailModel.setUssdDailyLimitCf(limitManagement.getNonInstantUssdLimitCarried().getUssdDailyLimitCf());

            summaryDetailModel.setThirdPartyDailyLimitBf(limitManagement.getNonInstantThirdPartyLimitCarried().getThirdPartyDailyLimitBf());
            summaryDetailModel.setThirdPartyDailyLimitCf(limitManagement.getNonInstantThirdPartyLimitCarried().getThirdPartyDailyLimitCf());

            summaryDetailModel.setTellerDailyLimitBf(limitManagement.getNonInstantTellerLimitCarried().getTellerDailyLimitBf());
            summaryDetailModel.setTellerDailyLimitCf(limitManagement.getNonInstantTellerLimitCarried().getTellerDailyLimitCf());

            summaryDetailModel.setPosDailyLimitBf(limitManagement.getNonInstantPosLimitCarried().getPosDailyLimitBf());
            summaryDetailModel.setPosDailyLimitCf(limitManagement.getNonInstantPosLimitCarried().getPosDailyLimitCf());

            summaryDetailModel.setPortalDailyLimitBf(limitManagement.getNonInstantPortalLimitCarried().getPortalDailyLimitBf());
            summaryDetailModel.setPortalDailyLimitCf(limitManagement.getNonInstantPortalLimitCarried().getPortalDailyLimitCf());

            summaryDetailModel.setOthersDailyLimitBf(limitManagement.getNonInstantOthersLimitCarried().getOthersDailyLimitBf());
            summaryDetailModel.setOthersDailyLimitCf(limitManagement.getNonInstantOthersLimitCarried().getOthersDailyLimitCf());

            summaryDetailModel.setMobileDailyLimitBf(limitManagement.getNonInstantMobileLimitCarried().getMobileDailyLimitBf());
            summaryDetailModel.setMobileDailyLimitCf(limitManagement.getNonInstantMobileLimitCarried().getMobileDailyLimitCf());

            summaryDetailModel.setInternetDailyLimitBf(limitManagement.getNonInstantInternetLimitCarried().getInternetDailyLimitBf());
            summaryDetailModel.setInternetDailyLimitCf(limitManagement.getNonInstantInternetLimitCarried().getInternetDailyLimitCf());

            summaryDetailModel.setAtmDailyLimitBf(limitManagement.getNonInstantAtmLimitCarried().getAtmDailyLimitBf());
            summaryDetailModel.setAtmDailyLimitCf(limitManagement.getNonInstantAtmLimitCarried().getAtmDailyLimitCf());

            summaryDetailModel.setLimitType(limitType);
            summaryDetailModel.setTranDate(customerLimitRepository.createDate());
            summaryDetailModel.setTranTime(createTime);
            summaryDetailModel.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
            summaryDetailModel.setLastModifiedDate(customerLimitRepository.createDate());
            summaryDetailModel.setLastModifiedDateTime(createTime);

            //setting checksum
//            String summaryChecksum = helperUtils.summaryChecksum(summaryDetailModel);

//            summaryDetailModel.setChecksum(summaryChecksum);


            ////daily
            DailyLimitUsageModel dailyLimitUsageModel = new DailyLimitUsageModel();

            dailyLimitUsageModel.setRequestAmount(totalArrayAmount);
            dailyLimitUsageModel.setCurrency(getCIfId.getCurrency());
            dailyLimitUsageModel.setCifId(getCIfId.getCifId());
            dailyLimitUsageModel.setRequestId(transferType);
            dailyLimitUsageModel.setServiceType(findByServiceTypeId.get().getServiceType());
            dailyLimitUsageModel.setServiceTypeId(findByServiceTypeId.get().getServiceTypeId());
            dailyLimitUsageModel.setDestination(destination.trim());
            dailyLimitUsageModel.setTransferType(summaryDetailModel.getTransferType());
            dailyLimitUsageModel.setProductType(summaryDetailModel.getProductType());

            //=>limit carried after and before
            dailyLimitUsageModel.setGlobalDailyLimitBf(limitManagement.getNonInstantGlobalLimitCarried().getGlobalDailyLimitBf());
            dailyLimitUsageModel.setGlobalDailyLimitCf(limitManagement.getNonInstantGlobalLimitCarried().getGlobalDailyLimitCf());
            dailyLimitUsageModel.setNipDailyLimitBf(limitManagement.getNonInstantNipLimitCarried().getNipDailyLimitBf());
            dailyLimitUsageModel.setNipDailyLimitCf(limitManagement.getNonInstantNipLimitCarried().getNipDailyLimitCf());
            dailyLimitUsageModel.setUssdDailyLimitBf(limitManagement.getNonInstantUssdLimitCarried().getUssdDailyLimitBf());
            dailyLimitUsageModel.setUssdDailyLimitCf(limitManagement.getNonInstantUssdLimitCarried().getUssdDailyLimitCf());
            dailyLimitUsageModel.setThirdPartyDailyLimitBf(limitManagement.getNonInstantThirdPartyLimitCarried().getThirdPartyDailyLimitBf());
            dailyLimitUsageModel.setThirdPartyDailyLimitCf(limitManagement.getNonInstantThirdPartyLimitCarried().getThirdPartyDailyLimitCf());
            dailyLimitUsageModel.setTellerDailyLimitBf(limitManagement.getNonInstantTellerLimitCarried().getTellerDailyLimitBf());
            dailyLimitUsageModel.setTellerDailyLimitCf(limitManagement.getNonInstantTellerLimitCarried().getTellerDailyLimitCf());
            dailyLimitUsageModel.setPosDailyLimitBf(limitManagement.getNonInstantPosLimitCarried().getPosDailyLimitBf());
            dailyLimitUsageModel.setPosDailyLimitCf(limitManagement.getNonInstantPosLimitCarried().getPosDailyLimitCf());
            dailyLimitUsageModel.setPortalDailyLimitBf(limitManagement.getNonInstantPortalLimitCarried().getPortalDailyLimitBf());
            dailyLimitUsageModel.setPortalDailyLimitCf(limitManagement.getNonInstantPortalLimitCarried().getPortalDailyLimitCf());
            dailyLimitUsageModel.setOthersDailyLimitBf(limitManagement.getNonInstantOthersLimitCarried().getOthersDailyLimitBf());
            dailyLimitUsageModel.setOthersDailyLimitCf(limitManagement.getNonInstantOthersLimitCarried().getOthersDailyLimitCf());
            dailyLimitUsageModel.setMobileDailyLimitBf(limitManagement.getNonInstantMobileLimitCarried().getMobileDailyLimitBf());
            dailyLimitUsageModel.setMobileDailyLimitCf(limitManagement.getNonInstantMobileLimitCarried().getMobileDailyLimitCf());
            dailyLimitUsageModel.setInternetDailyLimitBf(limitManagement.getNonInstantInternetLimitCarried().getInternetDailyLimitBf());
            dailyLimitUsageModel.setInternetDailyLimitCf(limitManagement.getNonInstantInternetLimitCarried().getInternetDailyLimitCf());
            dailyLimitUsageModel.setAtmDailyLimitBf(limitManagement.getNonInstantAtmLimitCarried().getAtmDailyLimitBf());
            dailyLimitUsageModel.setAtmDailyLimitCf(limitManagement.getNonInstantAtmLimitCarried().getAtmDailyLimitCf());
            dailyLimitUsageModel.setLimitType(limitType);
            dailyLimitUsageModel.setDestinationRequestId(transferMethodResponse.getDestinationRequestId());
            dailyLimitUsageModel.setChannelCode(requestDto.getChannelId());
            dailyLimitUsageModel.setTransactionRequest(transactionRequest);
            dailyLimitUsageModel.setLimitRequestStatus(transferMethodResponse.getStatus());
            dailyLimitUsageModel.setTranDate(customerLimitRepository.createDate());
            dailyLimitUsageModel.setTranTime(createTime);
            dailyLimitUsageModel.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
            dailyLimitUsageModel.setLastModifiedDate(customerLimitRepository.createDate());
            dailyLimitUsageModel.setLastModifiedDateTime(createTime);

            //setting hash
//            String hash = helperUtils.dailyValuesToHash(dailyLimitUsageModel);

//            String checksum = helperUtils.DailyValuesToChecksum(dailyLimitUsageModel);

//            dailyLimitUsageModel.setHash(hash);
//            dailyLimitUsageModel.setChecksum(checksum);

            try {
                savedSummaryModel = summaryDetailRepository.save(summaryDetailModel);
                savedDailyModel = dailyLimitUsageRepository.save(dailyLimitUsageModel);
            } catch (Exception e) {
                log.info(e.toString());
                throw new MyCustomizedException("kindly try again later...");
            }
        }
//        log.info("dailyLimitUsageModel: " + savedDailyModel);
//        log.info("summaryLimitUsageModel: " + savedSummaryModel);
    }

    private String transOrReverse(int requestId) {
        String type = "";

        if (requestId == 1) {
            type = "transfer";
        } else {
            type = "reversal";
        }
        return type;
    }

    private int transferType(String url, String transactionRequest, ArrayList<TransferDestinationDetailsDto> transferDestinationDetails, String presentDate, String accountNumber, String serviceToken, String serviceIpAddress, int channelId, int bulk, ProductType productType) {
        int requestReceived = 0;

        String dailyUrl = transfer;
        String reverseUrl = reversal;

        if (url.equalsIgnoreCase(dailyUrl)) {
            requestReceived = 1;
        } else if (url.equalsIgnoreCase(reverseUrl)) {

            helperUtils.serviceAuth(serviceToken, serviceIpAddress);

            BigDecimal totalAmount = totalAmount(transferDestinationDetails);

            if (bulk == 1) {
                log.info("reversal is from bulk...");

                DailyLimitUsageModel findByTransactionRequest = dailyLimitUsageRepository.findByTransactionRequestBulk(transactionRequest, presentDate, channelId, productType.getTransfer(), productType.getProduct());

                if (findByTransactionRequest == null) {
                    throw new MyCustomizedException("transaction request does not exist");
                }

                if (findByTransactionRequest.getRequestAmount().compareTo(totalAmount) < 0) {
                    throw new MyCustomizedException("requested amount cannot be greater than available amount");
                }
            } else {

                DailyLimitUsageModel findByTransactionRequest = dailyLimitUsageRepository.findByTransactionRequest(transactionRequest, totalAmount, presentDate, channelId, productType.getTransfer(), productType.getProduct());

                if (findByTransactionRequest == null) {
                    throw new MyCustomizedException("transaction request does not exist");
                }
            }
            requestReceived = -1;
        } else {
            throw new MyCustomizedException("invalid url");
        }
        return requestReceived;
    }

    private void requiredFieldsForTransfer(DailyLimitUsageRequestDto requestDto) {

        if (Strings.isNullOrEmpty(requestDto.getAccountNumber())) {
            throw new MyCustomizedException("account number cannot be null or empty");
        }
        if (Strings.isNullOrEmpty(requestDto.getRequestDestinationId())) {
            throw new MyCustomizedException("transaction request cannot be null or empty");
        }
        if (requestDto.getChannelId() < 0) {
            throw new MyCustomizedException("channel id cannot be null or less than 0");
        }
        if (requestDto.getTransferDestinationDetails().isEmpty()) {
            throw new MyCustomizedException("transferDestinationDetails cannot be empty");
        }
    }

    //=> getting the total daily transaction
    private BigDecimal totalDailyTransactions(BigDecimal requestAmount, String cifId, String presentDate, int channelId, ProductType productType) {
        BigDecimal totalDailyUsage = requestAmount;

        BigDecimal amount = dailyLimitUsageRepository.amount(cifId, presentDate, channelId, productType.getTransfer(), productType.getProduct());

        if (amount != null) {
            totalDailyUsage = totalDailyUsage.add(amount);
        }
        return totalDailyUsage;
    }

    //=> global limit method
    private GlobalResponse globalLimit(String cifId, BigDecimal requestAmount, String presentDate, int requestId, LimitManagement limitManagement, ProductType productType) {
        log.info("running global...");

        GlobalResponse globalResponse = new GlobalResponse();

        GlobalLimit findGlobalLimitByCifId = globalLimitRepository.findGlobalLimitByCifId(cifId, productType.getTransfer(), productType.getProduct());

        if (findGlobalLimitByCifId != null) {
            globalResponse.setPerTransaction(findGlobalLimitByCifId.getPerTransactionLimit());
            globalResponse.setDailyTotalTransaction(findGlobalLimitByCifId.getTotalDailyLimit());

            DailyLimitUsageModel getRecentTrans = dailyLimitUsageRepository.getRecentTrans(cifId, presentDate, productType.getTransfer(), productType.getProduct());

            if (getRecentTrans != null) {
                limitManagement.getGlobalLimitCarried().setGlobalDailyLimitBf(getRecentTrans.getGlobalDailyLimitCf());
                limitManagement.getGlobalLimitCarried().setGlobalDailyLimitCf(getRecentTrans.getGlobalDailyLimitCf().subtract(requestAmount));
            } else {
                limitManagement.getGlobalLimitCarried().setGlobalDailyLimitCf(findGlobalLimitByCifId.getTotalDailyLimit().subtract(requestAmount));
                limitManagement.getGlobalLimitCarried().setGlobalDailyLimitBf(findGlobalLimitByCifId.getTotalDailyLimit());
            }
        } else {
            globalResponse.setPerTransaction(limitManagement.getGlobalPerTransactionLimit());
            globalResponse.setDailyTotalTransaction(limitManagement.getGlobalDailyTransactionLimit());

            SummaryDetailModel getRecentTrans = summaryDetailRepository.findByCifId(cifId, presentDate, productType.getTransfer(), productType.getProduct());

            if (getRecentTrans != null) {
                limitManagement.getGlobalLimitCarried().setGlobalDailyLimitBf(getRecentTrans.getGlobalDailyLimitCf());
                limitManagement.getGlobalLimitCarried().setGlobalDailyLimitCf(getRecentTrans.getGlobalDailyLimitCf().subtract(requestAmount));
            } else {
                limitManagement.getGlobalLimitCarried().setGlobalDailyLimitCf(limitManagement.getGlobalDailyTransactionLimit().subtract(requestAmount));
                limitManagement.getGlobalLimitCarried().setGlobalDailyLimitBf(limitManagement.getGlobalDailyTransactionLimit());
            }
        }
        return globalResponse;
    }

    private GlobalResponse globalLimitNonInstant(String cifId, BigDecimal requestAmount, String presentDate, int requestId, LimitManagement limitManagement, GlobalResponse globalResponse) {
        log.info("running global...");

        String transferTypeNonInstant = "NON-INSTANT";
        String productTypeNonInstant = "ALL";
        ProductType productType = new ProductType();
        productType.setTransfer(transferTypeNonInstant);
        productType.setProduct(productTypeNonInstant);

        GlobalLimit findGlobalLimitByCifId = globalLimitRepository.findGlobalLimitByCifId(cifId, productType.getTransfer(), productType.getProduct());

        if (findGlobalLimitByCifId != null) {
            globalResponse.setPerTransactionNonInstant(findGlobalLimitByCifId.getPerTransactionLimit());
            globalResponse.setDailyTotalTransactionNonInstant(findGlobalLimitByCifId.getTotalDailyLimit());

            DailyLimitUsageModel getRecentTrans = dailyLimitUsageRepository.getRecentTrans(cifId, presentDate, productType.getTransfer(), productType.getProduct());

            if (getRecentTrans != null) {
                limitManagement.getNonInstantGlobalLimitCarried().setGlobalDailyLimitBf(getRecentTrans.getGlobalDailyLimitCf());
                limitManagement.getNonInstantGlobalLimitCarried().setGlobalDailyLimitCf(getRecentTrans.getGlobalDailyLimitCf().subtract(requestAmount));
            } else {
                limitManagement.getNonInstantGlobalLimitCarried().setGlobalDailyLimitCf(findGlobalLimitByCifId.getTotalDailyLimit().subtract(requestAmount));
                limitManagement.getNonInstantGlobalLimitCarried().setGlobalDailyLimitBf(findGlobalLimitByCifId.getTotalDailyLimit());
            }
        } else {
            globalResponse.setPerTransactionNonInstant(limitManagement.getNonInstantGlobalPerTransactionLimit());
            globalResponse.setDailyTotalTransactionNonInstant(limitManagement.getNonInstantGlobalDailyTransactionLimit());

            SummaryDetailModel getRecentTrans = summaryDetailRepository.findByCifId(cifId, presentDate, productType.getTransfer(), productType.getProduct());

            if (getRecentTrans != null) {
                limitManagement.getNonInstantGlobalLimitCarried().setGlobalDailyLimitBf(getRecentTrans.getGlobalDailyLimitCf());
                limitManagement.getNonInstantGlobalLimitCarried().setGlobalDailyLimitCf(getRecentTrans.getGlobalDailyLimitCf().subtract(requestAmount));
            } else {
                limitManagement.getNonInstantGlobalLimitCarried().setGlobalDailyLimitCf(limitManagement.getNonInstantGlobalDailyTransactionLimit().subtract(requestAmount));
                limitManagement.getNonInstantGlobalLimitCarried().setGlobalDailyLimitBf(limitManagement.getNonInstantGlobalDailyTransactionLimit());
            }
        }
        return globalResponse;
    }

    //=> transfer method after passing the global limit
    private TransferMethodResponse transferMethod(DailyLimitUsageRequestDto requestDto, String status
            , int channelId, int serviceTypeId, String serviceType
            , CifDto getCIfId, ArrayList<TransferDestinationDetailsDto> transferDestinationDetails
            , int destinationRequestId, BigDecimal totalArrayAmount, BigDecimal totalDailyAmount
            , int requestId, String destination, String presentDate, int typeId, LimitManagement limitManagement, String limitType, ProductType productType, String createTime) {

        AtomicReference<BigDecimal> nipAmount = new AtomicReference<>(BigDecimal.ZERO);

        String productTypeNonInstant = "ALL";

        if (destinationRequestId == 1 || destinationRequestId == 2) {
            AtomicReference<NipResponse> nipResponse = new AtomicReference<>();
            NipResponse nipResponse1 = new NipResponse();
            nipAmount.set(BigDecimal.ZERO);

            CompletableFuture<Void> nipCompletable = CompletableFuture.runAsync(() -> {
                nipResponse.set(nipResponse(getCIfId, transferDestinationDetails, presentDate, requestId, limitManagement, productType, nipResponse1));
                nipAmount.set(nipAmount(transferDestinationDetails, requestId));

                if (!productType.getProduct().equalsIgnoreCase(productTypeNonInstant) && getCIfId.getCifType().equalsIgnoreCase("CORP")) {
                    nipResponse.set(nipResponseNonInstant(getCIfId, transferDestinationDetails, presentDate, requestId, limitManagement, nipResponse1));
                }
            });
            nipCompletable.join();


            //=> completable future for nip limit validation
            CompletableFuture<Boolean> nipPerValidationLimit = CompletableFuture.supplyAsync(() ->
                    nipPerValidationLimit(nipResponse, nipAmount.get()));

            CompletableFuture<Boolean> nipDailyValidationLimit = CompletableFuture.supplyAsync(() ->
                    nipDailyValidationLimit(nipAmount.get(), getCIfId, presentDate, nipResponse, totalArrayAmount, productType));

            boolean nipPerLimit = nipPerValidationLimit.join();
            boolean nipDailyLimit = nipDailyValidationLimit.join();

            if (!nipPerLimit) {
                status = "failed nip per limit";
                try {
                    failedAttempts(requestDto, serviceType, serviceTypeId, destinationRequestId, getCIfId, status, totalArrayAmount, requestId, destination, nipAmount.get(), limitManagement, limitType, productType, createTime);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                String message = String.format("request amount %s is more than it nip limit per transaction", nipAmount.get());
                log.info(message);
                throw new MyCustomizedException("exceeded nip limit per transaction");
            }

            if (!nipDailyLimit) {
                status = "failed global daily limit";
                try {
                    failedAttempts(requestDto, serviceType, serviceTypeId, destinationRequestId, getCIfId, status, totalArrayAmount, requestId, destination, nipAmount.get(), limitManagement, limitType, productType, createTime);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                String message = String.format("cif id %s has surpassed it nip daily limit transaction", getCIfId.getCifId());
                log.info(message);
                throw new MyCustomizedException("You have exceeded your daily Instant transfer limit");
            }
        }


        //=> completable future for channel id
        AtomicReference<ChannelIdResponse> channelIdResponse = new AtomicReference<>();

        CompletableFuture<Void> channelCompletable = CompletableFuture.runAsync(() -> {
            channelIdResponse.set(channelMethod(getCIfId, channelId, totalArrayAmount, presentDate, limitManagement, productType));
        });
        channelCompletable.join();

        //=> completable future for channel limit validation
        CompletableFuture<Boolean> channelPerValidationLimit = CompletableFuture.supplyAsync(() ->
                channelPerValidationLimit(channelIdResponse, totalArrayAmount));

        CompletableFuture<Boolean> channelDailyValidationLimit = CompletableFuture.supplyAsync(() ->
                channelDailyValidationLimit(channelIdResponse, totalDailyAmount));

        boolean channelPerLimit = channelPerValidationLimit.join();
        boolean channelDailyLimit = channelDailyValidationLimit.join();

        if (!channelPerLimit) {
            status = "failed ussd per limit";
            try {
                failedAttempts(requestDto, serviceType, serviceTypeId, destinationRequestId, getCIfId, status, totalArrayAmount, requestId, destination, nipAmount.get(), limitManagement, limitType, productType, createTime);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            String message = String.format("request amount %s is more than the channel limit per transaction", totalArrayAmount);
            log.info(message);
            throw new MyCustomizedException("You have exceeded your Instant transfer limit per transaction");
        }

        if (!channelDailyLimit) {
            status = "failed ussd daily limit";
            try {
                failedAttempts(requestDto, serviceType, serviceTypeId, destinationRequestId, getCIfId, status, totalArrayAmount, requestId, destination, nipAmount.get(), limitManagement, limitType, productType, createTime);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            String message = String.format("cif id %s has surpassed it channel daily limit transaction", getCIfId.getCifId());
            log.info(message);
            throw new MyCustomizedException("You have exceeded your daily transaction limit for this channel");
        }

        TransferMethodResponse transferMethodResponse = new TransferMethodResponse();
        transferMethodResponse.setStatus(status);
        transferMethodResponse.setDestinationRequestId(destinationRequestId);
        transferMethodResponse.setNipAmount(nipAmount.get());

        return transferMethodResponse;
    }

    private boolean channelDailyValidationLimit(AtomicReference<ChannelIdResponse> channelIdResponse, BigDecimal totalDailyAmount) {

        return totalDailyAmount.compareTo(channelIdResponse.get().getDailyTotalTransaction()) <= 0;
    }

    private boolean channelPerValidationLimit(AtomicReference<ChannelIdResponse> channelIdResponse, BigDecimal totalArrayAmount) {

        return totalArrayAmount.compareTo(channelIdResponse.get().getPerTransaction()) <= 0;
    }

    private boolean nipDailyValidationLimit(BigDecimal nipAmount, CifDto getCIfId, String presentDate
            , AtomicReference<NipResponse> nipResponse, BigDecimal totalArrayAmount, ProductType productType) {

        boolean status = true;

        BigDecimal nipDailyAmount = totalNipTransactions(nipAmount, getCIfId, presentDate, productType);

        if (nipResponse.get().getDailyTotalTransaction().compareTo(totalArrayAmount) < 0) {
            status = false;
        }
        return status;
    }

    private boolean nipPerValidationLimit(AtomicReference<NipResponse> nipResponse, BigDecimal finalNipAmount) {

        return nipResponse.get().getPerTransaction().compareTo(finalNipAmount) >= 0;
    }

    private BigDecimal totalNipTransactions(BigDecimal nipAmount, CifDto cifId, String presentDate, ProductType productType) {
        BigDecimal totalNipUsage = nipAmount;

        SummaryDetailModel amount = summaryDetailRepository.findByCifId(cifId.getCifId(), presentDate, productType.getTransfer(), productType.getProduct());

        if (amount != null) {
            totalNipUsage = totalNipUsage.add(amount.getNipAmount());
        }
        return totalNipUsage;
    }

    //=> channel id method
    private ChannelIdResponse channelMethod(CifDto cifId, int channelId, BigDecimal totalArrayAmount, String presentDate, LimitManagement limitManagement, ProductType productType) {
        log.info("channel method...");

        String transferTypeNonInstant = "NON-INSTANT";
        String productTypeNonInstant = "ALL";

        ChannelIdResponse channelIdResponse = new ChannelIdResponse();

        String channel = String.valueOf(channelId);

        Map<String, BigDecimal> forUssd = new HashMap<>();

        ChannelCode findChannelCodeById = channelLimitRepository.findUssdLimitById(channel, cifId.getCifId(), productType.getTransfer(), productType.getProduct());
        ChannelCode findChannelCodeByIdNonInstant = channelLimitRepository.findUssdLimitById(channel, cifId.getCifId(), transferTypeNonInstant, productTypeNonInstant);

        if (findChannelCodeById != null) {
            channelIdResponse.setPerTransaction(findChannelCodeById.getPerTransactionLimit());
            channelIdResponse.setDailyTotalTransaction(findChannelCodeById.getTotalDailyLimit());

            channelIdIsConfigured(channelId, cifId, totalArrayAmount, presentDate, limitManagement, productType, forUssd);
        } else {
            ChannelIdResponse switchChannelResponse = eachChannel(channelId, channelIdResponse, totalArrayAmount, cifId, presentDate, limitManagement, productType, forUssd);
            channelIdResponse.setPerTransaction(switchChannelResponse.getPerTransaction());
            channelIdResponse.setDailyTotalTransaction(switchChannelResponse.getDailyTotalTransaction());
        }

        if (!productType.getProduct().equalsIgnoreCase(productTypeNonInstant) && cifId.getCifType().equalsIgnoreCase("CORP")) {
            if (findChannelCodeByIdNonInstant != null) {
                channelIdResponse.setPerTransactionNonInstant(findChannelCodeByIdNonInstant.getPerTransactionLimit());
                channelIdResponse.setDailyTotalTransactionNonInstant(findChannelCodeByIdNonInstant.getTotalDailyLimit());

                channelIdIsConfiguredNonInstant(channelId, cifId, totalArrayAmount, presentDate, limitManagement, forUssd);
            } else {
                ChannelIdResponse switchChannelResponse = eachChannelNonInstant(channelId, channelIdResponse, totalArrayAmount, cifId, presentDate, limitManagement, forUssd);
                channelIdResponse.setPerTransactionNonInstant(switchChannelResponse.getPerTransaction());
                channelIdResponse.setDailyTotalTransactionNonInstant(switchChannelResponse.getDailyTotalTransaction());
            }
        }

        return channelIdResponse;
    }

    private void channelIdIsConfigured(int channelId, CifDto cifId, BigDecimal totalArrayAmount, String presentDate, LimitManagement limitManagement, ProductType productType, Map<String, BigDecimal> forUssd) {
        log.info("channelIdIsConfigured...");

        SummaryDetailModel getRecentTransByChannel = summaryDetailRepository.findByCifId(cifId.getCifId(), presentDate, productType.getTransfer(), productType.getProduct());

        switch (channelId) {
            case 1 -> {
                if (getRecentTransByChannel != null) {
                    limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(getRecentTransByChannel.getTellerDailyLimitCf());
                    limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(getRecentTransByChannel.getTellerDailyLimitCf().subtract(totalArrayAmount));
                } else {
                    limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(limitManagement.getBankTellerDailyTransactionLimit().subtract(totalArrayAmount));
                    limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(limitManagement.getBankTellerDailyTransactionLimit());
                }
            }
            case 2 -> {
                if (getRecentTransByChannel != null) {
                    limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(getRecentTransByChannel.getInternetDailyLimitCf());
                    limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(getRecentTransByChannel.getInternetDailyLimitCf().subtract(totalArrayAmount));
                } else {
                    limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(limitManagement.getInternetBankingDailyTransactionLimit().subtract(totalArrayAmount));
                    limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(limitManagement.getInternetBankingDailyTransactionLimit());
                }
            }
            case 3 -> {
                if (getRecentTransByChannel != null) {
                    limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(getRecentTransByChannel.getMobileDailyLimitCf());
                    limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(getRecentTransByChannel.getMobileDailyLimitCf().subtract(totalArrayAmount));
                } else {
                    limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(limitManagement.getMobileDailyTransactionLimit().subtract(totalArrayAmount));
                    limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(limitManagement.getMobileDailyTransactionLimit());
                }
            }
            case 4 -> {
                if (getRecentTransByChannel != null) {
                    limitManagement.getPosLimitCarried().setPosDailyLimitBf(getRecentTransByChannel.getPosDailyLimitCf());
                    limitManagement.getPosLimitCarried().setPosDailyLimitCf(getRecentTransByChannel.getPosDailyLimitCf().subtract(totalArrayAmount));
                } else {
                    limitManagement.getPosLimitCarried().setPosDailyLimitCf(limitManagement.getPosDailyTransactionLimit().subtract(totalArrayAmount));
                    limitManagement.getPosLimitCarried().setPosDailyLimitBf(limitManagement.getPosDailyTransactionLimit());
                }
            }
            case 5 -> {
                if (getRecentTransByChannel != null) {
                    limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(getRecentTransByChannel.getAtmDailyLimitCf());
                    limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(getRecentTransByChannel.getAtmDailyLimitCf().subtract(totalArrayAmount));
                } else {
                    limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(limitManagement.getAtmDailyTransactionLimit().subtract(totalArrayAmount));
                    limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(limitManagement.getAtmDailyTransactionLimit());
                }
            }
            case 6 -> {
                if (getRecentTransByChannel != null) {
                    limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(getRecentTransByChannel.getPortalDailyLimitCf());
                    limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(getRecentTransByChannel.getPortalDailyLimitCf().subtract(totalArrayAmount));
                } else {
                    limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(limitManagement.getVendorDailyTransactionLimit().subtract(totalArrayAmount));
                    limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(limitManagement.getVendorDailyTransactionLimit());
                }
            }
            case 7 -> {
                if (getRecentTransByChannel != null) {
                    limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(getRecentTransByChannel.getThirdPartyDailyLimitCf());
                    limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(getRecentTransByChannel.getThirdPartyDailyLimitCf().subtract(totalArrayAmount));
                } else {
                    limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(limitManagement.getThirdPartyDailyTransactionLimit().subtract(totalArrayAmount));
                    limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(limitManagement.getThirdPartyDailyTransactionLimit());
                }
            }
            case 8 -> {
                if (getRecentTransByChannel != null) {
                    limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(getRecentTransByChannel.getUssdDailyLimitCf());
                    BigDecimal amt = getRecentTransByChannel.getUssdDailyLimitCf().subtract(totalArrayAmount);
                    limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(amt);

                    forUssd.put("ussdCf", amt);
                    forUssd.put("ussdBf", getRecentTransByChannel.getUssdDailyLimitCf());
                } else {
                    BigDecimal amt = limitManagement.getUssdDailyTransactionLimit().subtract(totalArrayAmount);
                    limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(amt);
                    limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(limitManagement.getUssdDailyTransactionLimit());

                    forUssd.put("ussdCf", amt);
                    forUssd.put("ussdBf", limitManagement.getUssdDailyTransactionLimit());
                }
            }
            case 9 -> {
                if (getRecentTransByChannel != null) {
                    limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(getRecentTransByChannel.getOthersDailyLimitCf());
                    limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(getRecentTransByChannel.getOthersDailyLimitCf().subtract(totalArrayAmount));
                } else {
                    limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(limitManagement.getOthersDailyTransactionLimit().subtract(totalArrayAmount));
                    limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(limitManagement.getOthersDailyTransactionLimit());
                }
            }
            default -> {
                String message = String.format("invalid channel id inserted %s", channelId);
                throw new MyCustomizedException(message);
            }
        }
    }

    private void channelIdIsConfiguredNonInstant(int channelId, CifDto cifId, BigDecimal totalArrayAmount, String presentDate, LimitManagement limitManagement, Map<String, BigDecimal> forUssd) {
        log.info("channelIdIsConfigured...");
        String transferTypeNonInstant = "NON-INSTANT";
        String productTypeNonInstant = "ALL";
        ProductType productType = new ProductType();
        productType.setTransfer(transferTypeNonInstant);
        productType.setProduct(productTypeNonInstant);

        SummaryDetailModel getRecentTransByChannel = summaryDetailRepository.findByCifId(cifId.getCifId(), presentDate, productType.getTransfer(), productType.getProduct());

        switch (channelId) {
            case 1 -> {
                if (getRecentTransByChannel != null) {
                    limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(getRecentTransByChannel.getTellerDailyLimitCf());
                    limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(getRecentTransByChannel.getTellerDailyLimitCf().subtract(totalArrayAmount));
                } else {
                    limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(limitManagement.getNonInstantBankTellerDailyTransactionLimit().subtract(totalArrayAmount));
                    limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(limitManagement.getNonInstantBankTellerDailyTransactionLimit());
                }
            }
            case 2 -> {
                if (getRecentTransByChannel != null) {
                    limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(getRecentTransByChannel.getInternetDailyLimitCf());
                    limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(getRecentTransByChannel.getInternetDailyLimitCf().subtract(totalArrayAmount));
                } else {
                    limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(limitManagement.getNonInstantInternetBankingDailyTransactionLimit().subtract(totalArrayAmount));
                    limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(limitManagement.getNonInstantInternetBankingDailyTransactionLimit());
                }
            }
            case 3 -> {
                if (getRecentTransByChannel != null) {
                    limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(getRecentTransByChannel.getMobileDailyLimitCf());
                    limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(getRecentTransByChannel.getMobileDailyLimitCf().subtract(totalArrayAmount));
                } else {
                    limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(limitManagement.getNonInstantMobileDailyTransactionLimit().subtract(totalArrayAmount));
                    limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(limitManagement.getNonInstantMobileDailyTransactionLimit());
                }
            }
            case 4 -> {
                if (getRecentTransByChannel != null) {
                    limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(getRecentTransByChannel.getPosDailyLimitCf());
                    limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(getRecentTransByChannel.getPosDailyLimitCf().subtract(totalArrayAmount));
                } else {
                    limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(limitManagement.getNonInstantPosDailyTransactionLimit().subtract(totalArrayAmount));
                    limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(limitManagement.getNonInstantPosDailyTransactionLimit());
                }
            }
            case 5 -> {
                if (getRecentTransByChannel != null) {
                    limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(getRecentTransByChannel.getAtmDailyLimitCf());
                    limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(getRecentTransByChannel.getAtmDailyLimitCf().subtract(totalArrayAmount));
                } else {
                    limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(limitManagement.getNonInstantAtmDailyTransactionLimit().subtract(totalArrayAmount));
                    limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(limitManagement.getNonInstantAtmDailyTransactionLimit());
                }
            }
            case 6 -> {
                if (getRecentTransByChannel != null) {
                    limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(getRecentTransByChannel.getPortalDailyLimitCf());
                    limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(getRecentTransByChannel.getPortalDailyLimitCf().subtract(totalArrayAmount));
                } else {
                    limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(limitManagement.getNonInstantVendorDailyTransactionLimit().subtract(totalArrayAmount));
                    limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(limitManagement.getNonInstantVendorDailyTransactionLimit());
                }
            }
            case 7 -> {
                if (getRecentTransByChannel != null) {
                    limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(getRecentTransByChannel.getThirdPartyDailyLimitCf());
                    limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(getRecentTransByChannel.getThirdPartyDailyLimitCf().subtract(totalArrayAmount));
                } else {
                    limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(limitManagement.getNonInstantThirdPartyDailyTransactionLimit().subtract(totalArrayAmount));
                    limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(limitManagement.getNonInstantThirdPartyDailyTransactionLimit());
                }
            }
            case 8 -> {
                if (getRecentTransByChannel != null) {
                    limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(forUssd.get("ussdBf"));
                    limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(forUssd.get("ussdCf"));
                } else {
                    limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(forUssd.get("ussdCf"));
                    limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(forUssd.get("ussdBf"));
                }
            }
            case 9 -> {
                if (getRecentTransByChannel != null) {
                    limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(getRecentTransByChannel.getOthersDailyLimitCf());
                    limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(getRecentTransByChannel.getOthersDailyLimitCf().subtract(totalArrayAmount));
                } else {
                    limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(limitManagement.getNonInstantOthersDailyTransactionLimit().subtract(totalArrayAmount));
                    limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(limitManagement.getNonInstantOthersDailyTransactionLimit());
                }
            }
            default -> {
                String message = String.format("invalid channel id inserted %s", channelId);
                throw new MyCustomizedException(message);
            }
        }
    }

    private ChannelIdResponse eachChannel(int channelId, ChannelIdResponse channelIdResponse, BigDecimal totalArrayAmount, CifDto cifId, String presentDate, LimitManagement limitManagement, ProductType productType, Map<String, BigDecimal> forUssd) {
        log.info("eachChannel...");

        DailyLimitUsageModel getRecentTransByChannel = dailyLimitUsageRepository.getRecentTransByChannel(cifId.getCifId(), channelId, presentDate, productType.getTransfer(), productType.getProduct());
        InternalLimitModel findByServiceTypeId = InternalLimitRepository.findByKycLevel(cifId.getKycLevel(), cifId.getCifType(), productType.getTransfer(), productType.getProduct());

        switch (channelId) {
            case 1 -> {
                log.info("1");
                channelIdResponse.setPerTransaction(limitManagement.getBankTellerPerTransactionLimit());
                channelIdResponse.setDailyTotalTransaction(limitManagement.getBankTellerDailyTransactionLimit());
                if (getRecentTransByChannel != null) {
                    if (findByServiceTypeId.getBankDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == 0) {
                        limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(BigDecimal.ZERO);
                        limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(getRecentTransByChannel.getTellerDailyLimitCf());
                        limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(getRecentTransByChannel.getTellerDailyLimitCf().subtract(totalArrayAmount));
                    }
                } else {
                    if (limitManagement.getBankTellerDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) == 0 ||
                            limitManagement.getBankTellerDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) < 0) {
                        limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(BigDecimal.ZERO);
                        limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getTellerLimitCarried().setTellerDailyLimitCf(limitManagement.getBankTellerDailyTransactionLimit().subtract(totalArrayAmount));
                        limitManagement.getTellerLimitCarried().setTellerDailyLimitBf(limitManagement.getBankTellerDailyTransactionLimit());
                    }
                }
            }
            case 2 -> {
                log.info("2");
                channelIdResponse.setPerTransaction(limitManagement.getInternetBankingPerTransactionLimit());
                channelIdResponse.setDailyTotalTransaction(limitManagement.getInternetBankingDailyTransactionLimit());
                if (getRecentTransByChannel != null) {
                    if (findByServiceTypeId.getInternetDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == 0) {
                        limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(BigDecimal.ZERO);
                        limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(getRecentTransByChannel.getInternetDailyLimitCf());
                        limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(getRecentTransByChannel.getInternetDailyLimitCf().subtract(totalArrayAmount));
                    }
                } else {
                    if (limitManagement.getInternetBankingDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) == 0 ||
                            limitManagement.getInternetBankingDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) < 0) {
                        limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(BigDecimal.ZERO);
                        limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getInternetLimitCarried().setInternetDailyLimitCf(limitManagement.getInternetBankingDailyTransactionLimit().subtract(totalArrayAmount));
                        limitManagement.getInternetLimitCarried().setInternetDailyLimitBf(limitManagement.getInternetBankingDailyTransactionLimit());
                    }
                }
            }
            case 3 -> {
                log.info("3");
                channelIdResponse.setPerTransaction(limitManagement.getMobilePerTransactionLimit());
                channelIdResponse.setDailyTotalTransaction(limitManagement.getMobileDailyTransactionLimit());
                if (getRecentTransByChannel != null) {
                    if (findByServiceTypeId.getMobileDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == 0) {
                        limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(BigDecimal.ZERO);
                        limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(getRecentTransByChannel.getMobileDailyLimitCf());
                        limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(getRecentTransByChannel.getMobileDailyLimitCf().subtract(totalArrayAmount));
                    }
                } else {
                    if (limitManagement.getMobileDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) == 0 ||
                            limitManagement.getMobileDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) < 0) {
                        limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(BigDecimal.ZERO);
                        limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getMobileLimitCarried().setMobileDailyLimitCf(limitManagement.getMobileDailyTransactionLimit().subtract(totalArrayAmount));
                        limitManagement.getMobileLimitCarried().setMobileDailyLimitBf(limitManagement.getMobileDailyTransactionLimit());
                    }
                }
            }
            case 4 -> {
                log.info("4");
                channelIdResponse.setPerTransaction(limitManagement.getPosPerTransactionLimit());
                channelIdResponse.setDailyTotalTransaction(limitManagement.getPosDailyTransactionLimit());
                if (getRecentTransByChannel != null) {
                    if (findByServiceTypeId.getPosDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == 0) {
                        limitManagement.getPosLimitCarried().setPosDailyLimitBf(BigDecimal.ZERO);
                        limitManagement.getPosLimitCarried().setPosDailyLimitCf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getPosLimitCarried().setPosDailyLimitBf(getRecentTransByChannel.getPosDailyLimitCf());
                        limitManagement.getPosLimitCarried().setPosDailyLimitCf(getRecentTransByChannel.getPosDailyLimitCf().subtract(totalArrayAmount));
                    }
                } else {
                    if (limitManagement.getPosDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) == 0 ||
                            limitManagement.getPosDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) < 0) {
                        limitManagement.getPosLimitCarried().setPosDailyLimitCf(BigDecimal.ZERO);
                        limitManagement.getPosLimitCarried().setPosDailyLimitBf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getPosLimitCarried().setPosDailyLimitCf(limitManagement.getPosDailyTransactionLimit().subtract(totalArrayAmount));
                        limitManagement.getPosLimitCarried().setPosDailyLimitBf(limitManagement.getPosDailyTransactionLimit());
                    }
                }
            }
            case 5 -> {
                log.info("5");
                channelIdResponse.setPerTransaction(limitManagement.getAtmPerTransactionLimit());
                channelIdResponse.setDailyTotalTransaction(limitManagement.getAtmDailyTransactionLimit());
                if (getRecentTransByChannel != null) {
                    if (findByServiceTypeId.getAtmDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == 0) {
                        limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(BigDecimal.ZERO);
                        limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(getRecentTransByChannel.getAtmDailyLimitCf());
                        limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(getRecentTransByChannel.getAtmDailyLimitCf().subtract(totalArrayAmount));
                    }
                } else {
                    if (limitManagement.getAtmDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) == 0 ||
                            limitManagement.getAtmDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) < 0) {
                        limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(BigDecimal.ZERO);
                        limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getAtmLimitCarried().setAtmDailyLimitCf(limitManagement.getAtmDailyTransactionLimit().subtract(totalArrayAmount));
                        limitManagement.getAtmLimitCarried().setAtmDailyLimitBf(limitManagement.getAtmDailyTransactionLimit());
                    }
                }
            }
            case 6 -> {
                log.info("6");
                channelIdResponse.setPerTransaction(limitManagement.getVendorPerTransactionLimit());
                channelIdResponse.setDailyTotalTransaction(limitManagement.getVendorDailyTransactionLimit());
                if (getRecentTransByChannel != null) {
                    if (findByServiceTypeId.getVendorDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == 0) {
                        limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(BigDecimal.ZERO);
                        limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(getRecentTransByChannel.getPortalDailyLimitCf());
                        limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(getRecentTransByChannel.getPortalDailyLimitCf().subtract(totalArrayAmount));
                    }
                } else {
                    if (limitManagement.getVendorDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) == 0 ||
                            limitManagement.getVendorDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) < 0) {
                        limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(BigDecimal.ZERO);
                        limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getPortalLimitCarried().setPortalDailyLimitCf(limitManagement.getVendorDailyTransactionLimit().subtract(totalArrayAmount));
                        limitManagement.getPortalLimitCarried().setPortalDailyLimitBf(limitManagement.getVendorDailyTransactionLimit());
                    }
                }
            }
            case 7 -> {
                log.info("7");
                channelIdResponse.setPerTransaction(limitManagement.getThirdPartyPerTransactionLimit());
                channelIdResponse.setDailyTotalTransaction(limitManagement.getThirdPartyDailyTransactionLimit());
                if (getRecentTransByChannel != null) {
                    if (findByServiceTypeId.getThirdDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == 0) {
                        limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(BigDecimal.ZERO);
                        limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(getRecentTransByChannel.getThirdPartyDailyLimitCf());
                        limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(getRecentTransByChannel.getThirdPartyDailyLimitCf().subtract(totalArrayAmount));
                    }
                } else {
                    if (limitManagement.getThirdPartyDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) == 0 ||
                            limitManagement.getThirdPartyDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) < 0) {
                        limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(BigDecimal.ZERO);
                        limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitCf(limitManagement.getThirdPartyDailyTransactionLimit().subtract(totalArrayAmount));
                        limitManagement.getThirdPartyLimitCarried().setThirdPartyDailyLimitBf(limitManagement.getThirdPartyDailyTransactionLimit());
                    }
                }
            }
            case 8 -> {
                log.info("8");
                channelIdResponse.setPerTransaction(limitManagement.getUssdPerTransactionLimit());
                channelIdResponse.setDailyTotalTransaction(limitManagement.getUssdDailyTransactionLimit());

                forUssd.put("per", limitManagement.getUssdPerTransactionLimit());
                forUssd.put("daily", limitManagement.getUssdDailyTransactionLimit());
                if (getRecentTransByChannel != null) {
                    if (findByServiceTypeId.getUssdDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == 0) {
                        limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(BigDecimal.ZERO);
                        limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(BigDecimal.ZERO);

                        forUssd.put("ussdCf", BigDecimal.ZERO);
                        forUssd.put("ussdBf", BigDecimal.ZERO);
                    } else {
                        limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(getRecentTransByChannel.getUssdDailyLimitCf());
                        BigDecimal amt = getRecentTransByChannel.getUssdDailyLimitCf().subtract(totalArrayAmount);
                        limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(amt);

                        forUssd.put("ussdCf", amt);
                        forUssd.put("ussdBf", getRecentTransByChannel.getUssdDailyLimitCf());
                    }
                } else {
                    if (limitManagement.getUssdDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) == 0 ||
                            limitManagement.getUssdDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) < 0) {
                        limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(BigDecimal.ZERO);
                        limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(BigDecimal.ZERO);

                        forUssd.put("ussdCf", BigDecimal.ZERO);
                        forUssd.put("ussdBf", BigDecimal.ZERO);
                    } else {
                        BigDecimal amt = limitManagement.getUssdDailyTransactionLimit().subtract(totalArrayAmount);
                        limitManagement.getUssdLimitCarried().setUssdDailyLimitCf(amt);
                        limitManagement.getUssdLimitCarried().setUssdDailyLimitBf(limitManagement.getUssdDailyTransactionLimit());

                        forUssd.put("ussdCf", amt);
                        forUssd.put("ussdBf", limitManagement.getUssdDailyTransactionLimit());
                    }
                }
            }
            case 9 -> {
                log.info("9");
                channelIdResponse.setPerTransaction(limitManagement.getOthersPerTransactionLimit());
                channelIdResponse.setDailyTotalTransaction(limitManagement.getOthersDailyTransactionLimit());
                if (getRecentTransByChannel != null) {
                    if (findByServiceTypeId.getOthersDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == 0) {
                        limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(BigDecimal.ZERO);
                        limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(getRecentTransByChannel.getOthersDailyLimitCf());
                        limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(getRecentTransByChannel.getOthersDailyLimitCf().subtract(totalArrayAmount));
                    }
                } else {
                    if (limitManagement.getOthersDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) == 0 ||
                            limitManagement.getOthersDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) < 0) {
                        limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(BigDecimal.ZERO);
                        limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getOthersLimitCarried().setOthersDailyLimitCf(limitManagement.getOthersDailyTransactionLimit().subtract(totalArrayAmount));
                        limitManagement.getOthersLimitCarried().setOthersDailyLimitBf(limitManagement.getOthersDailyTransactionLimit());
                    }
                }
            }
            default -> {
                String message = String.format("invalid channel id inserted %s", channelId);
                throw new MyCustomizedException(message);
            }
        }
        return channelIdResponse;
    }

    private ChannelIdResponse eachChannelNonInstant(int channelId, ChannelIdResponse channelIdResponse, BigDecimal totalArrayAmount, CifDto cifId, String presentDate, LimitManagement limitManagement, Map<String, BigDecimal> forUssd) {
        log.info("eachChannel...");

        String transferTypeNonInstant = "NON-INSTANT";
        String productTypeNonInstant = "ALL";
        ProductType productType = new ProductType();
        productType.setTransfer(transferTypeNonInstant);
        productType.setProduct(productTypeNonInstant);

        DailyLimitUsageModel getRecentTransByChannel = dailyLimitUsageRepository.getRecentTransByChannel(cifId.getCifId(), channelId, presentDate, productType.getTransfer(), productType.getProduct());
        InternalLimitModel findByServiceTypeId = InternalLimitRepository.findByKycLevel(cifId.getKycLevel(), cifId.getCifType(), productType.getTransfer(), productType.getProduct());

        switch (channelId) {
            case 1 -> {
                log.info("1");
                channelIdResponse.setPerTransactionNonInstant(limitManagement.getNonInstantBankTellerPerTransactionLimit());
                channelIdResponse.setDailyTotalTransactionNonInstant(limitManagement.getNonInstantBankTellerDailyTransactionLimit());
                if (getRecentTransByChannel != null) {
                    if (findByServiceTypeId.getBankDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == 0) {
                        limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(BigDecimal.ZERO);
                        limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(getRecentTransByChannel.getTellerDailyLimitCf());
                        limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(getRecentTransByChannel.getTellerDailyLimitCf().subtract(totalArrayAmount));
                    }
                } else {
                    if (limitManagement.getNonInstantBankTellerDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) == 0 ||
                            limitManagement.getNonInstantBankTellerDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) < 0) {
                        limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(BigDecimal.ZERO);
                        limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitCf(limitManagement.getNonInstantBankTellerDailyTransactionLimit().subtract(totalArrayAmount));
                        limitManagement.getNonInstantTellerLimitCarried().setTellerDailyLimitBf(limitManagement.getNonInstantBankTellerDailyTransactionLimit());
                    }
                }
            }
            case 2 -> {
                log.info("2");
                channelIdResponse.setPerTransactionNonInstant(limitManagement.getNonInstantInternetBankingPerTransactionLimit());
                channelIdResponse.setDailyTotalTransactionNonInstant(limitManagement.getNonInstantInternetBankingDailyTransactionLimit());
                if (getRecentTransByChannel != null) {
                    if (findByServiceTypeId.getInternetDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == 0) {
                        limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(BigDecimal.ZERO);
                        limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(getRecentTransByChannel.getInternetDailyLimitCf());
                        limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(getRecentTransByChannel.getInternetDailyLimitCf().subtract(totalArrayAmount));
                    }
                } else {
                    if (limitManagement.getNonInstantInternetBankingDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) == 0 ||
                            limitManagement.getNonInstantInternetBankingDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) < 0) {
                        limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(BigDecimal.ZERO);
                        limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitCf(limitManagement.getNonInstantInternetBankingDailyTransactionLimit().subtract(totalArrayAmount));
                        limitManagement.getNonInstantInternetLimitCarried().setInternetDailyLimitBf(limitManagement.getNonInstantInternetBankingDailyTransactionLimit());
                    }
                }
            }
            case 3 -> {
                log.info("3");
                channelIdResponse.setPerTransactionNonInstant(limitManagement.getNonInstantMobilePerTransactionLimit());
                channelIdResponse.setDailyTotalTransactionNonInstant(limitManagement.getNonInstantMobileDailyTransactionLimit());
                if (getRecentTransByChannel != null) {
                    if (findByServiceTypeId.getMobileDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == 0) {
                        limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(BigDecimal.ZERO);
                        limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(getRecentTransByChannel.getMobileDailyLimitCf());
                        limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(getRecentTransByChannel.getMobileDailyLimitCf().subtract(totalArrayAmount));
                    }
                } else {
                    if (limitManagement.getNonInstantMobileDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) == 0 ||
                            limitManagement.getNonInstantMobileDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) < 0) {
                        limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(BigDecimal.ZERO);
                        limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitCf(limitManagement.getNonInstantMobileDailyTransactionLimit().subtract(totalArrayAmount));
                        limitManagement.getNonInstantMobileLimitCarried().setMobileDailyLimitBf(limitManagement.getNonInstantMobileDailyTransactionLimit());
                    }
                }
            }
            case 4 -> {
                log.info("4");
                channelIdResponse.setPerTransactionNonInstant(limitManagement.getNonInstantPosPerTransactionLimit());
                channelIdResponse.setDailyTotalTransactionNonInstant(limitManagement.getNonInstantPosDailyTransactionLimit());
                if (getRecentTransByChannel != null) {
                    if (findByServiceTypeId.getPosDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == 0) {
                        limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(BigDecimal.ZERO);
                        limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(getRecentTransByChannel.getPosDailyLimitCf());
                        limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(getRecentTransByChannel.getPosDailyLimitCf().subtract(totalArrayAmount));
                    }
                } else {
                    if (limitManagement.getNonInstantPosDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) == 0 ||
                            limitManagement.getNonInstantPosDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) < 0) {
                        limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(BigDecimal.ZERO);
                        limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitCf(limitManagement.getNonInstantPosDailyTransactionLimit().subtract(totalArrayAmount));
                        limitManagement.getNonInstantPosLimitCarried().setPosDailyLimitBf(limitManagement.getNonInstantPosDailyTransactionLimit());
                    }
                }
            }
            case 5 -> {
                log.info("5");
                channelIdResponse.setPerTransactionNonInstant(limitManagement.getNonInstantAtmPerTransactionLimit());
                channelIdResponse.setDailyTotalTransactionNonInstant(limitManagement.getNonInstantAtmDailyTransactionLimit());
                if (getRecentTransByChannel != null) {
                    if (findByServiceTypeId.getAtmDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == 0) {
                        limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(BigDecimal.ZERO);
                        limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(getRecentTransByChannel.getAtmDailyLimitCf());
                        limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(getRecentTransByChannel.getAtmDailyLimitCf().subtract(totalArrayAmount));
                    }
                } else {
                    if (limitManagement.getNonInstantAtmDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) == 0 ||
                            limitManagement.getNonInstantAtmDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) < 0) {
                        limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(BigDecimal.ZERO);
                        limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitCf(limitManagement.getNonInstantAtmDailyTransactionLimit().subtract(totalArrayAmount));
                        limitManagement.getNonInstantAtmLimitCarried().setAtmDailyLimitBf(limitManagement.getNonInstantAtmDailyTransactionLimit());
                    }
                }
            }
            case 6 -> {
                log.info("6");
                channelIdResponse.setPerTransactionNonInstant(limitManagement.getNonInstantVendorPerTransactionLimit());
                channelIdResponse.setDailyTotalTransactionNonInstant(limitManagement.getNonInstantVendorDailyTransactionLimit());
                if (getRecentTransByChannel != null) {
                    if (findByServiceTypeId.getVendorDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == 0) {
                        limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(BigDecimal.ZERO);
                        limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(getRecentTransByChannel.getPortalDailyLimitCf());
                        limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(getRecentTransByChannel.getPortalDailyLimitCf().subtract(totalArrayAmount));
                    }
                } else {
                    if (limitManagement.getNonInstantVendorDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) == 0 ||
                            limitManagement.getNonInstantVendorDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) < 0) {
                        limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(BigDecimal.ZERO);
                        limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitCf(limitManagement.getNonInstantVendorDailyTransactionLimit().subtract(totalArrayAmount));
                        limitManagement.getNonInstantPortalLimitCarried().setPortalDailyLimitBf(limitManagement.getNonInstantVendorDailyTransactionLimit());
                    }
                }
            }
            case 7 -> {
                log.info("7");
                channelIdResponse.setPerTransactionNonInstant(limitManagement.getNonInstantThirdPartyPerTransactionLimit());
                channelIdResponse.setDailyTotalTransactionNonInstant(limitManagement.getNonInstantThirdPartyDailyTransactionLimit());
                if (getRecentTransByChannel != null) {
                    if (findByServiceTypeId.getThirdDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == 0) {
                        limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(BigDecimal.ZERO);
                        limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(getRecentTransByChannel.getThirdPartyDailyLimitCf());
                        limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(getRecentTransByChannel.getThirdPartyDailyLimitCf().subtract(totalArrayAmount));
                    }
                } else {
                    if (limitManagement.getNonInstantThirdPartyDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) == 0 ||
                            limitManagement.getNonInstantThirdPartyDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) < 0) {
                        limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(BigDecimal.ZERO);
                        limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitCf(limitManagement.getNonInstantThirdPartyDailyTransactionLimit().subtract(totalArrayAmount));
                        limitManagement.getNonInstantThirdPartyLimitCarried().setThirdPartyDailyLimitBf(limitManagement.getNonInstantThirdPartyDailyTransactionLimit());
                    }
                }
            }
            case 8 -> {
                log.info("8");
                channelIdResponse.setPerTransactionNonInstant(forUssd.get("per"));
                channelIdResponse.setDailyTotalTransactionNonInstant(forUssd.get("daily"));
                if (getRecentTransByChannel != null) {
                    if (findByServiceTypeId.getUssdDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == 0) {
                        limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(forUssd.get("ussdBf"));
                        limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(forUssd.get("ussdCf"));
                    } else {
                        limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(forUssd.get("ussdBf"));
                        limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(forUssd.get("ussdCf"));
                    }
                } else {
                    if (limitManagement.getNonInstantUssdDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) == 0 ||
                            limitManagement.getNonInstantUssdDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) < 0) {
                        limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(forUssd.get("ussdCf"));
                        limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(forUssd.get("ussdBf"));
                    } else {
                        limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitCf(forUssd.get("ussdCf"));
                        limitManagement.getNonInstantUssdLimitCarried().setUssdDailyLimitBf(forUssd.get("ussdBf"));
                    }
                }
            }
            case 9 -> {
                log.info("9");
                channelIdResponse.setPerTransaction(limitManagement.getNonInstantOthersPerTransactionLimit());
                channelIdResponse.setDailyTotalTransaction(limitManagement.getNonInstantOthersDailyTransactionLimit());
                if (getRecentTransByChannel != null) {
                    if (findByServiceTypeId.getOthersDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == 0) {
                        limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(BigDecimal.ZERO);
                        limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(getRecentTransByChannel.getOthersDailyLimitCf());
                        limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(getRecentTransByChannel.getOthersDailyLimitCf().subtract(totalArrayAmount));
                    }
                } else {
                    if (limitManagement.getNonInstantOthersDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) == 0 ||
                            limitManagement.getNonInstantOthersDailyTransactionLimit().compareTo(BigDecimal.valueOf(-1)) < 0) {
                        limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(BigDecimal.ZERO);
                        limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(BigDecimal.ZERO);
                    } else {
                        limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitCf(limitManagement.getNonInstantOthersDailyTransactionLimit().subtract(totalArrayAmount));
                        limitManagement.getNonInstantOthersLimitCarried().setOthersDailyLimitBf(limitManagement.getNonInstantOthersDailyTransactionLimit());
                    }
                }
            }
            default -> {
                String message = String.format("invalid channel id inserted %s", channelId);
                throw new MyCustomizedException(message);
            }
        }
        return channelIdResponse;
    }

    private void failedAttempts(DailyLimitUsageRequestDto requestDto
            , String serviceType, int serviceTypeId, int destinationRequestId, CifDto getCIfId, String finalStatus
            , BigDecimal totalArrayAmount, int requestId, String destination, BigDecimal finalNipAmount, LimitManagement limitManagement, String limitType, ProductType productType, String createTime) throws MyCustomException {

        totalArrayAmount = totalArrayAmount.multiply(BigDecimal.valueOf(requestId));

        FailedDailyLimitModel failedDailyLimitModel = new FailedDailyLimitModel();
        failedDailyLimitModel.setAccountNumber(requestDto.getAccountNumber());
        failedDailyLimitModel.setRequestAmount(totalArrayAmount);
        failedDailyLimitModel.setCifId(getCIfId.getCifId());
        failedDailyLimitModel.setServiceType(serviceType);
        failedDailyLimitModel.setServiceTypeId(serviceTypeId);
        failedDailyLimitModel.setRequestId(requestId);
        failedDailyLimitModel.setDestination(destination);
//        failedDailyLimitModel.setLimitCf(requestDto.getLimitBf());
        failedDailyLimitModel.setLimitType(limitType);
//        failedDailyLimitModel.setLimitBf(requestDto.getLimitBf());
        failedDailyLimitModel.setDestinationRequestId(destinationRequestId);
        failedDailyLimitModel.setChannelCode(requestDto.getChannelId());
        failedDailyLimitModel.setTransactionRequest(requestDto.getRequestDestinationId());
        failedDailyLimitModel.setLimitRequestStatus(finalStatus);
        failedDailyLimitModel.setTransferType(productType.getTransfer());
        failedDailyLimitModel.setProductType(productType.getProduct());
        failedDailyLimitModel.setTranDate(customerLimitRepository.createDate());
        failedDailyLimitModel.setTranTime(createTime);
        failedDailyLimitModel.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
        failedDailyLimitModel.setLastModifiedDate(customerLimitRepository.createDate());
        failedDailyLimitModel.setLastModifiedDateTime(createTime);
        failedDailyLimitModel.setNipAmount(finalNipAmount);
        //=>limit carried after and before
        failedDailyLimitModel.setGlobalDailyLimitBf(limitManagement.getGlobalLimitCarried().getGlobalDailyLimitBf());
        failedDailyLimitModel.setGlobalDailyLimitCf(limitManagement.getGlobalLimitCarried().getGlobalDailyLimitCf());
        failedDailyLimitModel.setNipDailyLimitBf(limitManagement.getNipLimitCarried().getNipDailyLimitBf());
        failedDailyLimitModel.setNipDailyLimitCf(limitManagement.getNipLimitCarried().getNipDailyLimitCf());
        failedDailyLimitModel.setUssdDailyLimitBf(limitManagement.getUssdLimitCarried().getUssdDailyLimitBf());
        failedDailyLimitModel.setUssdDailyLimitCf(limitManagement.getUssdLimitCarried().getUssdDailyLimitCf());
        failedDailyLimitModel.setThirdPartyDailyLimitBf(limitManagement.getThirdPartyLimitCarried().getThirdPartyDailyLimitBf());
        failedDailyLimitModel.setThirdPartyDailyLimitCf(limitManagement.getThirdPartyLimitCarried().getThirdPartyDailyLimitCf());
        failedDailyLimitModel.setTellerDailyLimitBf(limitManagement.getTellerLimitCarried().getTellerDailyLimitBf());
        failedDailyLimitModel.setTellerDailyLimitCf(limitManagement.getTellerLimitCarried().getTellerDailyLimitCf());
        failedDailyLimitModel.setPosDailyLimitBf(limitManagement.getPosLimitCarried().getPosDailyLimitBf());
        failedDailyLimitModel.setPosDailyLimitCf(limitManagement.getPosLimitCarried().getPosDailyLimitCf());
        failedDailyLimitModel.setPortalDailyLimitBf(limitManagement.getPortalLimitCarried().getPortalDailyLimitBf());
        failedDailyLimitModel.setPortalDailyLimitCf(limitManagement.getPortalLimitCarried().getPortalDailyLimitCf());
        failedDailyLimitModel.setOthersDailyLimitBf(limitManagement.getOthersLimitCarried().getOthersDailyLimitBf());
        failedDailyLimitModel.setOthersDailyLimitCf(limitManagement.getOthersLimitCarried().getOthersDailyLimitCf());
        failedDailyLimitModel.setMobileDailyLimitBf(limitManagement.getMobileLimitCarried().getMobileDailyLimitBf());
        failedDailyLimitModel.setMobileDailyLimitCf(limitManagement.getMobileLimitCarried().getMobileDailyLimitCf());
        failedDailyLimitModel.setInternetDailyLimitBf(limitManagement.getInternetLimitCarried().getInternetDailyLimitBf());
        failedDailyLimitModel.setInternetDailyLimitCf(limitManagement.getInternetLimitCarried().getInternetDailyLimitCf());
        failedDailyLimitModel.setAtmDailyLimitBf(limitManagement.getAtmLimitCarried().getAtmDailyLimitBf());
        failedDailyLimitModel.setAtmDailyLimitCf(limitManagement.getAtmLimitCarried().getAtmDailyLimitCf());

        //=>hash && checksum of values
        String hash = helperUtils.failedValuesToHash(failedDailyLimitModel);
        String checksum = helperUtils.failedValuesToChecksum(failedDailyLimitModel);

        failedDailyLimitModel.setHash(hash);
        failedDailyLimitModel.setChecksum(checksum);

        try {
            failedDailyLimitRepository.save(failedDailyLimitModel);
        } catch (Exception e) {
            log.info(e.toString());
            throw new MyCustomizedException("kindly try again later...");
        }
    }


    private NipResponse nipResponse(CifDto cifId, ArrayList<TransferDestinationDetailsDto> transferDestinationDetails, String presentDate, int requestId, LimitManagement limitManagement, ProductType productType, NipResponse nipResponse) {
        log.info("nipResponse...");

        //=> getting nip amount only
        BigDecimal nipAmount = nipAmount(transferDestinationDetails, requestId);

        NipLimit findNipLimitByCifId = nipLimitRepository.findNipLimitByCifId(cifId.getCifId(), productType.getTransfer(), productType.getProduct());

        if (findNipLimitByCifId != null) {
            nipResponse.setPerTransaction(findNipLimitByCifId.getPerTransactionLimit());
            nipResponse.setDailyTotalTransaction(findNipLimitByCifId.getTotalDailyLimit());

            SummaryDetailModel getRecentTrans = summaryDetailRepository.findByCifId(cifId.getCifId(), presentDate, productType.getTransfer(), productType.getProduct());

            if (getRecentTrans != null) {
                limitManagement.getNipLimitCarried().setNipDailyLimitBf(getRecentTrans.getNipDailyLimitCf());
                limitManagement.getNipLimitCarried().setNipDailyLimitCf(getRecentTrans.getNipDailyLimitCf().subtract(nipAmount));
            } else {
                limitManagement.getNipLimitCarried().setNipDailyLimitCf(findNipLimitByCifId.getTotalDailyLimit().subtract(nipAmount));
                limitManagement.getNipLimitCarried().setNipDailyLimitBf(findNipLimitByCifId.getTotalDailyLimit());
            }

        } else {
            nipResponse.setPerTransaction(limitManagement.getNipPerTransactionLimit());
            nipResponse.setDailyTotalTransaction(limitManagement.getNipDailyTransactionLimit());

            SummaryDetailModel getRecentTrans = summaryDetailRepository.findByCifId(cifId.getCifId(), presentDate, productType.getTransfer(), productType.getProduct());
            InternalLimitModel findByServiceTypeId = InternalLimitRepository.findByKycLevel(cifId.getKycLevel(), cifId.getCifType(), productType.getTransfer(), productType.getProduct());

            if (getRecentTrans != null) {
                limitManagement.getNipLimitCarried().setNipDailyLimitBf(getRecentTrans.getNipDailyLimitCf());
                limitManagement.getNipLimitCarried().setNipDailyLimitCf(getRecentTrans.getNipDailyLimitCf().subtract(nipAmount));
            } else {
                limitManagement.getNipLimitCarried().setNipDailyLimitCf(limitManagement.getNipDailyTransactionLimit().subtract(nipAmount));
                limitManagement.getNipLimitCarried().setNipDailyLimitBf(limitManagement.getNipDailyTransactionLimit());
            }
        }
        return nipResponse;
    }

    private NipResponse nipResponseNonInstant(CifDto cifId, ArrayList<TransferDestinationDetailsDto> transferDestinationDetails, String presentDate, int requestId, LimitManagement limitManagement, NipResponse nipResponse) {
        log.info("nipResponse...");
        String transferTypeNonInstant = "NON-INSTANT";
        String productTypeNonInstant = "ALL";
        ProductType productType = new ProductType();
        productType.setTransfer(transferTypeNonInstant);
        productType.setProduct(productTypeNonInstant);

        //=> getting nip amount only
        BigDecimal nipAmount = nipAmount(transferDestinationDetails, requestId);

        NipLimit findNipLimitByCifId = nipLimitRepository.findNipLimitByCifId(cifId.getCifId(), productType.getTransfer(), productType.getProduct());

        if (findNipLimitByCifId != null) {
            nipResponse.setPerTransactionNonInstant(findNipLimitByCifId.getPerTransactionLimit());
            nipResponse.setDailyTotalTransactionNonInstant(findNipLimitByCifId.getTotalDailyLimit());

            SummaryDetailModel getRecentTrans = summaryDetailRepository.findByCifId(cifId.getCifId(), presentDate, productType.getTransfer(), productType.getProduct());

            if (getRecentTrans != null) {
                limitManagement.getNonInstantNipLimitCarried().setNipDailyLimitBf(getRecentTrans.getNipDailyLimitCf());
                limitManagement.getNonInstantNipLimitCarried().setNipDailyLimitCf(getRecentTrans.getNipDailyLimitCf().subtract(nipAmount));
            } else {
                limitManagement.getNonInstantNipLimitCarried().setNipDailyLimitCf(findNipLimitByCifId.getTotalDailyLimit().subtract(nipAmount));
                limitManagement.getNonInstantNipLimitCarried().setNipDailyLimitBf(findNipLimitByCifId.getTotalDailyLimit());
            }

        } else {
            nipResponse.setPerTransactionNonInstant(limitManagement.getNonInstantNipPerTransactionLimit());
            nipResponse.setDailyTotalTransactionNonInstant(limitManagement.getNonInstantNipDailyTransactionLimit());

            SummaryDetailModel getRecentTrans = summaryDetailRepository.findByCifId(cifId.getCifId(), presentDate, productType.getTransfer(), productType.getProduct());

            if (getRecentTrans != null) {
                limitManagement.getNonInstantNipLimitCarried().setNipDailyLimitBf(getRecentTrans.getNipDailyLimitCf());
                limitManagement.getNonInstantNipLimitCarried().setNipDailyLimitCf(getRecentTrans.getNipDailyLimitCf().subtract(nipAmount));
            } else {
                limitManagement.getNonInstantNipLimitCarried().setNipDailyLimitCf(limitManagement.getNonInstantNipDailyTransactionLimit().subtract(nipAmount));
                limitManagement.getNonInstantNipLimitCarried().setNipDailyLimitBf(limitManagement.getNonInstantNipDailyTransactionLimit());
            }
        }
        return nipResponse;
    }

    private BigDecimal nipAmount(ArrayList<TransferDestinationDetailsDto> transferDestinationDetails, int requestId) {
        BigDecimal amount = BigDecimal.ZERO;

        if (transferDestinationDetails != null) {
            for (TransferDestinationDetailsDto destinationDetailsDto : transferDestinationDetails) {
                if (destinationDetailsDto.getDestination().equalsIgnoreCase("STO")) {
                    amount = destinationDetailsDto.getAmount().multiply(BigDecimal.valueOf(requestId));
                }
            }
        }
        return amount;
    }

    private String destinationRequestId(int destinationRequestId, ArrayList<TransferDestinationDetailsDto> transferDestinationDetails) {

        String Internal = "STS";
        String other = "STO";

        StringBuilder destination = new StringBuilder();
        if (destinationRequestId == 0) {
            for (TransferDestinationDetailsDto destinationDetailsDto : transferDestinationDetails) {
                if (!destinationDetailsDto.getDestination().toUpperCase().equalsIgnoreCase(Internal) || destinationDetailsDto.getDestination().toUpperCase().equalsIgnoreCase(other)) {
                    throw new MyCustomizedException("request destination id && destination mismatch, Destination must be Internal to Internal only (STS)");
                }
                destination.append(destinationDetailsDto.getDestination());
                destination.append(" ");
            }
        } else if (destinationRequestId == 1) {
            for (TransferDestinationDetailsDto destinationDetailsDto : transferDestinationDetails) {
                if (!destinationDetailsDto.getDestination().toUpperCase().equalsIgnoreCase(other) || destinationDetailsDto.getDestination().toUpperCase().equalsIgnoreCase(Internal)) {
                    throw new MyCustomizedException("request destination id && destination mismatch, Destination must be Internal to other banks only (STO)");
                }
                destination.append(destinationDetailsDto.getDestination());
                destination.append(" ");
            }
        } else if (destinationRequestId == 2) {
            if (transferDestinationDetails.size() != 2) {
                String message = String.format("request destination id && destination mismatch, expecting 2 transferDestination Details but supplied %s", transferDestinationDetails.size());
                throw new MyCustomizedException(message);
            }

            String[] sources = {Internal, other};
            String[] dest = new String[0];
            ArrayList<String> destSource = new ArrayList<String>(Arrays.asList(dest));
            boolean containsAll = true;

            for (TransferDestinationDetailsDto destinationDetailsDto : transferDestinationDetails) {
                destSource.add(destinationDetailsDto.getDestination().toUpperCase());
                dest = destSource.toArray(dest);
            }

            for (String values : sources) {
                boolean contains = false;
                for (String s : dest) {
                    if (s.equalsIgnoreCase(values)) {
                        contains = true;
                        break;
                    }
                }
                if (!contains) {
                    containsAll = false;
                    break;
                }
            }

            if (containsAll) {
                for (String s : dest) {
                    destination.append(s);
                    destination.append(" ");
                }
            } else {
                throw new MyCustomizedException("request destination id && destination mismatch, " +
                        "Destination must contain both Internal to other banks only (STO) " +
                        "& Internal to Internal (STS)");
            }
        } else {
            throw new MyCustomizedException("invalid destination request id inserted");
        }
        return destination.toString();
    }

}
