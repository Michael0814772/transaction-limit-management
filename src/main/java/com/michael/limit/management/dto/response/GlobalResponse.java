package com.michael.limit.management.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GlobalResponse {

    private BigDecimal perTransaction;

    private BigDecimal dailyTotalTransaction;

    //for non-instant transactions
    private BigDecimal perTransactionNonInstant;

    private BigDecimal dailyTotalTransactionNonInstant;
}
