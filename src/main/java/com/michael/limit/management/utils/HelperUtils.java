package com.michael.limit.management.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.michael.limit.management.dto.DailyLimitUsageDto.CifDto;
import com.michael.limit.management.dto.authentication.ResponseMessage;
import com.michael.limit.management.dto.authentication.ServiceResponseMessage;
import com.michael.limit.management.dto.databaseDto.AccountDetailsDto;
import com.michael.limit.management.exception.exceptionMethod.InternalServerException;
import com.michael.limit.management.exception.exceptionMethod.MyCustomizedException;
import com.michael.limit.management.exception.exceptionMethod.UnauthorizedException;
import com.michael.limit.management.httpCall.HttpCall;
import com.michael.limit.management.model.*;
import com.michael.limit.management.repository.ChannelLimitRepository;
import com.michael.limit.management.repository.DailyLimitUsageRepository;
import com.michael.limit.management.repository.GlobalLimitRepository;
import com.michael.limit.management.repository.NipLimitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class HelperUtils {

    private final Encryption encryption;

    private final DailyLimitUsageRepository dailyLimitUsageRepository;

    private final ChannelLimitRepository channelLimitRepository;

    private final GlobalLimitRepository globalLimitRepository;

    private final NipLimitRepository nipLimitRepository;

    private final HttpCall httpCall;

    private final ObjectMapper objectMapper;

    @Value("${application.auth.serviceAccessLevel}")
    private int serviceAccessLevel;

    @Value("${application.auth.userAccessLevel}")
    private int userAccessLevel;

    @Value("${application.auth.accessLevel}")
    private int accessLevel;

    public CifDto getCifIdAndType(String accountNumber) {
        log.info("getting account details...");

        CifDto cifDto = new CifDto();

        String getCif;

        try {
            getCif = dailyLimitUsageRepository.getCIfIdAndType(accountNumber);
        } catch (Exception e) {
            log.info(e.getMessage());
            throw new InternalServerException("Account does not exist");
        }

        Map<String, String> mapData = null;

        if (getCif == null) {
            String message = String.format("account number %s cannot be validated", accountNumber);
            throw new MyCustomizedException(message);
        }

        String[] separateVariable = getCif.split("[|]", 20);

        String[] keys = {"active", "name", "freezeCode", "actLimit", "kycLevel", "bvn", "cifId", "cifType", "Schmcode", "balance", "currency", "accountSegment"};

        mapData = new HashMap<>();
        for (int i = 0; i < keys.length; i++) {
            mapData.put(keys[i], separateVariable[i]);
        }
        try {
            cifDto.setCifType(mapData.get("cifType"));
            cifDto.setCifId(mapData.get("cifId"));
            cifDto.setKycLevel(Integer.parseInt(mapData.get("kycLevel")));
            cifDto.setFreezeCode(mapData.get("freezeCode"));
            cifDto.setAccountSegment(mapData.get("accountSegment"));
            cifDto.setCurrency(mapData.get("currency"));
        } catch (Exception e) {
            log.info(e.toString());
            throw new MyCustomizedException("error parsing data");
        }

        if (mapData.get("cifId").equalsIgnoreCase("null") || mapData.get("cifId") == null) {
            String message = String.format("account number %s does not exist", accountNumber);
            throw new MyCustomizedException(message);
        }
        return cifDto;
    }

    public AccountDetailsDto getWithCifOrAccount(String accountNumber) {
        log.info("getting account details...");

        String accountDetails = null;
        try {
            accountDetails = dailyLimitUsageRepository.fetchAccountDetailsWithAccount(accountNumber);
        } catch (Exception e) {
            log.info(e.getMessage());
            throw new InternalServerException("Account or cif does not exist");
        }

        Map<String, String> mapData = null;

        if (accountDetails == null) {
            String message = String.format("account number %s cannot be validated", accountNumber);
            throw new MyCustomizedException(message);
        }

        String[] separateVariable = accountDetails.split("[|]", 30);

        String[] keys = {"firstName", "middleName", "lastName", "gender", "dob", "bvn",
                "address", "nok", "nokRshp", "state", "country", "id", "idNos", "phoneNumber", "emailAddress",
                "relationshipManager", "accountSegment", "cifType", "kycLevel", "cifId", "creditStatus",
                "relationshipManagerName"};

        mapData = new HashMap<>();
        for (int i = 0; i < keys.length; i++) {
            mapData.put(keys[i], separateVariable[i]);
        }

        AccountDetailsDto accountDetailsDto = objectMapper.convertValue(mapData, AccountDetailsDto.class);

        if (accountDetailsDto.getCifId() == null
                || accountDetailsDto.getCifId().trim().isEmpty() ||
                accountDetailsDto.getCifId().equalsIgnoreCase("null")) {
            String message = String.format("account number %s does not exist", accountNumber);
            throw new MyCustomizedException(message);
        }

        return accountDetailsDto;
    }

//    public String DailyValuesToChecksum(DailyLimitUsageModel dailyLimitUsageModel) {
//
//        String values = String.valueOf(dailyLimitUsageModel.getAtmDailyLimitBf().intValue()) + dailyLimitUsageModel.getAtmDailyLimitCf().intValue() +
//                dailyLimitUsageModel.getGlobalDailyLimitBf().intValue() + dailyLimitUsageModel.getGlobalDailyLimitCf().intValue() +
//                dailyLimitUsageModel.getNipDailyLimitBf().intValue() +
//                dailyLimitUsageModel.getNipDailyLimitCf().intValue() + dailyLimitUsageModel.getUssdDailyLimitBf().intValue() +
//                dailyLimitUsageModel.getUssdDailyLimitCf().intValue() +
//                dailyLimitUsageModel.getTellerDailyLimitBf().intValue() + dailyLimitUsageModel.getTellerDailyLimitCf().intValue() +
//                dailyLimitUsageModel.getInternetDailyLimitBf().intValue() + dailyLimitUsageModel.getInternetDailyLimitCf().intValue() +
//                dailyLimitUsageModel.getMobileDailyLimitBf().intValue() + dailyLimitUsageModel.getMobileDailyLimitCf().intValue() +
//                dailyLimitUsageModel.getPosDailyLimitBf().intValue() + dailyLimitUsageModel.getPosDailyLimitCf().intValue() +
//                dailyLimitUsageModel.getPortalDailyLimitBf().intValue() +
//                dailyLimitUsageModel.getPortalDailyLimitCf().intValue() + dailyLimitUsageModel.getThirdPartyDailyLimitBf().intValue() +
//                dailyLimitUsageModel.getThirdPartyDailyLimitCf().intValue() +
//                dailyLimitUsageModel.getOthersDailyLimitBf().intValue() + dailyLimitUsageModel.getOthersDailyLimitCf().intValue();
//
//        return encryption.encrypt(values);
//    }

//    public String dailyValuesToHash(DailyLimitUsageModel dailyLimitUsageModel) {
//
//        String values = dailyLimitUsageModel.getCifId() +
//                dailyLimitUsageModel.getRequestAmount().intValue() +
//                dailyLimitUsageModel.getLastModifiedBy() + dailyLimitUsageModel.getTranDate() +
//                dailyLimitUsageModel.getServiceType() + dailyLimitUsageModel.getServiceTypeId();
//
//        return encryption.encrypt(values);
//    }

//    public String summaryChecksum(SummaryDetailModel summaryDetailModel) {
//
//        String values = String.valueOf(summaryDetailModel.getRequestAmount().intValue()) + summaryDetailModel.getAtmDailyLimitBf().intValue()
//                + summaryDetailModel.getAtmDailyLimitCf().intValue() +
//                summaryDetailModel.getGlobalDailyLimitBf().intValue() + summaryDetailModel.getGlobalDailyLimitCf().intValue()
//                + summaryDetailModel.getNipDailyLimitBf().intValue() +
//                summaryDetailModel.getNipDailyLimitCf().intValue() + summaryDetailModel.getUssdDailyLimitBf().intValue()
//                + summaryDetailModel.getUssdDailyLimitCf().intValue() +
//                summaryDetailModel.getTellerDailyLimitBf().intValue() + summaryDetailModel.getTellerDailyLimitCf().intValue() +
//                summaryDetailModel.getInternetDailyLimitBf().intValue() + summaryDetailModel.getInternetDailyLimitCf().intValue() +
//                summaryDetailModel.getMobileDailyLimitBf().intValue() + summaryDetailModel.getMobileDailyLimitCf().intValue() +
//                summaryDetailModel.getPosDailyLimitBf().intValue() + summaryDetailModel.getPosDailyLimitCf().intValue() +
//                summaryDetailModel.getPortalDailyLimitBf().intValue() +
//                summaryDetailModel.getPortalDailyLimitCf().intValue() + summaryDetailModel.getThirdPartyDailyLimitBf().intValue() +
//                summaryDetailModel.getThirdPartyDailyLimitCf().intValue() +
//                summaryDetailModel.getOthersDailyLimitBf().intValue() + summaryDetailModel.getOthersDailyLimitCf().intValue();
//
//        return encryption.encrypt(values);
//    }

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

    public String channelHashMethodForJustUpdate(ChannelCode channelCode) {

        String hash = channelCode.getCifId() + channelCode.getCreatedDate() + channelCode.getLastModifiedBy() +
                channelCode.getTotalDailyLimit().intValue() + channelCode.getPerTransactionLimit().intValue() + channelCode.getStatus();
        return encryption.encrypt(hash);
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

    public String nipHashMethodJustChecksum(NipLimit nipLimit) {

        String hash = nipLimit.getCifId() + nipLimit.getCreatedDate() + nipLimit.getLastModifiedBy() +
                nipLimit.getTotalDailyLimit().intValue() + nipLimit.getPerTransactionLimit().intValue() + nipLimit.getStatus();

        return encryption.encrypt(hash);
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

//        return encryption.encrypt(hash);
    }

    public String failedValuesToChecksum(FailedDailyLimitModel failedDailyLimitModel) {

        String values = String.valueOf(failedDailyLimitModel.getAtmDailyLimitBf().intValue()) + failedDailyLimitModel.getAtmDailyLimitCf().intValue() +
                failedDailyLimitModel.getGlobalDailyLimitBf().intValue() + failedDailyLimitModel.getGlobalDailyLimitCf().intValue() +
                failedDailyLimitModel.getNipDailyLimitBf().intValue() +
                failedDailyLimitModel.getNipDailyLimitCf().intValue() + failedDailyLimitModel.getUssdDailyLimitBf().intValue()
                + failedDailyLimitModel.getUssdDailyLimitCf().intValue() +
                failedDailyLimitModel.getTellerDailyLimitBf().intValue() + failedDailyLimitModel.getTellerDailyLimitCf().intValue() +
                failedDailyLimitModel.getInternetDailyLimitBf().intValue() + failedDailyLimitModel.getInternetDailyLimitCf().intValue() +
                failedDailyLimitModel.getMobileDailyLimitBf().intValue() + failedDailyLimitModel.getMobileDailyLimitCf().intValue() +
                failedDailyLimitModel.getPosDailyLimitBf().intValue() + failedDailyLimitModel.getPosDailyLimitCf().intValue()
                + failedDailyLimitModel.getPortalDailyLimitBf().intValue() +
                failedDailyLimitModel.getPortalDailyLimitCf().intValue() + failedDailyLimitModel.getThirdPartyDailyLimitBf().intValue()
                + failedDailyLimitModel.getThirdPartyDailyLimitCf().intValue() +
                failedDailyLimitModel.getOthersDailyLimitBf().intValue() + failedDailyLimitModel.getOthersDailyLimitCf().intValue();

        return encryption.encrypt(values);
    }

    public String failedValuesToHash(FailedDailyLimitModel failedDailyLimitModel) {

        String values = failedDailyLimitModel.getCifId() + failedDailyLimitModel.getAccountNumber() +
                failedDailyLimitModel.getRequestAmount().intValue() +
                failedDailyLimitModel.getLastModifiedBy() + failedDailyLimitModel.getTranDate() +
                failedDailyLimitModel.getServiceType() + failedDailyLimitModel.getServiceTypeId();

        return encryption.encrypt(values);
    }

    public String cbnValuesToHash(CBNMaxLimitModel cbnMaxLimitModel) {

        String toHash = cbnMaxLimitModel.getKycLevel() + "" + cbnMaxLimitModel.getGlobalDailyLimit().intValue()
                + "" + cbnMaxLimitModel.getGlobalPerTransaction().intValue() + cbnMaxLimitModel.getLastModifiedBy()
                + cbnMaxLimitModel.getLastModifiedDate() + cbnMaxLimitModel.getCreatedDate() + cbnMaxLimitModel.getCifType()
                + cbnMaxLimitModel.getTransferType();

        return encryption.encrypt(toHash);
    }

    public String InternalChecksumMethod(InternalLimitModel InternalLimitModel) {

        String checksum = InternalLimitModel.getCifType() + "" + InternalLimitModel.getKycLevel() + InternalLimitModel.getVendorPerTransaction().intValue() + InternalLimitModel.getVendorDailyTransaction().intValue() +
                InternalLimitModel.getUssdPerTransaction().intValue() + InternalLimitModel.getUssdDailyTransaction().intValue() +
                InternalLimitModel.getThirdPerTransaction().intValue() + InternalLimitModel.getThirdDailyTransaction().intValue() +
                InternalLimitModel.getPosPerTransaction().intValue() + InternalLimitModel.getPosDailyTransaction().intValue() +
                InternalLimitModel.getOthersPerTransaction().intValue() + InternalLimitModel.getOthersDailyTransaction().intValue() +
                InternalLimitModel.getNipPerTransaction().intValue() + InternalLimitModel.getNipDailyTransaction().intValue();

        return encryption.encrypt(checksum);
    }

    public String InternalHashMethod(InternalLimitModel InternalLimitModel) {

        String hash = "" + InternalLimitModel.getLastModifiedBy() + InternalLimitModel.getAtmDailyTransaction().intValue() +
                InternalLimitModel.getAtmDailyTransaction().intValue() + InternalLimitModel.getLastModifiedDate() +
                InternalLimitModel.getBankDailyTransaction().intValue() + InternalLimitModel.getBankPerTransaction().intValue() +
                InternalLimitModel.getGlobalDailyTransaction().intValue() + InternalLimitModel.getGlobalPerTransaction().intValue() +
                InternalLimitModel.getInternetDailyTransaction().intValue() + InternalLimitModel.getInternetPerTransaction().intValue() +
                InternalLimitModel.getMobileDailyTransaction().intValue() + InternalLimitModel.getMobilePerTransaction().intValue();

        return encryption.encrypt(hash);
    }

    public String auditLimitToHash(AuditLimitModel auditLimitModel) {

        String hash = auditLimitModel.getAuditId() + auditLimitModel.getUpdateDate() + auditLimitModel.getAtmDailyLimitCf()
                + auditLimitModel.getAtmDailyLimitBf() + auditLimitModel.getInternetDailyLimitBf() + auditLimitModel.getInternetDailyLimitCf()
                + auditLimitModel.getModifiedBy() + auditLimitModel.getGlobalDailyLimitCf() + auditLimitModel.getGlobalDailyLimitBf()
                + auditLimitModel.getUssdDailyLimitBf() + auditLimitModel.getUssdDailyLimitCf();

        return encryption.encrypt(hash);
    }

    public ServiceResponseMessage serviceAuthenticationValidation(String serviceToken, String serviceIpAddress) {

        ServiceResponseMessage responseMessage = new ServiceResponseMessage();

        try {
            Map<String, Object> getResponse = httpCall.serviceToken(serviceToken, serviceIpAddress);
            log.info("response: " + getResponse);

            if (getResponse.get("responseCode") != null) {
                responseMessage.setResponseCode((String) getResponse.get("responseCode"));
            } else {
                responseMessage.setResponseCode("99");
            }

            if (getResponse.get("accessLevel") != null) {
                responseMessage.setAccessLevel(Integer.parseInt((String) getResponse.get("accessLevel")));
            }

            if (getResponse.get("responseMsg") != null) {
                responseMessage.setResponseMsg((String) getResponse.get("responseMsg"));
            }
        } catch (Exception e) {
            log.info(e.toString());
            throw new InternalServerException("Err - Technical Error with dependency");
        }

        return responseMessage;
    }

    public ResponseMessage userAuthenticationValidation(String userToken, String serviceToken, String serviceIpAddress, String accountNumber, int channelId, String cif) {

        ResponseMessage responseMessage = new ResponseMessage();

        try {
            Map<String, Object> getResponse = httpCall.userTokens(userToken, serviceToken, serviceIpAddress, accountNumber, String.valueOf(channelId), cif);

            log.info("Response: " + getResponse);

            if (getResponse.get("responseCode") != null) {
                responseMessage.setResponseCode((String) getResponse.get("responseCode"));
            } else {
                responseMessage.setResponseCode("99");
            }

            if (getResponse.get("serviceAccessLevel") != null) {
                responseMessage.setServiceAccessLevel(Integer.valueOf((String) getResponse.get("serviceAccessLevel")));
            }

            if (getResponse.get("userAccessLevel") != null) {
                responseMessage.setUserAccessLevel(Integer.valueOf((String) getResponse.get("userAccessLevel")));
            }
        } catch (Exception e) {
            log.info(e.toString());
            throw new InternalServerException("Err - Technical Error with dependency");
        }

        return responseMessage;
    }

    public void userAuth(String userToken, String serviceToken, String serviceIpAddress, String acc, int channelCode, String cif) {

        ResponseMessage responseMessage = userAuthenticationValidation(userToken, serviceToken, serviceIpAddress, acc, channelCode, cif);

        if (!responseMessage.getResponseCode().equalsIgnoreCase("00")) {
            throw new MyCustomizedException("header authentication failed");
        }

        if (responseMessage.getServiceAccessLevel() < serviceAccessLevel || responseMessage.getUserAccessLevel() < userAccessLevel) {
            throw new UnauthorizedException("unauthorized access");
        }
    }

    public void serviceAuth(String serviceToken, String serviceIpAddress) {

        ServiceResponseMessage responseMessage = serviceAuthenticationValidation(serviceToken, serviceIpAddress);

        if (!responseMessage.getResponseCode().equalsIgnoreCase("00")) {
            throw new MyCustomizedException("header authentication failed");
        }

        if (responseMessage.getAccessLevel() < accessLevel) {
            throw new UnauthorizedException("unauthorized access");
        }
    }

    public String accountNumber(String accountNumber, String cifId) {
        log.info("validate account number/Cif...");

        String acc = "";

        if (accountNumber == null && cifId == null) {
            throw new MyCustomizedException("account number or cif id cannot be null");
        }

        if (accountNumber != null) {
            acc = accountNumber;
        }

        if (cifId != null) {
            acc = cifId;
        }

        return acc;
    }

}
