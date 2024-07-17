package com.michael.limit.management.dto.response.limitCarried.channel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OthersLimitCarried {

    private BigDecimal othersDailyLimitCf = BigDecimal.ZERO;

    private BigDecimal othersDailyLimitBf = BigDecimal.ZERO;
}
