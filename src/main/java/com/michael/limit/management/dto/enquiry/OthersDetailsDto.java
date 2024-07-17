package com.michael.limit.management.dto.enquiry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OthersDetailsDto {

    private BigDecimal maxAmount;

    private BigDecimal amountRemaining = BigDecimal.ZERO;

    private BigDecimal othersLimitPerTransaction;
}