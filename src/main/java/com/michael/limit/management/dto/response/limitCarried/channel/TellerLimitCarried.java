package com.michael.limit.management.dto.response.limitCarried.channel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TellerLimitCarried {

    private BigDecimal tellerDailyLimitCf = BigDecimal.ZERO;

    private BigDecimal tellerDailyLimitBf = BigDecimal.ZERO;
}
