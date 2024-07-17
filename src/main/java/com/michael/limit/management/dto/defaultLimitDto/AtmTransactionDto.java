package com.michael.limit.management.dto.defaultLimitDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AtmTransactionDto {

    private BigDecimal atmPerTransaction = BigDecimal.valueOf(-1);

    private BigDecimal atmDailyTransaction = BigDecimal.valueOf(-1);
}
