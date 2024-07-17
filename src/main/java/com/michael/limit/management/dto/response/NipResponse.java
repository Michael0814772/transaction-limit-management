package com.michael.limit.management.dto.response;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Data
@RequiredArgsConstructor
@Validated
public class NipResponse {

    private BigDecimal perTransaction;

    private BigDecimal dailyTotalTransaction;

    //non instant check
    private BigDecimal perTransactionNonInstant;

    private BigDecimal dailyTotalTransactionNonInstant;
}
