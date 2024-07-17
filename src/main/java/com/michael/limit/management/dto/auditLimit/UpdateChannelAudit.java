package com.michael.limit.management.dto.auditLimit;

import jakarta.persistence.Column;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Data
@RequiredArgsConstructor
@Validated
public class UpdateChannelAudit {

    @Column(name = "ussd_daily_Limit_Cf")
    private BigDecimal ussdDailyLimitCf;

    @Column(name = "ussd_daily_Limit_Bf")
    private BigDecimal ussdDailyLimitBf;

    @Column(name = "teller_daily_Limit_Cf")
    private BigDecimal tellerDailyLimitCf;

    @Column(name = "teller_daily_Limit_Bf")
    private BigDecimal tellerDailyLimitBf;

    @Column(name = "internet_daily_Limit_Cf")
    private BigDecimal internetDailyLimitCf;

    @Column(name = "internet_daily_Limit_Bf")
    private BigDecimal internetDailyLimitBf;

    @Column(name = "mobile_daily_Limit_Cf")
    private BigDecimal mobileDailyLimitCf;

    @Column(name = "mobile_daily_Limit_Bf")
    private BigDecimal mobileDailyLimitBf;

    @Column(name = "pos_daily_Limit_Cf")
    private BigDecimal posDailyLimitCf;

    @Column(name = "pos_daily_Limit_Bf")
    private BigDecimal posDailyLimitBf;

    @Column(name = "atm_daily_Limit_Cf")
    private BigDecimal atmDailyLimitCf;

    @Column(name = "atm_daily_Limit_Bf")
    private BigDecimal atmDailyLimitBf;

    @Column(name = "portal_daily_Limit_Cf")
    private BigDecimal portalDailyLimitCf;

    @Column(name = "portal_daily_Limit_Bf")
    private BigDecimal portalDailyLimitBf;

    @Column(name = "third_party_daily_Limit_Cf")
    private BigDecimal thirdPartyDailyLimitCf;

    @Column(name = "third_party_daily_Limit_Bf")
    private BigDecimal thirdPartyDailyLimitBf;

    @Column(name = "others_daily_Limit_Cf")
    private BigDecimal othersDailyLimitCf;

    @Column(name = "others_daily_Limit_Bf")
    private BigDecimal othersDailyLimitBf;
}
