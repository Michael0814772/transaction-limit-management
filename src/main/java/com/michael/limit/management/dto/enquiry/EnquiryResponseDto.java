package com.michael.limit.management.dto.enquiry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnquiryResponseDto {

    private String cifId;

    private String accountType;

    private BigDecimal CBNMaxLimitRetail;

    private BigDecimal CBNMaxLimitSME;

    private BigDecimal CBNperTransLimitRetail;

    private BigDecimal CBNperTransLimitSME;

    private BigDecimal amount;

    private String accountNumber;

    private BigDecimal globalDailyMaxLimit;

    private BigDecimal availableGlobalDailyLimit;

    private BigDecimal globalLimitPerTransaction;

    private BigDecimal nipDailyMaxLimit;

    private BigDecimal availableNipDailyLimit;

    private BigDecimal nipLimitPerTransaction;

    private BigDecimal ussdDailyMaxLimit;

    private BigDecimal availableUssdDailyLimit;

    private BigDecimal ussdLimitPerTransaction;

    private BigDecimal tellerDailyMaxLimit;

    private BigDecimal availableTellerDailyLimit;

    private BigDecimal tellerLimitPerTransaction;

    private BigDecimal internetDailyMaxLimit;

    private BigDecimal availableInternetDailyLimit;

    private BigDecimal internetLimitPerTransaction;

    private BigDecimal mobileDailyMaxLimit;

    private BigDecimal availableMobileDailyLimit;

    private BigDecimal mobileLimitPerTransaction;

    private BigDecimal posDailyMaxLimit;

    private BigDecimal availablePosDailyLimit;

    private BigDecimal posLimitPerTransaction;

    private BigDecimal atmDailyMaxLimit;

    private BigDecimal availableAtmDailyLimit;

    private BigDecimal atmLimitPerTransaction;

    private BigDecimal portalDailyMaxLimit;

    private BigDecimal availablePortalDailyLimit;

    private BigDecimal portalLimitPerTransaction;

    private BigDecimal thirdPartyDailyMaxLimit;

    private BigDecimal availableThirdPartyDailyLimit;

    private BigDecimal thirdLimitPerTransaction;

    private BigDecimal othersDailyMaxLimit;

    private BigDecimal availableOthersDailyLimit;

    private BigDecimal othersLimitPerTransaction;
}
