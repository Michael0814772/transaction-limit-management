package com.michael.limit.management.dto.response.limitCarried.channel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PortalLimitCarried {

    private BigDecimal portalDailyLimitCf = BigDecimal.ZERO;

    private BigDecimal portalDailyLimitBf = BigDecimal.ZERO;
}
