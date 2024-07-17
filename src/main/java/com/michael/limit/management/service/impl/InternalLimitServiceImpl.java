package com.michael.limit.management.service.impl;

import com.michael.limit.management.config.ExternalConfig;
import com.michael.limit.management.custom.CustomResponse;
import com.michael.limit.management.dto.authentication.ResponseMessage;
import com.michael.limit.management.dto.defaultLimitDto.*;
import com.michael.limit.management.exception.exceptionMethod.InternalServerException;
import com.michael.limit.management.exception.exceptionMethod.MyCustomException;
import com.michael.limit.management.exception.exceptionMethod.MyCustomizedException;
import com.michael.limit.management.httpCall.HttpCall;
import com.michael.limit.management.mapper.InternalConfigMapper;
import com.michael.limit.management.model.CBNMaxLimitModel;
import com.michael.limit.management.model.ProductType;
import com.michael.limit.management.model.InternalLimitModel;
import com.michael.limit.management.repository.CBNMaxLimitRepository;
import com.michael.limit.management.repository.CustomerLimitRepository;
import com.michael.limit.management.repository.ProductTypeRepository;
import com.michael.limit.management.repository.InternalLimitRepository;
import com.michael.limit.management.service.InternalLimitService;
import com.michael.limit.management.utils.HelperUtils;
import com.michael.limit.management.utils.LastModifiedBy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
@RequiredArgsConstructor
public class InternalLimitServiceImpl implements InternalLimitService {

    private final InternalLimitRepository InternalLimitRepository;

    private final CustomerLimitRepository customerLimitRepository;

    private final LastModifiedBy lastModifiedBy;

    private final HelperUtils helperUtils;

    private final CBNMaxLimitRepository cbnMaxLimitRepository;

    private final ProductTypeRepository productTypeRepository;

    private final InternalConfigMapper InternalConfigMapper;

    private final HttpCall httpCall;

    private final ExternalConfig externalConfig;

