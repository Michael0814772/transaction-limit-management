package com.michael.limit.management.dto.allLimit;

import com.michael.limit.management.dto.response.limitCarried.GlobalLimitCarried;
import com.michael.limit.management.dto.response.limitCarried.NipLimitCarried;
import com.michael.limit.management.dto.response.limitCarried.channel.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LimitManagement {

    GlobalLimitCarried globalLimitCarried = new GlobalLimitCarried();

    NipLimitCarried nipLimitCarried = new NipLimitCarried();

    UssdLimitCarried ussdLimitCarried = new UssdLimitCarried();

    ThirdPartyLimitCarried thirdPartyLimitCarried = new ThirdPartyLimitCarried();

    TellerLimitCarried tellerLimitCarried = new TellerLimitCarried();

    PosLimitCarried posLimitCarried = new PosLimitCarried();

    PortalLimitCarried portalLimitCarried = new PortalLimitCarried();

    OthersLimitCarried othersLimitCarried = new OthersLimitCarried();

    MobileLimitCarried mobileLimitCarried = new MobileLimitCarried();

    InternetLimitCarried internetLimitCarried = new InternetLimitCarried();

    AtmLimitCarried atmLimitCarried = new AtmLimitCarried();

    private BigDecimal globalPerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal globalDailyTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nipPerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nipDailyTransactionLimit = BigDecimal.ZERO;

    private BigDecimal ussdPerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal ussdDailyTransactionLimit = BigDecimal.ZERO;

    private BigDecimal bankTellerPerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal bankTellerDailyTransactionLimit = BigDecimal.ZERO;

    private BigDecimal internetBankingPerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal internetBankingDailyTransactionLimit = BigDecimal.ZERO;

    private BigDecimal mobilePerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal mobileDailyTransactionLimit = BigDecimal.ZERO;

    private BigDecimal posPerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal posDailyTransactionLimit = BigDecimal.ZERO;

    private BigDecimal atmPerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal atmDailyTransactionLimit = BigDecimal.ZERO;

    private BigDecimal vendorPerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal vendorDailyTransactionLimit = BigDecimal.ZERO;

    private BigDecimal thirdPartyPerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal thirdPartyDailyTransactionLimit = BigDecimal.ZERO;

    private BigDecimal othersPerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal othersDailyTransactionLimit = BigDecimal.ZERO;


    ///////////////////////////////////////////////////////////
    //for non-instant
    GlobalLimitCarried nonInstantGlobalLimitCarried = new GlobalLimitCarried();

    NipLimitCarried nonInstantNipLimitCarried = new NipLimitCarried();

    UssdLimitCarried nonInstantUssdLimitCarried = new UssdLimitCarried();

    ThirdPartyLimitCarried nonInstantThirdPartyLimitCarried = new ThirdPartyLimitCarried();

    TellerLimitCarried nonInstantTellerLimitCarried = new TellerLimitCarried();

    PosLimitCarried nonInstantPosLimitCarried = new PosLimitCarried();

    PortalLimitCarried nonInstantPortalLimitCarried = new PortalLimitCarried();

    OthersLimitCarried nonInstantOthersLimitCarried = new OthersLimitCarried();

    MobileLimitCarried nonInstantMobileLimitCarried = new MobileLimitCarried();

    InternetLimitCarried nonInstantInternetLimitCarried = new InternetLimitCarried();

    AtmLimitCarried nonInstantAtmLimitCarried = new AtmLimitCarried();

    private BigDecimal nonInstantGlobalPerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nonInstantGlobalDailyTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nonInstantNipPerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nonInstantNipDailyTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nonInstantUssdPerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nonInstantUssdDailyTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nonInstantBankTellerPerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nonInstantBankTellerDailyTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nonInstantInternetBankingPerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nonInstantInternetBankingDailyTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nonInstantMobilePerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nonInstantMobileDailyTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nonInstantPosPerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nonInstantPosDailyTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nonInstantAtmPerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nonInstantAtmDailyTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nonInstantVendorPerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nonInstantVendorDailyTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nonInstantThirdPartyPerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nonInstantThirdPartyDailyTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nonInstantOthersPerTransactionLimit = BigDecimal.ZERO;

    private BigDecimal nonInstantOthersDailyTransactionLimit = BigDecimal.ZERO;
}
