package com.michael.limit.management.dto.response.limitCarried;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NipLimitCarried {

    private BigDecimal nipDailyLimitCf = BigDecimal.ZERO;

    private BigDecimal nipDailyLimitBf = BigDecimal.ZERO;
}
