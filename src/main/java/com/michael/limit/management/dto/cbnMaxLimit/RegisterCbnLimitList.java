package com.michael.limit.management.dto.cbnMaxLimit;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.NumberFormat;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Data
@RequiredArgsConstructor
@Validated
public class RegisterCbnLimitList {

    @NotNull
    @NumberFormat
    private int kycLevel;

    @NotNull
    @NotEmpty
    private String cifType; //either RET or CORP

    @NotNull
    @NotEmpty
    private String transferType; //either INSTANT or NON-INSTANT

    @NotNull
    @NotEmpty
    private String productType;

    @NotNull
    @Digits(integer = 12, fraction = 2)
    private BigDecimal globalDailyLimit;

    @NotNull
    @Digits(integer = 12, fraction = 2)
    private BigDecimal globalPerTransaction;

    public void setCifType(String cifType) {
        this.cifType = cifType.toUpperCase();
    }

    public void setTransferType(String transferType) {
        this.transferType = transferType.toUpperCase().trim();
    }

    public void setProductType(String productType) {
        this.productType = productType.toUpperCase().trim();
    }
}
