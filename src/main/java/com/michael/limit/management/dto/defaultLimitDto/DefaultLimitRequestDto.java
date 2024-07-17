package com.michael.limit.management.dto.defaultLimitDto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Data
@RequiredArgsConstructor
@Validated
public class DefaultLimitRequestDto {

    private int kycLevel;

    @NotNull
    @NotEmpty
    private String cifType;

    @NotNull
    @NotEmpty
    private String transferType; //either INSTANT or NON-INSTANT

    @NotNull
    @NotEmpty
    private String productType;

    private BigDecimal globalPerTransaction;

    private BigDecimal globalDailyTransaction;

    private BigDecimal nipPerTransaction;

    private BigDecimal nipDailyTransaction;

    private BigDecimal ussdPerTransaction;

    private BigDecimal ussdDailyTransaction;

    private BigDecimal bankPerTransaction;

    private BigDecimal bankDailyTransaction;

    private BigDecimal internetPerTransaction;

    private BigDecimal internetDailyTransaction;

    private BigDecimal mobilePerTransaction;

    private BigDecimal mobileDailyTransaction;

    private BigDecimal posPerTransaction;

    private BigDecimal posDailyTransaction;

    private BigDecimal atmPerTransaction;

    private BigDecimal atmDailyTransaction;

    private BigDecimal vendorPerTransaction;

    private BigDecimal vendorDailyTransaction;

    private BigDecimal thirdPerTransaction;

    private BigDecimal thirdDailyTransaction;

    private BigDecimal othersPerTransaction;

    private BigDecimal othersDailyTransaction;

    public void setCifType(String cifType) {
        this.cifType = cifType.toUpperCase();
    }

    public void setTransferType(String transferType) {
        this.transferType = transferType.toUpperCase();
    }
}
