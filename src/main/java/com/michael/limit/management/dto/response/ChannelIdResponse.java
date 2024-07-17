package com.michael.limit.management.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChannelIdResponse {

    private BigDecimal perTransaction;

    private BigDecimal dailyTotalTransaction;

    private BigDecimal perTransactionNonInstant;

    private BigDecimal dailyTotalTransactionNonInstant;
}
