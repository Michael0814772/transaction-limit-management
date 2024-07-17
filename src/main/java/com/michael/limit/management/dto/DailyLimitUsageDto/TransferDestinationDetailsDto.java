package com.michael.limit.management.dto.DailyLimitUsageDto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Data
@RequiredArgsConstructor
@Validated
public class TransferDestinationDetailsDto {

    @NotNull
    @NotEmpty
    private String destination;

    @Digits(integer = 9, fraction = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    public void setDestination(String destination) {
        this.destination = destination.toUpperCase().trim();
    }
}
