package com.michael.limit.management.dto.createAccountLimit;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Data
@RequiredArgsConstructor
@Validated
public class UpdateGlobalLimit {

    @NotNull
    @Digits(integer = 40,fraction = 2)
    private BigDecimal totalDailyLimit;

    @NotNull
    @Digits(integer = 40,fraction = 2)
    private BigDecimal perTransactionLimit;
}
