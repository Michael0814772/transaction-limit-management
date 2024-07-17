package com.michael.limit.management.dto.response.limitCarried.channel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MobileLimitCarried {

    private BigDecimal mobileDailyLimitCf = BigDecimal.ZERO;

    private BigDecimal mobileDailyLimitBf = BigDecimal.ZERO;
}