    @Override
    public Map<String, Object> defaultLimit(DefaultLimitRequestDto defaultLimitRequestDto, String serviceToken, String serviceIpAddress) throws MyCustomException {
        log.info("running default limit config");
        log.info("{}", InternalLimitServiceImpl.class);

        String retailCif = "RET";
        String corpCif = "CORP";
        String transferType1 = "NON-INSTANT";
        String transferType2 = "INSTANT";

        AtomicReference<ResponseMessage> responseMessage = new AtomicReference<>();
        CompletableFuture<Void> authentication = new CompletableFuture<>();

        if (externalConfig.isCallAuthentication()) {
            authentication = CompletableFuture.runAsync(() ->
                    responseMessage.set(authenticationValidation(serviceToken, serviceIpAddress)));
        }

        CompletableFuture<Boolean> transferTypeAuth = CompletableFuture.supplyAsync(() ->
                getTransferType(defaultLimitRequestDto.getTransferType(), transferType1, transferType2));

        CompletableFuture<Boolean> globalPerAuth = CompletableFuture.supplyAsync(() ->
                getGlobalPerTransaction(defaultLimitRequestDto.getGlobalPerTransaction()));

        CompletableFuture<Boolean> globalDailyAuth = CompletableFuture.supplyAsync(() ->
                getGlobalDailyTransaction(defaultLimitRequestDto.getGlobalDailyTransaction()));

        if (externalConfig.isCallAuthentication()) {
            authentication.join();
        }
        boolean transferTypeAuthCheck = transferTypeAuth.join();
        boolean globalPerAuthCheck = globalPerAuth.join();
        boolean globalDailyAuthCheck = globalDailyAuth.join();

        if (externalConfig.isCallAuthentication()) {
            if (!responseMessage.get().getResponseCode().equalsIgnoreCase("00")) {
                throw new MyCustomizedException("header authentication failed");
            }

            if (responseMessage.get().getServiceAccessLevel() < 7) {
                throw new MyCustomizedException("unauthorized access");
            }
        }

        if (!transferTypeAuthCheck) {
            String message = String.format("invalid transfer type inserted, can either be %s or %s", transferType1, transferType2);
            throw new MyCustomizedException(message);
        }

        if (!globalPerAuthCheck) {
            throw new MyCustomizedException("global per transaction cannot be null");
        }

        if (!globalDailyAuthCheck) {
            throw new MyCustomizedException("global per transaction cannot be null");
        }

        String date = customerLimitRepository.createDate();
        String time = customerLimitRepository.createTime();

        ProductType productType = checkProductType(defaultLimitRequestDto);

        String cifType = cifType(defaultLimitRequestDto.getCifType(), retailCif, corpCif);

        int kycLevel = kycLevel(defaultLimitRequestDto.getKycLevel(), cifType, defaultLimitRequestDto.getTransferType(), defaultLimitRequestDto.getProductType());
        BigDecimal defaultValue = BigDecimal.valueOf(50000);

        //=> validations for all channels, nip, and global
        AtmTransactionDto atmTransactionDto = atmPerTransactionMethod(defaultLimitRequestDto, defaultValue);

        GlobalTransactionDto globalTransactionDto = globalTransactionMethod(defaultLimitRequestDto, defaultValue);

        BankTransactionDto bankTransactionDto = bankTransactionMethod(defaultLimitRequestDto, defaultValue);

        InternetTransactionDto internetTransactionDto = internetTransactionMethod(defaultLimitRequestDto, defaultValue);

        MobileTransactionDto mobileTransactionDto = mobileTransactionMethod(defaultLimitRequestDto, defaultValue);

        NipTransactionDto nipTransactionDto = nipTransactionMethod(defaultLimitRequestDto, defaultValue);

        OthersTransactionDto othersTransactionDto = othersTransactionMethod(defaultLimitRequestDto, defaultValue);

        PosTransactionDto posTransactionDto = posTransactionMethod(defaultLimitRequestDto, defaultValue);

        ThirdTransactionDto thirdTransactionDto = thirdTransactionMethod(defaultLimitRequestDto, defaultValue);

        UssdTransactionDto ussdTransactionDto = ussdTransactionMethod(defaultLimitRequestDto, defaultValue);

        VendorTransactionDto vendorTransactionDto = vendorTransactionMethod(defaultLimitRequestDto, defaultValue);

        //=>for soring models
        InternalLimitModel InternalLimitModel = new InternalLimitModel();

        InternalLimitModel.setKycLevel(kycLevel);
        InternalLimitModel.setCifType(cifType);
        InternalLimitModel.setTransferType(productType.getTransfer());
        InternalLimitModel.setAtmPerTransaction(atmTransactionDto.getAtmPerTransaction());
        InternalLimitModel.setAtmDailyTransaction(atmTransactionDto.getAtmDailyTransaction());
        InternalLimitModel.setBankDailyTransaction(bankTransactionDto.getBankDailyTransaction());
        InternalLimitModel.setBankPerTransaction(bankTransactionDto.getBankPerTransaction());
        InternalLimitModel.setGlobalDailyTransaction(globalTransactionDto.getGlobalDailyTransaction());
        InternalLimitModel.setGlobalPerTransaction(globalTransactionDto.getGlobalPerTransaction());
        InternalLimitModel.setInternetDailyTransaction(internetTransactionDto.getInternetDailyTransaction());
        InternalLimitModel.setInternetPerTransaction(internetTransactionDto.getInternetPerTransaction());
        InternalLimitModel.setMobileDailyTransaction(mobileTransactionDto.getMobileDailyTransaction());
        InternalLimitModel.setMobilePerTransaction(mobileTransactionDto.getMobilePerTransaction());
        InternalLimitModel.setNipDailyTransaction(nipTransactionDto.getNipDailyTransaction());
        InternalLimitModel.setNipPerTransaction(nipTransactionDto.getNipPerTransaction());
        InternalLimitModel.setOthersDailyTransaction(othersTransactionDto.getOthersDailyTransaction());
        InternalLimitModel.setOthersPerTransaction(othersTransactionDto.getOthersPerTransaction());
        InternalLimitModel.setPosDailyTransaction(posTransactionDto.getPosDailyTransaction());
        InternalLimitModel.setPosPerTransaction(posTransactionDto.getPosPerTransaction());
        InternalLimitModel.setThirdDailyTransaction(thirdTransactionDto.getThirdDailyTransaction());
        InternalLimitModel.setThirdPerTransaction(thirdTransactionDto.getThirdPerTransaction());
        InternalLimitModel.setUssdDailyTransaction(ussdTransactionDto.getUssdDailyTransaction());
        InternalLimitModel.setUssdPerTransaction(ussdTransactionDto.getUssdPerTransaction());
        InternalLimitModel.setVendorDailyTransaction(vendorTransactionDto.getVendorDailyTransaction());
        InternalLimitModel.setVendorPerTransaction(vendorTransactionDto.getVendorPerTransaction());
        InternalLimitModel.setCreatedDate(date);
        InternalLimitModel.setCreatedTime(time);
        InternalLimitModel.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
        InternalLimitModel.setLastModifiedDate(date);
        InternalLimitModel.setLastModifiedDateTime(time);
        InternalLimitModel.setProductType(productType.getProduct());

        String hash = helperUtils.InternalHashMethod(InternalLimitModel);
        String checksum = helperUtils.InternalChecksumMethod(InternalLimitModel);

        InternalLimitModel.setHash(hash);
        InternalLimitModel.setChecksum(checksum);

        InternalLimitModel savedModel;

        log.info("InternalLimitModel: " + InternalLimitModel);

        try {
            savedModel = InternalLimitRepository.save(InternalLimitModel);
        } catch (Exception e) {
            log.info(e.toString());
            throw new MyCustomizedException("please try again later");
        }

        log.info("savedModel: " + savedModel);

        return CustomResponse.response("success", "00", InternalConfigMapper.mapCreateCurrentInternalDto(savedModel));
    }

