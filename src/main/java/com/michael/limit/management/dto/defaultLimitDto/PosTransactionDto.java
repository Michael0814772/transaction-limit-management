package com.michael.limit.management.dto.defaultLimitDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PosTransactionDto {

    private BigDecimal posPerTransaction;

    private BigDecimal posDailyTransaction;
}
