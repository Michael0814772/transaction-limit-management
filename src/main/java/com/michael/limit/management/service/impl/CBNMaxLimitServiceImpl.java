package com.michael.limit.management.service.impl;

import com.michael.limit.management.custom.CustomResponse;
import com.michael.limit.management.dto.cbnMaxLimit.RegisterCbnLimitDto;
import com.michael.limit.management.dto.cbnMaxLimit.RegisterCbnLimitList;
import com.michael.limit.management.dto.cbnMaxLimit.UpdateCbnLimitDto;
import com.michael.limit.management.exception.exceptionMethod.MyCustomException;
import com.michael.limit.management.exception.exceptionMethod.MyCustomizedException;
import com.michael.limit.management.httpCall.HttpCall;
import com.michael.limit.management.model.CBNMaxLimitModel;
import com.michael.limit.management.model.ProductType;
import com.michael.limit.management.repository.CBNMaxLimitRepository;
import com.michael.limit.management.repository.CustomerLimitRepository;
import com.michael.limit.management.repository.ProductTypeRepository;
import com.michael.limit.management.service.CBNMaxLimitService;
import com.michael.limit.management.utils.HelperUtils;
import com.michael.limit.management.utils.LastModifiedBy;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class CBNMaxLimitServiceImpl implements CBNMaxLimitService {

    private final CBNMaxLimitRepository cbnMaxLimitRepository;

    private final HelperUtils helperUtils;

    private final CustomerLimitRepository customerLimitRepository;

    private final ProductTypeRepository productTypeRepository;

    private final LastModifiedBy lastModifiedBy;

    private final HttpCall httpCall;

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Override
    public Map<String, Object> registerCbnLimit(RegisterCbnLimitDto registerCbnLimitDto, String serviceToken, String serviceIpAddress) {
        log.info("register cbn limit...");

        String retailCif = "RET";
        String corpCif = "CORP";

        helperUtils.serviceAuth(serviceToken, serviceIpAddress);

        for (RegisterCbnLimitList registerCbn : registerCbnLimitDto.getRegisterCbnLimit()) {

            ProductType productType = checkProductType(registerCbn);

            CompletableFuture<Boolean> kycLevelAuth = CompletableFuture.supplyAsync(() ->
                    validateKycLevel(registerCbn.getKycLevel()));

            //checking cif type
            CompletableFuture<Boolean> cifTypeAuth = CompletableFuture.supplyAsync(() ->
                    validateCifType(registerCbn.getCifType(), retailCif, corpCif));

            boolean kycLeveAuthCheck = kycLevelAuth.join();
            boolean cifTypeCheck = cifTypeAuth.join();

            if (!kycLeveAuthCheck) {
                throw new MyCustomizedException("kyc level cannot be less than 1");
            }

            if (!cifTypeCheck) {
                String message = "invalid cif type supplied, expecting RET or CORP";
                throw new MyCustomizedException(message);
            }

            CBNMaxLimitModel cbnMaxLimitModel = new CBNMaxLimitModel();

            cbnMaxLimitModel.setKycLevel(registerCbn.getKycLevel());

            CompletableFuture<Boolean> globalPerAuth = CompletableFuture.supplyAsync(() ->
                    validateGlobalPer(registerCbn.getGlobalPerTransaction()));

            CompletableFuture<Boolean> globalDailyAuth = CompletableFuture.supplyAsync(() ->
                    validateGlobalDaily(registerCbn.getGlobalDailyLimit()));

            boolean globalPerCheck = globalPerAuth.join();
            boolean globalDailyCheck = globalDailyAuth.join();

            if (!globalPerCheck) {
                throw new MyCustomizedException("invalid global per transaction inserted");
            }

            if (!globalDailyCheck) {
                throw new MyCustomizedException("invalid global daily transaction inserted");
            }

            CBNMaxLimitModel findByKycId = cbnMaxLimitRepository.findByKycId(registerCbn.getKycLevel(), registerCbn.getCifType(), productType.getTransfer(), productType.getProduct());

            CompletableFuture<Boolean> findKycAuth = CompletableFuture.supplyAsync(() ->
                    validateFindKyc(findByKycId));

            CompletableFuture<Boolean> kycSetAuth = CompletableFuture.supplyAsync(() ->
                    validateSetKyc(registerCbn.getKycLevel()));

            boolean findKycCheck = findKycAuth.join();

            if (!findKycCheck) {
                String message = String.format("kyc level %s already exist", registerCbn.getKycLevel());
                throw new MyCustomizedException(message);
            }

            cbnMaxLimitModel.setGlobalPerTransaction(registerCbn.getGlobalPerTransaction());
            cbnMaxLimitModel.setGlobalDailyLimit(registerCbn.getGlobalDailyLimit());
            cbnMaxLimitModel.setCreatedDate(customerLimitRepository.createDate());
            cbnMaxLimitModel.setCreatedTime(customerLimitRepository.createTime());
            cbnMaxLimitModel.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
            cbnMaxLimitModel.setLastModifiedDate(customerLimitRepository.createDate());
            cbnMaxLimitModel.setLastModifiedDateTime(customerLimitRepository.createTime());
            cbnMaxLimitModel.setProductType(productType.getProduct());
            cbnMaxLimitModel.setTransferType(productType.getTransfer());
            cbnMaxLimitModel.setCifType(registerCbn.getCifType());

            String hash = helperUtils.cbnValuesToHash(cbnMaxLimitModel);
            cbnMaxLimitModel.setHash(hash);
            log.info("cbnMaxLimitModel: " + cbnMaxLimitModel);

            try {
                cbnMaxLimitRepository.save(cbnMaxLimitModel);
            } catch (Exception e) {
                log.info(e.toString());
                throw new MyCustomizedException("please try again later");
            }
        }

        return CustomResponse.response("success", "00", registerCbnLimitDto);
    }

    private ProductType checkProductType(RegisterCbnLimitList registerCbn) {

        ProductType productType = productTypeRepository.findByProduct(registerCbn.getProductType(), registerCbn.getTransferType());

        if (productType == null) {
            String message = String.format("Transfer of transferType %s and product type %s has not been configured", registerCbn.getTransferType(), registerCbn.getProductType());
            throw new MyCustomException(message);
        }
        return productType;
    }

    private boolean validateSetKyc(int kycLevel) {

        boolean status = true;

        if (kycLevel == 1) {
            status = false;
        }
        return status;
    }

    private boolean validateFindKyc(CBNMaxLimitModel findByKycId) {

        boolean status = true;

        if (findByKycId != null) {
            status = false;
        }
        return status;
    }

    private boolean validateGlobalDaily(BigDecimal globalDailyLimit) {

        return globalDailyLimit != null && globalDailyLimit.compareTo(BigDecimal.valueOf(5000)) >= 0;
    }

    private boolean validateGlobalPer(BigDecimal globalPerTransaction) {

        return globalPerTransaction != null && globalPerTransaction.compareTo(BigDecimal.valueOf(1)) >= 0;
    }

    private boolean validateTransferType(String transferType, String transferType1, String transferType2) {

        boolean status = true;

        if (!transferType.equalsIgnoreCase(transferType1)) {
            if (!transferType.equalsIgnoreCase(transferType2)) {
                status = false;
            }
        }
        return status;
    }

    private boolean validateCifType(String cifType, String retailCif, String corpCif) {

        boolean status = true;

        if (!cifType.equalsIgnoreCase(retailCif)) {
            if (!cifType.equalsIgnoreCase(corpCif)) {
                status = false;
            }
        }
        return status;
    }

    private boolean validateKycLevel(int kycLevel) {

        return kycLevel >= 1;
    }

    @Override
    public Map<String, Object> updateCbnLimit(UpdateCbnLimitDto updateCbnLimitDto, String serviceToken, String serviceIpAddress) {
        log.info("update cbn limit...");

        helperUtils.serviceAuth(serviceToken, serviceIpAddress);

        CBNMaxLimitModel findByKycId = cbnMaxLimitRepository.findByKycId(updateCbnLimitDto.getKycLevel(), updateCbnLimitDto.getCifType(), updateCbnLimitDto.getTransferType(), updateCbnLimitDto.getProductType());

        if (findByKycId == null) {
            String message = String.format("kyc level %s, Cif type %s, and transfer type %s does not exist", updateCbnLimitDto.getKycLevel(), updateCbnLimitDto.getCifType(), updateCbnLimitDto.getTransferType());
            throw new MyCustomizedException(message);
        }

        String hashToCheck = helperUtils.cbnValuesToHash(findByKycId);

        if (!hashToCheck.equals(findByKycId.getHash())) {
            throw new MyCustomizedException("failed update validation");
        }

        if (updateCbnLimitDto.getGlobalPerTransaction() != null) {
            if (updateCbnLimitDto.getGlobalPerTransaction().compareTo(BigDecimal.valueOf(1)) < 0) {
                throw new MyCustomizedException("invalid global per transaction inserted");
            } else {
                findByKycId.setGlobalPerTransaction(updateCbnLimitDto.getGlobalPerTransaction());
            }
        }

        if (updateCbnLimitDto.getGlobalDailyLimit() != null) {
            if (updateCbnLimitDto.getGlobalDailyLimit().compareTo(BigDecimal.valueOf(1)) < 0) {
                throw new MyCustomizedException("invalid global daily transaction inserted");
            } else {
                findByKycId.setGlobalDailyLimit(updateCbnLimitDto.getGlobalDailyLimit());
            }
        }

        findByKycId.setLastModifiedBy(lastModifiedBy.lastModifiedBy());
        findByKycId.setLastModifiedDate(customerLimitRepository.createDate());
        findByKycId.setLastModifiedDateTime(customerLimitRepository.createTime());
        findByKycId.setProductType(updateCbnLimitDto.getProductType());

        String hash = helperUtils.cbnValuesToHash(findByKycId);
        findByKycId.setHash(hash);

        try {
            cbnMaxLimitRepository.save(findByKycId);
        } catch (Exception e) {
            log.info(e.toString());
            throw new MyCustomizedException("please try again later");
        }

        return CustomResponse.response("successful", "00", updateCbnLimitDto);
    }
}