    private ProductType checkProductType(DefaultLimitRequestDto defaultLimitRequestDto) {

        ProductType productType = productTypeRepository.findByProduct(defaultLimitRequestDto.getProductType(), defaultLimitRequestDto.getTransferType());

        if (productType == null) {
            String message = String.format("Transfer of transferType %s and product type %s has not been configured", defaultLimitRequestDto.getTransferType(), defaultLimitRequestDto.getProductType());
            throw new MyCustomException(message);
        }
        return productType;
    }

    private boolean getGlobalDailyTransaction(BigDecimal globalDailyTransaction) {

        boolean status = true;

        if (globalDailyTransaction == null) {
            status = false;
        }
        return status;
    }

    private boolean getGlobalPerTransaction(BigDecimal globalPerTransaction) {

        boolean status = true;

        if (globalPerTransaction == null) {
            status = false;
        }
        return status;
    }

    private boolean getTransferType(String transferType, String transferType1, String transferType2) {

        boolean status = true;

        if (!transferType.equalsIgnoreCase(transferType1)) {
            if (!transferType.equalsIgnoreCase(transferType2)) {
                status = false;
            }
        }
        return status;
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

    private String cifType(String cifType, String retailCif, String corpCif) {
        log.info("check cif type");

        if (!cifType.equalsIgnoreCase(retailCif)) {
            if (!cifType.equalsIgnoreCase(corpCif)) {
                String message = "invalid cif type supplied, expecting RET or CORP";
                throw new MyCustomizedException(message);
            }
        }
        return cifType;
    }

    @Override
    public Map<String, Object> updateDefaultLimit(DefaultLimitRequestDto defaultLimitRequestDto, String serviceToken, String serviceIpAddress) throws MyCustomException {
        log.info("running update default limit config");
        log.info("{}", InternalLimitServiceImpl.class);

        AtomicReference<ResponseMessage> responseMessage = new AtomicReference<>();

        CompletableFuture<Void> authentication = CompletableFuture.runAsync(() ->
                responseMessage.set(authenticationValidation(serviceToken, serviceIpAddress)));

        authentication.join();

        authentication.join();
        if (!responseMessage.get().getResponseCode().equalsIgnoreCase("00")) {
            throw new MyCustomizedException("header authentication failed");
        }

        if (responseMessage.get().getServiceAccessLevel() < 7) {
            throw new MyCustomizedException("unauthorized access");
        }

        String date = customerLimitRepository.createDate();
        String time = customerLimitRepository.createTime();

        InternalLimitModel findByServiceTypeId = InternalLimitRepository.findByKycLevel(defaultLimitRequestDto.getKycLevel(), defaultLimitRequestDto.getCifType(), defaultLimitRequestDto.getTransferType(), defaultLimitRequestDto.getProductType());

        if (findByServiceTypeId == null) {
            String message = String.format("kyc level %s and cif type %s already exist", defaultLimitRequestDto.getKycLevel(), defaultLimitRequestDto.getCifType());
            throw new MyCustomizedException(message);
        }

        String validateHash = helperUtils.InternalHashMethod(findByServiceTypeId);

        if (!validateHash.equals(findByServiceTypeId.getHash())) {
            throw new MyCustomizedException("hash validation failed");
        }

        String validateChecksum = helperUtils.InternalChecksumMethod(findByServiceTypeId);

        if (!validateChecksum.equals(findByServiceTypeId.getChecksum())) {
            throw new MyCustomizedException("checksum validation failed");
        }

        if (defaultLimitRequestDto.getVendorDailyTransaction() != null) {
            vendorTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setVendorDailyTransaction(defaultLimitRequestDto.getVendorDailyTransaction());
        }

        if (defaultLimitRequestDto.getVendorPerTransaction() != null) {
            vendorTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setVendorPerTransaction(defaultLimitRequestDto.getVendorPerTransaction());
        }

        if (defaultLimitRequestDto.getThirdPerTransaction() != null) {
            thirdTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setThirdPerTransaction(defaultLimitRequestDto.getThirdPerTransaction());
        }

        if (defaultLimitRequestDto.getThirdDailyTransaction() != null) {
            thirdTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setThirdDailyTransaction(defaultLimitRequestDto.getThirdDailyTransaction());
        }

        if (defaultLimitRequestDto.getUssdPerTransaction() != null) {
            ussdTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setUssdPerTransaction(defaultLimitRequestDto.getUssdPerTransaction());
        }

        if (defaultLimitRequestDto.getUssdDailyTransaction() != null) {
            ussdTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setUssdDailyTransaction(defaultLimitRequestDto.getUssdDailyTransaction());
        }

        if (defaultLimitRequestDto.getPosDailyTransaction() != null) {
            posTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setPosDailyTransaction(defaultLimitRequestDto.getPosDailyTransaction());
        }

        if (defaultLimitRequestDto.getPosPerTransaction() != null) {
            posTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setPosPerTransaction(defaultLimitRequestDto.getPosPerTransaction());
        }

        if (defaultLimitRequestDto.getOthersDailyTransaction() != null) {
            othersTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setOthersDailyTransaction(defaultLimitRequestDto.getOthersDailyTransaction());
        }

        if (defaultLimitRequestDto.getOthersPerTransaction() != null) {
            othersTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setOthersPerTransaction(defaultLimitRequestDto.getOthersPerTransaction());
        }

        if (defaultLimitRequestDto.getNipDailyTransaction() != null) {
            nipTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setNipDailyTransaction(defaultLimitRequestDto.getNipDailyTransaction());
        }

        if (defaultLimitRequestDto.getNipPerTransaction() != null) {
            nipTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setNipPerTransaction(defaultLimitRequestDto.getNipPerTransaction());
        }

        if (defaultLimitRequestDto.getMobileDailyTransaction() != null) {
            mobileTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setMobileDailyTransaction(defaultLimitRequestDto.getMobileDailyTransaction());
        }

        if (defaultLimitRequestDto.getMobilePerTransaction() != null) {
            mobileTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setMobilePerTransaction(defaultLimitRequestDto.getMobilePerTransaction());
        }

        if (defaultLimitRequestDto.getInternetDailyTransaction() != null) {
            internetTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setInternetDailyTransaction(defaultLimitRequestDto.getInternetDailyTransaction());
        }

        if (defaultLimitRequestDto.getInternetPerTransaction() != null) {
            internetTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setInternetPerTransaction(defaultLimitRequestDto.getInternetPerTransaction());
        }

        if (defaultLimitRequestDto.getBankDailyTransaction() != null) {
            bankTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setBankDailyTransaction(defaultLimitRequestDto.getBankDailyTransaction());
        }

        if (defaultLimitRequestDto.getBankPerTransaction() != null) {
            bankTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setBankPerTransaction(defaultLimitRequestDto.getBankPerTransaction());
        }

        if (defaultLimitRequestDto.getAtmDailyTransaction() != null) {
            atmPerTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setAtmDailyTransaction(defaultLimitRequestDto.getAtmDailyTransaction());
        }

        if (defaultLimitRequestDto.getAtmPerTransaction() != null) {
            atmPerTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setAtmPerTransaction(defaultLimitRequestDto.getAtmPerTransaction());
        }

        if (defaultLimitRequestDto.getGlobalDailyTransaction() != null) {
            globalTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setGlobalDailyTransaction(defaultLimitRequestDto.getGlobalDailyTransaction());
        }

        if (defaultLimitRequestDto.getGlobalPerTransaction() != null) {
            globalTransactionValidMethod(defaultLimitRequestDto);

            findByServiceTypeId.setGlobalPerTransaction(defaultLimitRequestDto.getGlobalPerTransaction());
        }

        findByServiceTypeId.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
        findByServiceTypeId.setLastModifiedDate(date);
        findByServiceTypeId.setLastModifiedDateTime(time);
        findByServiceTypeId.setProductType(defaultLimitRequestDto.getProductType());

        String hash = helperUtils.InternalHashMethod(findByServiceTypeId);
        String checksum = helperUtils.InternalChecksumMethod(findByServiceTypeId);

        findByServiceTypeId.setHash(hash);
        findByServiceTypeId.setChecksum(checksum);

        InternalLimitModel updatedModel;


        try {
            updatedModel = InternalLimitRepository.save(findByServiceTypeId);
        } catch (Exception e) {
            log.info(e.toString());
            throw new MyCustomizedException("please try again later");
        }

        log.info("updatedModel: " + updatedModel);

        return CustomResponse.response("Successful", "00", InternalConfigMapper.mapCreateCurrentInternalDto(updatedModel));
    }

    private VendorTransactionDto vendorTransactionMethod(DefaultLimitRequestDto defaultLimitRequestDto, BigDecimal defaultValue) {

        vendorTransactionValidMethod(defaultLimitRequestDto);

        VendorTransactionDto vendorTransactionDto = new VendorTransactionDto();

        if (defaultLimitRequestDto.getVendorDailyTransaction() == null) {
            vendorTransactionDto.setVendorDailyTransaction(defaultValue);
        } else {
            vendorTransactionDto.setVendorDailyTransaction(defaultLimitRequestDto.getVendorDailyTransaction());
        }

        if (defaultLimitRequestDto.getVendorPerTransaction() == null) {
            vendorTransactionDto.setVendorPerTransaction(defaultValue);
        } else {
            vendorTransactionDto.setVendorPerTransaction(defaultLimitRequestDto.getVendorPerTransaction());
        }
        return vendorTransactionDto;
    }

    private void vendorTransactionValidMethod(DefaultLimitRequestDto defaultLimitRequestDto) {

        if (defaultLimitRequestDto.getVendorPerTransaction() != null && defaultLimitRequestDto.getVendorPerTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
            throw new MyCustomizedException("vendor per transaction cannot be less than -1");
        }

        if (defaultLimitRequestDto.getVendorDailyTransaction() != null && defaultLimitRequestDto.getVendorDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
            throw new MyCustomizedException("vendor daily transaction cannot be less than -1");
        }
    }

    private UssdTransactionDto ussdTransactionMethod(DefaultLimitRequestDto defaultLimitRequestDto, BigDecimal defaultValue) {

        ussdTransactionValidMethod(defaultLimitRequestDto);

        UssdTransactionDto ussdTransactionDto = new UssdTransactionDto();

        if (defaultLimitRequestDto.getUssdDailyTransaction() == null) {
            ussdTransactionDto.setUssdDailyTransaction(defaultValue);
        } else {
            ussdTransactionDto.setUssdDailyTransaction(defaultLimitRequestDto.getUssdDailyTransaction());
        }

        if (defaultLimitRequestDto.getUssdPerTransaction() == null) {
            ussdTransactionDto.setUssdPerTransaction(defaultValue);
        } else {
            ussdTransactionDto.setUssdPerTransaction(defaultLimitRequestDto.getUssdPerTransaction());
        }
        return ussdTransactionDto;
    }

    private void ussdTransactionValidMethod(DefaultLimitRequestDto defaultLimitRequestDto) {

        if (defaultLimitRequestDto.getUssdPerTransaction() != null && defaultLimitRequestDto.getUssdPerTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
            throw new MyCustomizedException("ussd per transaction cannot be less than -1");
        }

        if (defaultLimitRequestDto.getUssdDailyTransaction() != null && defaultLimitRequestDto.getUssdDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
            throw new MyCustomizedException("ussd daily transaction cannot be less than -1");
        }
    }

    private ThirdTransactionDto thirdTransactionMethod(DefaultLimitRequestDto defaultLimitRequestDto, BigDecimal defaultValue) {

        thirdTransactionValidMethod(defaultLimitRequestDto);

        ThirdTransactionDto thirdTransactionDto = new ThirdTransactionDto();

        if (defaultLimitRequestDto.getThirdDailyTransaction() == null) {
            thirdTransactionDto.setThirdDailyTransaction(defaultValue);
        } else {
            thirdTransactionDto.setThirdDailyTransaction(defaultLimitRequestDto.getThirdDailyTransaction());
        }

        if (defaultLimitRequestDto.getThirdPerTransaction() == null) {
            thirdTransactionDto.setThirdPerTransaction(defaultValue);
        } else {
            thirdTransactionDto.setThirdPerTransaction(defaultLimitRequestDto.getThirdPerTransaction());
        }
        return thirdTransactionDto;
    }

    private void thirdTransactionValidMethod(DefaultLimitRequestDto defaultLimitRequestDto) {

        if (defaultLimitRequestDto.getThirdPerTransaction() != null && defaultLimitRequestDto.getThirdPerTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
            throw new MyCustomizedException("third per transaction cannot be less than -1");
        }

        if (defaultLimitRequestDto.getThirdDailyTransaction() != null && defaultLimitRequestDto.getThirdDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
            throw new MyCustomizedException("third daily transaction cannot be less than -1");
        }
    }

    private PosTransactionDto posTransactionMethod(DefaultLimitRequestDto defaultLimitRequestDto, BigDecimal defaultValue) {

        posTransactionValidMethod(defaultLimitRequestDto);

        PosTransactionDto posTransactionDto = new PosTransactionDto();

        if (defaultLimitRequestDto.getPosDailyTransaction() == null) {
            posTransactionDto.setPosDailyTransaction(defaultValue);
        } else {
            posTransactionDto.setPosDailyTransaction(defaultLimitRequestDto.getPosDailyTransaction());
        }

        if (defaultLimitRequestDto.getPosPerTransaction() == null) {
            posTransactionDto.setPosPerTransaction(defaultValue);
        } else {
            posTransactionDto.setPosPerTransaction(defaultLimitRequestDto.getPosPerTransaction());
        }
        return posTransactionDto;
    }

    private void posTransactionValidMethod(DefaultLimitRequestDto defaultLimitRequestDto) {

        if (defaultLimitRequestDto.getPosPerTransaction() != null && defaultLimitRequestDto.getPosPerTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
            throw new MyCustomizedException("pos per transaction cannot be less than -1");
        }

        if (defaultLimitRequestDto.getPosDailyTransaction() != null && defaultLimitRequestDto.getPosDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
            throw new MyCustomizedException("pos daily transaction cannot be less than -1");
        }
    }

    private OthersTransactionDto othersTransactionMethod(DefaultLimitRequestDto defaultLimitRequestDto, BigDecimal defaultValue) {

        othersTransactionValidMethod(defaultLimitRequestDto);

        OthersTransactionDto othersTransactionDto = new OthersTransactionDto();

        if (defaultLimitRequestDto.getOthersDailyTransaction() == null) {
            othersTransactionDto.setOthersDailyTransaction(defaultValue);
        } else {
            othersTransactionDto.setOthersDailyTransaction(defaultLimitRequestDto.getOthersDailyTransaction());
        }

        if (defaultLimitRequestDto.getOthersPerTransaction() == null) {
            othersTransactionDto.setOthersPerTransaction(defaultValue);
        } else {
            othersTransactionDto.setOthersPerTransaction(defaultLimitRequestDto.getOthersPerTransaction());
        }
        return othersTransactionDto;
    }

    private void othersTransactionValidMethod(DefaultLimitRequestDto defaultLimitRequestDto) {

        if (defaultLimitRequestDto.getOthersPerTransaction() != null && defaultLimitRequestDto.getOthersPerTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
            throw new MyCustomizedException("others per transaction cannot be less than -1");
        }

        if (defaultLimitRequestDto.getOthersDailyTransaction() != null && defaultLimitRequestDto.getOthersDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
            throw new MyCustomizedException("others daily transaction cannot be less than -1");
        }
    }

    private NipTransactionDto nipTransactionMethod(DefaultLimitRequestDto defaultLimitRequestDto, BigDecimal defaultValue) {

        nipTransactionValidMethod(defaultLimitRequestDto);

        NipTransactionDto nipTransactionDto = new NipTransactionDto();

        if (defaultLimitRequestDto.getNipDailyTransaction() == null) {
            nipTransactionDto.setNipDailyTransaction(defaultValue);
        } else {
            nipTransactionDto.setNipDailyTransaction(defaultLimitRequestDto.getNipDailyTransaction());
        }

        if (defaultLimitRequestDto.getNipPerTransaction() == null) {
            nipTransactionDto.setNipPerTransaction(defaultValue);
        } else {
            nipTransactionDto.setNipPerTransaction(defaultLimitRequestDto.getNipPerTransaction());
        }
        return nipTransactionDto;
    }

    private void nipTransactionValidMethod(DefaultLimitRequestDto defaultLimitRequestDto) {

        if (defaultLimitRequestDto.getNipPerTransaction() != null && defaultLimitRequestDto.getNipPerTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
            throw new MyCustomizedException("nip per transaction cannot be less than -1");
        }

        if (defaultLimitRequestDto.getNipDailyTransaction() != null && defaultLimitRequestDto.getNipDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
            throw new MyCustomizedException("nip daily transaction cannot be less than -1");
        }
    }

    private MobileTransactionDto mobileTransactionMethod(DefaultLimitRequestDto defaultLimitRequestDto, BigDecimal defaultValue) {

        mobileTransactionValidMethod(defaultLimitRequestDto);

        MobileTransactionDto mobileTransactionDto = new MobileTransactionDto();

        if (defaultLimitRequestDto.getMobileDailyTransaction() == null) {
            mobileTransactionDto.setMobileDailyTransaction(defaultValue);
        } else {
            mobileTransactionDto.setMobileDailyTransaction(defaultLimitRequestDto.getMobileDailyTransaction());
        }

        if (defaultLimitRequestDto.getMobilePerTransaction() == null) {
            mobileTransactionDto.setMobilePerTransaction(defaultValue);
        } else {
            mobileTransactionDto.setMobilePerTransaction(defaultLimitRequestDto.getMobilePerTransaction());
        }
        return mobileTransactionDto;
    }

    private void mobileTransactionValidMethod(DefaultLimitRequestDto defaultLimitRequestDto) {

        if (defaultLimitRequestDto.getMobilePerTransaction() != null && defaultLimitRequestDto.getMobilePerTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
            throw new MyCustomizedException("mobile per transaction cannot be less than -1");
        }

        if (defaultLimitRequestDto.getMobileDailyTransaction() != null && defaultLimitRequestDto.getMobileDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
            throw new MyCustomizedException("mobile daily transaction cannot be less than -1");
        }
    }

    private InternetTransactionDto internetTransactionMethod(DefaultLimitRequestDto defaultLimitRequestDto, BigDecimal defaultValue) {

        internetTransactionValidMethod(defaultLimitRequestDto);

        InternetTransactionDto internetTransactionDto = new InternetTransactionDto();

        if (defaultLimitRequestDto.getInternetDailyTransaction() == null) {
            internetTransactionDto.setInternetDailyTransaction(defaultValue);
        } else {
            internetTransactionDto.setInternetDailyTransaction(defaultLimitRequestDto.getInternetDailyTransaction());
        }

        if (defaultLimitRequestDto.getInternetPerTransaction() == null) {
            internetTransactionDto.setInternetPerTransaction(defaultValue);
        } else {
            internetTransactionDto.setInternetPerTransaction(defaultLimitRequestDto.getInternetPerTransaction());
        }
        return internetTransactionDto;
    }

    private void internetTransactionValidMethod(DefaultLimitRequestDto defaultLimitRequestDto) {

        if (defaultLimitRequestDto.getInternetPerTransaction() != null && defaultLimitRequestDto.getInternetPerTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
            throw new MyCustomizedException("internet per transaction cannot be less than -1");
        }

        if (defaultLimitRequestDto.getInternetDailyTransaction() != null && defaultLimitRequestDto.getInternetDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
            throw new MyCustomizedException("internet daily transaction cannot be less than -1");
        }
    }

    private BankTransactionDto bankTransactionMethod(DefaultLimitRequestDto defaultLimitRequestDto, BigDecimal defaultValue) {

        bankTransactionValidMethod(defaultLimitRequestDto);

        BankTransactionDto bankTransactionDto = new BankTransactionDto();

        if (defaultLimitRequestDto.getBankDailyTransaction() == null) {
            bankTransactionDto.setBankDailyTransaction(defaultValue);
        } else {
            bankTransactionDto.setBankDailyTransaction(defaultLimitRequestDto.getBankDailyTransaction());
        }

        if (defaultLimitRequestDto.getBankPerTransaction() == null) {
            bankTransactionDto.setBankPerTransaction(defaultValue);
        } else {
            bankTransactionDto.setBankPerTransaction(defaultLimitRequestDto.getBankPerTransaction());
        }
        return bankTransactionDto;
    }

    private void bankTransactionValidMethod(DefaultLimitRequestDto defaultLimitRequestDto) {

        if (defaultLimitRequestDto.getBankPerTransaction() != null && defaultLimitRequestDto.getBankPerTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
            throw new MyCustomizedException("bank per transaction cannot be less than -1");
        }

        if (defaultLimitRequestDto.getBankDailyTransaction() != null && defaultLimitRequestDto.getBankDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
            throw new MyCustomizedException("bank daily transaction cannot be less than -1");
        }
    }

    private GlobalTransactionDto globalTransactionMethod(DefaultLimitRequestDto defaultLimitRequestDto, BigDecimal defaultValue) throws MyCustomException {

        globalTransactionValidMethod(defaultLimitRequestDto);

        GlobalTransactionDto globalTransactionDto = new GlobalTransactionDto();

        if (defaultLimitRequestDto.getGlobalDailyTransaction() == null) {
            globalTransactionDto.setGlobalDailyTransaction(defaultValue);
        } else {
            globalTransactionDto.setGlobalDailyTransaction(defaultLimitRequestDto.getGlobalDailyTransaction());
        }

        if (defaultLimitRequestDto.getGlobalPerTransaction() == null) {
            globalTransactionDto.setGlobalPerTransaction(defaultValue);
        } else {
            globalTransactionDto.setGlobalPerTransaction(defaultLimitRequestDto.getGlobalPerTransaction());
        }
        return globalTransactionDto;
    }

    private void globalTransactionValidMethod(DefaultLimitRequestDto defaultLimitRequestDto) throws MyCustomException {

        CBNMaxLimitModel findByKycId = cbnMaxLimitRepository.findByKycId(defaultLimitRequestDto.getKycLevel(), defaultLimitRequestDto.getCifType(), defaultLimitRequestDto.getTransferType(), defaultLimitRequestDto.getProductType());

        if (findByKycId == null) {
            String message = String.format("Cbn max limit has not be configured for kyc level %s, cif type %s and transfer type %s",
                    defaultLimitRequestDto.getKycLevel(), defaultLimitRequestDto.getCifType(), defaultLimitRequestDto.getTransferType());
            throw new MyCustomizedException(message);
        }
        log.info("findByKycId: " + findByKycId);

        if (defaultLimitRequestDto.getGlobalPerTransaction() != null) {
            boolean dailyIsGreater = defaultLimitRequestDto.getGlobalPerTransaction().compareTo(findByKycId.getGlobalPerTransaction()) == 1;

            if (defaultLimitRequestDto.getGlobalPerTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
                throw new MyCustomizedException("global per transaction cannot be less than -1");
            }

            if (dailyIsGreater) {
                throw new MyCustomizedException("global limit per transaction cannot be greater than cbn max limit");
            }
        }

        if (defaultLimitRequestDto.getGlobalDailyTransaction() != null) {
            boolean dailyIsGreater = defaultLimitRequestDto.getGlobalDailyTransaction().compareTo(findByKycId.getGlobalDailyLimit()) == 1;

            if (defaultLimitRequestDto.getGlobalDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
                throw new MyCustomizedException("global daily transaction cannot be less than -1");
            }

            if (dailyIsGreater) {
                throw new MyCustomizedException("global daily limit transaction cannot be greater than cbn max limit");
            }
        }
    }

    private AtmTransactionDto atmPerTransactionMethod(DefaultLimitRequestDto defaultLimitRequestDto, BigDecimal defaultValue) {

        atmPerTransactionValidMethod(defaultLimitRequestDto);

        AtmTransactionDto atmTransactionDto = new AtmTransactionDto();

        if (defaultLimitRequestDto.getAtmDailyTransaction() == null) {
            atmTransactionDto.setAtmDailyTransaction(defaultValue);
        } else {
            atmTransactionDto.setAtmDailyTransaction(defaultLimitRequestDto.getAtmDailyTransaction());
        }

        if (defaultLimitRequestDto.getAtmPerTransaction() == null) {
            atmTransactionDto.setAtmPerTransaction(defaultValue);
        } else {
            atmTransactionDto.setAtmPerTransaction(defaultLimitRequestDto.getAtmPerTransaction());
        }
        return atmTransactionDto;
    }

    private void atmPerTransactionValidMethod(DefaultLimitRequestDto defaultLimitRequestDto) {

        if (defaultLimitRequestDto.getAtmPerTransaction() != null && defaultLimitRequestDto.getAtmPerTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
            throw new MyCustomizedException("atm per transaction cannot be less than -1");
        }

        if (defaultLimitRequestDto.getAtmDailyTransaction() != null && defaultLimitRequestDto.getAtmDailyTransaction().compareTo(BigDecimal.valueOf(-1)) == -1) {
            throw new MyCustomizedException("atm daily transaction cannot be less than -1");
        }
    }

    private int kycLevel(int kycLevel, String cifType, String transferType, String productType) {

        int kyc = 0;

        InternalLimitModel findByServiceTypeId = InternalLimitRepository.findByKycLevel(kycLevel, cifType, transferType, productType);

        if (findByServiceTypeId != null) {
            String message = String.format("kyc level %s & cif type %s & transfer type %s already exist", kycLevel, cifType, transferType);
            throw new MyCustomizedException(message);
        }

        if (kycLevel == 1) {
            if (!transferType.equalsIgnoreCase("INSTANT")) {
                throw new MyCustomizedException("invalid transfer type, expecting INSTANT for kyc level 1");
            }
            kyc = kycLevel;
        } else if (kycLevel == 2) {
            kyc = kycLevel;
        } else if (kycLevel == 3) {
            kyc = kycLevel;
        } else {
            String message = String.format("invalid kyc level supplied, supplied %s", kycLevel);
            throw new MyCustomizedException(message);
        }
        return kyc;
    }
}

