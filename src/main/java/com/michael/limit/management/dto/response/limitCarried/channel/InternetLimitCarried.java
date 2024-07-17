package com.michael.limit.management.dto.response.limitCarried.channel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InternetLimitCarried {

    private BigDecimal internetDailyLimitCf = BigDecimal.ZERO;

    private BigDecimal internetDailyLimitBf = BigDecimal.ZERO;
}
