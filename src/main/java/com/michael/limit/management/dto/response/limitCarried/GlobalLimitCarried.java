package com.michael.limit.management.dto.response.limitCarried;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GlobalLimitCarried {

    private BigDecimal globalDailyLimitCf = BigDecimal.ZERO;

    private BigDecimal globalDailyLimitBf = BigDecimal.ZERO;
}
