package com.michael.limit.management.dto.response.limitCarried.channel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ThirdPartyLimitCarried {

    private BigDecimal thirdPartyDailyLimitCf = BigDecimal.ZERO;

    private BigDecimal thirdPartyDailyLimitBf = BigDecimal.ZERO;
}
