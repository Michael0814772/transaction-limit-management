package com.michael.limit.management.dto.defaultLimitDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MobileTransactionDto {

    private BigDecimal mobilePerTransaction;

    private BigDecimal mobileDailyTransaction;
}
