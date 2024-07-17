package com.michael.limit.management.dto.DailyLimitUsageDto;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Data
@RequiredArgsConstructor
@Validated
public class DailyChannelAmt {

    private BigDecimal ussdDailyLimitCf = BigDecimal.ZERO;

    private BigDecimal ussdDailyLimitBf = BigDecimal.ZERO;

    private BigDecimal tellerDailyLimitCf = BigDecimal.ZERO;

    private BigDecimal tellerDailyLimitBf = BigDecimal.ZERO;

    private BigDecimal internetDailyLimitCf = BigDecimal.ZERO;

    private BigDecimal internetDailyLimitBf = BigDecimal.ZERO;

    private BigDecimal mobileDailyLimitCf = BigDecimal.ZERO;

    private BigDecimal mobileDailyLimitBf = BigDecimal.ZERO;

    private BigDecimal posDailyLimitCf = BigDecimal.ZERO;

    private BigDecimal posDailyLimitBf = BigDecimal.ZERO;

    private BigDecimal atmDailyLimitCf = BigDecimal.ZERO;

    private BigDecimal atmDailyLimitBf = BigDecimal.ZERO;

    private BigDecimal portalDailyLimitCf = BigDecimal.ZERO;

    private BigDecimal portalDailyLimitBf = BigDecimal.ZERO;

    private BigDecimal thirdPartyDailyLimitCf = BigDecimal.ZERO;

    private BigDecimal thirdPartyDailyLimitBf = BigDecimal.ZERO;

    private BigDecimal othersDailyLimitCf = BigDecimal.ZERO;

    private BigDecimal othersDailyLimitBf = BigDecimal.ZERO;
}
